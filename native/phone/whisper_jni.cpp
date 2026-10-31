// JNI bridge for whisper.cpp (MIT): on-device speech-to-text with word timestamps.
// Written for AlcorCut Pro's text-based editing.
#include <jni.h>
#include <atomic>
#include <string>
#include <thread>
#include "whisper.h"

static std::atomic<int> g_progress{0};
static std::atomic<bool> g_abort{false};

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_dronedirector_veyrocutpro_editor_Transcriber_nativeInit(JNIEnv *env, jclass, jstring path) {
    const char *p = env->GetStringUTFChars(path, nullptr);
    whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(p, cp);
    env->ReleaseStringUTFChars(path, p);
    return (jlong) (intptr_t) ctx;
}

JNIEXPORT void JNICALL
Java_com_dronedirector_veyrocutpro_editor_Transcriber_nativeFree(JNIEnv *, jclass, jlong h) {
    if (h) whisper_free((whisper_context *) (intptr_t) h);
}

JNIEXPORT jint JNICALL
Java_com_dronedirector_veyrocutpro_editor_Transcriber_nativeProgress(JNIEnv *, jclass) { return g_progress.load(); }

JNIEXPORT void JNICALL
Java_com_dronedirector_veyrocutpro_editor_Transcriber_nativeAbort(JNIEnv *, jclass, jboolean a) { g_abort.store(a); }

static void on_progress(whisper_context *, whisper_state *, int p, void *) { g_progress.store(p); }
static bool on_abort(void *) { return g_abort.load(); }

// pcm: 16 kHz mono float. Returns one word per line: "startMs\tendMs\ttext".
JNIEXPORT jstring JNICALL
Java_com_dronedirector_veyrocutpro_editor_Transcriber_nativeTranscribe(JNIEnv *env, jclass, jlong h, jfloatArray pcm, jstring lang, jint threads, jstring prompt) {
    whisper_context *ctx = (whisper_context *) (intptr_t) h;
    if (!ctx) return env->NewStringUTF("");
    g_progress.store(0);
    g_abort.store(false);
    jsize n = env->GetArrayLength(pcm);
    jfloat *data = env->GetFloatArrayElements(pcm, nullptr);
    std::string language = "auto";
    if (lang) { const char *l = env->GetStringUTFChars(lang, nullptr); language = l; env->ReleaseStringUTFChars(lang, l); }
    std::string initial;
    if (prompt) { const char *p = env->GetStringUTFChars(prompt, nullptr); initial = p; env->ReleaseStringUTFChars(prompt, p); }

    whisper_full_params wp = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wp.n_threads = threads > 0 ? threads : (int) std::max(1u, std::thread::hardware_concurrency() / 2);
    wp.print_progress = false; wp.print_realtime = false; wp.print_special = false; wp.print_timestamps = false;
    wp.translate = false;
    wp.no_context = true;
    wp.token_timestamps = true;
    wp.max_len = 1;
    wp.split_on_word = true;
    wp.suppress_blank = true;
    wp.language = language.c_str();
    // A verbatim-style prompt keeps "um", "uh" and repeats in the transcript (Whisper drops them otherwise).
    if (!initial.empty()) wp.initial_prompt = initial.c_str();
    wp.progress_callback = on_progress;
    wp.abort_callback = on_abort;

    int rc = whisper_full(ctx, wp, data, n);
    env->ReleaseFloatArrayElements(pcm, data, JNI_ABORT);
    if (rc != 0) return env->NewStringUTF("");

    std::string out;
    const int segs = whisper_full_n_segments(ctx);
    for (int i = 0; i < segs; i++) {
        const char *t = whisper_full_get_segment_text(ctx, i);
        if (!t) continue;
        long t0 = (long) whisper_full_get_segment_t0(ctx, i) * 10L;
        long t1 = (long) whisper_full_get_segment_t1(ctx, i) * 10L;
        std::string s(t);
        // Tabs / newlines would break the line format.
        for (auto &c : s) if (c == '\t' || c == '\n' || c == '\r') c = ' ';
        out += std::to_string(t0) + "\t" + std::to_string(t1) + "\t" + s + "\n";
    }
    g_progress.store(100);
    return env->NewStringUTF(out.c_str());
}

}
