# ============================================================================
# The media superbuild: libgoldberry-media
# https://goldberry.dev/docs/components/media.html#the-module
#
# ONE shared library per target, with the reference decoders linked into it
# statically, and nothing else of a media framework: the demuxers, the clock,
# the I/O and the audio path are Java, and the colour conversion on the CPU is
# libyuv's, in here. Separate from the libgoldberry superbuild in
# natives/src/main/cmake, and on purpose:
#
#   - An application that plays no media should carry none of this. libgoldberry
#     is in every application; this library is in the ones that add
#     goldberry-media, as its `natives-<target>` classifier.
#   - dav1d's build is meson and libvpx's is a configure script. Neither is a
#     CMake project FetchContent can add_subdirectory, so both are
#     ExternalProjects, and the four Xiph libraries follow them for uniformity:
#     every upstream installs into one stage, and the library below links the
#     stage.
#
# Every upstream is BSD-2, BSD-3 or CC0: dav1d, libvpx, Opus, libogg, libvorbis,
# FLAC, libyuv and minimp3. That is what lets them join one library and ship
# under the project's own licence with a notice, where FFmpeg (LGPL) had to stay
# five replaceable shared objects with its source published beside them.
#
# What crosses the boundary is the upstreams' own C APIs, named one per line in
# exports/goldberry-media.symbols; nothing is wrapped. The one source file of
# our own, goldberry_media.c, instantiates minimp3 (a header-only decoder) and
# answers the versions. Everything not on the list stays local to the library,
# so a process that also holds the distribution's dav1d or libvpx -- GStreamer's
# plugins do -- sees no collision.
#
# Output, in ${CMAKE_INSTALL_PREFIX}/lib:
#   libgoldberry-media.so / libgoldberry-media.dylib / goldberry-media.dll, stripped;
#   media-layout.properties, the layout probe's output for this target;
#   media-NOTICE.txt, naming what was built and from which tags.
#
# Driven from Gradle by `:media:nativesBuild` for the host. CI invokes it directly
# per runner, like the libgoldberry superbuild. On Windows it is built with
# MinGW-w64 (MSYS2's UCRT64 environment), not MSVC: libvpx's configure wants a
# POSIX shell and a GNU toolchain, and with one toolchain the Windows leg reads
# like the Linux one. The DLL links its runtime support statically and depends
# on the universal CRT alone.
# ============================================================================
cmake_minimum_required(VERSION 3.28)
project(goldberry_media C CXX)

include(ExternalProject)
include(FetchContent)
include(ProcessorCount)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

if(MSVC)
    message(FATAL_ERROR
        "libgoldberry-media is built with MinGW-w64 on Windows, not MSVC: libvpx's configure "
        "needs a GNU toolchain, and one toolchain keeps the Windows leg like the others. "
        "Configure from an MSYS2 UCRT64 shell, or pass -DCMAKE_C_COMPILER=gcc -DCMAKE_CXX_COMPILER=g++.")
endif()

set(GOLDBERRY_TARGET_ID "host" CACHE STRING "Distribution target id, e.g. linux-x64")
set(GOLDBERRY_MEDIA_DEPS_DIR "${CMAKE_BINARY_DIR}/deps" CACHE PATH
    "Where the upstreams are cloned and built; kept outside build/ by Gradle")
set(GOLDBERRY_MESON "meson" CACHE FILEPATH "meson, for dav1d")
set(GOLDBERRY_MAKE "make" CACHE FILEPATH "make, for libvpx")
# libvpx's configure is a shell script. CMake starts it directly everywhere but
# Windows, where there is no kernel support for `#!` and it has to be handed to
# MSYS2's sh.
set(GOLDBERRY_SH "sh" CACHE FILEPATH "a POSIX sh, for libvpx's configure on Windows")

# The size gate: target 5 MB, fail above 6.
set(GOLDBERRY_MEDIA_SIZE_LIMIT 6291456 CACHE STRING "Largest size of the stripped library, in bytes")

# ----------------------------------------------------------------------------
# Pins, read from gradle/libs.versions.toml and nowhere else. The
# reader is the libgoldberry superbuild's, repeated because the two builds share
# no CMake code; a pin that floats is refused the same way.
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
            # Saved before the next MATCHES, which resets CMAKE_MATCH_1 whether or
            # not it matches.
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

goldberry_pin(dav1d GOLDBERRY_DAV1D_REF)
goldberry_pin(dav1dCommit GOLDBERRY_DAV1D_COMMIT)
goldberry_pin(libvpx GOLDBERRY_LIBVPX_REF)
goldberry_pin(opus GOLDBERRY_OPUS_REF)
goldberry_pin(ogg GOLDBERRY_OGG_REF)
goldberry_pin(vorbis GOLDBERRY_VORBIS_REF)
goldberry_pin(flac GOLDBERRY_FLAC_REF)
goldberry_pin(libyuv GOLDBERRY_LIBYUV_REF)
goldberry_pin(minimp3 GOLDBERRY_MINIMP3_REF)
message(STATUS "goldberry-media pins: dav1d ${GOLDBERRY_DAV1D_REF}, libvpx ${GOLDBERRY_LIBVPX_REF}, "
    "opus ${GOLDBERRY_OPUS_REF}, ogg ${GOLDBERRY_OGG_REF}, vorbis ${GOLDBERRY_VORBIS_REF}, "
    "flac ${GOLDBERRY_FLAC_REF}, libyuv ${GOLDBERRY_LIBYUV_REF}, minimp3 ${GOLDBERRY_MINIMP3_REF}; "
    "target ${GOLDBERRY_TARGET_ID}")

ProcessorCount(_jobs)
if(_jobs EQUAL 0)
    set(_jobs 4)
endif()

# Every upstream installs here, and the probe compiles against these headers.
set(_stage "${GOLDBERRY_MEDIA_DEPS_DIR}/stage")

# Each upstream is a shallow clone at its pinned ref, kept beside the stage so
# that `clean` does not throw it away.
function(goldberry_source _name _repository _ref _out)
    set(${_out}
        GIT_REPOSITORY ${_repository}
        GIT_TAG ${_ref}
        GIT_SHALLOW TRUE
        SOURCE_DIR "${GOLDBERRY_MEDIA_DEPS_DIR}/${_name}-src"
        PARENT_SCOPE)
endfunction()

# The flags every upstream is compiled with: -O2, position-independent, and no
# symbol hidden. Visibility is decided once, at the final link, by the export
# list: a symbol an upstream hid could not be exported from there.
set(_cflags "-O2 -fPIC")
if(APPLE AND CMAKE_OSX_DEPLOYMENT_TARGET)
    string(APPEND _cflags " -mmacosx-version-min=${CMAKE_OSX_DEPLOYMENT_TARGET}")
endif()
set(_cmake_common
    -G Ninja
    -DCMAKE_BUILD_TYPE=Release
    "-DCMAKE_INSTALL_PREFIX=${_stage}"
    -DCMAKE_INSTALL_LIBDIR=lib
    -DCMAKE_POSITION_INDEPENDENT_CODE=ON
    -DBUILD_SHARED_LIBS=OFF
    # libvorbis 1.3.7 still declares a CMake 2.8 minimum, which CMake 4 refuses
    # without this floor; the others are unaffected by it.
    -DCMAKE_POLICY_VERSION_MINIMUM=3.5
    "-DCMAKE_C_FLAGS=${_cflags}"
    "-DCMAKE_CXX_FLAGS=${_cflags}"
    "-DCMAKE_C_COMPILER=${CMAKE_C_COMPILER}"
    "-DCMAKE_CXX_COMPILER=${CMAKE_CXX_COMPILER}"
    "-DCMAKE_MAKE_PROGRAM=${CMAKE_MAKE_PROGRAM}")
if(APPLE AND CMAKE_OSX_DEPLOYMENT_TARGET)
    list(APPEND _cmake_common "-DCMAKE_OSX_DEPLOYMENT_TARGET=${CMAKE_OSX_DEPLOYMENT_TARGET}")
endif()

# ----------------------------------------------------------------------------
# dav1d: the AV1 decoder. meson, static, position-independent. -O2 rather than
# release's -O3: its hot paths are assembly, so -O3 buys no speed and costs
# 255 KB on linux-x64. The buildtype stays release, which keeps NDEBUG and
# trim_dsp. Needs nasm on x86; its arm64 assembly is GNU as.
# ----------------------------------------------------------------------------
goldberry_source(dav1d https://code.videolan.org/videolan/dav1d.git ${GOLDBERRY_DAV1D_REF} _dav1d_source)
ExternalProject_Add(dav1d
    ${_dav1d_source}
    PREFIX "${GOLDBERRY_MEDIA_DEPS_DIR}/dav1d"
    BINARY_DIR "${GOLDBERRY_MEDIA_DEPS_DIR}/dav1d-build"
    CONFIGURE_COMMAND ${GOLDBERRY_MESON} setup <BINARY_DIR> <SOURCE_DIR>
        --prefix=${_stage} --libdir=lib
        --buildtype=release -Doptimization=2 --default-library=static
        -Db_staticpic=true
        -Denable_tools=false -Denable_tests=false -Denable_examples=false -Denable_docs=false
    BUILD_COMMAND ${GOLDBERRY_MESON} compile -C <BINARY_DIR>
    INSTALL_COMMAND ${GOLDBERRY_MESON} install -C <BINARY_DIR>
    BUILD_BYPRODUCTS "${_stage}/lib/libdav1d.a"
    UPDATE_DISCONNECTED TRUE)
find_package(Git QUIET)
ExternalProject_Add_Step(dav1d verify_commit
    COMMAND ${CMAKE_COMMAND} -DGIT=${GIT_EXECUTABLE} -DSOURCE_DIR=<SOURCE_DIR>
        -DNAME=dav1d -DCOMMIT=${GOLDBERRY_DAV1D_COMMIT} -P "${CMAKE_CURRENT_SOURCE_DIR}/checkout.cmake"
    DEPENDEES download
    DEPENDERS configure
    COMMENT "Checking that dav1d is at the pinned commit")

# ----------------------------------------------------------------------------
# libvpx: VP9 and VP8, decoders only. Its configure script, with the encoders,
# the examples, the tools and the tests off, and the 10-bit VP9 profiles on.
# `--as=nasm` on x86: its default assembler is yasm, which nobody else needs.
# ----------------------------------------------------------------------------
goldberry_source(libvpx https://chromium.googlesource.com/webm/libvpx ${GOLDBERRY_LIBVPX_REF} _libvpx_source)
set(GOLDBERRY_LIBVPX_CONFIGURE
    --prefix=${_stage}
    --disable-examples --disable-tools --disable-docs --disable-unit-tests
    --disable-install-bins --disable-install-docs --disable-install-srcs
    --disable-vp8-encoder --disable-vp9-encoder
    --enable-vp9-highbitdepth
    --enable-pic --disable-shared --enable-static
    --extra-cflags=-O2)
if(CMAKE_SYSTEM_PROCESSOR MATCHES "^(x86_64|AMD64|amd64)$")
    list(APPEND GOLDBERRY_LIBVPX_CONFIGURE --as=nasm)
endif()
if(APPLE)
    # configure's own detection names the SDK of the machine it runs on; the
    # target is said so that the one built on a newer machine runs on the floor.
    list(APPEND GOLDBERRY_LIBVPX_CONFIGURE --target=arm64-darwin20-gcc)
endif()
if(WIN32)
    set(_vpx_configure ${GOLDBERRY_SH} <SOURCE_DIR>/configure --target=x86_64-win64-gcc)
else()
    set(_vpx_configure <SOURCE_DIR>/configure)
endif()
ExternalProject_Add(libvpx
    ${_libvpx_source}
    PREFIX "${GOLDBERRY_MEDIA_DEPS_DIR}/libvpx"
    BINARY_DIR "${GOLDBERRY_MEDIA_DEPS_DIR}/libvpx-build"
    CONFIGURE_COMMAND ${CMAKE_COMMAND} -E env "CC=${CMAKE_C_COMPILER}" "CXX=${CMAKE_CXX_COMPILER}"
        ${_vpx_configure} ${GOLDBERRY_LIBVPX_CONFIGURE}
    BUILD_COMMAND ${GOLDBERRY_MAKE} -j${_jobs}
    INSTALL_COMMAND ${GOLDBERRY_MAKE} install
    BUILD_BYPRODUCTS "${_stage}/lib/libvpx.a"
    UPDATE_DISCONNECTED TRUE)

# ----------------------------------------------------------------------------
# Opus. The machine-learning extensions (DRED, OSCE) stay off, which is the
# default; they are weights nobody here plays.
# ----------------------------------------------------------------------------
goldberry_source(opus https://gitlab.xiph.org/xiph/opus.git ${GOLDBERRY_OPUS_REF} _opus_source)
ExternalProject_Add(opus
    ${_opus_source}
    PREFIX "${GOLDBERRY_MEDIA_DEPS_DIR}/opus"
    BINARY_DIR "${GOLDBERRY_MEDIA_DEPS_DIR}/opus-build"
    CMAKE_ARGS ${_cmake_common}
        -DOPUS_BUILD_SHARED_LIBRARY=OFF -DOPUS_BUILD_PROGRAMS=OFF -DOPUS_BUILD_TESTING=OFF
        -DOPUS_INSTALL_PKG_CONFIG_MODULE=OFF -DOPUS_INSTALL_CMAKE_CONFIG_MODULE=OFF
        -DOPUS_DRED=OFF -DOPUS_OSCE=OFF
    BUILD_BYPRODUCTS "${_stage}/lib/libopus.a"
    UPDATE_DISCONNECTED TRUE)

# ----------------------------------------------------------------------------
# libogg and libvorbis. Ogg's framing is read in Java; libogg is here because
# libvorbis's bit reader is libogg's, and its `ogg_packet` is how a packet is
# handed over. Vorbis finds Ogg in the stage, so Ogg installs first.
# ----------------------------------------------------------------------------
goldberry_source(ogg https://gitlab.xiph.org/xiph/ogg.git ${GOLDBERRY_OGG_REF} _ogg_source)
ExternalProject_Add(ogg
    ${_ogg_source}
    PREFIX "${GOLDBERRY_MEDIA_DEPS_DIR}/ogg"
    BINARY_DIR "${GOLDBERRY_MEDIA_DEPS_DIR}/ogg-build"
    CMAKE_ARGS ${_cmake_common}
        -DINSTALL_DOCS=OFF -DINSTALL_PKG_CONFIG_MODULE=OFF -DINSTALL_CMAKE_PACKAGE_MODULE=ON
        -DBUILD_TESTING=OFF
    BUILD_BYPRODUCTS "${_stage}/lib/libogg.a"
    UPDATE_DISCONNECTED TRUE)

goldberry_source(vorbis https://gitlab.xiph.org/xiph/vorbis.git ${GOLDBERRY_VORBIS_REF} _vorbis_source)
ExternalProject_Add(vorbis
    DEPENDS ogg
    ${_vorbis_source}
    PREFIX "${GOLDBERRY_MEDIA_DEPS_DIR}/vorbis"
    BINARY_DIR "${GOLDBERRY_MEDIA_DEPS_DIR}/vorbis-build"
    CMAKE_ARGS ${_cmake_common}
        "-DCMAKE_PREFIX_PATH=${_stage}"
        "-DOGG_INCLUDE_DIR=${_stage}/include" "-DOGG_LIBRARY=${_stage}/lib/libogg.a"
        -DBUILD_TESTING=OFF -DINSTALL_CMAKE_PACKAGE_MODULE=OFF -DINSTALL_PKG_CONFIG_MODULE=OFF
    BUILD_BYPRODUCTS "${_stage}/lib/libvorbis.a"
    UPDATE_DISCONNECTED TRUE)

# ----------------------------------------------------------------------------
# FLAC: the stream decoder alone. No Ogg support: FLAC in Ogg is demuxed in
# Java like FLAC in Matroska, and the decoder sees frames either way.
# ----------------------------------------------------------------------------
goldberry_source(flac https://gitlab.xiph.org/xiph/flac.git ${GOLDBERRY_FLAC_REF} _flac_source)
ExternalProject_Add(flac
    ${_flac_source}
    PREFIX "${GOLDBERRY_MEDIA_DEPS_DIR}/flac"
    BINARY_DIR "${GOLDBERRY_MEDIA_DEPS_DIR}/flac-build"
    CMAKE_ARGS ${_cmake_common}
        -DBUILD_CXXLIBS=OFF -DBUILD_PROGRAMS=OFF -DBUILD_EXAMPLES=OFF -DBUILD_TESTING=OFF
        -DBUILD_DOCS=OFF -DINSTALL_MANPAGES=OFF -DINSTALL_PKGCONFIG_MODULES=OFF
        -DINSTALL_CMAKE_CONFIG_MODULE=OFF -DWITH_OGG=OFF -DWITH_STACK_PROTECTOR=OFF
        -DENABLE_MULTITHREADING=OFF -DWITH_FORTIFY_SOURCE=OFF
    BUILD_BYPRODUCTS "${_stage}/lib/libFLAC.a"
    UPDATE_DISCONNECTED TRUE)

# ----------------------------------------------------------------------------
# libyuv and minimp3 are compiled into the library directly: libyuv's CMake
# project builds a shared copy and a converter tool nobody needs, and minimp3 is
# one header. FetchContent populates them at configure time, into the same
# deps directory, and nothing of theirs is configured.
# ----------------------------------------------------------------------------
FetchContent_Declare(libyuv
    GIT_REPOSITORY https://chromium.googlesource.com/libyuv/libyuv
    GIT_TAG ${GOLDBERRY_LIBYUV_REF}
    SOURCE_DIR "${GOLDBERRY_MEDIA_DEPS_DIR}/libyuv-src"
    SOURCE_SUBDIR does-not-exist)
FetchContent_Declare(minimp3
    GIT_REPOSITORY https://github.com/lieff/minimp3.git
    GIT_TAG ${GOLDBERRY_MINIMP3_REF}
    GIT_SHALLOW FALSE
    SOURCE_DIR "${GOLDBERRY_MEDIA_DEPS_DIR}/minimp3-src"
    SOURCE_SUBDIR does-not-exist)
FetchContent_MakeAvailable(libyuv minimp3)

file(GLOB _libyuv_sources CONFIGURE_DEPENDS "${libyuv_SOURCE_DIR}/source/*.cc")
# The conversion tool's JPEG path and its tests are not library code.
list(FILTER _libyuv_sources EXCLUDE REGEX "(mjpeg_decoder|convert_jpeg|_test)\\.cc$")

# ----------------------------------------------------------------------------
# The library.
# ----------------------------------------------------------------------------
add_library(goldberry_media SHARED goldberry_media.c ${_libyuv_sources})
add_dependencies(goldberry_media dav1d libvpx opus ogg vorbis flac)
set_target_properties(goldberry_media PROPERTIES
    OUTPUT_NAME "goldberry-media"
    C_VISIBILITY_PRESET default
    CXX_VISIBILITY_PRESET default)
if(WIN32)
    # goldberry-media.dll, not libgoldberry-media.dll: System.mapLibraryName's name.
    set_target_properties(goldberry_media PROPERTIES PREFIX "")
endif()
target_include_directories(goldberry_media PRIVATE
    "${_stage}/include"
    "${libyuv_SOURCE_DIR}/include"
    "${minimp3_SOURCE_DIR}")
target_compile_options(goldberry_media PRIVATE -O2)
target_compile_definitions(goldberry_media PRIVATE
    # libyuv's SVE and SME paths need a compiler that knows them; NEON is the
    # arm64 path here, on every arm64 toolchain.
    LIBYUV_DISABLE_SVE LIBYUV_DISABLE_SME
    # The exact blue gain of limited-range BT.601, BT.709 and BT.2020. Without
    # it libyuv caps the coefficient at 128/64 so its SIMD can multiply in
    # signed bytes, and a saturated blue lands up to 12 levels short of what
    # GPU present draws; with it the SIMD multiplies unsigned, the range libyuv's
    # own tests run with.
    LIBYUV_UNLIMITED_DATA
    # The version the probe and the loader agree on: goldberry_media_abi().
    GOLDBERRY_MEDIA_ABI=2)
if(WIN32)
    # FLAC's header exports its API for a DLL unless told the library is static.
    target_compile_definitions(goldberry_media PRIVATE FLAC__NO_DLL)
endif()

# The static archives, dependants first: libvorbis needs libogg.
set(_archives
    "${_stage}/lib/libdav1d.a"
    "${_stage}/lib/libvpx.a"
    "${_stage}/lib/libopus.a"
    "${_stage}/lib/libvorbis.a"
    "${_stage}/lib/libogg.a"
    "${_stage}/lib/libFLAC.a")
target_link_libraries(goldberry_media PRIVATE ${_archives})
if(NOT WIN32)
    target_link_libraries(goldberry_media PRIVATE m pthread)
endif()
if(WIN32)
    # libgcc, libwinpthread and libstdc++ inside the DLL: it depends on the
    # universal CRT and the system libraries alone, like the JDK's own.
    target_link_options(goldberry_media PRIVATE -static -static-libgcc -static-libstdc++)
endif()

# ----------------------------------------------------------------------------
# The export list. exports/goldberry-media.symbols is the single, explicit list
# of what crosses the boundary. For each symbol the build does two things:
# forces the linker to pull it out of its static archive (-u), and marks it
# exported in the platform's own dialect: an ELF version script, Mach-O's
# -exported_symbols_list, a .def for the DLL. Nothing else escapes.
# ----------------------------------------------------------------------------
set(_symbols_file "${CMAKE_CURRENT_SOURCE_DIR}/exports/goldberry-media.symbols")
# The export dialects below are written at configure time, so a changed list
# reconfigures rather than relinking against the old one.
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${_symbols_file}")
file(STRINGS "${_symbols_file}" _symbol_lines)
set(GOLDBERRY_MEDIA_EXPORTED_SYMBOLS "")
foreach(_line IN LISTS _symbol_lines)
    string(STRIP "${_line}" _line)
    if(_line STREQUAL "" OR _line MATCHES "^#")
        continue()
    endif()
    if(NOT _line MATCHES "^[A-Za-z_][A-Za-z0-9_]*$")
        message(FATAL_ERROR
            "exports/goldberry-media.symbols: '${_line}' is not a valid symbol name.\n"
            "Every non-comment line must be a C identifier.")
    endif()
    list(APPEND GOLDBERRY_MEDIA_EXPORTED_SYMBOLS "${_line}")
endforeach()
list(LENGTH GOLDBERRY_MEDIA_EXPORTED_SYMBOLS _symbol_count)
message(STATUS "goldberry-media exports ${_symbol_count} native symbols")
set_property(TARGET goldberry_media APPEND PROPERTY LINK_DEPENDS "${_symbols_file}")

if(APPLE)
    set(_export_list "${CMAKE_BINARY_DIR}/goldberry-media.exported_symbols")
    set(_export_lines "")
    foreach(_symbol IN LISTS GOLDBERRY_MEDIA_EXPORTED_SYMBOLS)
        # Mach-O symbols carry a leading underscore.
        string(APPEND _export_lines "_${_symbol}\n")
        target_link_options(goldberry_media PRIVATE "LINKER:-u,_${_symbol}")
    endforeach()
    file(WRITE "${_export_list}" "${_export_lines}")
    target_link_options(goldberry_media PRIVATE "LINKER:-exported_symbols_list,${_export_list}")
    set_property(TARGET goldberry_media APPEND PROPERTY LINK_DEPENDS "${_export_list}")
elseif(WIN32)
    set(_def_file "${CMAKE_BINARY_DIR}/goldberry-media.def")
    set(_def_lines "LIBRARY goldberry-media\nEXPORTS\n")
    foreach(_symbol IN LISTS GOLDBERRY_MEDIA_EXPORTED_SYMBOLS)
        string(APPEND _def_lines "    ${_symbol}\n")
        target_link_options(goldberry_media PRIVATE "LINKER:-u,${_symbol}")
    endforeach()
    file(WRITE "${_def_file}" "${_def_lines}")
    target_sources(goldberry_media PRIVATE "${_def_file}")
else()
    set(_version_script "${CMAKE_BINARY_DIR}/goldberry-media.map")
    string(REPLACE ";" ";\n    " _sym_block "${GOLDBERRY_MEDIA_EXPORTED_SYMBOLS}")
    file(WRITE "${_version_script}" "GOLDBERRY_MEDIA_1 {\n  global:\n    ${_sym_block};\n  local:\n    *;\n};\n")
    foreach(_symbol IN LISTS GOLDBERRY_MEDIA_EXPORTED_SYMBOLS)
        target_link_options(goldberry_media PRIVATE "LINKER:-u,${_symbol}")
    endforeach()
    target_link_options(goldberry_media PRIVATE
        "LINKER:--version-script=${_version_script}"
        "LINKER:--no-undefined"
        "LINKER:-z,defs")
    set_property(TARGET goldberry_media APPEND PROPERTY LINK_DEPENDS "${_version_script}")
endif()

# ----------------------------------------------------------------------------
# The layout probe (media_layout.c): compiled against the headers the upstreams
# just installed, and run on this machine, since every value it prints is a
# compile-time constant of this target.
# ----------------------------------------------------------------------------
add_executable(media_layout media_layout.c)
add_dependencies(media_layout dav1d libvpx opus ogg vorbis flac)
target_include_directories(media_layout PRIVATE "${_stage}/include" "${libyuv_SOURCE_DIR}/include" "${minimp3_SOURCE_DIR}")
target_compile_definitions(media_layout PRIVATE GOLDBERRY_MEDIA_ABI=2)
if(WIN32)
    target_compile_definitions(media_layout PRIVATE FLAC__NO_DLL)
endif()

set(_out "${CMAKE_INSTALL_PREFIX}/lib")

add_custom_target(media_package ALL
    COMMAND ${CMAKE_COMMAND} -E make_directory "${_out}"
    COMMAND ${CMAKE_COMMAND}
        -DLIBRARY=$<TARGET_FILE:goldberry_media>
        -DSYMBOLS=${_symbols_file}
        -DNM=${CMAKE_NM}
        -DSYSTEM=${CMAKE_SYSTEM_NAME}
        -P "${CMAKE_CURRENT_SOURCE_DIR}/check_exports.cmake"
    COMMAND media_layout "${_out}/media-layout.properties"
    COMMAND ${CMAKE_COMMAND}
        -DLIBRARY=$<TARGET_FILE:goldberry_media>
        -DOUT=${_out}
        -DSYSTEM=${CMAKE_SYSTEM_NAME}
        -DSTRIP=${CMAKE_STRIP}
        -DSIZE_LIMIT=${GOLDBERRY_MEDIA_SIZE_LIMIT}
        -DDAV1D_REF=${GOLDBERRY_DAV1D_REF}
        -DLIBVPX_REF=${GOLDBERRY_LIBVPX_REF}
        -DOPUS_REF=${GOLDBERRY_OPUS_REF}
        -DOGG_REF=${GOLDBERRY_OGG_REF}
        -DVORBIS_REF=${GOLDBERRY_VORBIS_REF}
        -DFLAC_REF=${GOLDBERRY_FLAC_REF}
        -DLIBYUV_REF=${GOLDBERRY_LIBYUV_REF}
        -DMINIMP3_REF=${GOLDBERRY_MINIMP3_REF}
        -DTARGET_ID=${GOLDBERRY_TARGET_ID}
        -P "${CMAKE_CURRENT_SOURCE_DIR}/package.cmake"
    DEPENDS goldberry_media media_layout
    VERBATIM
    COMMENT "Packaging libgoldberry-media for ${GOLDBERRY_TARGET_ID}")

# `cmake --build --target install` is what Gradle runs; the package target above
# already wrote everything into the install prefix, so install() has nothing left
# to do but exist.
install(CODE "message(STATUS \"libgoldberry-media for ${GOLDBERRY_TARGET_ID} is in ${_out}\")")
