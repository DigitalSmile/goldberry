# ============================================================================
# Packages libgoldberry-media: run with `cmake -P` by the superbuild's
# media_package target.
#
# 1. Copies the library into OUT under its own name. Any library already in OUT
#    goes first: the natives jar packs the whole directory, and a library under
#    a name no longer built would ship beside the new one.
# 2. Strips it.
# 3. Fails the build when it is larger than SIZE_LIMIT (target 5 MB, fail
#    above 6).
# 4. Writes media-NOTICE.txt: what was built, from which tags, under which
#    licences. Every upstream is BSD or CC0, so the notice is the whole of what
#    their licences ask for; the texts themselves travel in licenses/ as they
#    do for libgoldberry's upstreams.
#
# In: LIBRARY, OUT, SYSTEM, STRIP, SIZE_LIMIT, DAV1D_REF, LIBVPX_REF, OPUS_REF,
#     OGG_REF, VORBIS_REF, FLAC_REF, LIBYUV_REF, MINIMP3_REF, TARGET_ID.
# ============================================================================
cmake_minimum_required(VERSION 3.28)

file(GLOB _stale "${OUT}/*.so" "${OUT}/*.so.*" "${OUT}/*.dylib" "${OUT}/*.dll")
if(_stale)
    file(REMOVE ${_stale})
endif()

get_filename_component(_name "${LIBRARY}" NAME)
file(COPY_FILE "${LIBRARY}" "${OUT}/${_name}")

if(STRIP)
    if(SYSTEM STREQUAL "Darwin")
        # -x: local symbols only. The exported ones are what FFM looks up.
        execute_process(COMMAND "${STRIP}" -x "${OUT}/${_name}" COMMAND_ERROR_IS_FATAL ANY)
    else()
        execute_process(COMMAND "${STRIP}" --strip-unneeded "${OUT}/${_name}" COMMAND_ERROR_IS_FATAL ANY)
    endif()
endif()

file(SIZE "${OUT}/${_name}" _size)
math(EXPR _kb "${_size} / 1024")
message(STATUS "libgoldberry-media for ${TARGET_ID}: ${_name} (${_kb} KB)")
if(_size GREATER SIZE_LIMIT)
    math(EXPR _limit_kb "${SIZE_LIMIT} / 1024")
    message(FATAL_ERROR
        "libgoldberry-media for ${TARGET_ID} is ${_kb} KB, over the ${_limit_kb} KB gate.\n"
        "Something was enabled that the superbuild did not mean to enable.")
endif()

file(WRITE "${OUT}/media-NOTICE.txt"
"goldberry-media natives for ${TARGET_ID}

This directory contains ${_name}, one shared library that links the following
statically. Each is used under its own licence, whose text is in the licenses/
directory of every Goldberry jar.

  dav1d     ${DAV1D_REF}    BSD-2-Clause  https://code.videolan.org/videolan/dav1d
  libvpx    ${LIBVPX_REF}   BSD-3-Clause  https://chromium.googlesource.com/webm/libvpx
  Opus      ${OPUS_REF}     BSD-3-Clause  https://opus-codec.org
  libogg    ${OGG_REF}      BSD-3-Clause  https://xiph.org/ogg
  libvorbis ${VORBIS_REF}   BSD-3-Clause  https://xiph.org/vorbis
  FLAC      ${FLAC_REF}     BSD-3-Clause  https://xiph.org/flac (libFLAC)
  libyuv    ${LIBYUV_REF}   BSD-3-Clause  https://chromium.googlesource.com/libyuv/libyuv
  minimp3   ${MINIMP3_REF}  CC0-1.0       https://github.com/lieff/minimp3

Nothing in this library is under the GPL or the LGPL. It was built by
media/src/main/cmake of the Goldberry repository at the tags and commits
above, which gradle/libs.versions.toml pins.

Library: ${_name} (${_kb} KB)
")
