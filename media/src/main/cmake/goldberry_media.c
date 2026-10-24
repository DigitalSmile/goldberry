/*
 * The one source file of libgoldberry-media's own.
 *
 * Everything else in the library is an upstream's: dav1d, libvpx, Opus,
 * libogg, libvorbis, FLAC and libyuv, linked in statically and exported under
 * their own names (exports/goldberry-media.symbols). This file does two things
 * nobody else does:
 *
 *   1. It instantiates minimp3, which is a header-only decoder: the one
 *      translation unit that defines MINIMP3_IMPLEMENTATION is the one whose
 *      mp3dec_init and mp3dec_decode_frame the library exports. Float output,
 *      which is the format the audio sink is fed.
 *
 *   2. It answers which build this is: an ABI number the Java loader checks
 *      before it binds anything, and the upstream versions, for diagnostics
 *      and MediaCapabilities.
 */
#define MINIMP3_IMPLEMENTATION
#define MINIMP3_FLOAT_OUTPUT
#define MINIMP3_NO_STDIO
#include "minimp3.h"

#include <stdio.h>

#include <dav1d/dav1d.h>
#include <vpx/vpx_codec.h>
#include <opus/opus.h>
#include <vorbis/codec.h>
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
        snprintf(line, sizeof line, "dav1d %s; libvpx %s; opus %s; vorbis %s; FLAC %s; minimp3",
                 dav1d_version(), vpx_codec_version_str(), opus_get_version_string(),
                 vorbis_version_string(), FLAC__VERSION_STRING);
    }
    return line;
}
