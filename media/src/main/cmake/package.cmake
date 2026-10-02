# ============================================================================
# Packages the staged FFmpeg for goldberry-media: run with `cmake -P` by the
# superbuild's ffmpeg_package target.
#
# 1. Copies each of the five libraries under the name the others link against,
#    resolving the version symlinks, because a jar cannot carry a symlink. Any
#    library already in OUT goes first: the natives jar packs the whole
#    directory, and a library under a name no longer built would ship beside
#    the new one.
# 2. Strips them.
# 3. Fails the build when they are larger together than SIZE_LIMIT
#    (target 6 MB, fail above 7).
# 4. Writes ffmpeg-NOTICE.txt: what was built, from which tags, with which
#    configure line, and where the source is: the `ffmpeg-sources` classifier
#    of the same goldberry-media version. The LGPL's relinking promise
#    is only useful to someone who can rebuild what shipped.
#
# In: STAGE, OUT, SYSTEM, STRIP, SIZE_LIMIT, BUILD_SUFFIX, FFMPEG_REF, DAV1D_REF,
#     FFMPEG_COMMIT, DAV1D_COMMIT, CONFIGURE_LINE, TARGET_ID.
# ============================================================================
cmake_minimum_required(VERSION 3.28)

# Load order, and the pinned majors. FfmpegLibrary in Java is the other copy of
# this table; FfmpegNativeTest fails if they disagree, because the file names
# would not be found.
set(_libraries avutil:60 swresample:6 swscale:9 avcodec:62 avformat:62)

file(GLOB _stale "${OUT}/*.so*" "${OUT}/*.dylib" "${OUT}/*.dll")
if(_stale)
    file(REMOVE ${_stale})
endif()

set(_total 0)
set(_shipped "")
foreach(_entry IN LISTS _libraries)
    string(REPLACE ":" ";" _pair "${_entry}")
    list(GET _pair 0 _stem)
    # FFmpeg's FULLNAME: the library's name with --build-suffix after it.
    set(_stem "${_stem}${BUILD_SUFFIX}")
    list(GET _pair 1 _major)
    if(SYSTEM STREQUAL "Darwin")
        set(_name "lib${_stem}.${_major}.dylib")
        set(_from "${STAGE}/lib/${_name}")
    elseif(SYSTEM STREQUAL "Windows")
        set(_name "${_stem}-${_major}.dll")
        set(_from "${STAGE}/bin/${_name}")
    else()
        set(_name "lib${_stem}.so.${_major}")
        set(_from "${STAGE}/lib/${_name}")
    endif()
    if(NOT EXISTS "${_from}")
        message(FATAL_ERROR "FFmpeg did not install ${_from}. Is ${_stem} ${_major} the major the pin builds?")
    endif()
    file(REAL_PATH "${_from}" _real)
    file(COPY_FILE "${_real}" "${OUT}/${_name}")

    if(STRIP AND NOT SYSTEM STREQUAL "Windows")
        if(SYSTEM STREQUAL "Darwin")
            # -x: local symbols only. The exported ones are what FFM looks up.
            execute_process(COMMAND "${STRIP}" -x "${OUT}/${_name}" COMMAND_ERROR_IS_FATAL ANY)
        else()
            execute_process(COMMAND "${STRIP}" --strip-unneeded "${OUT}/${_name}" COMMAND_ERROR_IS_FATAL ANY)
        endif()
    endif()

    file(SIZE "${OUT}/${_name}" _size)
    math(EXPR _total "${_total} + ${_size}")
    math(EXPR _kb "${_size} / 1024")
    string(APPEND _shipped "  ${_name} (${_kb} KB)\n")
endforeach()

math(EXPR _total_kb "${_total} / 1024")
message(STATUS "FFmpeg for ${TARGET_ID}: ${_total_kb} KB in five libraries\n${_shipped}")
if(_total GREATER SIZE_LIMIT)
    math(EXPR _limit_kb "${SIZE_LIMIT} / 1024")
    message(FATAL_ERROR
        "FFmpeg for ${TARGET_ID} is ${_total_kb} KB, over the ${_limit_kb} KB gate.\n"
        "Something was enabled that the configure line did not mean to enable.\n${_shipped}")
endif()

file(WRITE "${OUT}/ffmpeg-NOTICE.txt"
"goldberry-media natives for ${TARGET_ID}

This directory contains FFmpeg (https://ffmpeg.org), licensed under the GNU
Lesser General Public License version 2.1 or later, and dav1d
(https://code.videolan.org/videolan/dav1d), licensed under the BSD 2-Clause
licence and linked statically into libavcodec.

FFmpeg is dynamically linked. You may replace these libraries with your own
build of the same major versions, by pointing -Dgoldberry.media.libdir at a
directory holding them and an ffmpeg-layout.properties produced by
ffmpeg_layout.c against your build's headers. Configure it with
--build-suffix=${BUILD_SUFFIX}, as below, so that its libraries have the names
these have.

FFmpeg  ${FFMPEG_REF}  ${FFMPEG_COMMIT}  https://git.ffmpeg.org/ffmpeg.git
dav1d   ${DAV1D_REF}  ${DAV1D_COMMIT}  https://code.videolan.org/videolan/dav1d.git

The complete corresponding source of these libraries is published beside them,
in the same repository and under the same coordinates, as the `ffmpeg-sources`
classifier of the same goldberry-media version:

  dev.goldberry:goldberry-media:<version>:ffmpeg-sources
  goldberry-media-<version>-ffmpeg-sources.jar

It holds FFmpeg and dav1d at exactly the tags and commits above, the build
recipe that produced this directory, and a README.txt saying how to rebuild
and relink.

FFmpeg was configured with:
  ${CONFIGURE_LINE}

Libraries:
${_shipped}")
