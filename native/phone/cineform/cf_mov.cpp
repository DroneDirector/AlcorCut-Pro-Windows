// CineForm export: frames are encoded with the open-source CineForm SDK (Apache 2.0 / MIT)
// and written with their sound into a QuickTime .mov. Own code.
#include "cf_mov.h"

#include <cstring>
#include <cstdlib>
#include <vector>
#include <thread>
#include <unistd.h>

#include "CFHDEncoder.h"
#include "CFHDTypes.h"
#include "CFHDError.h"

namespace {

struct Buf {
    std::vector<uint8_t> b;
    void u8(uint32_t v) { b.push_back((uint8_t)v); }
    void u16(uint32_t v) { u8(v >> 8); u8(v); }
    void u32(uint32_t v) { u16(v >> 16); u16(v); }
    void u64(uint64_t v) { u32((uint32_t)(v >> 32)); u32((uint32_t)v); }
    void fourcc(const char *t) { for (int i = 0; i < 4; i++) u8((uint8_t)t[i]); }
    void zeros(int n) { b.insert(b.end(), n, 0); }
    void bytes(const std::vector<uint8_t> &o) { b.insert(b.end(), o.begin(), o.end()); }
    void pascal(const char *s, int field) {
        int n = (int)strlen(s); if (n > field - 1) n = field - 1;
        u8(n); for (int i = 0; i < n; i++) u8((uint8_t)s[i]); zeros(field - 1 - n);
    }
};

std::vector<uint8_t> box(const char *type, std::initializer_list<std::vector<uint8_t>> parts) {
    Buf o; size_t n = 8; for (auto &p : parts) n += p.size();
    o.u32((uint32_t)n); o.fourcc(type);
    for (auto &p : parts) o.bytes(p);
    return o.b;
}
std::vector<uint8_t> box(const char *type, const std::vector<uint8_t> &body) { return box(type, {body}); }

void matrix(Buf &o) {
    o.u32(0x10000); o.u32(0); o.u32(0);
    o.u32(0); o.u32(0x10000); o.u32(0);
    o.u32(0); o.u32(0); o.u32(0x40000000);
}

}  // namespace

struct CfMov {
    int fd = -1;
    int width = 0, height = 0;
    uint32_t tsNum = 30, tsDen = 1;  // media timescale = tsNum, one frame = tsDen
    int rate = 0, channels = 0;
    CFHD_EncoderPoolRef pool = nullptr;
    int queue = 0;
    std::vector<std::vector<uint8_t>> frames;  // YUY2 input buffers, one per job in flight
    uint32_t submitted = 0, collected = 0;
    uint64_t pos = 0, mdatAt = 0;
    std::vector<uint64_t> vOff; std::vector<uint32_t> vSize;
    std::vector<uint64_t> aOff; std::vector<uint32_t> aFrames;
    std::vector<uint8_t> pcm;  // waiting to be written (16-bit little-endian, interleaved)
    uint64_t audioFrames = 0;
    bool failed = false;

    bool write(const void *p, size_t n) {
        const uint8_t *q = (const uint8_t *)p;
        while (n > 0) {
            ssize_t k = pwrite(fd, q, n, (off_t)pos);
            if (k <= 0) { failed = true; return false; }
            q += k; n -= (size_t)k; pos += (uint64_t)k;
        }
        return true;
    }
    bool writeAt(uint64_t at, const void *p, size_t n) {
        const uint8_t *q = (const uint8_t *)p;
        while (n > 0) {
            ssize_t k = pwrite(fd, q, n, (off_t)at);
            if (k <= 0) { failed = true; return false; }
            q += k; n -= (size_t)k; at += (uint64_t)k;
        }
        return true;
    }

    /** Takes the next finished frame from the encoders and writes it. */
    bool collectOne() {
        uint32_t number = 0; CFHD_SampleBufferRef sb = nullptr;
        if (CFHD_WaitForSample(pool, &number, &sb) != CFHD_ERROR_OKAY || !sb) { failed = true; return false; }
        void *data = nullptr; size_t size = 0;
        bool ok = CFHD_GetEncodedSample(sb, &data, &size) == CFHD_ERROR_OKAY && data && size > 0;
        if (ok) { vOff.push_back(pos); vSize.push_back((uint32_t)size); ok = write(data, size); }
        CFHD_ReleaseSampleBuffer(pool, sb);
        collected++;
        if (!ok) failed = true;
        return ok;
    }

    void flushAudio(bool all) {
        if (channels <= 0) return;
        size_t frameBytes = (size_t)channels * 2;
        size_t frames = pcm.size() / frameBytes;
        // Chunks of about half a second, between the video frames.
        size_t chunk = (size_t)rate / 2;
        while (frames > 0 && (all || frames >= chunk)) {
            size_t n = all ? frames : chunk;
            if (n > frames) n = frames;
            aOff.push_back(pos); aFrames.push_back((uint32_t)n);
            write(pcm.data(), n * frameBytes);
            pcm.erase(pcm.begin(), pcm.begin() + (long)(n * frameBytes));
            frames -= n;
            if (!all) break;
        }
    }

    std::vector<uint8_t> moov() {
        const uint32_t movieTs = 1000;
        uint64_t vDurMedia = (uint64_t)vSize.size() * tsDen;
        uint64_t vDurMovie = tsNum ? vDurMedia * movieTs / tsNum : 0;
        uint64_t aDurMovie = rate ? audioFrames * movieTs / (uint64_t)rate : 0;
        uint64_t dur = vDurMovie > aDurMovie ? vDurMovie : aDurMovie;
        bool audio = channels > 0 && !aFrames.empty();

        Buf mvhd; mvhd.u32(0); mvhd.u32(0); mvhd.u32(0); mvhd.u32(movieTs); mvhd.u32((uint32_t)dur);
        mvhd.u32(0x10000); mvhd.u16(0x100); mvhd.zeros(10); matrix(mvhd); mvhd.zeros(24); mvhd.u32(audio ? 3 : 2);

        auto dataHandler = [] {
            Buf h; h.u32(0); h.fourcc("dhlr"); h.fourcc("alis"); h.zeros(12); h.pascal("DataHandler", 12);
            return box("hdlr", h.b);
        };
        auto dinf = [] {
            Buf d; d.u32(0); d.u32(1);
            Buf a; a.u32(1);
            return box("dinf", box("dref", {d.b, box("alis", a.b)}));
        };
        auto co64 = [](const std::vector<uint64_t> &off) {
            Buf c; c.u32(0); c.u32((uint32_t)off.size()); for (auto o : off) c.u64(o); return box("co64", c.b);
        };

        // ---- video
        Buf tkhd; tkhd.u32(0x0000000F); tkhd.u32(0); tkhd.u32(0); tkhd.u32(1); tkhd.u32(0); tkhd.u32((uint32_t)vDurMovie);
        tkhd.zeros(8); tkhd.u16(0); tkhd.u16(0); tkhd.u16(0); tkhd.u16(0); matrix(tkhd); tkhd.u32((uint32_t)width << 16); tkhd.u32((uint32_t)height << 16);
        Buf mdhd; mdhd.u32(0); mdhd.u32(0); mdhd.u32(0); mdhd.u32(tsNum); mdhd.u32((uint32_t)vDurMedia); mdhd.u16(0x55C4); mdhd.u16(0);
        Buf hdlr; hdlr.u32(0); hdlr.fourcc("mhlr"); hdlr.fourcc("vide"); hdlr.zeros(12); hdlr.pascal("VideoHandler", 13);
        Buf vmhd; vmhd.u32(1); vmhd.u16(0x40); vmhd.u16(0x8000); vmhd.u16(0x8000); vmhd.u16(0x8000);
        Buf entry; entry.zeros(6); entry.u16(1); entry.u16(0); entry.u16(0); entry.fourcc("appl"); entry.u32(0); entry.u32(0x200);
        entry.u16((uint32_t)width); entry.u16((uint32_t)height); entry.u32(72u << 16); entry.u32(72u << 16); entry.u32(0); entry.u16(1);
        entry.pascal("CineForm HD", 32); entry.u16(24); entry.u16(0xFFFF);
        // Progressive, BT.709 colour.
        Buf fiel; fiel.u8(1); fiel.u8(0);
        Buf colr; colr.fourcc("nclc"); colr.u16(1); colr.u16(1); colr.u16(1);
        entry.bytes(box("fiel", fiel.b)); entry.bytes(box("colr", colr.b));
        Buf stsd; stsd.u32(0); stsd.u32(1); stsd.bytes(box("CFHD", entry.b));
        Buf stts; stts.u32(0); stts.u32(1); stts.u32((uint32_t)vSize.size()); stts.u32(tsDen);
        Buf stsc; stsc.u32(0); stsc.u32(1); stsc.u32(1); stsc.u32(1); stsc.u32(1);
        Buf stsz; stsz.u32(0); stsz.u32(0); stsz.u32((uint32_t)vSize.size()); for (auto s : vSize) stsz.u32(s);
        auto vstbl = box("stbl", {box("stsd", stsd.b), box("stts", stts.b), box("stsc", stsc.b), box("stsz", stsz.b), co64(vOff)});
        auto vtrak = box("trak", {box("tkhd", tkhd.b), box("mdia", {box("mdhd", mdhd.b), box("hdlr", hdlr.b),
            box("minf", {box("vmhd", vmhd.b), dataHandler(), dinf(), vstbl})})});

        if (!audio) return box("moov", {box("mvhd", mvhd.b), vtrak});

        // ---- sound: 16-bit PCM (little-endian 'sowt')
        Buf atkhd; atkhd.u32(0x0000000F); atkhd.u32(0); atkhd.u32(0); atkhd.u32(2); atkhd.u32(0); atkhd.u32((uint32_t)aDurMovie);
        atkhd.zeros(8); atkhd.u16(0); atkhd.u16(1); atkhd.u16(0x100); atkhd.u16(0); matrix(atkhd); atkhd.u32(0); atkhd.u32(0);
        Buf amdhd; amdhd.u32(0); amdhd.u32(0); amdhd.u32(0); amdhd.u32((uint32_t)rate); amdhd.u32((uint32_t)audioFrames); amdhd.u16(0x55C4); amdhd.u16(0);
        Buf ahdlr; ahdlr.u32(0); ahdlr.fourcc("mhlr"); ahdlr.fourcc("soun"); ahdlr.zeros(12); ahdlr.pascal("SoundHandler", 13);
        Buf smhd; smhd.u32(0); smhd.u16(0); smhd.u16(0);
        Buf aentry; aentry.zeros(6); aentry.u16(1); aentry.u16(0); aentry.u16(0); aentry.u32(0);
        aentry.u16((uint32_t)channels); aentry.u16(16); aentry.u16(0); aentry.u16(0); aentry.u32((uint32_t)rate << 16);
        Buf astsd; astsd.u32(0); astsd.u32(1); astsd.bytes(box("sowt", aentry.b));
        Buf astts; astts.u32(0); astts.u32(1); astts.u32((uint32_t)audioFrames); astts.u32(1);
        // Runs of chunks with the same number of frames.
        Buf astsc; astsc.u32(0);
        std::vector<std::pair<uint32_t, uint32_t>> runs;
        for (size_t i = 0; i < aFrames.size(); i++) if (runs.empty() || runs.back().second != aFrames[i]) runs.push_back({(uint32_t)i + 1, aFrames[i]});
        astsc.u32((uint32_t)runs.size()); for (auto &r : runs) { astsc.u32(r.first); astsc.u32(r.second); astsc.u32(1); }
        Buf astsz; astsz.u32(0); astsz.u32(1); astsz.u32((uint32_t)audioFrames);
        auto astbl = box("stbl", {box("stsd", astsd.b), box("stts", astts.b), box("stsc", astsc.b), box("stsz", astsz.b), co64(aOff)});
        auto atrak = box("trak", {box("tkhd", atkhd.b), box("mdia", {box("mdhd", amdhd.b), box("hdlr", ahdlr.b),
            box("minf", {box("smhd", smhd.b), dataHandler(), dinf(), astbl})})});
        return box("moov", {box("mvhd", mvhd.b), vtrak, atrak});
    }
};

static void frameRate(double fps, uint32_t &num, uint32_t &den) {
    struct R { double f; uint32_t n, d; } known[] = {
        {23.976, 24000, 1001}, {29.97, 30000, 1001}, {59.94, 60000, 1001}, {47.952, 48000, 1001}, {119.88, 120000, 1001},
    };
    for (auto &k : known) if (fps > k.f - 0.01 && fps < k.f + 0.01) { num = k.n; den = k.d; return; }
    long r = lround(fps * 1000.0);
    if (r % 1000 == 0) { num = (uint32_t)(r / 1000); den = 1; } else { num = (uint32_t)r; den = 1000; }
    if (num == 0) { num = 30; den = 1; }
}

CfMov *cf_open(int fd, int width, int height, double fps, int quality, int rate, int channels, int threads) {
    if (fd < 0 || width < 16 || height < 16 || (width % 8) != 0) return nullptr;
    CfMov *m = new CfMov();
    m->fd = fd; m->width = width; m->height = height;
    frameRate(fps, m->tsNum, m->tsDen);
    m->rate = rate; m->channels = (rate > 0) ? channels : 0;
    int n = threads > 0 ? threads : (int)std::thread::hardware_concurrency();
    if (n < 1) n = 1; if (n > 8) n = 8;
    m->queue = n * 2;
    CFHD_EncodingQuality q = quality <= 0 ? CFHD_ENCODING_QUALITY_MEDIUM : quality == 1 ? CFHD_ENCODING_QUALITY_HIGH
        : quality == 2 ? CFHD_ENCODING_QUALITY_FILMSCAN1 : CFHD_ENCODING_QUALITY_FILMSCAN2;
    if (CFHD_CreateEncoderPool(&m->pool, n, m->queue, nullptr) != CFHD_ERROR_OKAY ||
        CFHD_PrepareEncoderPool(m->pool, (uint_least16_t)width, (uint_least16_t)height, CFHD_PIXEL_FORMAT_YUY2,
                                CFHD_ENCODED_FORMAT_YUV_422, CFHD_ENCODING_FLAGS_NONE, q) != CFHD_ERROR_OKAY ||
        CFHD_StartEncoderPool(m->pool) != CFHD_ERROR_OKAY) {
        if (m->pool) CFHD_ReleaseEncoderPool(m->pool);
        delete m; return nullptr;
    }
    m->frames.resize((size_t)m->queue + 1);
    for (auto &f : m->frames) f.resize((size_t)width * height * 2);
    // ftyp + wide + mdat (64-bit size, filled in at the end)
    Buf h; h.u32(20); h.fourcc("ftyp"); h.fourcc("qt  "); h.u32(0x20050300); h.fourcc("qt  ");
    h.u32(8); h.fourcc("wide");
    m->write(h.b.data(), h.b.size());
    m->mdatAt = m->pos;
    Buf md; md.u32(1); md.fourcc("mdat"); md.u64(0);
    m->write(md.b.data(), md.b.size());
    return m;
}

uint8_t *cf_frame_buffer(CfMov *m) {
    if (!m || m->failed) return nullptr;
    // A buffer whose job has finished: wait for the oldest one first when all are in use.
    while (m->submitted - m->collected >= (uint32_t)m->queue) if (!m->collectOne()) return nullptr;
    return m->frames[m->submitted % m->frames.size()].data();
}

bool cf_submit(CfMov *m) {
    if (!m || m->failed) return false;
    uint8_t *f = m->frames[m->submitted % m->frames.size()].data();
    if (CFHD_EncodeAsyncSample(m->pool, m->submitted, f, (intptr_t)m->width * 2, nullptr) != CFHD_ERROR_OKAY) { m->failed = true; return false; }
    m->submitted++;
    m->flushAudio(false);
    return true;
}

bool cf_audio(CfMov *m, const int16_t *pcm, int frames) {
    if (!m || m->failed || m->channels <= 0 || frames <= 0) return m && !m->failed;
    const uint8_t *p = (const uint8_t *)pcm;
    size_t n = (size_t)frames * m->channels * 2;
    // Stored little-endian ('sowt').
    size_t at = m->pcm.size();
    m->pcm.resize(at + n);
    for (size_t i = 0; i < (size_t)frames * m->channels; i++) {
        int16_t v = pcm[i];
        m->pcm[at + i * 2] = (uint8_t)(v & 0xFF); m->pcm[at + i * 2 + 1] = (uint8_t)((v >> 8) & 0xFF);
    }
    (void)p;
    m->audioFrames += (uint64_t)frames;
    return true;
}

bool cf_close(CfMov *m, bool keep) {
    if (!m) return false;
    bool ok = !m->failed;
    while (ok && m->collected < m->submitted) ok = m->collectOne();
    if (m->pool) { CFHD_StopEncoderPool(m->pool); CFHD_ReleaseEncoderPool(m->pool); m->pool = nullptr; }
    if (ok && keep) {
        m->flushAudio(true);
        uint64_t mdatEnd = m->pos;
        Buf sz; sz.u64(mdatEnd - m->mdatAt);
        ok = m->writeAt(m->mdatAt + 8, sz.b.data(), 8);
        auto mv = m->moov();
        ok = ok && m->write(mv.data(), mv.size()) && !m->failed;
        if (ok) ftruncate(m->fd, (off_t)m->pos);
    }
    if (!ok || !keep) ok = false;
    delete m;
    return ok;
}

// ------------------------------------------------------------------ pixels

static inline uint8_t clampU8(int v) { return (uint8_t)(v < 0 ? 0 : v > 255 ? 255 : v); }

void cf_yuv420_to_yuy2(uint8_t *dst, int width, int height,
                       const uint8_t *y, int yRow, const uint8_t *u, const uint8_t *v, int cRow, int cPixel) {
    for (int r = 0; r < height; r++) {
        const uint8_t *yr = y + (size_t)r * yRow;
        const uint8_t *ur = u + (size_t)(r / 2) * cRow;
        const uint8_t *vr = v + (size_t)(r / 2) * cRow;
        uint8_t *o = dst + (size_t)r * width * 2;
        for (int c = 0; c < width; c += 2) {
            int ci = (c / 2) * cPixel;
            o[0] = yr[c]; o[1] = ur[ci]; o[2] = yr[c + 1]; o[3] = vr[ci];
            o += 4;
        }
    }
}

void cf_rgba_to_yuy2(uint8_t *dst, int width, int height, const uint8_t *rgba, int row) {
    // BT.709, video range.
    for (int r = 0; r < height; r++) {
        const uint8_t *p = rgba + (size_t)r * row;
        uint8_t *o = dst + (size_t)r * width * 2;
        for (int c = 0; c < width; c += 2) {
            int R0 = p[0], G0 = p[1], B0 = p[2], R1 = p[4], G1 = p[5], B1 = p[6];
            int Y0 = (47 * R0 + 157 * G0 + 16 * B0 + 4096 + 128) >> 8;
            int Y1 = (47 * R1 + 157 * G1 + 16 * B1 + 4096 + 128) >> 8;
            int R = (R0 + R1) >> 1, G = (G0 + G1) >> 1, B = (B0 + B1) >> 1;
            int U = ((-26 * R - 87 * G + 112 * B) >> 8) + 128;
            int V = ((112 * R - 102 * G - 10 * B) >> 8) + 128;
            o[0] = clampU8(Y0); o[1] = clampU8(U); o[2] = clampU8(Y1); o[3] = clampU8(V);
            o += 4; p += 8;
        }
    }
}
