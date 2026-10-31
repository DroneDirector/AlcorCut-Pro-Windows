// AlcorCut Pro — dense optical flow and flow-based frame synthesis (morph cut).
// Written for AlcorCut Pro; no third-party code.
//
// Flow: coarse-to-fine. On every pyramid level, small overlapping patches find their motion by
// inverse-compositional Lucas–Kanade search (starting from the coarser level), the patch motions are
// blended into a dense field weighted by how well each one fits, and a variational refinement
// (brightness + gradient constancy, robust smoothness, SOR) polishes it.
// Synthesis: the A→B motion is forward-projected to the in-between time, holes are filled, and
// every pixel is taken from A and B along that motion, using only the frame that actually sees
// it where something is covered or uncovered.
#include "morph.h"

#include <algorithm>
#include <cmath>
#include <cstring>

namespace morph {

float Img::bilinear(float x, float y) const {
    if (x < 0) x = 0; else if (x > w - 1) x = (float)(w - 1);
    if (y < 0) y = 0; else if (y > h - 1) y = (float)(h - 1);
    int x0 = (int)x, y0 = (int)y;
    int x1 = std::min(x0 + 1, w - 1), y1 = std::min(y0 + 1, h - 1);
    float fx = x - x0, fy = y - y0;
    const float* r0 = &d[(size_t)y0 * w];
    const float* r1 = &d[(size_t)y1 * w];
    float a = r0[x0] + (r0[x1] - r0[x0]) * fx;
    float b = r1[x0] + (r1[x1] - r1[x0]) * fx;
    return a + (b - a) * fy;
}

Img lumaFromRgba(const uint8_t* p, int w, int h) {
    Img o(w, h);
    for (size_t i = 0, n = (size_t)w * h; i < n; i++)
        o.d[i] = 0.299f * p[i * 4] + 0.587f * p[i * 4 + 1] + 0.114f * p[i * 4 + 2];
    return o;
}

// ---------------------------------------------------------------- helpers

static Img blur(const Img& s, float sigma) {
    int r = std::max(1, (int)std::ceil(sigma * 2.5f));
    std::vector<float> k(2 * r + 1);
    float sum = 0;
    for (int i = -r; i <= r; i++) { k[i + r] = std::exp(-(i * i) / (2 * sigma * sigma)); sum += k[i + r]; }
    for (auto& v : k) v /= sum;
    Img t(s.w, s.h), o(s.w, s.h);
    for (int y = 0; y < s.h; y++)
        for (int x = 0; x < s.w; x++) {
            float a = 0;
            for (int i = -r; i <= r; i++) a += k[i + r] * s.clampAt(x + i, y);
            t.at(x, y) = a;
        }
    for (int y = 0; y < s.h; y++)
        for (int x = 0; x < s.w; x++) {
            float a = 0;
            for (int i = -r; i <= r; i++) a += k[i + r] * t.clampAt(x, y + i);
            o.at(x, y) = a;
        }
    return o;
}

static Img downsample(const Img& s) {
    Img b = blur(s, 0.85f);
    int w = std::max(1, (s.w + 1) / 2), h = std::max(1, (s.h + 1) / 2);
    Img o(w, h);
    for (int y = 0; y < h; y++)
        for (int x = 0; x < w; x++) o.at(x, y) = b.bilinear(x * 2 + 0.5f, y * 2 + 0.5f);
    return o;
}

static void gradients(const Img& s, Img& gx, Img& gy) {
    gx = Img(s.w, s.h); gy = Img(s.w, s.h);
    for (int y = 0; y < s.h; y++)
        for (int x = 0; x < s.w; x++) {
            // 5-point derivative (less noise than central differences).
            gx.at(x, y) = (s.clampAt(x - 2, y) - 8 * s.clampAt(x - 1, y) + 8 * s.clampAt(x + 1, y) - s.clampAt(x + 2, y)) / 12.f;
            gy.at(x, y) = (s.clampAt(x, y - 2) - 8 * s.clampAt(x, y - 1) + 8 * s.clampAt(x, y + 1) - s.clampAt(x, y + 2)) / 12.f;
        }
}

static Flow upsample(const Flow& f, int w, int h) {
    Flow o(w, h);
    if (f.w == 0) return o;
    float sx = (float)f.w / w, sy = (float)f.h / h;
    Img fu(f.w, f.h), fv(f.w, f.h);
    fu.d = f.u; fv.d = f.v;
    for (int y = 0; y < h; y++)
        for (int x = 0; x < w; x++) {
            float X = (x + 0.5f) * sx - 0.5f, Y = (y + 0.5f) * sy - 0.5f;
            o.u[(size_t)y * w + x] = fu.bilinear(X, Y) / sx;
            o.v[(size_t)y * w + x] = fv.bilinear(X, Y) / sy;
        }
    return o;
}

// ---------------------------------------------------------------- patch search (one level)

// Images are luma + two colour channels (so colour edges count, not only brightness).
static constexpr int C = 3;
struct Pyr { Img c[C]; Img gx[C], gy[C]; int w = 0, h = 0; };

static void patchSearch(const Pyr& A, const Pyr& B, Flow& F, int P, int S, int iters) {
    const int W = A.w, H = A.h;
    if (W < P || H < P) return;
    std::vector<int> xs, ys;
    for (int x = 0; x + P <= W; x += S) xs.push_back(x);
    if (xs.back() + P < W) xs.push_back(W - P);
    for (int y = 0; y + P <= H; y += S) ys.push_back(y);
    if (ys.back() + P < H) ys.push_back(H - P);
    const int NX = (int)xs.size(), NY = (int)ys.size();
    const int PP = P * P;
    std::vector<float> pu((size_t)NX * NY), pv((size_t)NX * NY);
#pragma omp parallel for schedule(dynamic, 2)
    for (int j = 0; j < NY; j++) {
        std::vector<float> tmpl(C * PP), tgx(C * PP), tgy(C * PP), warped(C * PP);
        float m0[C];
        for (int i = 0; i < NX; i++) {
            const int px = xs[i], py = ys[j];
            float u = 0, v = 0;
            double hxx = 0, hxy = 0, hyy = 0;
            for (int yy = 0; yy < P; yy++)
                for (int xx = 0; xx < P; xx++) { size_t k = (size_t)(py + yy) * W + px + xx; u += F.u[k]; v += F.v[k]; }
            u /= PP; v /= PP;
            for (int c = 0; c < C; c++) {
                float m = 0, sx = 0, sy = 0;
                for (int yy = 0; yy < P; yy++)
                    for (int xx = 0; xx < P; xx++) {
                        size_t k = (size_t)(py + yy) * W + px + xx;
                        int q = c * PP + yy * P + xx;
                        tmpl[q] = A.c[c].d[k]; tgx[q] = A.gx[c].d[k]; tgy[q] = A.gy[c].d[k];
                        m += tmpl[q]; sx += tgx[q]; sy += tgy[q];
                    }
                m0[c] = m / PP; sx /= PP; sy /= PP;
                for (int q = c * PP; q < (c + 1) * PP; q++) {
                    tgx[q] -= sx; tgy[q] -= sy;
                    hxx += tgx[q] * tgx[q]; hxy += tgx[q] * tgy[q]; hyy += tgy[q] * tgy[q];
                }
            }
            auto sampleAll = [&](float cu, float cv, float m1[C]) {
                for (int c = 0; c < C; c++) {
                    float m = 0;
                    for (int yy = 0; yy < P; yy++)
                        for (int xx = 0; xx < P; xx++) { float w1 = B.c[c].bilinear(px + xx + cu, py + yy + cv); warped[c * PP + yy * P + xx] = w1; m += w1; }
                    m1[c] = m / PP;
                }
            };
            auto cost = [&](float cu, float cv) {
                float m1[C]; sampleAll(cu, cv, m1);
                float cs = 0;
                for (int c = 0; c < C; c++)
                    for (int q = 0; q < PP; q++) { float r = (warped[c * PP + q] - m1[c]) - (tmpl[c * PP + q] - m0[c]); cs += r * r; }
                return cs;
            };
            // Also try the motion of the neighbourhood (thin objects can be lost on coarse levels):
            // the start that fits best wins (like PatchMatch propagation).
            float bestC = cost(u, v);
            auto tryCand = [&](float cu, float cv) {
                if (!(std::isfinite(cu) && std::isfinite(cv))) return;
                if (std::fabs(cu - u) < 0.25f && std::fabs(cv - v) < 0.25f) return;
                float cc = cost(cu, cv);
                if (cc < bestC) { bestC = cc; u = cu; v = cv; }
            };
            {
                const float cu0 = u, cv0 = v;
                (void)cu0; (void)cv0;
                const int cx = px + P / 2, cy = py + P / 2;
                const int offs[8][2] = {{-P, 0}, {P, 0}, {0, -P}, {0, P}, {-2 * P, 0}, {2 * P, 0}, {0, -2 * P}, {0, 2 * P}};
                for (auto& o : offs) {
                    int qx = cx + o[0], qy = cy + o[1];
                    if (qx < 0 || qy < 0 || qx >= W || qy >= H) continue;
                    size_t q = (size_t)qy * W + qx;
                    tryCand(F.u[q], F.v[q]);
                }
                if (i > 0) tryCand(pu[(size_t)j * NX + i - 1], pv[(size_t)j * NX + i - 1]);
            }
            const float u0 = u, v0 = v;
            const float c0 = bestC;
            double det = hxx * hyy - hxy * hxy;
            if (det > 1e-6 && det > 1e-3 * (hxx + hyy) * (hxx + hyy)) {
                const double ixx = hyy / det, ixy = -hxy / det, iyy = hxx / det;
                for (int it = 0; it < iters; it++) {
                    float m1[C]; sampleAll(u, v, m1);
                    double bx = 0, by = 0;
                    for (int c = 0; c < C; c++)
                        for (int q = 0; q < PP; q++) {
                            int z = c * PP + q;
                            float e = (warped[z] - m1[c]) - (tmpl[z] - m0[c]);
                            bx += tgx[z] * e; by += tgy[z] * e;
                        }
                    float du = (float)(ixx * bx + ixy * by), dv = (float)(ixy * bx + iyy * by);
                    // Inverse compositional: the template moves by +d, so the warp moves by −d.
                    u -= du; v -= dv;
                    if (du * du + dv * dv < 1e-4f) break;
                }
                if (!(std::isfinite(u) && std::isfinite(v)) || cost(u, v) > c0 || std::fabs(u - u0) > P * 2 || std::fabs(v - v0) > P * 2) { u = u0; v = v0; }
            }
            pu[(size_t)j * NX + i] = u; pv[(size_t)j * NX + i] = v;
        }
    }
    // Densify: each pixel blends the patches covering it, weighted by how well each one fits there.
    std::vector<float> su((size_t)W * H, 0.f), sv((size_t)W * H, 0.f), sw((size_t)W * H, 0.f);
    for (int j = 0; j < NY; j++)
        for (int i = 0; i < NX; i++) {
            const int px = xs[i], py = ys[j];
            const float u = pu[(size_t)j * NX + i], v = pv[(size_t)j * NX + i];
            for (int yy = 0; yy < P; yy++)
                for (int xx = 0; xx < P; xx++) {
                    int x = px + xx, y = py + yy;
                    size_t k = (size_t)y * W + x;
                    float e = 0;
                    for (int c = 0; c < C; c++) e += std::fabs(B.c[c].bilinear(x + u, y + v) - A.c[c].d[k]);
                    float wgt = 1.f / std::max(1.f, e);
                    su[k] += wgt * u; sv[k] += wgt * v; sw[k] += wgt;
                }
        }
    for (size_t k = 0; k < (size_t)W * H; k++)
        if (sw[k] > 0) { F.u[k] = su[k] / sw[k]; F.v[k] = sv[k] / sw[k]; }
}

// ---------------------------------------------------------------- variational refinement (one level)

static void refine(const Pyr& A, const Pyr& B, Flow& F, int outer, int inner, float alpha, float gamma, float delta) {
    const int W = A.w, H = A.h;
    const size_t N = (size_t)W * H;
    // Gradient constancy on luma (second derivatives of both frames).
    Img g1xx, g1xy, g1yx, g1yy; gradients(B.gx[0], g1xx, g1xy); gradients(B.gy[0], g1yx, g1yy);
    Img g0xx, g0xy, g0yx, g0yy; gradients(A.gx[0], g0xx, g0xy); gradients(A.gy[0], g0yx, g0yy);
    std::vector<float> du(N, 0.f), dv(N, 0.f);
    std::vector<float> a11(N), a12(N), a22(N), b1(N), b2(N), psS(N);
    // Edge-aware smoothness: motion may change where the picture has an edge (an object's outline).
    std::vector<float> edge(N);
    for (size_t k = 0; k < N; k++) {
        float g2 = 0;
        for (int c = 0; c < C; c++) g2 += A.gx[c].d[k] * A.gx[c].d[k] + A.gy[c].d[k] * A.gy[c].d[k];
        edge[k] = std::max(0.08f, std::exp(-g2 / (2.f * 9.f * 9.f)));
    }
    const float eps2 = 1e-3f * 1e-3f, epsD = 1e-2f;
    std::vector<float> Ix(C * N), Iy(C * N), Iz(C * N), Ixx(N), Ixy(N), Iyy(N), Ixz(N), Iyz(N);
    for (int o = 0; o < outer; o++) {
        std::fill(du.begin(), du.end(), 0.f); std::fill(dv.begin(), dv.end(), 0.f);
        // Linearise the data terms around the current flow (warped second image).
#pragma omp parallel for schedule(static)
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                size_t k = (size_t)y * W + x;
                float X = x + F.u[k], Y = y + F.v[k];
                bool out = X < 0 || Y < 0 || X > W - 1 || Y > H - 1;
                for (int c = 0; c < C; c++) {
                    size_t q = c * N + k;
                    Ix[q] = out ? 0.f : 0.5f * (B.gx[c].bilinear(X, Y) + A.gx[c].d[k]);
                    Iy[q] = out ? 0.f : 0.5f * (B.gy[c].bilinear(X, Y) + A.gy[c].d[k]);
                    Iz[q] = out ? 0.f : B.c[c].bilinear(X, Y) - A.c[c].d[k];
                }
                Ixx[k] = out ? 0.f : 0.5f * (g1xx.bilinear(X, Y) + g0xx.d[k]);
                Ixy[k] = out ? 0.f : 0.5f * (g1xy.bilinear(X, Y) + g0xy.d[k]);
                Iyy[k] = out ? 0.f : 0.5f * (g1yy.bilinear(X, Y) + g0yy.d[k]);
                Ixz[k] = out ? 0.f : B.gx[0].bilinear(X, Y) - A.gx[0].d[k];
                Iyz[k] = out ? 0.f : B.gy[0].bilinear(X, Y) - A.gy[0].d[k];
            }
        for (int it = 0; it < inner; it++) {
#pragma omp parallel for schedule(static)
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) {
                    size_t k = (size_t)y * W + x;
                    float s11 = 0, s12 = 0, s22 = 0, sb1 = 0, sb2 = 0;
                    for (int c = 0; c < C; c++) {
                        size_t q = c * N + k;
                        float r = Iz[q] + Ix[q] * du[k] + Iy[q] * dv[k];
                        float pd = delta / std::sqrt(r * r + epsD);
                        s11 += pd * Ix[q] * Ix[q]; s12 += pd * Ix[q] * Iy[q]; s22 += pd * Iy[q] * Iy[q];
                        sb1 += pd * Ix[q] * Iz[q]; sb2 += pd * Iy[q] * Iz[q];
                    }
                    float rx = Ixz[k] + Ixx[k] * du[k] + Ixy[k] * dv[k];
                    float ry = Iyz[k] + Ixy[k] * du[k] + Iyy[k] * dv[k];
                    float pg = gamma / std::sqrt(rx * rx + ry * ry + epsD);
                    a11[k] = s11 + pg * (Ixx[k] * Ixx[k] + Ixy[k] * Ixy[k]);
                    a12[k] = s12 + pg * (Ixx[k] * Ixy[k] + Ixy[k] * Iyy[k]);
                    a22[k] = s22 + pg * (Ixy[k] * Ixy[k] + Iyy[k] * Iyy[k]);
                    b1[k] = sb1 + pg * (Ixx[k] * Ixz[k] + Ixy[k] * Iyz[k]);
                    b2[k] = sb2 + pg * (Ixy[k] * Ixz[k] + Iyy[k] * Iyz[k]);
                    float ux = (x + 1 < W ? (F.u[k + 1] + du[k + 1]) - (F.u[k] + du[k]) : 0.f);
                    float uy = (y + 1 < H ? (F.u[k + W] + du[k + W]) - (F.u[k] + du[k]) : 0.f);
                    float vx = (x + 1 < W ? (F.v[k + 1] + dv[k + 1]) - (F.v[k] + dv[k]) : 0.f);
                    float vy = (y + 1 < H ? (F.v[k + W] + dv[k + W]) - (F.v[k] + dv[k]) : 0.f);
                    psS[k] = edge[k] * alpha / std::sqrt(ux * ux + uy * uy + vx * vx + vy * vy + eps2);
                }
            const float omega = 1.6f;
            // Red-black ordering, so each half-sweep can run on all cores.
            for (int sweep = 0; sweep < 6; sweep++)
#pragma omp parallel for schedule(static)
                for (int y = 0; y < H; y++)
                    for (int x = (y + sweep) & 1; x < W; x += 2) {
                        size_t k = (size_t)y * W + x;
                        float sw = 0, su = 0, sv = 0;
                        auto nb = [&](size_t n, float wgt) {
                            sw += wgt;
                            su += wgt * (F.u[n] + du[n] - F.u[k]);
                            sv += wgt * (F.v[n] + dv[n] - F.v[k]);
                        };
                        if (x > 0) nb(k - 1, 0.5f * (psS[k] + psS[k - 1]));
                        if (x + 1 < W) nb(k + 1, 0.5f * (psS[k] + psS[k + 1]));
                        if (y > 0) nb(k - W, 0.5f * (psS[k] + psS[k - W]));
                        if (y + 1 < H) nb(k + W, 0.5f * (psS[k] + psS[k + W]));
                        float nu = (su - b1[k] - a12[k] * dv[k]) / (sw + a11[k] + 1e-9f);
                        du[k] = (1 - omega) * du[k] + omega * nu;
                        float nv = (sv - b2[k] - a12[k] * du[k]) / (sw + a22[k] + 1e-9f);
                        dv[k] = (1 - omega) * dv[k] + omega * nv;
                    }
        }
        for (size_t k = 0; k < N; k++) { F.u[k] += du[k]; F.v[k] += dv[k]; }
    }
}

static void median3(Flow& F) {
    Flow o = F;
    const int W = F.w, H = F.h;
    float a[9], b[9];
    for (int y = 1; y < H - 1; y++)
        for (int x = 1; x < W - 1; x++) {
            int n = 0;
            for (int j = -1; j <= 1; j++)
                for (int i = -1; i <= 1; i++) { size_t k = (size_t)(y + j) * W + x + i; a[n] = F.u[k]; b[n] = F.v[k]; n++; }
            std::nth_element(a, a + 4, a + 9); std::nth_element(b, b + 4, b + 9);
            o.u[(size_t)y * W + x] = a[4]; o.v[(size_t)y * W + x] = b[4];
        }
    F = o;
}

/** Weighted median of the flow, guided by the picture (similar colour and nearby count more): keeps motion edges on object outlines. */
static void weightedMedian(Flow& F, const Pyr& I, int r) {
    const int W = F.w, H = F.h;
    Flow o = F;
    const int n = (2 * r + 1) * (2 * r + 1);
    const float sc = 1.f / (2.f * 8.f * 8.f), sd = 1.f / (2.f * (r * 0.7f) * (r * 0.7f));
#pragma omp parallel for schedule(static)
    for (int y = 0; y < H; y++) {
        std::vector<std::pair<float, float>> au(n), av(n);
        for (int x = 0; x < W; x++) {
            int m = 0; float tw = 0;
            for (int j = -r; j <= r; j++)
                for (int i = -r; i <= r; i++) {
                    int xi = x + i, yi = y + j;
                    if (xi < 0 || yi < 0 || xi >= W || yi >= H) continue;
                    float dc = 0;
                    for (int c = 0; c < C; c++) { float d = I.c[c].at(xi, yi) - I.c[c].at(x, y); dc += d * d; }
                    float wgt = std::exp(-dc * sc - (i * i + j * j) * sd);
                    size_t q = (size_t)yi * W + xi;
                    au[m] = {F.u[q], wgt}; av[m] = {F.v[q], wgt}; m++; tw += wgt;
                }
            auto med = [&](std::vector<std::pair<float, float>>& a) {
                std::sort(a.begin(), a.begin() + m, [](auto& p, auto& q) { return p.first < q.first; });
                float acc = 0, half = tw * 0.5f;
                for (int z = 0; z < m; z++) { acc += a[z].second; if (acc >= half) return a[z].first; }
                return a[m - 1].first;
            };
            size_t k = (size_t)y * W + x;
            o.u[k] = med(au); o.v[k] = med(av);
        }
    }
    F = o;
}

static std::vector<Pyr> pyramid(const uint8_t* p, int w, int h) {
    Pyr base; base.w = w; base.h = h;
    for (int c = 0; c < C; c++) base.c[c] = Img(w, h);
    for (size_t i = 0, n = (size_t)w * h; i < n; i++) {
        float r = p[i * 4], g = p[i * 4 + 1], b = p[i * 4 + 2];
        float y = 0.299f * r + 0.587f * g + 0.114f * b;
        base.c[0].d[i] = y;
        base.c[1].d[i] = (b - y) * 0.85f;  // colour differences, weighted a little below luma
        base.c[2].d[i] = (r - y) * 0.85f;
    }
    std::vector<Pyr> out;
    for (int c = 0; c < C; c++) base.c[c] = blur(base.c[c], 0.6f);
    out.push_back(base);
    while (std::min(out.back().w, out.back().h) >= 32) {
        Pyr n;
        for (int c = 0; c < C; c++) n.c[c] = downsample(out.back().c[c]);
        n.w = n.c[0].w; n.h = n.c[0].h;
        out.push_back(n);
    }
    for (auto& l : out) for (int c = 0; c < C; c++) gradients(l.c[c], l.gx[c], l.gy[c]);
    return out;
}

Flow computeFlow(const uint8_t* aRgba, const uint8_t* bRgba, int w, int h) {
    std::vector<Pyr> pa = pyramid(aRgba, w, h), pb = pyramid(bRgba, w, h);
    Flow F;
    for (int lv = (int)pa.size() - 1; lv >= 0; lv--) {
        F = upsample(F, pa[lv].w, pa[lv].h);
        const bool fine = lv == 0;
        patchSearch(pa[lv], pb[lv], F, 8, fine ? 3 : 4, 16);
        refine(pa[lv], pb[lv], F, fine ? 3 : 2, fine ? 4 : 3, 15.f, 6.f, 1.f);
        weightedMedian(F, pa[lv], fine ? 3 : 2);
    }
    return F;
}

// ---------------------------------------------------------------- occlusion and projection

Img occlusion(const Flow& ab, const Flow& ba, float tol) {
    const int W = ab.w, H = ab.h;
    Img fu(W, H), fv(W, H);
    fu.d = ba.u; fv.d = ba.v;
    Img o(W, H);
    for (int y = 0; y < H; y++)
        for (int x = 0; x < W; x++) {
            size_t k = (size_t)y * W + x;
            float X = x + ab.u[k], Y = y + ab.v[k];
            if (X < -0.5f || Y < -0.5f || X > W - 0.5f || Y > H - 0.5f) { o.d[k] = 1.f; continue; }
            float ex = ab.u[k] + fu.bilinear(X, Y), ey = ab.v[k] + fv.bilinear(X, Y);
            float e = std::sqrt(ex * ex + ey * ey);
            // Larger motions are allowed a bit more error.
            float t = tol + 0.05f * std::sqrt(ab.u[k] * ab.u[k] + ab.v[k] * ab.v[k]);
            o.d[k] = std::min(1.f, std::max(0.f, (e - t) / t));
        }
    return blur(o, 1.2f);
}

Flow projectFlow(const Flow& ab, const Flow& ba, const Img& A, const Img& B, const Img& occA, const Img& occB, float t) {
    const int W = ab.w, H = ab.h;
    const size_t N = (size_t)W * H;
    Flow out(W, H);
    std::vector<float> best(N, 1e30f);
    std::vector<uint8_t> has(N, 0);
    auto splat = [&](float X, float Y, float u, float v, float e) {
        int x0 = (int)std::floor(X), y0 = (int)std::floor(Y);
        for (int j = 0; j <= 1; j++)
            for (int i = 0; i <= 1; i++) {
                int xi = x0 + i, yi = y0 + j;
                if (xi < 0 || yi < 0 || xi >= W || yi >= H) continue;
                size_t q = (size_t)yi * W + xi;
                // Where two motions land on the same pixel, the better-matching one is in front.
                if (e < best[q]) { best[q] = e; out.u[q] = u; out.v[q] = v; has[q] = 1; }
            }
    };
    // From A along A→B, and from B along B→A (each sees what the other can't).
    for (int y = 0; y < H; y++)
        for (int x = 0; x < W; x++) {
            size_t k = (size_t)y * W + x;
            float u = ab.u[k], v = ab.v[k];
            splat(x + t * u, y + t * v, u, v, std::fabs(A.d[k] - B.bilinear(x + u, y + v)) + 255.f * occA.d[k]);
            if (ba.w == W) {
                float bu = ba.u[k], bv = ba.v[k];
                splat(x + (1 - t) * bu, y + (1 - t) * bv, -bu, -bv, std::fabs(B.d[k] - A.bilinear(x + bu, y + bv)) + 255.f * occB.d[k]);
            }
        }
    // Fill holes from the outside in (average of filled neighbours).
    bool any = true;
    for (int pass = 0; pass < W + H && any; pass++) {
        any = false;
        std::vector<uint8_t> nh = has;
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                size_t k = (size_t)y * W + x;
                if (has[k]) continue;
                float su = 0, sv = 0; int n = 0;
                for (int j = -1; j <= 1; j++)
                    for (int i = -1; i <= 1; i++) {
                        int xi = x + i, yi = y + j;
                        if (xi < 0 || yi < 0 || xi >= W || yi >= H) continue;
                        size_t q = (size_t)yi * W + xi;
                        if (has[q]) { su += out.u[q]; sv += out.v[q]; n++; }
                    }
                if (n > 0) { out.u[k] = su / n; out.v[k] = sv / n; nh[k] = 1; any = true; }
            }
        has.swap(nh);
    }
    median3(out);
    return out;
}

static inline void sampleRgba(const uint8_t* p, int w, int h, float x, float y, float c[4]) {
    if (x < 0) x = 0; else if (x > w - 1) x = (float)(w - 1);
    if (y < 0) y = 0; else if (y > h - 1) y = (float)(h - 1);
    int x0 = (int)x, y0 = (int)y;
    int x1 = std::min(x0 + 1, w - 1), y1 = std::min(y0 + 1, h - 1);
    float fx = x - x0, fy = y - y0;
    const uint8_t* a = p + ((size_t)y0 * w + x0) * 4;
    const uint8_t* b = p + ((size_t)y0 * w + x1) * 4;
    const uint8_t* cc = p + ((size_t)y1 * w + x0) * 4;
    const uint8_t* d = p + ((size_t)y1 * w + x1) * 4;
    for (int i = 0; i < 4; i++) {
        float top = a[i] + (b[i] - a[i]) * fx, bot = cc[i] + (d[i] - cc[i]) * fx;
        c[i] = top + (bot - top) * fy;
    }
}

void render(const uint8_t* A, const uint8_t* B, int w, int h, const Flow& mt, const Img& occA, const Img& occB, float t, uint8_t* out) {
    for (int y = 0; y < h; y++)
        for (int x = 0; x < w; x++) {
            size_t k = (size_t)y * w + x;
            float u = mt.u[k], v = mt.v[k];
            float ax = x - t * u, ay = y - t * v;
            float bx = x + (1 - t) * u, by = y + (1 - t) * v;
            float ca[4], cb[4];
            sampleRgba(A, w, h, ax, ay, ca);
            sampleRgba(B, w, h, bx, by, cb);
            float oa = occA.bilinear(ax, ay), ob = occB.bilinear(bx, by);
            bool inA = ax >= -0.5f && ay >= -0.5f && ax <= w - 0.5f && ay <= h - 0.5f;
            bool inB = bx >= -0.5f && by >= -0.5f && bx <= w - 0.5f && by <= h - 0.5f;
            // A pixel A can't see in B is taken from A only, and the other way round.
            float wa = (1 - t) * (1 - ob) * (inA ? 1.f : 0.f);
            float wb = t * (1 - oa) * (inB ? 1.f : 0.f);
            if (wa + wb < 1e-4f) { wa = 1 - t; wb = t; }
            float s = 1.f / (wa + wb);
            for (int i = 0; i < 4; i++) out[k * 4 + i] = (uint8_t)std::min(255.f, std::max(0.f, (ca[i] * wa + cb[i] * wb) * s + 0.5f));
        }
}

}  // namespace morph
