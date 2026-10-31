// JNI bridge for RNNoise (voice isolation). Written for AlcorCut Pro.
#include <jni.h>
#include <stdlib.h>
#include "rnnoise.h"

#define FRAME 480

JNIEXPORT jlong JNICALL
Java_com_dronedirector_veyrocutpro_editor_VoiceIsolator_nativeCreate(JNIEnv *env, jclass clazz) {
    return (jlong)(intptr_t) rnnoise_create(NULL);
}

JNIEXPORT void JNICALL
Java_com_dronedirector_veyrocutpro_editor_VoiceIsolator_nativeDestroy(JNIEnv *env, jclass clazz, jlong h) {
    if (h) rnnoise_destroy((DenoiseState *)(intptr_t) h);
}

// in / out: 480 samples at 48 kHz in the 16-bit range (-32768..32767). Returns voice probability.
JNIEXPORT jfloat JNICALL
Java_com_dronedirector_veyrocutpro_editor_VoiceIsolator_nativeProcess(JNIEnv *env, jclass clazz, jlong h, jfloatArray in, jfloatArray out) {
    if (!h) return 0.f;
    float buf_in[FRAME], buf_out[FRAME];
    (*env)->GetFloatArrayRegion(env, in, 0, FRAME, buf_in);
    float vad = rnnoise_process_frame((DenoiseState *)(intptr_t) h, buf_out, buf_in);
    (*env)->SetFloatArrayRegion(env, out, 0, FRAME, buf_out);
    return vad;
}

// ---------------------------------------------------------------- DeepFilterNet3 (studio voice)
// libdf.so (DeepFilterNet, MIT / Apache 2.0) is loaded at run time, so the app still works without it.
#include <dlfcn.h>
typedef void *(*df_create_t)(const char *, float);
typedef size_t (*df_len_t)(void *);
typedef float (*df_proc_t)(void *, float *, float *);
typedef void (*df_free_t)(void *);
typedef void (*df_beta_t)(void *, float);
static void *g_df = NULL;
static df_create_t p_create; static df_len_t p_len; static df_proc_t p_proc; static df_free_t p_free; static df_beta_t p_beta;

static int df_load(void) {
    if (g_df) return 1;
    g_df = dlopen("libdf.so", RTLD_NOW);
    if (!g_df) return 0;
    p_create = (df_create_t) dlsym(g_df, "df_create");
    p_len = (df_len_t) dlsym(g_df, "df_get_frame_length");
    p_proc = (df_proc_t) dlsym(g_df, "df_process_frame");
    p_free = (df_free_t) dlsym(g_df, "df_free");
    p_beta = (df_beta_t) dlsym(g_df, "df_set_post_filter_beta");
    if (!p_create || !p_len || !p_proc || !p_free) { dlclose(g_df); g_df = NULL; return 0; }
    return 1;
}

JNIEXPORT jboolean JNICALL
Java_com_dronedirector_veyrocutpro_editor_VoiceIsolator_nativeDfAvailable(JNIEnv *env, jclass clazz) { return df_load() ? JNI_TRUE : JNI_FALSE; }

// model: path of DeepFilterNet3_onnx.tar.gz; atten: how far the noise may be pushed down (dB).
JNIEXPORT jlong JNICALL
Java_com_dronedirector_veyrocutpro_editor_VoiceIsolator_nativeDfCreate(JNIEnv *env, jclass clazz, jstring model, jfloat atten) {
    if (!df_load() || !model) return 0;
    const char *path = (*env)->GetStringUTFChars(env, model, NULL);
    void *st = p_create(path, atten);
    (*env)->ReleaseStringUTFChars(env, model, path);
    if (st && p_beta) p_beta(st, 0.02f);
    return (jlong)(intptr_t) st;
}

JNIEXPORT jint JNICALL
Java_com_dronedirector_veyrocutpro_editor_VoiceIsolator_nativeDfFrame(JNIEnv *env, jclass clazz, jlong h) { return h ? (jint) p_len((void *)(intptr_t) h) : 0; }

JNIEXPORT void JNICALL
Java_com_dronedirector_veyrocutpro_editor_VoiceIsolator_nativeDfDestroy(JNIEnv *env, jclass clazz, jlong h) { if (h && p_free) p_free((void *)(intptr_t) h); }

// in / out: one frame at 48 kHz in -1..1. Returns the frame's speech-to-noise estimate (dB).
JNIEXPORT jfloat JNICALL
Java_com_dronedirector_veyrocutpro_editor_VoiceIsolator_nativeDfProcess(JNIEnv *env, jclass clazz, jlong h, jfloatArray in, jfloatArray out) {
    if (!h) return 0.f;
    jsize n = (*env)->GetArrayLength(env, in);
    float *bi = (*env)->GetFloatArrayElements(env, in, NULL);
    float *bo = (*env)->GetFloatArrayElements(env, out, NULL);
    float r = p_proc((void *)(intptr_t) h, bi, bo);
    (*env)->ReleaseFloatArrayElements(env, in, bi, JNI_ABORT);
    (*env)->ReleaseFloatArrayElements(env, out, bo, 0);
    (void) n;
    return r;
}
