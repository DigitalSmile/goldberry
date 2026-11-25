# ============================================================================
# Packages libgoldberry-vector: run with `cmake -P` by the superbuild's
# vector_package target.
#
# 1. Copies the library into OUT under its own name. Any library already in OUT
#    goes first: the natives jar packs the whole directory, and a library under
#    a name no longer built would ship beside the new one.
# 2. Strips it, unless STRIP is empty: a sanitizer build keeps its symbols.
# 3. Fails the build when it is larger than SIZE_LIMIT. A SIZE_LIMIT of 0, a
#    sanitizer build's, gates nothing.
# 4. Writes vector.properties, what the Java loader checks before it loads the
#    library and the natives jar task checks before it packs the directory.
# 5. Writes vector-NOTICE.txt: what was built, from which commit, under which
#    licences. The texts themselves travel in licenses/ in every Goldberry jar.
#
# In: LIBRARY, OUT, SYSTEM, STRIP, SIZE_LIMIT, SANITIZE, ABI, SIMD, THORVG_REF,
#     THORVG_COMMIT, TARGET_ID.
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
message(STATUS "libgoldberry-vector for ${TARGET_ID}: ${_name} (${_kb} KB)")
if(SIZE_LIMIT GREATER 0 AND _size GREATER SIZE_LIMIT)
    math(EXPR _limit_kb "${SIZE_LIMIT} / 1024")
    message(FATAL_ERROR
        "libgoldberry-vector for ${TARGET_ID} is ${_kb} KB, over the ${_limit_kb} KB gate.\n"
        "Something was enabled that the superbuild did not mean to enable.")
endif()

if(SIMD)
    set(_simd "on")
else()
    set(_simd "off")
endif()
if(SANITIZE)
    set(_sanitize "${SANITIZE}")
else()
    set(_sanitize "none")
endif()
file(WRITE "${OUT}/vector.properties"
"# libgoldberry-vector for ${TARGET_ID}, written by vector/src/main/cmake/package.cmake.
abi=${ABI}
target=${TARGET_ID}
library=${_name}
thorvg=${THORVG_REF}
thorvg.commit=${THORVG_COMMIT}
simd=${_simd}
sanitize=${_sanitize}
")

file(WRITE "${OUT}/vector-NOTICE.txt"
"goldberry-vector natives for ${TARGET_ID}

This directory contains ${_name}, one shared library that links the following
statically. Each is used under its own licence, whose text is in
licenses/thorvg.txt in every Goldberry jar.

  ThorVG    ${THORVG_REF} (${THORVG_COMMIT})  MIT  https://github.com/thorvg/thorvg
    with, inside it:
  LodePNG   as ThorVG carries it               Zlib  https://github.com/lvandeve/lodepng
  jpgd      as ThorVG carries it               public domain

ThorVG was built with the CPU engine, the svg, png and jpg loaders, no file
I/O, no task scheduler and SIMD ${_simd}. It was built by vector/src/main/cmake
of the Goldberry repository at the commit above, which gradle/libs.versions.toml
pins, with Goldberry's patches from vector/src/main/cmake/patches/:
thorvg-bounded-strokes.patch draws a dashed stroke whose dashes would number
more than a million undashed and strokes no path with a point at no finite
place, and thorvg-bounded-coordinates.patch clamps a point more than 2^21
pixels out to that distance. thorvg-svg-character-bytes.patch passes unsigned
bytes to the C runtime when the SVG parser classifies characters.

Library: ${_name} (${_kb} KB)
")
if(SANITIZE)
    file(APPEND "${OUT}/vector-NOTICE.txt" "
Built with -fsanitize=${SANITIZE} for testing; not for distribution.
")
endif()
