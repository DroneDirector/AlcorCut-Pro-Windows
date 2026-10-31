// Included before the .mov writer on Windows: 64-bit file positions (files over 2 GB). Own code.
#pragma once
#include <sys/types.h>
#include "unistd.h"
#define off_t long long
