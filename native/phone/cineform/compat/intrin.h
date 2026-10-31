#pragma once
#if defined(__aarch64__) || defined(__arm__)
#include "sse2neon.h"
#else
#include_next <intrin.h>
#endif
