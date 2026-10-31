#pragma once
#include <stdio.h>
#define ANDROID_LOG_INFO 4
#define ANDROID_LOG_WARN 5
#define ANDROID_LOG_ERROR 6
#define ANDROID_LOG_DEBUG 3
#define __android_log_print(prio, tag, ...) (fprintf(stderr, __VA_ARGS__), fprintf(stderr, "\n"), 0)
