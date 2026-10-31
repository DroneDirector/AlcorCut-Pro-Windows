// JNI bridge for the morph cut engine (AlcorCut Pro).
#include <jni.h>
#include <android/bitmap.h>
#include <cstring>
#include <memory>
#include "morph.h"

using namespace morph;

namespace {
struct Session {
    int w = 0, h = 0;
    std::vector<uint8_t> a, b;
    Img la, lb, oa, ob;
    Flow ab, ba;
};

bool readBitmap(JNIEnv* env, jobject bmp, std::vector<uint8_t>& out, int& w, int& h) {
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bmp, &info) != ANDROID_BITMAP_RESULT_SUCCESS) return false;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return false;
    void* px = nullptr;
    if (AndroidBitmap_lockPixels(env, bmp, &px) != ANDROID_BITMAP_RESULT_SUCCESS) return false;
    w = (int)info.width; h = (int)info.height;
    out.resize((size_t)w * h * 4);
    for (int y = 0; y < h; y++) memcpy(&out[(size_t)y * w * 4], (uint8_t*)px + (size_t)y * info.stride, (size_t)w * 4);
    AndroidBitmap_unlockPixels(env, bmp);
    return true;
}
}  // namespace

extern "C" {

/** Analyses two same-size RGBA bitmaps (A = last frame before the cut, B = first after). Returns a handle or 0. */
JNIEXPORT jlong JNICALL Java_com_dronedirector_veyrocutpro_editor_MorphNative_analyze(JNIEnv* env, jclass, jobject bmpA, jobject bmpB) {
    auto s = std::make_unique<Session>();
    int wa, ha, wb, hb;
    if (!readBitmap(env, bmpA, s->a, wa, ha) || !readBitmap(env, bmpB, s->b, wb, hb) || wa != wb || ha != hb || wa < 16 || ha < 16) return 0;
    s->w = wa; s->h = ha;
    s->la = lumaFromRgba(s->a.data(), wa, ha);
    s->lb = lumaFromRgba(s->b.data(), wa, ha);
    s->ab = computeFlow(s->a.data(), s->b.data(), wa, ha);
    s->ba = computeFlow(s->b.data(), s->a.data(), wa, ha);
    s->oa = occlusion(s->ab, s->ba);
    s->ob = occlusion(s->ba, s->ab);
    return (jlong)s.release();
}

/** Draws the in-between frame at t (0..1) into an RGBA bitmap of the analysed size (preview). */
JNIEXPORT jboolean JNICALL Java_com_dronedirector_veyrocutpro_editor_MorphNative_render(JNIEnv* env, jclass, jlong h, jfloat t, jobject out) {
    auto* s = (Session*)h;
    if (!s) return JNI_FALSE;
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, out, &info) != ANDROID_BITMAP_RESULT_SUCCESS || (int)info.width != s->w || (int)info.height != s->h || info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return JNI_FALSE;
    Flow mt = projectFlow(s->ab, s->ba, s->la, s->lb, s->oa, s->ob, t);
    std::vector<uint8_t> buf((size_t)s->w * s->h * 4);
    render(s->a.data(), s->b.data(), s->w, s->h, mt, s->oa, s->ob, t, buf.data());
    void* px = nullptr;
    if (AndroidBitmap_lockPixels(env, out, &px) != ANDROID_BITMAP_RESULT_SUCCESS) return JNI_FALSE;
    for (int y = 0; y < s->h; y++) memcpy((uint8_t*)px + (size_t)y * info.stride, &buf[(size_t)y * s->w * 4], (size_t)s->w * 4);
    AndroidBitmap_unlockPixels(env, out);
    return JNI_TRUE;
}

/**
 * The motion field at t, packed for the GPU into a direct buffer of w·h·4 bytes:
 * (u·32 + 32768) and (v·32 + 32768) as 16-bit values, high byte first (R, G = u; B, A = v).
 */
JNIEXPORT jboolean JNICALL Java_com_dronedirector_veyrocutpro_editor_MorphNative_motion(JNIEnv* env, jclass, jlong h, jfloat t, jobject buf) {
    auto* s = (Session*)h;
    if (!s) return JNI_FALSE;
    auto* p = (uint8_t*)env->GetDirectBufferAddress(buf);
    if (!p || env->GetDirectBufferCapacity(buf) < (jlong)s->w * s->h * 4) return JNI_FALSE;
    Flow mt = projectFlow(s->ab, s->ba, s->la, s->lb, s->oa, s->ob, t);
    for (size_t i = 0, n = (size_t)s->w * s->h; i < n; i++) {
        int u = (int)(mt.u[i] * 32.f + 32768.5f), v = (int)(mt.v[i] * 32.f + 32768.5f);
        u = u < 0 ? 0 : (u > 65535 ? 65535 : u);
        v = v < 0 ? 0 : (v > 65535 ? 65535 : v);
        p[i * 4] = (uint8_t)(u >> 8); p[i * 4 + 1] = (uint8_t)(u & 255);
        p[i * 4 + 2] = (uint8_t)(v >> 8); p[i * 4 + 3] = (uint8_t)(v & 255);
    }
    return JNI_TRUE;
}

/** Occlusion of A (R) and B (G) packed into w·h·4 bytes. */
JNIEXPORT jboolean JNICALL Java_com_dronedirector_veyrocutpro_editor_MorphNative_occlusion(JNIEnv* env, jclass, jlong h, jobject buf) {
    auto* s = (Session*)h;
    if (!s) return JNI_FALSE;
    auto* p = (uint8_t*)env->GetDirectBufferAddress(buf);
    if (!p || env->GetDirectBufferCapacity(buf) < (jlong)s->w * s->h * 4) return JNI_FALSE;
    for (size_t i = 0, n = (size_t)s->w * s->h; i < n; i++) {
        float a = s->oa.d[i], b = s->ob.d[i];
        p[i * 4] = (uint8_t)(a < 0 ? 0 : a > 1 ? 255 : a * 255.f + 0.5f);
        p[i * 4 + 1] = (uint8_t)(b < 0 ? 0 : b > 1 ? 255 : b * 255.f + 0.5f);
        p[i * 4 + 2] = 0; p[i * 4 + 3] = 255;
    }
    return JNI_TRUE;
}

/** Dense flow a → b (same-size RGBA bitmaps) into out (w·h·2 floats: dx, dy per pixel). */
JNIEXPORT jboolean JNICALL Java_com_dronedirector_veyrocutpro_editor_MorphNative_flow(JNIEnv* env, jclass, jobject bmpA, jobject bmpB, jfloatArray out) {
    std::vector<uint8_t> a, b; int wa, ha, wb, hb;
    if (!readBitmap(env, bmpA, a, wa, ha) || !readBitmap(env, bmpB, b, wb, hb) || wa != wb || ha != hb || wa < 16 || ha < 16) return JNI_FALSE;
    if (env->GetArrayLength(out) < (jsize)((size_t)wa * ha * 2)) return JNI_FALSE;
    Flow f = computeFlow(a.data(), b.data(), wa, ha);
    std::vector<float> tmp((size_t)wa * ha * 2);
    for (size_t i = 0, n = (size_t)wa * ha; i < n; i++) { tmp[i * 2] = f.u[i]; tmp[i * 2 + 1] = f.v[i]; }
    env->SetFloatArrayRegion(out, 0, (jsize)tmp.size(), tmp.data());
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL Java_com_dronedirector_veyrocutpro_editor_MorphNative_width(JNIEnv*, jclass, jlong h) { return h ? ((Session*)h)->w : 0; }
JNIEXPORT jint JNICALL Java_com_dronedirector_veyrocutpro_editor_MorphNative_height(JNIEnv*, jclass, jlong h) { return h ? ((Session*)h)->h : 0; }
JNIEXPORT void JNICALL Java_com_dronedirector_veyrocutpro_editor_MorphNative_free(JNIEnv*, jclass, jlong h) { delete (Session*)h; }

}  // extern "C"
