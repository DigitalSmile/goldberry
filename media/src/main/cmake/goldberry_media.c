/*
 * The one source file of libgoldberry-media's own.
 *
 * Everything else in the library is an upstream's: dav1d, libvpx, Opus,
 * libogg, libvorbis, libtheora, FLAC and libyuv, linked in statically and
 * exported under their own names (exports/goldberry-media.symbols). This file does three
 * things nobody else does:
 *
 *   1. It instantiates minimp3, which is a header-only decoder: the one
 *      translation unit that defines MINIMP3_IMPLEMENTATION is the one whose
 *      mp3dec_init and mp3dec_decode_frame the library exports. Float output,
 *      which is the format the audio sink is fed.
 *
 *   2. It answers which build this is: an ABI number the Java loader checks
 *      before it binds anything, and the upstream versions, for diagnostics
 *      and MediaCapabilities.
 *
 *   3. It tone-maps CPU present's HDR and wide-gamut pictures to SDR BT.709,
 *      in place, through tables the Java side builds (ToneCurve): libyuv
 *      converts to R'G'B' and nothing upstream maps colour.
 */
#define MINIMP3_IMPLEMENTATION
#define MINIMP3_FLOAT_OUTPUT
#define MINIMP3_NO_STDIO
#include "minimp3.h"

#include <math.h>
#include <stdint.h>
#include <stdio.h>

#include <dav1d/dav1d.h>
#include <vpx/vpx_codec.h>
#include <opus/opus.h>
#include <vorbis/codec.h>
#include <theora/theoradec.h>
#include <FLAC/format.h>

#ifndef GOLDBERRY_MEDIA_ABI
#error "GOLDBERRY_MEDIA_ABI is set by the superbuild"
#endif

/* The ABI of this library: bumped when a symbol leaves the export list or a
 * struct the Java side reads changes shape. The loader refuses a library whose
 * number is not the one it was written against. */
int goldberry_media_abi(void) {
    return GOLDBERRY_MEDIA_ABI;
}

/* The upstream versions, as one line: `dav1d 1.5.4; libvpx v1.17.0; ...`. The
 * libraries that report a version at run time are asked; the ones that only
 * have a compile-time string give that. */
const char *goldberry_media_versions(void) {
    static char line[512];
    if (line[0] == '\0') {
        snprintf(line, sizeof line, "dav1d %s; libvpx %s; opus %s; vorbis %s; theora %s; FLAC %s; minimp3",
                 dav1d_version(), vpx_codec_version_str(), opus_get_version_string(),
                 vorbis_version_string(), th_version_string(), FLAC__VERSION_STRING);
    }
    return line;
}

/* goldberry_media_tone_map's formats: the frame contract's opaque layouts. */
#define TONE_MAP_I420 0
#define TONE_MAP_NV12 1
#define TONE_MAP_I010 2
#define TONE_MAP_P010 3

/* Its flags. */
#define TONE_MAP_HLG 2 /* apply BT.2100's HLG OOTF after decoding */
#define TONE_MAP_EETF 4 /* tone-map the largest channel through the ratio table */

/* The layout of `tables`: a header of constants, then four curves of
 * TONE_MAP_STEPS + 1 points each (ToneCurve.tables()). */
#define TONE_MAP_STEPS 4096
#define TONE_MAP_POINTS (TONE_MAP_STEPS + 1)
#define TONE_MAP_HEADER 32
#define H_LUMA_OFFSET 0
#define H_LUMA_GAIN 1
#define H_CHROMA_OFFSET 2
#define H_CHROMA_GAIN 3
#define H_CR_TO_R 4
#define H_CB_TO_G 5
#define H_CR_TO_G 6
#define H_CB_TO_B 7
#define H_MATRIX 8 /* 9 floats, rows: the picture's primaries to BT.709's, in light */
#define H_HLG_WEIGHTS 17 /* 3 floats: the scene luminance in the picture's primaries */
#define H_RATIO_SCALE 20 /* steps of the ratio table a unit of light */
#define T_DECODE 0 /* R' to light, in units of the target white; HLG's is relative scene light */
#define T_HLG_GAIN 1 /* the OOTF's gain over the square root of the scene luminance */
#define T_RATIO 2 /* m'/m over the largest channel m */
#define T_ENCODE 3 /* the 8-bit value of a light, 0 to 1, over its square root */

/* Table `t` of TONE_MAP_POINTS at `x` steps, read linearly between its two
 * neighbours; clamped at both ends. */
static float lerp_table(const float *t, float x) {
    if (!(x > 0.0f)) {
        return t[0];
    }
    if (x >= (float)TONE_MAP_STEPS) {
        return t[TONE_MAP_STEPS];
    }
    int i = (int)x;
    float f = x - (float)i;
    return t[i] + (t[i + 1] - t[i]) * f;
}

static float clamp01(float v) {
    return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
}

/* One sample of a plane, as a code value in its own bit depth. */
static float code_at(const uint8_t *plane, int stride, int x, int y, int sixteen, int high) {
    const uint8_t *row = plane + (ptrdiff_t)y * stride;
    if (!sixteen) {
        return (float)row[x];
    }
    uint16_t v = (uint16_t)(row[2 * x] | row[2 * x + 1] << 8);
    return high ? (float)v / 64.0f : (float)v;
}

/* Cb (component 0) or Cr (1) at chroma texel (x, y). */
static float chroma_at(const uint8_t *const planes[3], const int strides[3], int format, int component, int x,
                       int y) {
    int sixteen = format == TONE_MAP_I010 || format == TONE_MAP_P010;
    int high = format == TONE_MAP_P010;
    if (format == TONE_MAP_NV12 || format == TONE_MAP_P010) {
        return code_at(planes[1], strides[1], 2 * x + component, y, sixteen, high);
    }
    return code_at(planes[1 + component], strides[1 + component], x, y, sixteen, high);
}

/*
 * Converts a `width` x `height` picture of `format` planes to opaque BGRA in
 * SDR BT.709 at `dst`, `dst_stride` bytes a row, mapping its HDR or wide gamut
 * on the way: CPU present's whole pass for a picture libyuv's conversions
 * would round before the mapping, which a PQ picture's highlights magnify.
 *
 * Y'CbCr to R'G'B' as GPU present does it, in floats: each chroma sample at
 * the centre of its 2x2 block, read bilinearly with the edges repeated; the
 * range and matrix from the header. Then the four steps of ToneCurve through
 * its tables. A plane's stride is in bytes.
 */
void goldberry_media_tone_map(const uint8_t *plane0, int stride0, const uint8_t *plane1, int stride1,
                              const uint8_t *plane2, int stride2, int format, uint8_t *dst, int dst_stride,
                              int width, int height, int flags, const float *tables) {
    const uint8_t *const planes[3] = {plane0, plane1, plane2};
    const int strides[3] = {stride0, stride1, stride2};
    const int sixteen = format == TONE_MAP_I010 || format == TONE_MAP_P010;
    const int high = format == TONE_MAP_P010;
    const float max_code = sixteen ? 1023.0f : 255.0f;
    const int chroma_width = (width + 1) / 2;
    const int chroma_height = (height + 1) / 2;
    const float *h = tables;
    const float *decode = tables + TONE_MAP_HEADER + T_DECODE * TONE_MAP_POINTS;
    const float *hlg_gain = tables + TONE_MAP_HEADER + T_HLG_GAIN * TONE_MAP_POINTS;
    const float *ratio = tables + TONE_MAP_HEADER + T_RATIO * TONE_MAP_POINTS;
    const float *encode = tables + TONE_MAP_HEADER + T_ENCODE * TONE_MAP_POINTS;
    const int ootf = (flags & TONE_MAP_HLG) != 0;
    const int eetf = (flags & TONE_MAP_EETF) != 0;
    const float steps = (float)TONE_MAP_STEPS;
    for (int y = 0; y < height; y++) {
        uint8_t *out = dst + (ptrdiff_t)y * dst_stride;
        float cy = (float)y * 0.5f - 0.25f;
        int y0 = (int)floorf(cy);
        float fy = cy - (float)y0;
        int top = y0 < 0 ? 0 : (y0 >= chroma_height ? chroma_height - 1 : y0);
        int bottom = y0 + 1 >= chroma_height ? chroma_height - 1 : (y0 + 1 < 0 ? 0 : y0 + 1);
        for (int x = 0; x < width; x++) {
            float cx = (float)x * 0.5f - 0.25f;
            int x0 = (int)floorf(cx);
            float fx = cx - (float)x0;
            int left = x0 < 0 ? 0 : (x0 >= chroma_width ? chroma_width - 1 : x0);
            int right = x0 + 1 >= chroma_width ? chroma_width - 1 : (x0 + 1 < 0 ? 0 : x0 + 1);
            float c[2];
            for (int k = 0; k < 2; k++) {
                float upper = chroma_at(planes, strides, format, k, left, top) * (1.0f - fx)
                              + chroma_at(planes, strides, format, k, right, top) * fx;
                float lower = chroma_at(planes, strides, format, k, left, bottom) * (1.0f - fx)
                              + chroma_at(planes, strides, format, k, right, bottom) * fx;
                c[k] = upper * (1.0f - fy) + lower * fy;
            }
            float luma = (code_at(planes[0], strides[0], x, y, sixteen, high) / max_code - h[H_LUMA_OFFSET])
                         * h[H_LUMA_GAIN];
            float blue = (c[0] / max_code - h[H_CHROMA_OFFSET]) * h[H_CHROMA_GAIN];
            float red = (c[1] / max_code - h[H_CHROMA_OFFSET]) * h[H_CHROMA_GAIN];
            float er = clamp01(luma + h[H_CR_TO_R] * red);
            float eg = clamp01(luma + h[H_CB_TO_G] * blue + h[H_CR_TO_G] * red);
            float eb = clamp01(luma + h[H_CB_TO_B] * blue);

            float lr = lerp_table(decode, er * steps);
            float lg = lerp_table(decode, eg * steps);
            float lb = lerp_table(decode, eb * steps);
            if (ootf) {
                float ys = h[H_HLG_WEIGHTS] * lr + h[H_HLG_WEIGHTS + 1] * lg + h[H_HLG_WEIGHTS + 2] * lb;
                float gain = lerp_table(hlg_gain, sqrtf(ys > 0.0f ? ys : 0.0f) * steps);
                lr *= gain;
                lg *= gain;
                lb *= gain;
            }
            const float *m = h + H_MATRIX;
            float mr = m[0] * lr + m[1] * lg + m[2] * lb;
            float mg = m[3] * lr + m[4] * lg + m[5] * lb;
            float mb = m[6] * lr + m[7] * lg + m[8] * lb;
            mr = mr > 0.0f ? mr : 0.0f;
            mg = mg > 0.0f ? mg : 0.0f;
            mb = mb > 0.0f ? mb : 0.0f;
            if (eetf) {
                float largest = mr > mg ? mr : mg;
                largest = largest > mb ? largest : mb;
                if (largest > 0.0f) {
                    float at = largest * h[H_RATIO_SCALE];
                    float k = at >= steps ? 1.0f / largest : lerp_table(ratio, at);
                    mr *= k;
                    mg *= k;
                    mb *= k;
                }
            }
            uint8_t *p = out + 4 * x;
            p[0] = (uint8_t)(lerp_table(encode, sqrtf(mb < 1.0f ? mb : 1.0f) * steps) + 0.5f);
            p[1] = (uint8_t)(lerp_table(encode, sqrtf(mg < 1.0f ? mg : 1.0f) * steps) + 0.5f);
            p[2] = (uint8_t)(lerp_table(encode, sqrtf(mr < 1.0f ? mr : 1.0f) * steps) + 0.5f);
            p[3] = 255;
        }
    }
}
