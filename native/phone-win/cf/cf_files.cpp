// Windows: opens the CineForm output file for the .mov writer (a C runtime file number, binary mode). Own code.
#include <jni.h>
#include <io.h>
#include <fcntl.h>
#include <sys/stat.h>

extern "C" {

JNIEXPORT jint JNICALL Java_com_dronedirector_veyrocutpro_windows_CfFiles_nOpen(JNIEnv *env, jclass, jstring path) {
    const jchar *p = env->GetStringChars(path, nullptr);
    jsize n = env->GetStringLength(path);
    wchar_t buf[2048];
    if (n >= 2047) { env->ReleaseStringChars(path, p); return -1; }
    for (jsize i = 0; i < n; i++) buf[i] = (wchar_t)p[i];
    buf[n] = 0;
    env->ReleaseStringChars(path, p);
    return _wopen(buf, _O_RDWR | _O_CREAT | _O_TRUNC | _O_BINARY, _S_IREAD | _S_IWRITE);
}

JNIEXPORT void JNICALL Java_com_dronedirector_veyrocutpro_windows_CfFiles_nClose(JNIEnv *, jclass, jint fd) {
    if (fd >= 0) _close(fd);
}

}
