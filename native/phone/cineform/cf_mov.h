// CineForm .mov writer (own code around the open-source CineForm SDK).
#pragma once
#include <cstdint>
#include <cmath>

struct CfMov;

/** Starts a .mov on [fd] (read / write). quality 0 medium, 1 high, 2 film scan 1, 3 film scan 2. rate 0 = no sound. */
CfMov *cf_open(int fd, int width, int height, double fps, int quality, int rate, int channels, int threads);
/** The buffer to fill with the next frame (YUY2, width * 2 bytes a row); null on failure. */
uint8_t *cf_frame_buffer(CfMov *m);
/** Hands the filled buffer to the encoders. */
bool cf_submit(CfMov *m);
/** 16-bit interleaved sound, [frames] frames. */
bool cf_audio(CfMov *m, const int16_t *pcm, int frames);
/** Finishes the file (keep = true) or abandons it. Frees [m]. */
bool cf_close(CfMov *m, bool keep);

void cf_yuv420_to_yuy2(uint8_t *dst, int width, int height,
                       const uint8_t *y, int yRow, const uint8_t *u, const uint8_t *v, int cRow, int cPixel);
void cf_rgba_to_yuy2(uint8_t *dst, int width, int height, const uint8_t *rgba, int row);
