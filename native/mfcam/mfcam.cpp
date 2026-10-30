// PC cameras for AlcorCut Pro on Windows: Media Foundation capture (webcams, capture cards,
// cameras in webcam mode), camera controls (exposure, focus, white balance, gain, zoom...)
// and recording to MP4 (H.264 / HEVC + AAC) with the PC's own encoders. Own code.
#include <windows.h>
#include <mfapi.h>
#include <mfidl.h>
#include <mfreadwrite.h>
#include <mferror.h>
#include <strmif.h>
#include <ks.h>
#include <ksmedia.h>
#include <codecapi.h>
#include <wmcodecdsp.h>
#include <PortableDeviceApi.h>
#include <PortableDevice.h>
#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <cstdio>

template <class T> static void release(T*& p) { if (p) { p->Release(); p = nullptr; } }

static thread_local std::string g_err;
static std::once_flag g_once;

static void com() {
    CoInitializeEx(nullptr, COINIT_MULTITHREADED);
    std::call_once(g_once, [] { MFStartup(MF_VERSION, MFSTARTUP_FULL); });
}

static bool fail(const char* what, HRESULT hr) {
    char b[160]; snprintf(b, sizeof b, "%s (0x%08lX)", what, (unsigned long)hr); g_err = b; return false;
}

static std::string utf8(const wchar_t* w) {
    if (!w) return {};
    int n = WideCharToMultiByte(CP_UTF8, 0, w, -1, nullptr, 0, nullptr, nullptr);
    std::string s(n > 0 ? n - 1 : 0, '\0');
    if (n > 1) WideCharToMultiByte(CP_UTF8, 0, w, -1, &s[0], n, nullptr, nullptr);
    return s;
}
static std::wstring wide(JNIEnv* env, jstring js) {
    const jchar* c = env->GetStringChars(js, nullptr);
    std::wstring w((const wchar_t*)c, env->GetStringLength(js));
    env->ReleaseStringChars(js, c);
    return w;
}
static jobjectArray strings(JNIEnv* env, const std::vector<std::string>& v) {
    jclass sc = env->FindClass("java/lang/String");
    jobjectArray a = env->NewObjectArray((jsize)v.size(), sc, nullptr);
    for (size_t i = 0; i < v.size(); i++) {
        jstring s = env->NewStringUTF(v[i].c_str());
        env->SetObjectArrayElement(a, (jsize)i, s);
        env->DeleteLocalRef(s);
    }
    return a;
}

static std::string fourcc(const GUID& g) {
    if (g == MFVideoFormat_RGB32) return "RGB32";
    if (g == MFVideoFormat_RGB24) return "RGB24";
    char s[5] = { (char)(g.Data1 & 0xFF), (char)((g.Data1 >> 8) & 0xFF), (char)((g.Data1 >> 16) & 0xFF), (char)((g.Data1 >> 24) & 0xFF), 0 };
    for (int i = 0; i < 4; i++) if (s[i] < 32 || s[i] > 126) s[i] = '?';
    return s;
}

// ------------------------------------------------------------------ capture

struct Cam {
    IMFMediaSource* source = nullptr;
    IMFSourceReader* reader = nullptr;
    IAMCameraControl* camCtl = nullptr;
    IAMVideoProcAmp* procAmp = nullptr;
    UINT32 w = 0, h = 0;
    std::mutex lock;
};

#define JN(name) Java_com_dronedirector_veyrocutpro_windows_MfNative_##name

extern "C" {

JNIEXPORT jstring JNICALL JN(lastError)(JNIEnv* env, jclass) { return env->NewStringUTF(g_err.c_str()); }

/** Every video capture device: "symbolic link \u0001 name". */
JNIEXPORT jobjectArray JNICALL JN(list)(JNIEnv* env, jclass) {
    com();
    std::vector<std::string> out;
    IMFAttributes* a = nullptr;
    IMFActivate** devs = nullptr; UINT32 n = 0;
    if (SUCCEEDED(MFCreateAttributes(&a, 1)) &&
        SUCCEEDED(a->SetGUID(MF_DEVSOURCE_ATTRIBUTE_SOURCE_TYPE, MF_DEVSOURCE_ATTRIBUTE_SOURCE_TYPE_VIDCAP_GUID)) &&
        SUCCEEDED(MFEnumDeviceSources(a, &devs, &n))) {
        for (UINT32 i = 0; i < n; i++) {
            WCHAR* name = nullptr; WCHAR* link = nullptr; UINT32 len = 0;
            devs[i]->GetAllocatedString(MF_DEVSOURCE_ATTRIBUTE_FRIENDLY_NAME, &name, &len);
            devs[i]->GetAllocatedString(MF_DEVSOURCE_ATTRIBUTE_SOURCE_TYPE_VIDCAP_SYMBOLIC_LINK, &link, &len);
            if (link) out.push_back(utf8(link) + "\x01" + utf8(name ? name : L"Camera"));
            CoTaskMemFree(name); CoTaskMemFree(link);
            devs[i]->Release();
        }
        CoTaskMemFree(devs);
    }
    release(a);
    return strings(env, out);
}

JNIEXPORT jlong JNICALL JN(open)(JNIEnv* env, jclass, jstring jlink) {
    com();
    std::wstring link = wide(env, jlink);
    Cam* c = new Cam();
    IMFAttributes* a = nullptr; IMFAttributes* ra = nullptr;
    HRESULT hr = MFCreateAttributes(&a, 2);
    if (SUCCEEDED(hr)) hr = a->SetGUID(MF_DEVSOURCE_ATTRIBUTE_SOURCE_TYPE, MF_DEVSOURCE_ATTRIBUTE_SOURCE_TYPE_VIDCAP_GUID);
    if (SUCCEEDED(hr)) hr = a->SetString(MF_DEVSOURCE_ATTRIBUTE_SOURCE_TYPE_VIDCAP_SYMBOLIC_LINK, link.c_str());
    if (SUCCEEDED(hr)) hr = MFCreateDeviceSource(a, &c->source);
    if (FAILED(hr)) { fail("The camera couldn't be opened", hr); release(a); delete c; return 0; }
    hr = MFCreateAttributes(&ra, 2);
    if (SUCCEEDED(hr)) ra->SetUINT32(MF_SOURCE_READER_ENABLE_VIDEO_PROCESSING, TRUE);
    if (SUCCEEDED(hr)) ra->SetUINT32(MF_READWRITE_DISABLE_CONVERTERS, FALSE);
    if (SUCCEEDED(hr)) hr = MFCreateSourceReaderFromMediaSource(c->source, ra, &c->reader);
    release(a); release(ra);
    if (FAILED(hr)) { fail("The camera's picture couldn't be read", hr); c->source->Shutdown(); release(c->source); delete c; return 0; }
    c->source->QueryInterface(IID_PPV_ARGS(&c->camCtl));
    c->source->QueryInterface(IID_PPV_ARGS(&c->procAmp));
    return (jlong)c;
}

/** The camera's own picture formats: "index width height fpsNum fpsDen FOURCC". */
JNIEXPORT jobjectArray JNICALL JN(formats)(JNIEnv* env, jclass, jlong h) {
    com();
    Cam* c = (Cam*)h; std::vector<std::string> out;
    if (c) {
        std::lock_guard<std::mutex> g(c->lock);
        for (DWORD i = 0;; i++) {
            IMFMediaType* t = nullptr;
            if (FAILED(c->reader->GetNativeMediaType((DWORD)MF_SOURCE_READER_FIRST_VIDEO_STREAM, i, &t))) break;
            UINT32 w = 0, hh = 0, fn = 0, fd = 1; GUID sub = GUID_NULL;
            MFGetAttributeSize(t, MF_MT_FRAME_SIZE, &w, &hh);
            MFGetAttributeRatio(t, MF_MT_FRAME_RATE, &fn, &fd);
            t->GetGUID(MF_MT_SUBTYPE, &sub);
            char b[96]; snprintf(b, sizeof b, "%lu %u %u %u %u %s", (unsigned long)i, w, hh, fn, fd, fourcc(sub).c_str());
            out.push_back(b);
            t->Release();
        }
    }
    return strings(env, out);
}

/** Picks native format [index] and asks for 32-bit RGB out of it. Returns (w << 16 | h), or 0. */
JNIEXPORT jint JNICALL JN(setFormat)(JNIEnv* env, jclass, jlong h, jint index) {
    com();
    Cam* c = (Cam*)h; if (!c) return 0;
    std::lock_guard<std::mutex> g(c->lock);
    IMFMediaType* nt = nullptr; IMFMediaType* ot = nullptr;
    HRESULT hr = c->reader->GetNativeMediaType((DWORD)MF_SOURCE_READER_FIRST_VIDEO_STREAM, (DWORD)index, &nt);
    if (SUCCEEDED(hr)) hr = c->reader->SetCurrentMediaType((DWORD)MF_SOURCE_READER_FIRST_VIDEO_STREAM, nullptr, nt);
    UINT32 w = 0, hh = 0;
    if (SUCCEEDED(hr)) MFGetAttributeSize(nt, MF_MT_FRAME_SIZE, &w, &hh);
    if (SUCCEEDED(hr)) hr = MFCreateMediaType(&ot);
    if (SUCCEEDED(hr)) hr = ot->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Video);
    if (SUCCEEDED(hr)) hr = ot->SetGUID(MF_MT_SUBTYPE, MFVideoFormat_RGB32);
    if (SUCCEEDED(hr)) hr = MFSetAttributeSize(ot, MF_MT_FRAME_SIZE, w, hh);
    if (SUCCEEDED(hr)) hr = c->reader->SetCurrentMediaType((DWORD)MF_SOURCE_READER_FIRST_VIDEO_STREAM, nullptr, ot);
    release(nt); release(ot);
    if (FAILED(hr)) { fail("This picture size isn't available", hr); return 0; }
    c->reader->SetStreamSelection((DWORD)MF_SOURCE_READER_ALL_STREAMS, FALSE);
    c->reader->SetStreamSelection((DWORD)MF_SOURCE_READER_FIRST_VIDEO_STREAM, TRUE);
    IMFMediaType* cur = nullptr;
    if (SUCCEEDED(c->reader->GetCurrentMediaType((DWORD)MF_SOURCE_READER_FIRST_VIDEO_STREAM, &cur))) { MFGetAttributeSize(cur, MF_MT_FRAME_SIZE, &w, &hh); cur->Release(); }
    c->w = w; c->h = hh;
    return (jint)((w << 16) | (hh & 0xFFFF));
}

/**
 * Waits for the next picture and copies it (ARGB, top row first) into [dst] (w*h ints).
 * Returns its time in 100 ns units, -1 when the camera stopped, -2 on an error, -3 when none came.
 */
JNIEXPORT jlong JNICALL JN(read)(JNIEnv* env, jclass, jlong h, jintArray dst) {
    com();
    Cam* c = (Cam*)h; if (!c) return -2;
    std::lock_guard<std::mutex> g(c->lock);
    DWORD stream = 0, flags = 0; LONGLONG ts = 0; IMFSample* s = nullptr;
    HRESULT hr = c->reader->ReadSample((DWORD)MF_SOURCE_READER_FIRST_VIDEO_STREAM, 0, &stream, &flags, &ts, &s);
    if (FAILED(hr)) { fail("Reading the camera failed", hr); return -2; }
    if (flags & (MF_SOURCE_READERF_ENDOFSTREAM | MF_SOURCE_READERF_ERROR)) { release(s); return -1; }
    if (!s) return -3;
    IMFMediaBuffer* b = nullptr; jlong ret = -3;
    if (SUCCEEDED(s->ConvertToContiguousBuffer(&b))) {
        const UINT32 w = c->w, hh = c->h;
        const jsize need = (jsize)(w * hh);
        if (env->GetArrayLength(dst) >= need && w > 0 && hh > 0) {
            BYTE* base = nullptr; LONG pitch = 0; bool locked2d = false;
            IMF2DBuffer* b2 = nullptr;
            if (SUCCEEDED(b->QueryInterface(IID_PPV_ARGS(&b2))) && SUCCEEDED(b2->Lock2D(&base, &pitch))) locked2d = true;
            DWORD max = 0, len = 0;
            if (!locked2d) {
                if (SUCCEEDED(b->Lock(&base, &max, &len))) {
                    // Without a pitch: 32-bit RGB from the video processor is stored bottom row first.
                    pitch = -(LONG)(w * 4);
                    if (len >= w * hh * 4) base = base + (size_t)(hh - 1) * w * 4; else base = nullptr;
                } else base = nullptr;
            }
            if (base) {
                jint* out = (jint*)env->GetPrimitiveArrayCritical(dst, nullptr);
                if (out) {
                    for (UINT32 y = 0; y < hh; y++) {
                        const DWORD* row = (const DWORD*)(base + (LONG_PTR)pitch * (LONG_PTR)y);
                        jint* o = out + (size_t)y * w;
                        for (UINT32 x = 0; x < w; x++) o[x] = (jint)(row[x] | 0xFF000000u);
                    }
                    env->ReleasePrimitiveArrayCritical(dst, out, 0);
                    ret = ts < 0 ? 0 : ts;
                }
                if (locked2d) b2->Unlock2D(); else b->Unlock();
            }
            release(b2);
        }
        b->Release();
    }
    s->Release();
    return ret;
}

/**
 * Controls the camera offers: "kind id min max step default value auto" per line; kind 0 = camera
 * (pan, tilt, roll, zoom, exposure, iris, focus), kind 1 = picture (brightness ... gain, power line).
 */
JNIEXPORT jobjectArray JNICALL JN(controls)(JNIEnv* env, jclass, jlong h) {
    com();
    Cam* c = (Cam*)h; std::vector<std::string> out;
    if (c) {
        std::lock_guard<std::mutex> g(c->lock);
        if (c->camCtl) for (long id = CameraControl_Pan; id <= CameraControl_Focus; id++) {
            long mn, mx, st, df, fl, v = 0, vf = 0;
            if (FAILED(c->camCtl->GetRange(id, &mn, &mx, &st, &df, &fl))) continue;
            c->camCtl->Get(id, &v, &vf);
            char b[128]; snprintf(b, sizeof b, "0 %ld %ld %ld %ld %ld %ld %d %d", id, mn, mx, st, df, v, (vf & CameraControl_Flags_Auto) ? 1 : 0, (fl & CameraControl_Flags_Auto) ? 1 : 0);
            out.push_back(b);
        }
        if (c->procAmp) for (long id = VideoProcAmp_Brightness; id <= 13; id++) {
            long mn, mx, st, df, fl, v = 0, vf = 0;
            if (FAILED(c->procAmp->GetRange(id, &mn, &mx, &st, &df, &fl))) continue;
            c->procAmp->Get(id, &v, &vf);
            char b[128]; snprintf(b, sizeof b, "1 %ld %ld %ld %ld %ld %ld %d %d", id, mn, mx, st, df, v, (vf & VideoProcAmp_Flags_Auto) ? 1 : 0, (fl & VideoProcAmp_Flags_Auto) ? 1 : 0);
            out.push_back(b);
        }
    }
    return strings(env, out);
}

JNIEXPORT jboolean JNICALL JN(setControl)(JNIEnv* env, jclass, jlong h, jint kind, jint id, jint value, jboolean autoMode) {
    com();
    Cam* c = (Cam*)h; if (!c) return JNI_FALSE;
    HRESULT hr = E_NOINTERFACE;
    if (kind == 0 && c->camCtl) hr = c->camCtl->Set(id, value, autoMode ? CameraControl_Flags_Auto : CameraControl_Flags_Manual);
    if (kind == 1 && c->procAmp) hr = c->procAmp->Set(id, value, autoMode ? VideoProcAmp_Flags_Auto : VideoProcAmp_Flags_Manual);
    if (FAILED(hr)) { fail("The camera didn't take the setting", hr); return JNI_FALSE; }
    return JNI_TRUE;
}

JNIEXPORT void JNICALL JN(close)(JNIEnv* env, jclass, jlong h) {
    com();
    Cam* c = (Cam*)h; if (!c) return;
    {
        std::lock_guard<std::mutex> g(c->lock);
        release(c->camCtl); release(c->procAmp); release(c->reader);
        if (c->source) c->source->Shutdown();
        release(c->source);
    }
    delete c;
}

// ------------------------------------------------------------------ recording

struct Rec {
    IMFSinkWriter* writer = nullptr;
    DWORD video = 0, audio = 0;
    bool hasAudio = false;
    UINT32 w = 0, h = 0;
    std::mutex lock;
};

/**
 * Starts an MP4 at [path]: video [w]x[h] at fpsNum/fpsDen, [bitrate] bit/s, HEVC when [hevc],
 * and 16-bit PCM audio (AAC in the file) when [rate] > 0. Uses the graphics card's encoder when there is one.
 */
JNIEXPORT jlong JNICALL JN(recOpen)(JNIEnv* env, jclass, jstring jpath, jint w, jint h, jint fpsNum, jint fpsDen, jint bitrate, jboolean hevc, jint rate, jint channels) {
    com();
    std::wstring path = wide(env, jpath);
    Rec* r = new Rec(); r->w = w; r->h = h;
    IMFAttributes* a = nullptr;
    HRESULT hr = MFCreateAttributes(&a, 3);
    if (SUCCEEDED(hr)) a->SetUINT32(MF_READWRITE_ENABLE_HARDWARE_TRANSFORMS, TRUE);
    if (SUCCEEDED(hr)) a->SetUINT32(MF_SINK_WRITER_DISABLE_THROTTLING, TRUE);
    if (SUCCEEDED(hr)) a->SetGUID(MF_TRANSCODE_CONTAINERTYPE, MFTranscodeContainerType_MPEG4);
    if (SUCCEEDED(hr)) hr = MFCreateSinkWriterFromURL(path.c_str(), nullptr, a, &r->writer);
    release(a);
    if (FAILED(hr)) { fail("The video file couldn't be created", hr); delete r; return 0; }

    IMFMediaType* out = nullptr; IMFMediaType* in = nullptr;
    hr = MFCreateMediaType(&out);
    if (SUCCEEDED(hr)) out->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Video);
    if (SUCCEEDED(hr)) out->SetGUID(MF_MT_SUBTYPE, hevc ? MFVideoFormat_HEVC : MFVideoFormat_H264);
    if (SUCCEEDED(hr)) out->SetUINT32(MF_MT_AVG_BITRATE, (UINT32)bitrate);
    if (SUCCEEDED(hr)) out->SetUINT32(MF_MT_INTERLACE_MODE, MFVideoInterlace_Progressive);
    if (SUCCEEDED(hr)) MFSetAttributeSize(out, MF_MT_FRAME_SIZE, w, h);
    if (SUCCEEDED(hr)) MFSetAttributeRatio(out, MF_MT_FRAME_RATE, fpsNum, fpsDen);
    if (SUCCEEDED(hr)) MFSetAttributeRatio(out, MF_MT_PIXEL_ASPECT_RATIO, 1, 1);
    if (SUCCEEDED(hr) && !hevc) out->SetUINT32(MF_MT_MPEG2_PROFILE, eAVEncH264VProfile_High);
    if (SUCCEEDED(hr)) hr = r->writer->AddStream(out, &r->video);
    if (SUCCEEDED(hr)) hr = MFCreateMediaType(&in);
    if (SUCCEEDED(hr)) in->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Video);
    if (SUCCEEDED(hr)) in->SetGUID(MF_MT_SUBTYPE, MFVideoFormat_RGB32);
    if (SUCCEEDED(hr)) in->SetUINT32(MF_MT_INTERLACE_MODE, MFVideoInterlace_Progressive);
    if (SUCCEEDED(hr)) in->SetUINT32(MF_MT_DEFAULT_STRIDE, (UINT32)(w * 4));
    if (SUCCEEDED(hr)) MFSetAttributeSize(in, MF_MT_FRAME_SIZE, w, h);
    if (SUCCEEDED(hr)) MFSetAttributeRatio(in, MF_MT_FRAME_RATE, fpsNum, fpsDen);
    if (SUCCEEDED(hr)) MFSetAttributeRatio(in, MF_MT_PIXEL_ASPECT_RATIO, 1, 1);
    if (SUCCEEDED(hr)) hr = r->writer->SetInputMediaType(r->video, in, nullptr);
    release(out); release(in);
    if (FAILED(hr)) { fail(hevc ? "No HEVC encoder on this PC" : "No H.264 encoder on this PC", hr); release(r->writer); delete r; return 0; }

    if (rate > 0) {
        IMFMediaType* ao = nullptr; IMFMediaType* ai = nullptr;
        HRESULT ah = MFCreateMediaType(&ao);
        if (SUCCEEDED(ah)) ao->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Audio);
        if (SUCCEEDED(ah)) ao->SetGUID(MF_MT_SUBTYPE, MFAudioFormat_AAC);
        if (SUCCEEDED(ah)) ao->SetUINT32(MF_MT_AUDIO_BITS_PER_SAMPLE, 16);
        if (SUCCEEDED(ah)) ao->SetUINT32(MF_MT_AUDIO_SAMPLES_PER_SECOND, (UINT32)rate);
        if (SUCCEEDED(ah)) ao->SetUINT32(MF_MT_AUDIO_NUM_CHANNELS, (UINT32)channels);
        if (SUCCEEDED(ah)) ao->SetUINT32(MF_MT_AUDIO_AVG_BYTES_PER_SECOND, 24000);
        if (SUCCEEDED(ah)) ah = r->writer->AddStream(ao, &r->audio);
        if (SUCCEEDED(ah)) ah = MFCreateMediaType(&ai);
        if (SUCCEEDED(ah)) ai->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Audio);
        if (SUCCEEDED(ah)) ai->SetGUID(MF_MT_SUBTYPE, MFAudioFormat_PCM);
        if (SUCCEEDED(ah)) ai->SetUINT32(MF_MT_AUDIO_BITS_PER_SAMPLE, 16);
        if (SUCCEEDED(ah)) ai->SetUINT32(MF_MT_AUDIO_SAMPLES_PER_SECOND, (UINT32)rate);
        if (SUCCEEDED(ah)) ai->SetUINT32(MF_MT_AUDIO_NUM_CHANNELS, (UINT32)channels);
        if (SUCCEEDED(ah)) ai->SetUINT32(MF_MT_AUDIO_BLOCK_ALIGNMENT, (UINT32)(channels * 2));
        if (SUCCEEDED(ah)) ai->SetUINT32(MF_MT_AUDIO_AVG_BYTES_PER_SECOND, (UINT32)(rate * channels * 2));
        if (SUCCEEDED(ah)) ah = r->writer->SetInputMediaType(r->audio, ai, nullptr);
        release(ao); release(ai);
        r->hasAudio = SUCCEEDED(ah);
        if (!r->hasAudio) { fail("Sound couldn't be added to the video", ah); release(r->writer); delete r; return 0; }
    }
    hr = r->writer->BeginWriting();
    if (FAILED(hr)) { fail(hevc ? "The HEVC encoder couldn't start" : "The H.264 encoder couldn't start", hr); release(r->writer); delete r; return 0; }
    return (jlong)r;
}

/** One picture (ARGB, top row first) at [ts] for [dur] (100 ns units). */
JNIEXPORT jboolean JNICALL JN(recVideo)(JNIEnv* env, jclass, jlong h, jintArray px, jlong ts, jlong dur) {
    com();
    Rec* r = (Rec*)h; if (!r) return JNI_FALSE;
    std::lock_guard<std::mutex> g(r->lock);
    const DWORD bytes = r->w * r->h * 4;
    IMFMediaBuffer* b = nullptr; IMFSample* s = nullptr;
    HRESULT hr = MFCreateMemoryBuffer(bytes, &b);
    BYTE* d = nullptr;
    if (SUCCEEDED(hr)) hr = b->Lock(&d, nullptr, nullptr);
    if (SUCCEEDED(hr)) {
        env->GetIntArrayRegion(px, 0, (jsize)(r->w * r->h), (jint*)d);
        b->Unlock();
        b->SetCurrentLength(bytes);
    }
    if (SUCCEEDED(hr)) hr = MFCreateSample(&s);
    if (SUCCEEDED(hr)) hr = s->AddBuffer(b);
    if (SUCCEEDED(hr)) hr = s->SetSampleTime(ts);
    if (SUCCEEDED(hr)) hr = s->SetSampleDuration(dur);
    if (SUCCEEDED(hr)) hr = r->writer->WriteSample(r->video, s);
    release(s); release(b);
    if (FAILED(hr)) { fail("Writing the video failed", hr); return JNI_FALSE; }
    return JNI_TRUE;
}

/** Sound: [n] bytes of 16-bit PCM at [ts] for [dur]. */
JNIEXPORT jboolean JNICALL JN(recAudio)(JNIEnv* env, jclass, jlong h, jbyteArray pcm, jint n, jlong ts, jlong dur) {
    com();
    Rec* r = (Rec*)h; if (!r || !r->hasAudio || n <= 0) return JNI_FALSE;
    std::lock_guard<std::mutex> g(r->lock);
    IMFMediaBuffer* b = nullptr; IMFSample* s = nullptr; BYTE* d = nullptr;
    HRESULT hr = MFCreateMemoryBuffer((DWORD)n, &b);
    if (SUCCEEDED(hr)) hr = b->Lock(&d, nullptr, nullptr);
    if (SUCCEEDED(hr)) { env->GetByteArrayRegion(pcm, 0, n, (jbyte*)d); b->Unlock(); b->SetCurrentLength((DWORD)n); }
    if (SUCCEEDED(hr)) hr = MFCreateSample(&s);
    if (SUCCEEDED(hr)) hr = s->AddBuffer(b);
    if (SUCCEEDED(hr)) hr = s->SetSampleTime(ts);
    if (SUCCEEDED(hr)) hr = s->SetSampleDuration(dur);
    if (SUCCEEDED(hr)) hr = r->writer->WriteSample(r->audio, s);
    release(s); release(b);
    if (FAILED(hr)) { fail("Writing the sound failed", hr); return JNI_FALSE; }
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL JN(recClose)(JNIEnv* env, jclass, jlong h) {
    com();
    Rec* r = (Rec*)h; if (!r) return JNI_FALSE;
    HRESULT hr;
    {
        std::lock_guard<std::mutex> g(r->lock);
        hr = r->writer->Finalize();
        release(r->writer);
    }
    delete r;
    if (FAILED(hr)) { fail("Finishing the video failed", hr); return JNI_FALSE; }
    return JNI_TRUE;
}

// ------------------------------------------------------------------ live encoding (raw H.264 + AAC for streaming)

struct Enc {
    IMFTransform* video = nullptr;
    IMFTransform* audio = nullptr;
    UINT32 w = 0, h = 0;
    std::vector<BYTE> nv12;
    std::string sps;   // sequence header (Annex-B SPS + PPS)
    std::mutex lock;
};

static HRESULT drain(IMFTransform* t, std::string& out, bool lengthPrefix, std::string* header) {
    MFT_OUTPUT_STREAM_INFO info = {};
    HRESULT hr = t->GetOutputStreamInfo(0, &info);
    if (FAILED(hr)) return hr;
    for (;;) {
        MFT_OUTPUT_DATA_BUFFER ob = {};
        IMFSample* s = nullptr; IMFMediaBuffer* b = nullptr;
        if (!(info.dwFlags & (MFT_OUTPUT_STREAM_PROVIDES_SAMPLES | MFT_OUTPUT_STREAM_CAN_PROVIDE_SAMPLES))) {
            MFCreateSample(&s); MFCreateMemoryBuffer(info.cbSize > 0 ? info.cbSize : (1 << 20), &b); s->AddBuffer(b); ob.pSample = s;
        }
        DWORD status = 0;
        hr = t->ProcessOutput(0, 1, &ob, &status);
        if (hr == MF_E_TRANSFORM_NEED_MORE_INPUT) { release(b); release(s); if (ob.pEvents) ob.pEvents->Release(); return S_OK; }
        if (hr == MF_E_TRANSFORM_STREAM_CHANGE) {
            IMFMediaType* t2 = nullptr;
            if (SUCCEEDED(t->GetOutputAvailableType(0, 0, &t2))) { t->SetOutputType(0, t2, 0); t2->Release(); }
            release(b); release(s); if (ob.pEvents) ob.pEvents->Release();
            t->GetOutputStreamInfo(0, &info);
            continue;
        }
        if (FAILED(hr)) { release(b); release(s); if (ob.pEvents) ob.pEvents->Release(); return hr; }
        IMFSample* got = ob.pSample;
        IMFMediaBuffer* cb = nullptr;
        if (got && SUCCEEDED(got->ConvertToContiguousBuffer(&cb))) {
            BYTE* d = nullptr; DWORD len = 0;
            if (SUCCEEDED(cb->Lock(&d, nullptr, &len))) {
                if (lengthPrefix) { BYTE l2[2] = { (BYTE)(len >> 8), (BYTE)len }; out.append((char*)l2, 2); }
                else {
                    // Each frame: a 4-byte marker (0 0 0 0 = key frame, 0 0 0 1 = other) and a 4-byte length, then the data.
                    UINT32 key = 0; got->GetUINT32(MFSampleExtension_CleanPoint, &key);
                    if (key) out.append("\x00\x00\x00\x00", 4); else out.append("\x00\x00\x00\x01", 4);
                    BYTE l4[4] = { (BYTE)(len >> 24), (BYTE)(len >> 16), (BYTE)(len >> 8), (BYTE)len }; out.append((char*)l4, 4);
                }
                out.append((char*)d, len);
                cb->Unlock();
            }
            cb->Release();
        }
        if (got && got != s) got->Release();
        release(b); release(s);
        if (ob.pEvents) ob.pEvents->Release();
        if (header && header->empty()) {
            IMFMediaType* ot = nullptr;
            if (SUCCEEDED(t->GetOutputCurrentType(0, &ot))) {
                UINT32 n = 0;
                if (SUCCEEDED(ot->GetBlobSize(MF_MT_MPEG_SEQUENCE_HEADER, &n)) && n > 0) {
                    header->resize(n); ot->GetBlob(MF_MT_MPEG_SEQUENCE_HEADER, (UINT8*)&(*header)[0], n, nullptr);
                }
                ot->Release();
            }
        }
    }
}

/** A live encoder: H.264 (Microsoft's encoder, constant bit rate, a key frame every 2 s) and AAC 48 kHz stereo. */
JNIEXPORT jlong JNICALL JN(encOpen)(JNIEnv* env, jclass, jint w, jint h, jint fps, jint bitrate, jint abitrate) {
    com();
    Enc* e = new Enc(); e->w = w; e->h = h; e->nv12.resize((size_t)w * h * 3 / 2);
    HRESULT hr = CoCreateInstance(CLSID_CMSH264EncoderMFT, nullptr, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&e->video));
    IMFMediaType* ot = nullptr; IMFMediaType* it = nullptr;
    if (SUCCEEDED(hr)) hr = MFCreateMediaType(&ot);
    if (SUCCEEDED(hr)) { ot->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Video); ot->SetGUID(MF_MT_SUBTYPE, MFVideoFormat_H264);
        ot->SetUINT32(MF_MT_AVG_BITRATE, (UINT32)bitrate); ot->SetUINT32(MF_MT_INTERLACE_MODE, MFVideoInterlace_Progressive);
        MFSetAttributeSize(ot, MF_MT_FRAME_SIZE, w, h); MFSetAttributeRatio(ot, MF_MT_FRAME_RATE, fps, 1); MFSetAttributeRatio(ot, MF_MT_PIXEL_ASPECT_RATIO, 1, 1);
        ot->SetUINT32(MF_MT_MPEG2_PROFILE, eAVEncH264VProfile_Main);
        hr = e->video->SetOutputType(0, ot, 0); }
    if (SUCCEEDED(hr)) hr = MFCreateMediaType(&it);
    if (SUCCEEDED(hr)) { it->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Video); it->SetGUID(MF_MT_SUBTYPE, MFVideoFormat_NV12);
        it->SetUINT32(MF_MT_INTERLACE_MODE, MFVideoInterlace_Progressive);
        MFSetAttributeSize(it, MF_MT_FRAME_SIZE, w, h); MFSetAttributeRatio(it, MF_MT_FRAME_RATE, fps, 1); MFSetAttributeRatio(it, MF_MT_PIXEL_ASPECT_RATIO, 1, 1);
        hr = e->video->SetInputType(0, it, 0); }
    release(ot); release(it);
    if (SUCCEEDED(hr)) {
        ICodecAPI* api = nullptr;
        if (SUCCEEDED(e->video->QueryInterface(IID_PPV_ARGS(&api)))) {
            VARIANT v; VariantInit(&v);
            v.vt = VT_UI4; v.ulVal = eAVEncCommonRateControlMode_CBR; api->SetValue(&CODECAPI_AVEncCommonRateControlMode, &v);
            v.vt = VT_UI4; v.ulVal = (ULONG)(fps * 2); api->SetValue(&CODECAPI_AVEncMPVGOPSize, &v);
            v.vt = VT_BOOL; v.boolVal = VARIANT_TRUE; api->SetValue(&CODECAPI_AVLowLatencyMode, &v);
            v.vt = VT_UI4; v.ulVal = 0; api->SetValue(&CODECAPI_AVEncMPVDefaultBPictureCount, &v);
            api->Release();
        }
        e->video->ProcessMessage(MFT_MESSAGE_NOTIFY_BEGIN_STREAMING, 0);
        e->video->ProcessMessage(MFT_MESSAGE_NOTIFY_START_OF_STREAM, 0);
    }
    if (FAILED(hr)) { fail("The H.264 encoder couldn't start", hr); release(e->video); delete e; return 0; }

    HRESULT ah = CoCreateInstance(CLSID_AACMFTEncoder, nullptr, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&e->audio));
    IMFMediaType* ai = nullptr; IMFMediaType* ao = nullptr;
    if (SUCCEEDED(ah)) ah = MFCreateMediaType(&ai);
    if (SUCCEEDED(ah)) { ai->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Audio); ai->SetGUID(MF_MT_SUBTYPE, MFAudioFormat_PCM);
        ai->SetUINT32(MF_MT_AUDIO_BITS_PER_SAMPLE, 16); ai->SetUINT32(MF_MT_AUDIO_SAMPLES_PER_SECOND, 48000); ai->SetUINT32(MF_MT_AUDIO_NUM_CHANNELS, 2);
        ah = e->audio->SetInputType(0, ai, 0); }
    if (SUCCEEDED(ah)) ah = MFCreateMediaType(&ao);
    if (SUCCEEDED(ah)) { ao->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Audio); ao->SetGUID(MF_MT_SUBTYPE, MFAudioFormat_AAC);
        ao->SetUINT32(MF_MT_AUDIO_BITS_PER_SAMPLE, 16); ao->SetUINT32(MF_MT_AUDIO_SAMPLES_PER_SECOND, 48000); ao->SetUINT32(MF_MT_AUDIO_NUM_CHANNELS, 2);
        ao->SetUINT32(MF_MT_AUDIO_AVG_BYTES_PER_SECOND, (UINT32)(abitrate / 8)); ao->SetUINT32(MF_MT_AAC_PAYLOAD_TYPE, 0);
        ah = e->audio->SetOutputType(0, ao, 0); }
    release(ai); release(ao);
    if (SUCCEEDED(ah)) { e->audio->ProcessMessage(MFT_MESSAGE_NOTIFY_BEGIN_STREAMING, 0); e->audio->ProcessMessage(MFT_MESSAGE_NOTIFY_START_OF_STREAM, 0); }
    else release(e->audio);
    return (jlong)e;
}

static jbyteArray bytes(JNIEnv* env, const std::string& s) {
    jbyteArray a = env->NewByteArray((jsize)s.size());
    if (!s.empty()) env->SetByteArrayRegion(a, 0, (jsize)s.size(), (const jbyte*)s.data());
    return a;
}

/**
 * One picture (ARGB) at [ts] (100 ns). Returns the encoded output so far: per frame a 4-byte marker
 * (0 0 0 0 = key frame, 0 0 0 1 = other), a 4-byte length and the H.264 data (Annex-B start codes).
 */
JNIEXPORT jbyteArray JNICALL JN(encVideo)(JNIEnv* env, jclass, jlong h, jintArray px, jlong ts, jlong dur) {
    com();
    Enc* e = (Enc*)h; if (!e || !e->video) return nullptr;
    std::lock_guard<std::mutex> g(e->lock);
    const UINT32 w = e->w, hh = e->h;
    jint* p = (jint*)env->GetPrimitiveArrayCritical(px, nullptr);
    if (!p) return nullptr;
    BYTE* Y = e->nv12.data(); BYTE* UV = Y + (size_t)w * hh;
    for (UINT32 y = 0; y < hh; y++) {
        for (UINT32 x = 0; x < w; x++) {
            UINT32 c = (UINT32)p[(size_t)y * w + x];
            int r = (c >> 16) & 255, gg = (c >> 8) & 255, b = c & 255;
            Y[(size_t)y * w + x] = (BYTE)(((66 * r + 129 * gg + 25 * b + 128) >> 8) + 16);
            if (!(y & 1) && !(x & 1)) {
                UV[(size_t)(y / 2) * w + x] = (BYTE)(((-38 * r - 74 * gg + 112 * b + 128) >> 8) + 128);
                UV[(size_t)(y / 2) * w + x + 1] = (BYTE)(((112 * r - 94 * gg - 18 * b + 128) >> 8) + 128);
            }
        }
    }
    env->ReleasePrimitiveArrayCritical(px, p, JNI_ABORT);
    IMFMediaBuffer* b = nullptr; IMFSample* s = nullptr; BYTE* d = nullptr;
    const DWORD n = (DWORD)e->nv12.size();
    HRESULT hr = MFCreateMemoryBuffer(n, &b);
    if (SUCCEEDED(hr)) hr = b->Lock(&d, nullptr, nullptr);
    if (SUCCEEDED(hr)) { memcpy(d, e->nv12.data(), n); b->Unlock(); b->SetCurrentLength(n); }
    if (SUCCEEDED(hr)) hr = MFCreateSample(&s);
    if (SUCCEEDED(hr)) { s->AddBuffer(b); s->SetSampleTime(ts); s->SetSampleDuration(dur); }
    std::string out;
    if (SUCCEEDED(hr)) {
        hr = e->video->ProcessInput(0, s, 0);
        if (hr == MF_E_NOTACCEPTING) { drain(e->video, out, false, &e->sps); hr = e->video->ProcessInput(0, s, 0); }
        drain(e->video, out, false, &e->sps);
    }
    release(s); release(b);
    if (FAILED(hr)) { fail("Encoding the picture failed", hr); return nullptr; }
    return bytes(env, out);
}

/** The H.264 sequence header (SPS + PPS, Annex-B), once the encoder has produced it. */
JNIEXPORT jbyteArray JNICALL JN(encHeader)(JNIEnv* env, jclass, jlong h) {
    Enc* e = (Enc*)h; if (!e) return nullptr;
    std::lock_guard<std::mutex> g(e->lock);
    if (e->sps.empty() && e->video) {
        IMFMediaType* ot = nullptr;
        if (SUCCEEDED(e->video->GetOutputCurrentType(0, &ot))) {
            UINT32 n = 0;
            if (SUCCEEDED(ot->GetBlobSize(MF_MT_MPEG_SEQUENCE_HEADER, &n)) && n > 0) { e->sps.resize(n); ot->GetBlob(MF_MT_MPEG_SEQUENCE_HEADER, (UINT8*)&e->sps[0], n, nullptr); }
            ot->Release();
        }
    }
    return bytes(env, e->sps);
}

/** Sound: 16-bit stereo PCM in; AAC frames out, each with a 2-byte length. */
JNIEXPORT jbyteArray JNICALL JN(encAudio)(JNIEnv* env, jclass, jlong h, jbyteArray pcm, jint n, jlong ts, jlong dur) {
    com();
    Enc* e = (Enc*)h; if (!e || !e->audio || n <= 0) return nullptr;
    std::lock_guard<std::mutex> g(e->lock);
    IMFMediaBuffer* b = nullptr; IMFSample* s = nullptr; BYTE* d = nullptr;
    HRESULT hr = MFCreateMemoryBuffer((DWORD)n, &b);
    if (SUCCEEDED(hr)) hr = b->Lock(&d, nullptr, nullptr);
    if (SUCCEEDED(hr)) { env->GetByteArrayRegion(pcm, 0, n, (jbyte*)d); b->Unlock(); b->SetCurrentLength((DWORD)n); }
    if (SUCCEEDED(hr)) hr = MFCreateSample(&s);
    if (SUCCEEDED(hr)) { s->AddBuffer(b); s->SetSampleTime(ts); s->SetSampleDuration(dur); }
    std::string out;
    if (SUCCEEDED(hr)) {
        hr = e->audio->ProcessInput(0, s, 0);
        if (hr == MF_E_NOTACCEPTING) { drain(e->audio, out, true, nullptr); hr = e->audio->ProcessInput(0, s, 0); }
        drain(e->audio, out, true, nullptr);
    }
    release(s); release(b);
    if (FAILED(hr)) return nullptr;
    return bytes(env, out);
}

JNIEXPORT void JNICALL JN(encClose)(JNIEnv* env, jclass, jlong h) {
    com();
    Enc* e = (Enc*)h; if (!e) return;
    { std::lock_guard<std::mutex> g(e->lock); release(e->video); release(e->audio); }
    delete e;
}

// ------------------------------------------------------------------ H.264 decoding (the phone's live picture)

struct Dec {
    IMFTransform* t = nullptr;
    UINT32 w = 0, h = 0, stride = 0;
    std::mutex lock;
};

static void decSizes(Dec* d) {
    IMFMediaType* ot = nullptr;
    if (SUCCEEDED(d->t->GetOutputCurrentType(0, &ot))) {
        MFGetAttributeSize(ot, MF_MT_FRAME_SIZE, &d->w, &d->h);
        UINT32 st = 0; if (SUCCEEDED(ot->GetUINT32(MF_MT_DEFAULT_STRIDE, &st))) d->stride = st; else d->stride = d->w;
        ot->Release();
    }
}

static HRESULT decSetOutput(Dec* d) {
    for (DWORD i = 0;; i++) {
        IMFMediaType* t = nullptr;
        HRESULT hr = d->t->GetOutputAvailableType(0, i, &t);
        if (FAILED(hr)) return hr;
        GUID sub = GUID_NULL; t->GetGUID(MF_MT_SUBTYPE, &sub);
        if (sub == MFVideoFormat_NV12) { hr = d->t->SetOutputType(0, t, 0); t->Release(); decSizes(d); return hr; }
        t->Release();
    }
}

JNIEXPORT jlong JNICALL JN(decOpen)(JNIEnv* env, jclass) {
    com();
    Dec* d = new Dec();
    HRESULT hr = CoCreateInstance(CLSID_CMSH264DecoderMFT, nullptr, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&d->t));
    IMFMediaType* it = nullptr;
    if (SUCCEEDED(hr)) hr = MFCreateMediaType(&it);
    if (SUCCEEDED(hr)) { it->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Video); it->SetGUID(MF_MT_SUBTYPE, MFVideoFormat_H264); hr = d->t->SetInputType(0, it, 0); }
    release(it);
    if (SUCCEEDED(hr)) decSetOutput(d);
    if (SUCCEEDED(hr)) { d->t->ProcessMessage(MFT_MESSAGE_NOTIFY_BEGIN_STREAMING, 0); d->t->ProcessMessage(MFT_MESSAGE_NOTIFY_START_OF_STREAM, 0); }
    if (FAILED(hr)) { fail("The H.264 decoder couldn't start", hr); release(d->t); delete d; return 0; }
    return (jlong)d;
}

/**
 * One piece of H.264 (Annex-B) in; when a picture comes out it is written into [dst] as ARGB and
 * (width << 16 | height) is returned. 0 = no picture yet; -(w*h) = [dst] is too small.
 */
JNIEXPORT jlong JNICALL JN(decFrame)(JNIEnv* env, jclass, jlong h, jbyteArray data, jlong ts, jintArray dst) {
    com();
    Dec* d = (Dec*)h; if (!d) return 0;
    std::lock_guard<std::mutex> g(d->lock);
    const jsize n = env->GetArrayLength(data);
    IMFMediaBuffer* b = nullptr; IMFSample* s = nullptr; BYTE* p = nullptr;
    HRESULT hr = MFCreateMemoryBuffer((DWORD)n, &b);
    if (SUCCEEDED(hr)) hr = b->Lock(&p, nullptr, nullptr);
    if (SUCCEEDED(hr)) { env->GetByteArrayRegion(data, 0, n, (jbyte*)p); b->Unlock(); b->SetCurrentLength((DWORD)n); }
    if (SUCCEEDED(hr)) hr = MFCreateSample(&s);
    if (SUCCEEDED(hr)) { s->AddBuffer(b); s->SetSampleTime(ts); }
    if (SUCCEEDED(hr)) hr = d->t->ProcessInput(0, s, 0);
    release(s); release(b);
    jlong ret = 0;
    for (;;) {
        MFT_OUTPUT_STREAM_INFO info = {};
        d->t->GetOutputStreamInfo(0, &info);
        MFT_OUTPUT_DATA_BUFFER ob = {};
        IMFSample* os = nullptr; IMFMediaBuffer* obuf = nullptr;
        if (!(info.dwFlags & (MFT_OUTPUT_STREAM_PROVIDES_SAMPLES | MFT_OUTPUT_STREAM_CAN_PROVIDE_SAMPLES))) {
            MFCreateSample(&os); MFCreateMemoryBuffer(info.cbSize > 0 ? info.cbSize : (DWORD)(d->w * d->h * 2 + 1024), &obuf); os->AddBuffer(obuf); ob.pSample = os;
        }
        DWORD status = 0;
        HRESULT oh = d->t->ProcessOutput(0, 1, &ob, &status);
        if (ob.pEvents) ob.pEvents->Release();
        if (oh == MF_E_TRANSFORM_STREAM_CHANGE) { release(obuf); release(os); decSetOutput(d); continue; }
        if (FAILED(oh)) { release(obuf); release(os); break; }
        IMFSample* got = ob.pSample;
        IMFMediaBuffer* cb = nullptr;
        if (got && SUCCEEDED(got->ConvertToContiguousBuffer(&cb))) {
            const UINT32 w = d->w, hh = d->h, st = d->stride ? d->stride : d->w;
            if ((jsize)(w * hh) > env->GetArrayLength(dst)) ret = -(jlong)(w * hh);
            else {
                BYTE* y0 = nullptr; DWORD len = 0;
                if (SUCCEEDED(cb->Lock(&y0, nullptr, &len))) {
                    UINT32 rows = len / st * 2 / 3;   // the buffer's own height (may be padded)
                    if (rows < hh) rows = hh;
                    const BYTE* uv0 = y0 + (size_t)st * rows;
                    jint* o = (jint*)env->GetPrimitiveArrayCritical(dst, nullptr);
                    if (o) {
                        for (UINT32 y = 0; y < hh; y++) {
                            const BYTE* Y = y0 + (size_t)y * st; const BYTE* UV = uv0 + (size_t)(y / 2) * st;
                            jint* row = o + (size_t)y * w;
                            for (UINT32 x = 0; x < w; x++) {
                                int c = Y[x] - 16, dd = UV[x & ~1u] - 128, e = UV[(x & ~1u) + 1] - 128;
                                int r = (298 * c + 409 * e + 128) >> 8, gg = (298 * c - 100 * dd - 208 * e + 128) >> 8, bb = (298 * c + 516 * dd + 128) >> 8;
                                r = r < 0 ? 0 : r > 255 ? 255 : r; gg = gg < 0 ? 0 : gg > 255 ? 255 : gg; bb = bb < 0 ? 0 : bb > 255 ? 255 : bb;
                                row[x] = (jint)(0xFF000000u | (r << 16) | (gg << 8) | bb);
                            }
                        }
                        env->ReleasePrimitiveArrayCritical(dst, o, 0);
                        ret = ((jlong)w << 16) | hh;
                    }
                    cb->Unlock();
                }
            }
            cb->Release();
        }
        if (got && got != os) got->Release();
        release(obuf); release(os);
    }
    return ret;
}

JNIEXPORT void JNICALL JN(decClose)(JNIEnv* env, jclass, jlong h) {
    com();
    Dec* d = (Dec*)h; if (!d) return;
    { std::lock_guard<std::mutex> g(d->lock); release(d->t); }
    delete d;
}

// ------------------------------------------------------------------ tethered cameras (PTP through Windows Portable Devices)

/** Cameras Windows sees as portable devices: "id \u0001 name \u0001 maker". */
JNIEXPORT jobjectArray JNICALL JN(ptpList)(JNIEnv* env, jclass) {
    com();
    std::vector<std::string> out;
    IPortableDeviceManager* m = nullptr;
    if (SUCCEEDED(CoCreateInstance(CLSID_PortableDeviceManager, nullptr, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&m)))) {
        m->RefreshDeviceList();
        DWORD n = 0;
        if (SUCCEEDED(m->GetDevices(nullptr, &n)) && n > 0) {
            std::vector<PWSTR> ids(n);
            if (SUCCEEDED(m->GetDevices(ids.data(), &n))) {
                for (DWORD i = 0; i < n; i++) {
                    WCHAR name[256] = L""; DWORD len = 256; m->GetDeviceFriendlyName(ids[i], name, &len);
                    WCHAR maker[256] = L""; DWORD ml = 256; m->GetDeviceManufacturer(ids[i], maker, &ml);
                    out.push_back(utf8(ids[i]) + "\x01" + utf8(name) + "\x01" + utf8(maker));
                    CoTaskMemFree(ids[i]);
                }
            }
        }
        m->Release();
    }
    return strings(env, out);
}

JNIEXPORT jlong JNICALL JN(ptpOpen)(JNIEnv* env, jclass, jstring jid) {
    com();
    std::wstring id = wide(env, jid);
    IPortableDevice* d = nullptr; IPortableDeviceValues* ci = nullptr;
    HRESULT hr = CoCreateInstance(CLSID_PortableDeviceFTM, nullptr, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&d));
    if (SUCCEEDED(hr)) hr = CoCreateInstance(CLSID_PortableDeviceValues, nullptr, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&ci));
    if (SUCCEEDED(hr)) {
        ci->SetStringValue(WPD_CLIENT_NAME, L"AlcorCut Pro");
        ci->SetUnsignedIntegerValue(WPD_CLIENT_MAJOR_VERSION, 1);
        ci->SetUnsignedIntegerValue(WPD_CLIENT_MINOR_VERSION, 0);
        ci->SetUnsignedIntegerValue(WPD_CLIENT_REVISION, 0);
        ci->SetUnsignedIntegerValue(WPD_CLIENT_DESIRED_ACCESS, GENERIC_READ | GENERIC_WRITE);
        hr = d->Open(id.c_str(), ci);
    }
    release(ci);
    if (FAILED(hr)) { fail("The camera couldn't be opened (close other camera apps)", hr); release(d); return 0; }
    return (jlong)d;
}

static HRESULT cmd(IPortableDevice* d, const PROPERTYKEY& key, IPortableDeviceValues* in, IPortableDeviceValues** out) {
    in->SetGuidValue(WPD_PROPERTY_COMMON_COMMAND_CATEGORY, key.fmtid);
    in->SetUnsignedIntegerValue(WPD_PROPERTY_COMMON_COMMAND_ID, key.pid);
    HRESULT hr = d->SendCommand(0, in, out);
    if (SUCCEEDED(hr) && *out) { HRESULT inner = S_OK; if (SUCCEEDED((*out)->GetErrorValue(WPD_PROPERTY_COMMON_HRESULT, &inner))) hr = inner; }
    return hr;
}

static void response(IPortableDeviceValues* r, std::string& out) {
    ULONG code = 0; r->GetUnsignedIntegerValue(WPD_PROPERTY_MTP_EXT_RESPONSE_CODE, &code);
    IPortableDevicePropVariantCollection* ps = nullptr; DWORD n = 0;
    if (SUCCEEDED(r->GetIPortableDevicePropVariantCollectionValue(WPD_PROPERTY_MTP_EXT_RESPONSE_PARAMS, &ps)) && ps) ps->GetCount(&n);
    out.push_back((char)(code & 0xFF)); out.push_back((char)((code >> 8) & 0xFF)); out.push_back((char)n);
    for (DWORD i = 0; i < n; i++) {
        PROPVARIANT v; PropVariantInit(&v); ULONG x = 0;
        if (SUCCEEDED(ps->GetAt(i, &v))) { x = v.ulVal; PropVariantClear(&v); }
        for (int k = 0; k < 4; k++) out.push_back((char)((x >> (8 * k)) & 0xFF));
    }
    release(ps);
}

/**
 * One PTP operation: [op] with up to 5 [params]; [data] is sent to the camera when given, else when
 * [read] the camera's data is read. Returns code (2 bytes), param count (1), params (4 each), then data.
 */
JNIEXPORT jbyteArray JNICALL JN(ptpCommand)(JNIEnv* env, jclass, jlong h, jint op, jintArray jparams, jbyteArray data, jboolean read) {
    com();
    IPortableDevice* d = (IPortableDevice*)h; if (!d) return nullptr;
    IPortableDeviceValues* in = nullptr; IPortableDeviceValues* res = nullptr; IPortableDevicePropVariantCollection* ps = nullptr;
    HRESULT hr = CoCreateInstance(CLSID_PortableDeviceValues, nullptr, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&in));
    if (SUCCEEDED(hr)) hr = CoCreateInstance(CLSID_PortableDevicePropVariantCollection, nullptr, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&ps));
    if (FAILED(hr)) { release(in); release(ps); fail("Windows Portable Devices isn't available", hr); return nullptr; }
    jsize np = jparams ? env->GetArrayLength(jparams) : 0;
    if (np > 0) {
        jint* pp = env->GetIntArrayElements(jparams, nullptr);
        for (jsize i = 0; i < np; i++) { PROPVARIANT v; PropVariantInit(&v); v.vt = VT_UI4; v.ulVal = (ULONG)pp[i]; ps->Add(&v); }
        env->ReleaseIntArrayElements(jparams, pp, JNI_ABORT);
    }
    in->SetUnsignedIntegerValue(WPD_PROPERTY_MTP_EXT_OPERATION_CODE, (ULONG)op);
    in->SetIPortableDevicePropVariantCollectionValue(WPD_PROPERTY_MTP_EXT_OPERATION_PARAMS, ps);
    std::string out; std::string payload;
    const jsize dn = data ? env->GetArrayLength(data) : 0;
    if (!data && !read) {
        hr = cmd(d, WPD_COMMAND_MTP_EXT_EXECUTE_COMMAND_WITHOUT_DATA_PHASE, in, &res);
        if (SUCCEEDED(hr)) response(res, out);
    } else {
        if (data) in->SetUnsignedLargeIntegerValue(WPD_PROPERTY_MTP_EXT_TRANSFER_TOTAL_DATA_SIZE, (ULONGLONG)dn);
        hr = cmd(d, data ? WPD_COMMAND_MTP_EXT_EXECUTE_COMMAND_WITH_DATA_TO_WRITE : WPD_COMMAND_MTP_EXT_EXECUTE_COMMAND_WITH_DATA_TO_READ, in, &res);
        PWSTR ctx = nullptr; ULONGLONG total = 0;
        if (SUCCEEDED(hr)) hr = res->GetStringValue(WPD_PROPERTY_MTP_EXT_TRANSFER_CONTEXT, &ctx);
        if (SUCCEEDED(hr) && read) res->GetUnsignedLargeIntegerValue(WPD_PROPERTY_MTP_EXT_TRANSFER_TOTAL_DATA_SIZE, &total);
        release(res);
        if (SUCCEEDED(hr) && data) {
            jbyte* db = env->GetByteArrayElements(data, nullptr);
            IPortableDeviceValues* w = nullptr;
            if (SUCCEEDED(CoCreateInstance(CLSID_PortableDeviceValues, nullptr, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&w)))) {
                w->SetStringValue(WPD_PROPERTY_MTP_EXT_TRANSFER_CONTEXT, ctx);
                w->SetUnsignedLargeIntegerValue(WPD_PROPERTY_MTP_EXT_TRANSFER_NUM_BYTES_TO_WRITE, (ULONGLONG)dn);
                w->SetBufferValue(WPD_PROPERTY_MTP_EXT_TRANSFER_DATA, (BYTE*)db, (DWORD)dn);
                IPortableDeviceValues* wr = nullptr;
                hr = cmd(d, WPD_COMMAND_MTP_EXT_WRITE_DATA, w, &wr);
                release(wr); release(w);
            }
            env->ReleaseByteArrayElements(data, db, JNI_ABORT);
        }
        if (SUCCEEDED(hr) && read) {
            ULONGLONG left = total; const ULONGLONG chunk = 1 << 20;
            while (SUCCEEDED(hr) && (left > 0 || total == 0)) {
                IPortableDeviceValues* rq = nullptr; IPortableDeviceValues* rr = nullptr;
                if (FAILED(CoCreateInstance(CLSID_PortableDeviceValues, nullptr, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&rq)))) break;
                ULONGLONG want = total == 0 ? chunk : (left < chunk ? left : chunk);
                std::vector<BYTE> empty((size_t)want);
                rq->SetStringValue(WPD_PROPERTY_MTP_EXT_TRANSFER_CONTEXT, ctx);
                rq->SetUnsignedLargeIntegerValue(WPD_PROPERTY_MTP_EXT_TRANSFER_NUM_BYTES_TO_READ, want);
                rq->SetBufferValue(WPD_PROPERTY_MTP_EXT_TRANSFER_DATA, empty.data(), (DWORD)want);
                hr = cmd(d, WPD_COMMAND_MTP_EXT_READ_DATA, rq, &rr);
                ULONGLONG got = 0;
                if (SUCCEEDED(hr)) {
                    BYTE* b = nullptr; DWORD bl = 0;
                    rr->GetUnsignedLargeIntegerValue(WPD_PROPERTY_MTP_EXT_TRANSFER_NUM_BYTES_READ, &got);
                    if (SUCCEEDED(rr->GetBufferValue(WPD_PROPERTY_MTP_EXT_TRANSFER_DATA, &b, &bl)) && b) { payload.append((char*)b, (size_t)(got < bl ? got : bl)); CoTaskMemFree(b); }
                }
                release(rq); release(rr);
                if (got == 0) break;
                if (total > 0) left -= got < left ? got : left;
            }
        }
        if (ctx) {
            IPortableDeviceValues* e = nullptr; IPortableDeviceValues* er = nullptr;
            if (SUCCEEDED(CoCreateInstance(CLSID_PortableDeviceValues, nullptr, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&e)))) {
                e->SetStringValue(WPD_PROPERTY_MTP_EXT_TRANSFER_CONTEXT, ctx);
                HRESULT eh = cmd(d, WPD_COMMAND_MTP_EXT_END_DATA_TRANSFER, e, &er);
                if (SUCCEEDED(eh) && er) response(er, out); else if (SUCCEEDED(hr)) hr = eh;
                release(er); release(e);
            }
            CoTaskMemFree(ctx);
        }
        res = nullptr;
    }
    release(res); release(in); release(ps);
    if (FAILED(hr) || out.empty()) { fail("The camera didn't answer", hr); return nullptr; }
    out += payload;
    return bytes(env, out);
}

JNIEXPORT void JNICALL JN(ptpClose)(JNIEnv* env, jclass, jlong h) {
    com();
    IPortableDevice* d = (IPortableDevice*)h; if (!d) return;
    d->Close(); d->Release();
}

} // extern "C"
