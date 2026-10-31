// AlcorCut Pro — dense optical flow and flow-based frame synthesis (morph cut).
// Written for AlcorCut Pro; no third-party code.
#pragma once
#include <cstdint>
#include <vector>

namespace morph {

/** Single-channel float image. */
struct Img {
    int w = 0, h = 0;
    std::vector<float> d;
    Img() = default;
    Img(int w_, int h_, float v = 0.f) : w(w_), h(h_), d((size_t)w_ * h_, v) {}
    float& at(int x, int y) { return d[(size_t)y * w + x]; }
    float at(int x, int y) const { return d[(size_t)y * w + x]; }
    float clampAt(int x, int y) const {
        x = x < 0 ? 0 : (x >= w ? w - 1 : x);
        y = y < 0 ? 0 : (y >= h ? h - 1 : y);
        return d[(size_t)y * w + x];
    }
    float bilinear(float x, float y) const;
};

/** Dense flow: two floats (dx, dy) per pixel, in pixels of the input size. */
struct Flow {
    int w = 0, h = 0;
    std::vector<float> u, v;
    Flow() = default;
    Flow(int w_, int h_) : w(w_), h(h_), u((size_t)w_ * h_, 0.f), v((size_t)w_ * h_, 0.f) {}
};

/** Luma (0..255) from RGBA8888 pixels. */
Img lumaFromRgba(const uint8_t* rgba, int w, int h);

/** Dense optical flow from [a] to [b] (a(x) ≈ b(x + flow(x))). Coarse-to-fine patch search + variational refinement. */
Flow computeFlow(const uint8_t* aRgba, const uint8_t* bRgba, int w, int h);

/** Pixels of A with no match in B (forward-backward check), softened; 1 = occluded. */
Img occlusion(const Flow& ab, const Flow& ba, float tolPx = 1.5f);

/**
 * Motion field at time t (0 = A, 1 = B): for each pixel of the in-between frame, the A→B motion that
 * passes through it (forward-projected, holes filled). The in-between pixel x comes from A at
 * x − t·m(x) and from B at x + (1 − t)·m(x).
 */
Flow projectFlow(const Flow& ab, const Flow& ba, const Img& a, const Img& b, const Img& occA, const Img& occB, float t);

/** Renders the in-between RGBA frame at time t (preview / tests). Images are RGBA8888 at the flow size. */
void render(const uint8_t* aRgba, const uint8_t* bRgba, int w, int h, const Flow& mt, const Img& occA, const Img& occB, float t, uint8_t* out);

}  // namespace morph
