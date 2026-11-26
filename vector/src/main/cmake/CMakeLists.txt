# ============================================================================
# The vector superbuild: libgoldberry-vector
# https://goldberry.dev/docs/components/drawing.html#image
#
# ONE shared library per target, with ThorVG linked into it statically: its
# SVG loader and its CPU rasterizer, and the PNG and JPEG loaders it carries
# for images a document embeds. Separate from the libgoldberry superbuild in
# natives/src/main/cmake, and on purpose:
#
#   - An application that draws no SVG should carry none of this. libgoldberry
#     is in every application; this library is in the ones that add
#     goldberry-vector, as its `natives-<target>` classifier.
#   - ThorVG's build is meson. It is not a CMake project FetchContent can
#     add_subdirectory, so it is an ExternalProject, as dav1d is in the media
#     superbuild, installing into a stage the library below links.
#
# ThorVG is MIT; its LodePNG is zlib and its jpgd public domain. What is built
# is decided here and nowhere else:
#
#   - The CPU engine only. No OpenGL, no WebGPU.
#   - The svg, png and jpg loaders. No Lottie (the toolkit's own renderer draws
#     Lottie), no fonts (a document's <text> draws nothing), no WebP, no GIF,
#     no video.
#   - No file I/O (-Dfile=false): a document is parsed from the bytes it is
#     handed, and a reference to a file beside it draws nothing. Nothing is
#     fetched either: no asset resolver is ever set.
#   - No task scheduler (-Dthreads=false): the Java side owns the threads.
#   - SIMD off on x64 and on for arm64. ThorVG chooses it at compile time
#     (-mavx2, NEON) with no run-time dispatch, and an x64 build with AVX2
#     would die with SIGILL on a CPU without it. Every arm64 CPU has NEON.
#
# What crosses the boundary is ThorVG's own C API, named one per line in
# exports/goldberry-vector.symbols; nothing is wrapped. The one source file of
# our own, goldberry_vector.c, answers the ABI and the version.
#
# Output, in ${CMAKE_INSTALL_PREFIX}/lib:
#   libgoldberry-vector.so / libgoldberry-vector.dylib / goldberry-vector.dll, stripped;
#   vector.properties, the ABI and the build the Java loader checks;
#   vector-NOTICE.txt, naming what was built and from which commit.
#
# Driven from Gradle by `:vector:nativesBuild` for the host. CI invokes it
# directly per runner. On Windows it is built with MinGW-w64 (MSYS2's UCRT64
# environment), as the media library is, so the two share one toolchain.
# ============================================================================
cmake_minimum_required(VERSION 3.28)
project(goldberry_vector C CXX)

include(ExternalProject)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

if(MSVC)
    message(FATAL_ERROR
        "libgoldberry-vector is built with MinGW-w64 on Windows, not MSVC, as libgoldberry-media is. "
        "Configure from an MSYS2 UCRT64 shell, or pass -DCMAKE_C_COMPILER=gcc -DCMAKE_CXX_COMPILER=g++.")
endif()

set(GOLDBERRY_TARGET_ID "host" CACHE STRING "Distribution target id, e.g. linux-x64")
set(GOLDBERRY_VECTOR_DEPS_DIR "${CMAKE_BINARY_DIR}/deps" CACHE PATH
    "Where ThorVG is cloned and built; kept outside build/ by Gradle")
set(GOLDBERRY_MESON "meson" CACHE FILEPATH "meson, for ThorVG")

# `auto` is the shipped choice above; `on` and `off` exist for measuring it.
set(GOLDBERRY_VECTOR_SIMD "auto" CACHE STRING "ThorVG's SIMD: auto (off on x64, on on arm64), on or off")
if(GOLDBERRY_VECTOR_SIMD STREQUAL "auto")
    if(CMAKE_SYSTEM_PROCESSOR MATCHES "^(aarch64|arm64|ARM64)$")
        set(_simd ON)
    else()
        set(_simd OFF)
    endif()
elseif(GOLDBERRY_VECTOR_SIMD MATCHES "^(on|ON|true|TRUE)$")
    set(_simd ON)
elseif(GOLDBERRY_VECTOR_SIMD MATCHES "^(off|OFF|false|FALSE)$")
    set(_simd OFF)
else()
    message(FATAL_ERROR "GOLDBERRY_VECTOR_SIMD is '${GOLDBERRY_VECTOR_SIMD}'; it takes auto, on or off.")
endif()
if(_simd)
    set(_simd_meson true)
    set(_simd_number 1)
else()
    set(_simd_meson false)
    set(_simd_number 0)
endif()

# The sanitizer build: empty for the library that ships; `address`, or
# `address,undefined`, for the one the sanitizer lane tests against. ThorVG and
# the file of our own are compiled with it, so a read past the end of a
# malformed document is reported where it happens. Kept with its symbols and
# not size-gated, in an install prefix and a deps directory of its own.
set(GOLDBERRY_VECTOR_SANITIZE "" CACHE STRING
    "Sanitizers to build with (-fsanitize=), e.g. address or address,undefined; empty for a release build")
if(GOLDBERRY_VECTOR_SANITIZE)
    if(NOT GOLDBERRY_VECTOR_SANITIZE MATCHES "^(address|undefined|address,undefined|undefined,address)$")
        message(FATAL_ERROR "GOLDBERRY_VECTOR_SANITIZE is '${GOLDBERRY_VECTOR_SANITIZE}'; "
            "it takes address, undefined or address,undefined.")
    endif()
    if(WIN32 OR APPLE)
        message(FATAL_ERROR "The sanitizer build is a Linux build: GCC's libasan, preloaded into the JVM.")
    endif()
    set(_sanitize_flags "-fsanitize=${GOLDBERRY_VECTOR_SANITIZE} -fno-omit-frame-pointer -g")
    message(STATUS "goldberry-vector: sanitizer build (${_sanitize_flags})")
else()
    set(_sanitize_flags "")
endif()

# The size gate: about 640 KB on linux-x64; fail above 1.5 MB, which only a
# loader or an engine the superbuild did not mean to enable reaches. A
# sanitizer build is several times the size and is never shipped.
set(GOLDBERRY_VECTOR_SIZE_LIMIT 1572864 CACHE STRING "Largest size of the stripped library, in bytes")
if(GOLDBERRY_VECTOR_SANITIZE)
    set(_size_limit 0)
else()
    set(_size_limit ${GOLDBERRY_VECTOR_SIZE_LIMIT})
endif()

# ----------------------------------------------------------------------------
# Pins, read from gradle/libs.versions.toml and nowhere else, by the reader the
# other two superbuilds repeat; a pin that floats is refused the same way.
# ----------------------------------------------------------------------------
set(GOLDBERRY_VERSION_CATALOG "${CMAKE_CURRENT_SOURCE_DIR}/../../../../gradle/libs.versions.toml"
    CACHE FILEPATH "gradle/libs.versions.toml -- the one place upstream refs are pinned")
if(NOT EXISTS "${GOLDBERRY_VERSION_CATALOG}")
    message(FATAL_ERROR "The version catalog is not at '${GOLDBERRY_VERSION_CATALOG}'.")
endif()
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${GOLDBERRY_VERSION_CATALOG}")
file(STRINGS "${GOLDBERRY_VERSION_CATALOG}" _catalog_lines)

function(goldberry_pin _key _out)
    foreach(_line IN LISTS _catalog_lines)
        if(_line MATCHES "^[ \t]*${_key}[ \t]*=[ \t]*\"([^\"]*)\"")
            set(_value "${CMAKE_MATCH_1}")
            if(_value MATCHES "^(master|main|HEAD|latest|trunk|stable)$")
                message(FATAL_ERROR "gradle/libs.versions.toml pins ${_key} to '${_value}', which floats; pin a tag or a commit.")
            endif()
            set(${_out} "${_value}" PARENT_SCOPE)
            return()
        endif()
    endforeach()
    message(FATAL_ERROR "gradle/libs.versions.toml has no `${_key} = \"...\"` under [versions].")
endfunction()

goldberry_pin(thorvg GOLDBERRY_THORVG_REF)
goldberry_pin(thorvgCommit GOLDBERRY_THORVG_COMMIT)
message(STATUS "goldberry-vector pins: ThorVG ${GOLDBERRY_THORVG_REF} (${GOLDBERRY_THORVG_COMMIT}); "
    "SIMD ${_simd}; target ${GOLDBERRY_TARGET_ID}")

set(_stage "${GOLDBERRY_VECTOR_DEPS_DIR}/stage")

# ----------------------------------------------------------------------------
# ThorVG. meson, static, position-independent.
#
# -fvisibility=default after ThorVG's own -fvisibility=hidden: a static build
# defines TVG_STATIC, which leaves its API without a visibility attribute, so
# without this every tvg_* function would be hidden in the archive and no
# export list could put it back. Visibility is decided once, at the final link,
# by the export list, as in the media library.
# ----------------------------------------------------------------------------
set(_cflags "-fvisibility=default")
if(APPLE AND CMAKE_OSX_DEPLOYMENT_TARGET)
    string(APPEND _cflags " -mmacosx-version-min=${CMAKE_OSX_DEPLOYMENT_TARGET}")
endif()
set(_meson_link "")
if(_sanitize_flags)
    string(APPEND _cflags " ${_sanitize_flags}")
    set(_meson_link "-Dcpp_link_args=-fsanitize=${GOLDBERRY_VECTOR_SANITIZE}" -Db_lundef=false)
endif()

# Patches on every target. apply_patch.cmake stops the configure when a
# bump moves the lines under one, and each patch's hash is part of the command,
# so a changed patch reapplies. Drop one when upstream fixes the same.
#
#   patches/thorvg-bounded-strokes.patch: the dasher steps one dash at a time
#   until a segment's length is used up; for a length that is not finite, or so
#   long that subtracting a dash leaves it as it was in float, it never stops
#   and appends a dash a step until memory runs out (a 150-byte document with
#   `stroke-dasharray` on a line to y=1e12). Such a path is stroked undashed, as
#   Skia does past a million dashes. And the stroker itself never finishes a
#   path with a point at no finite place (a rounded <rect> of height -inf), so
#   such a path, or one past 10^18, draws no stroke.
#
#   patches/thorvg-bounded-coordinates.patch: the rasterizer's cells are 24.8
#   fixed point in int32, so a point a few million pixels out overflows a
#   difference and a line's cell walk runs until the counter wraps (a <use>
#   that scaled a star by -2^31 drew for hours). Points are clamped to 2^21
#   pixels, and one that is not a number goes to the origin.
#
#   patches/thorvg-svg-character-bytes.patch: the C runtime's character
#   classifiers take unsigned bytes. UTF-8 class names and style values can
#   contain bytes above 127 even on a target where plain char is signed.

find_package(Git REQUIRED)
set(_thorvg_patch_command "")
foreach(_patch_name IN ITEMS thorvg-bounded-strokes thorvg-bounded-coordinates thorvg-svg-character-bytes)
    set(_patch "${CMAKE_CURRENT_SOURCE_DIR}/patches/${_patch_name}.patch")
    file(SHA256 "${_patch}" _patch_hash)
    set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${_patch}")
    if(_thorvg_patch_command)
        list(APPEND _thorvg_patch_command COMMAND)
    endif()
    list(APPEND _thorvg_patch_command ${CMAKE_COMMAND} -DGIT=${GIT_EXECUTABLE} -DPATCH=${_patch}
        -DPATCH_HASH=${_patch_hash} -P "${CMAKE_CURRENT_SOURCE_DIR}/apply_patch.cmake")
endforeach()
ExternalProject_Add(thorvg
    GIT_REPOSITORY https://github.com/thorvg/thorvg.git
    GIT_TAG ${GOLDBERRY_THORVG_REF}
    GIT_SHALLOW TRUE
    SOURCE_DIR "${GOLDBERRY_VECTOR_DEPS_DIR}/thorvg-src"
    PREFIX "${GOLDBERRY_VECTOR_DEPS_DIR}/thorvg"
    BINARY_DIR "${GOLDBERRY_VECTOR_DEPS_DIR}/thorvg-build"
    CONFIGURE_COMMAND ${CMAKE_COMMAND} -E env "CC=${CMAKE_C_COMPILER}" "CXX=${CMAKE_CXX_COMPILER}"
        ${GOLDBERRY_MESON} setup <BINARY_DIR> <SOURCE_DIR>
        --prefix=${_stage} --libdir=lib
        --buildtype=release --default-library=static
        -Db_staticpic=true
        -Dengines=cpu
        -Dloaders=svg,png,jpg
        -Dsavers=
        -Dbindings=capi
        -Dtools=
        -Dtests=false
        -Dlog=false
        -Dstatic=true
        -Dfile=false
        -Dthreads=false
        -Dpartial=false
        -Dextra=
        -Dsimd=${_simd_meson}
        "-Dcpp_args=${_cflags}"
        ${_meson_link}
    BUILD_COMMAND ${GOLDBERRY_MESON} compile -C <BINARY_DIR>
    INSTALL_COMMAND ${GOLDBERRY_MESON} install -C <BINARY_DIR>
    BUILD_BYPRODUCTS "${_stage}/lib/libthorvg-1.a"
    PATCH_COMMAND ${_thorvg_patch_command}
    UPDATE_DISCONNECTED TRUE)
ExternalProject_Add_Step(thorvg verify_commit
    COMMAND ${CMAKE_COMMAND} -DGIT=${GIT_EXECUTABLE} -DSOURCE_DIR=<SOURCE_DIR>
        -DNAME=ThorVG -DCOMMIT=${GOLDBERRY_THORVG_COMMIT} -P "${CMAKE_CURRENT_SOURCE_DIR}/checkout.cmake"
    DEPENDEES download
    DEPENDERS configure
    COMMENT "Checking that ThorVG is at the pinned commit")

# ----------------------------------------------------------------------------
# The library.
# ----------------------------------------------------------------------------
# The version the loader checks before it binds anything: goldberry_vector_abi().
set(GOLDBERRY_VECTOR_ABI 1)

add_library(goldberry_vector SHARED goldberry_vector.c)
add_dependencies(goldberry_vector thorvg)
set_target_properties(goldberry_vector PROPERTIES
    OUTPUT_NAME "goldberry-vector"
    C_VISIBILITY_PRESET default
    # ThorVG is C++: the C++ driver links it, so its runtime support comes along.
    LINKER_LANGUAGE CXX)
if(WIN32)
    # goldberry-vector.dll, not libgoldberry-vector.dll: System.mapLibraryName's name.
    set_target_properties(goldberry_vector PROPERTIES PREFIX "")
endif()
target_include_directories(goldberry_vector PRIVATE "${_stage}/include/thorvg-1")
target_compile_options(goldberry_vector PRIVATE -O2)
target_compile_definitions(goldberry_vector PRIVATE
    # The archive is static: no dllimport on Windows.
    TVG_STATIC
    GOLDBERRY_VECTOR_ABI=${GOLDBERRY_VECTOR_ABI}
    GOLDBERRY_VECTOR_SIMD=${_simd_number})
if(_sanitize_flags)
    separate_arguments(_sanitize_list UNIX_COMMAND "${_sanitize_flags}")
    target_compile_options(goldberry_vector PRIVATE ${_sanitize_list})
    # GCC links its sanitizer runtimes as shared libraries; the JVM gets libasan
    # first through LD_PRELOAD.
    target_link_options(goldberry_vector PRIVATE "-fsanitize=${GOLDBERRY_VECTOR_SANITIZE}")
endif()
target_link_libraries(goldberry_vector PRIVATE "${_stage}/lib/libthorvg-1.a")
if(NOT WIN32 AND NOT APPLE)
    target_link_libraries(goldberry_vector PRIVATE m pthread)
endif()
if(WIN32)
    # libgcc, libwinpthread and libstdc++ inside the DLL: it depends on the
    # universal CRT and the system libraries alone, like the media library.
    target_link_options(goldberry_vector PRIVATE -static -static-libgcc -static-libstdc++)
endif()

# ----------------------------------------------------------------------------
# The export list. exports/goldberry-vector.symbols is the single, explicit list
# of what crosses the boundary. For each symbol the build forces the linker to
# pull it out of the archive (-u) and marks it exported in the platform's own
# dialect: an ELF version script, Mach-O's -exported_symbols_list, a .def for
# the DLL. Nothing else escapes, so a process that also holds the system's
# ThorVG sees no collision.
# ----------------------------------------------------------------------------
set(_symbols_file "${CMAKE_CURRENT_SOURCE_DIR}/exports/goldberry-vector.symbols")
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${_symbols_file}")
file(STRINGS "${_symbols_file}" _symbol_lines)
set(_exported "")
foreach(_line IN LISTS _symbol_lines)
    string(STRIP "${_line}" _line)
    if(_line STREQUAL "" OR _line MATCHES "^#")
        continue()
    endif()
    if(NOT _line MATCHES "^[A-Za-z_][A-Za-z0-9_]*$")
        message(FATAL_ERROR
            "exports/goldberry-vector.symbols: '${_line}' is not a valid symbol name.\n"
            "Every non-comment line must be a C identifier.")
    endif()
    list(APPEND _exported "${_line}")
endforeach()
list(LENGTH _exported _symbol_count)
message(STATUS "goldberry-vector exports ${_symbol_count} native symbols")
set_property(TARGET goldberry_vector APPEND PROPERTY LINK_DEPENDS "${_symbols_file}")

if(APPLE)
    set(_export_list "${CMAKE_BINARY_DIR}/goldberry-vector.exported_symbols")
    set(_export_lines "")
    foreach(_symbol IN LISTS _exported)
        string(APPEND _export_lines "_${_symbol}\n")
        target_link_options(goldberry_vector PRIVATE "LINKER:-u,_${_symbol}")
    endforeach()
    file(WRITE "${_export_list}" "${_export_lines}")
    target_link_options(goldberry_vector PRIVATE "LINKER:-exported_symbols_list,${_export_list}")
    set_property(TARGET goldberry_vector APPEND PROPERTY LINK_DEPENDS "${_export_list}")
elseif(WIN32)
    set(_def_file "${CMAKE_BINARY_DIR}/goldberry-vector.def")
    set(_def_lines "LIBRARY goldberry-vector\nEXPORTS\n")
    foreach(_symbol IN LISTS _exported)
        string(APPEND _def_lines "    ${_symbol}\n")
        target_link_options(goldberry_vector PRIVATE "LINKER:-u,${_symbol}")
    endforeach()
    file(WRITE "${_def_file}" "${_def_lines}")
    target_sources(goldberry_vector PRIVATE "${_def_file}")
else()
    set(_version_script "${CMAKE_BINARY_DIR}/goldberry-vector.map")
    string(REPLACE ";" ";\n    " _sym_block "${_exported}")
    file(WRITE "${_version_script}" "GOLDBERRY_VECTOR_1 {\n  global:\n    ${_sym_block};\n  local:\n    *;\n};\n")
    foreach(_symbol IN LISTS _exported)
        target_link_options(goldberry_vector PRIVATE "LINKER:-u,${_symbol}")
    endforeach()
    target_link_options(goldberry_vector PRIVATE
        "LINKER:--version-script=${_version_script}"
        "LINKER:--no-undefined"
        "LINKER:-z,defs")
    set_property(TARGET goldberry_vector APPEND PROPERTY LINK_DEPENDS "${_version_script}")
endif()

set(_out "${CMAKE_INSTALL_PREFIX}/lib")
if(_sanitize_flags)
    set(_strip "")
else()
    set(_strip "${CMAKE_STRIP}")
endif()

add_custom_target(vector_package ALL
    COMMAND ${CMAKE_COMMAND} -E make_directory "${_out}"
    COMMAND ${CMAKE_COMMAND}
        -DLIBRARY=$<TARGET_FILE:goldberry_vector>
        -DSYMBOLS=${_symbols_file}
        -DNM=${CMAKE_NM}
        -DSYSTEM=${CMAKE_SYSTEM_NAME}
        -P "${CMAKE_CURRENT_SOURCE_DIR}/check_exports.cmake"
    COMMAND ${CMAKE_COMMAND}
        -DLIBRARY=$<TARGET_FILE:goldberry_vector>
        -DOUT=${_out}
        -DSYSTEM=${CMAKE_SYSTEM_NAME}
        -DSTRIP=${_strip}
        -DSIZE_LIMIT=${_size_limit}
        -DSANITIZE=${GOLDBERRY_VECTOR_SANITIZE}
        -DABI=${GOLDBERRY_VECTOR_ABI}
        -DSIMD=${_simd_number}
        -DTHORVG_REF=${GOLDBERRY_THORVG_REF}
        -DTHORVG_COMMIT=${GOLDBERRY_THORVG_COMMIT}
        -DTARGET_ID=${GOLDBERRY_TARGET_ID}
        -P "${CMAKE_CURRENT_SOURCE_DIR}/package.cmake"
    DEPENDS goldberry_vector
    VERBATIM
    COMMENT "Packaging libgoldberry-vector for ${GOLDBERRY_TARGET_ID}")

install(CODE "message(STATUS \"libgoldberry-vector for ${GOLDBERRY_TARGET_ID} is in ${_out}\")")
