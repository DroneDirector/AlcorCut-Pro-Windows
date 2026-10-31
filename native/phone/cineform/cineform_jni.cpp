// Bridge between the app's CineForm export and the native .mov writer.
#include <jni.h>
#include <cstdint>
#include "cf_mov.h"

#define FN(name) Java_com_dronedirector_veyrocutpro_editor_CineForm_##name

extern "C" {

JNIEXPORT jlong JNICALL FN(nOpen)(JNIEnv *, jclass, jint fd, jint w, jint h, jdouble fps, jint quality, jint rate, jint channels) {
    return (jlong)(intptr_t)cf_open(fd, w, h, fps, quality, rate, channels, 0);
}

/** One picture from the decoder (YUV 4:2:0 planes, with the crop offsets already worked out). */
JNIEXPORT jboolean JNICALL FN(nFrame)(JNIEnv *env, jclass, jlong handle, jint w, jint h,
                                      jobject y, jint yOff, jint yRow, jobject u, jint uOff, jobject v, jint vOff, jint cRow, jint cPixel) {
    CfMov *m = (CfMov *)(intptr_t)handle;
    auto *py = (uint8_t *)env->GetDirectBufferAddress(y);
    auto *pu = (uint8_t *)env->GetDirectBufferAddress(u);
    auto *pv = (uint8_t *)env->GetDirectBufferAddress(v);
    if (!m || !py || !pu || !pv) return JNI_FALSE;
    uint8_t *dst = cf_frame_buffer(m);
    if (!dst) return JNI_FALSE;
    cf_yuv420_to_yuy2(dst, w, h, py + yOff, yRow, pu + uOff, pv + vOff, cRow, cPixel);
    return cf_submit(m) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL FN(nAudio)(JNIEnv *env, jclass, jlong handle, jshortArray pcm, jint frames) {
    CfMov *m = (CfMov *)(intptr_t)handle;
    if (!m) return JNI_FALSE;
    jshort *p = env->GetShortArrayElements(pcm, nullptr);
    bool ok = cf_audio(m, (const int16_t *)p, frames);
    env->ReleaseShortArrayElements(pcm, p, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL FN(nClose)(JNIEnv *, jclass, jlong handle, jboolean keep) {
    return cf_close((CfMov *)(intptr_t)handle, keep == JNI_TRUE) ? JNI_TRUE : JNI_FALSE;
}

}
