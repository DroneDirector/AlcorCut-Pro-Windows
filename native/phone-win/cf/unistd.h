// Windows stand-ins for the two POSIX file calls the CineForm .mov writer uses. Own code.
#pragma once
#include <io.h>
#include <stdio.h>
#include <stdint.h>
#ifndef CF_SSIZE_T
#define CF_SSIZE_T
typedef long long ssize_t;
#endif
static inline ssize_t pwrite(int fd, const void *p, size_t n, long long at) {
    if (_lseeki64(fd, at, SEEK_SET) < 0) return -1;
    return (ssize_t)_write(fd, p, (unsigned)n);
}
static inline int ftruncate(int fd, long long len) { return _chsize_s(fd, len) == 0 ? 0 : -1; }
