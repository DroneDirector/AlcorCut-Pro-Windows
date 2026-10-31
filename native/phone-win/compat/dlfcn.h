// Windows: loading an optional library at run time.
#pragma once
#include <windows.h>
#include <stdio.h>
#include <string.h>
#define RTLD_NOW 0
#define RTLD_LAZY 0
static void* dlopen(const char* name, int flags) {
    // "libdf.so" → "df.dll" next to the app's other native libraries.
    char n[260]; const char* s = name; if (!strncmp(s, "lib", 3)) s += 3;
    snprintf(n, sizeof n, "%s", s); char* dot = strstr(n, ".so"); if (dot) strcpy(dot, ".dll");
    return (void*)LoadLibraryA(n);
}
static void* dlsym(void* h, const char* sym) { return (void*)GetProcAddress((HMODULE)h, sym); }
static int dlclose(void* h) { FreeLibrary((HMODULE)h); return 0; }
