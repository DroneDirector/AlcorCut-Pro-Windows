// Windows: the phone's bitmap access for native code, on the app's Bitmap (ARGB ints, read and
// written through JNI; RGBA bytes while locked, as on the phone).
#pragma once
#include <jni.h>
#include <cstdint>
#include <cstdlib>
#include <mutex>
#include <vector>

#define ANDROID_BITMAP_RESULT_SUCCESS 0
#define ANDROID_BITMAP_RESULT_BAD_PARAMETER -1
#define ANDROID_BITMAP_FORMAT_RGBA_8888 1

typedef struct { uint32_t width, height, stride; int32_t format; uint32_t flags; } AndroidBitmapInfo;

namespace winbmp {
struct Lock { jobject ref; jintArray arr; uint8_t* buf; int n; };
inline std::mutex& mu() { static std::mutex m; return m; }
inline std::vector<Lock>& locks() { static std::vector<Lock> v; return v; }
inline jintArray pixels(JNIEnv* env, jobject bmp) {
    jclass c = env->GetObjectClass(bmp);
    jmethodID m = env->GetMethodID(c, "nativePixels", "()[I");
    return (jintArray)env->CallObjectMethod(bmp, m);
}
}

static inline int AndroidBitmap_getInfo(JNIEnv* env, jobject bmp, AndroidBitmapInfo* info) {
    if (!bmp || !info) return ANDROID_BITMAP_RESULT_BAD_PARAMETER;
    jclass c = env->GetObjectClass(bmp);
    info->width = (uint32_t)env->CallIntMethod(bmp, env->GetMethodID(c, "getWidth", "()I"));
    info->height = (uint32_t)env->CallIntMethod(bmp, env->GetMethodID(c, "getHeight", "()I"));
    info->stride = info->width * 4; info->format = ANDROID_BITMAP_FORMAT_RGBA_8888; info->flags = 0;
    return ANDROID_BITMAP_RESULT_SUCCESS;
}

static inline int AndroidBitmap_lockPixels(JNIEnv* env, jobject bmp, void** px) {
    jintArray arr = winbmp::pixels(env, bmp);
    if (!arr) return ANDROID_BITMAP_RESULT_BAD_PARAMETER;
    int n = env->GetArrayLength(arr);
    uint8_t* buf = (uint8_t*)malloc((size_t)n * 4);
    jint* p = env->GetIntArrayElements(arr, nullptr);
    for (int i = 0; i < n; i++) {
        uint32_t v = (uint32_t)p[i]; uint32_t a = v >> 24;
        uint32_t r = (v >> 16) & 255, g = (v >> 8) & 255, b = v & 255;
        if (a != 255) { r = r * a / 255; g = g * a / 255; b = b * a / 255; }   // premultiplied, like the phone
        buf[i * 4] = (uint8_t)r; buf[i * 4 + 1] = (uint8_t)g; buf[i * 4 + 2] = (uint8_t)b; buf[i * 4 + 3] = (uint8_t)a;
    }
    env->ReleaseIntArrayElements(arr, p, JNI_ABORT);
    std::lock_guard<std::mutex> g(winbmp::mu());
    winbmp::locks().push_back({ env->NewGlobalRef(bmp), (jintArray)env->NewGlobalRef(arr), buf, n });
    *px = buf;
    return ANDROID_BITMAP_RESULT_SUCCESS;
}

static inline int AndroidBitmap_unlockPixels(JNIEnv* env, jobject bmp) {
    std::lock_guard<std::mutex> g(winbmp::mu());
    auto& v = winbmp::locks();
    for (size_t k = 0; k < v.size(); k++) {
        if (!env->IsSameObject(v[k].ref, bmp)) continue;
        winbmp::Lock l = v[k];
        jint* p = env->GetIntArrayElements(l.arr, nullptr);
        for (int i = 0; i < l.n; i++) {
            uint32_t r = l.buf[i * 4], gg = l.buf[i * 4 + 1], b = l.buf[i * 4 + 2], a = l.buf[i * 4 + 3];
            if (a != 255 && a != 0) { r = r * 255 / a; gg = gg * 255 / a; b = b * 255 / a; if (r > 255) r = 255; if (gg > 255) gg = 255; if (b > 255) b = 255; }
            p[i] = (jint)((a << 24) | (r << 16) | (gg << 8) | b);
        }
        env->ReleaseIntArrayElements(l.arr, p, 0);
        jclass c = env->GetObjectClass(bmp);
        env->CallVoidMethod(bmp, env->GetMethodID(c, "nativeChanged", "()V"));
        free(l.buf); env->DeleteGlobalRef(l.ref); env->DeleteGlobalRef(l.arr);
        v.erase(v.begin() + (long)k);
        return ANDROID_BITMAP_RESULT_SUCCESS;
    }
    return ANDROID_BITMAP_RESULT_BAD_PARAMETER;
}
