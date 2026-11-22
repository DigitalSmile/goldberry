/*
 * The one source file of libgoldberry-vector's own.
 *
 * Everything else in the library is ThorVG's, linked in statically and
 * exported under its own names through its C API (exports/goldberry-vector.symbols).
 * This file answers which build this is: an ABI number the Java loader checks
 * before it binds anything, and a line naming the ThorVG version and how it
 * was configured, for diagnostics.
 */
#include <stdint.h>
#include <stdio.h>

#include <thorvg_capi.h>

#ifndef GOLDBERRY_VECTOR_ABI
#error "GOLDBERRY_VECTOR_ABI is set by the superbuild"
#endif

#ifndef GOLDBERRY_VECTOR_SIMD
#error "GOLDBERRY_VECTOR_SIMD is set by the superbuild"
#endif

/* The ABI of this library: bumped when a symbol leaves the export list or the
 * meaning of one changes. The loader refuses a library whose number is not the
 * one it was written against. */
int goldberry_vector_abi(void) {
    return GOLDBERRY_VECTOR_ABI;
}

/* `ThorVG 1.1.4; cpu; svg png jpg; simd off`, asked of ThorVG itself. */
const char *goldberry_vector_versions(void) {
    static char line[128];
    if (line[0] == '\0') {
        const char *version = NULL;
        uint32_t major = 0, minor = 0, micro = 0;
        if (tvg_engine_version(&major, &minor, &micro, &version) != TVG_RESULT_SUCCESS || version == NULL) {
            version = "unknown";
        }
        snprintf(line, sizeof line, "ThorVG %s; cpu; svg png jpg; simd %s", version,
                 GOLDBERRY_VECTOR_SIMD ? "on" : "off");
    }
    return line;
}
