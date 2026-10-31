/* Minimal uuid.h for Android (no libuuid): random version-4 ids. */
#pragma once
#include <stdlib.h>
#include <string.h>
#include <time.h>
typedef unsigned char uuid_t[16];
static inline void uuid_generate(uuid_t out) {
    static unsigned long long s = 0;
    if (s == 0) s = (unsigned long long)time(NULL) ^ (unsigned long long)(size_t)&s ^ 0x9E3779B97F4A7C15ULL;
    for (int i = 0; i < 16; i++) { s ^= s << 13; s ^= s >> 7; s ^= s << 17; out[i] = (unsigned char)(s >> 24); }
    out[6] = (out[6] & 0x0F) | 0x40; out[8] = (out[8] & 0x3F) | 0x80;
}
static inline void uuid_clear(uuid_t u) { memset(u, 0, 16); }
static inline int uuid_is_null(const uuid_t u) { for (int i = 0; i < 16; i++) if (u[i]) return 0; return 1; }
static inline void uuid_copy(uuid_t d, const uuid_t s) { memcpy(d, s, 16); }
static inline int uuid_compare(const uuid_t a, const uuid_t b) { return memcmp(a, b, 16); }
