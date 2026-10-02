# ============================================================================
# The media superbuild: FFmpeg + dav1d for goldberry-media
# https://goldberry.dev/docs/components/media.html#the-module
#
# Separate from the libgoldberry superbuild in natives/src/main/cmake, and on
# purpose:
#
#   - FFmpeg is LGPL-2.1+. It must stay a set of SHARED libraries that a user can
#     replace, so it cannot be linked into libgoldberry the way every other upstream
#     is.
#   - Its build is autotools-style `configure` + make, and dav1d's is meson. Neither
#     is a CMake project FetchContent can add_subdirectory, so both are
#     ExternalProjects.
#   - An application that plays no media should not need a toolchain that can
#     build FFmpeg to build Goldberry.
#
# Output, in ${CMAKE_INSTALL_PREFIX}/lib:
#   the five libraries under the names they link against each other by
#     (libavcodec-goldberry.62.dylib, libavcodec-goldberry.so.62,
#     avcodec-goldberry-62.dll), stripped;
#   ffmpeg-layout.properties, the layout probe's output for this target;
#   ffmpeg-NOTICE.txt, with the tags and the configure line the LGPL asks for.
#
# The same files, with gradle/libs.versions.toml, are the build recipe in the
# `ffmpeg-sources` classifier. GOLDBERRY_FFMPEG_SOURCE_DIR and
# GOLDBERRY_DAV1D_SOURCE_DIR build from that jar's source trees instead of
# cloning, with nothing fetched.
#
# Driven from Gradle by `:media:ffmpegBuild` for the host. CI invokes it directly
# per runner, like the libgoldberry superbuild.
# ============================================================================
cmake_minimum_required(VERSION 3.28)
project(goldberry_media C)

include(ExternalProject)
include(ProcessorCount)

set(GOLDBERRY_TARGET_ID "host" CACHE STRING "Distribution target id, e.g. linux-x64")
set(GOLDBERRY_MEDIA_DEPS_DIR "${CMAKE_BINARY_DIR}/deps" CACHE PATH
    "Where FFmpeg and dav1d are cloned and built; kept outside build/ by Gradle")
set(GOLDBERRY_MESON "meson" CACHE FILEPATH "meson, for dav1d")
set(GOLDBERRY_PKG_CONFIG "pkg-config" CACHE FILEPATH "pkg-config, for FFmpeg's configure to find dav1d")
set(GOLDBERRY_MAKE "make" CACHE FILEPATH "make, for FFmpeg")
# FFmpeg's configure is a shell script. CMake starts it directly everywhere but
# Windows, where there is no kernel support for `#!` and it has to be handed to
# MSYS2's sh.
set(GOLDBERRY_SH "sh" CACHE FILEPATH "a POSIX sh, for FFmpeg's configure on Windows")

# Empty: clone each upstream at its pinned tag, as every build of the shipped
# libraries does. A directory: build from that tree instead, which is how the
# `ffmpeg-sources` classifier is rebuilt with no network (its README.txt).
set(GOLDBERRY_FFMPEG_SOURCE_DIR "" CACHE PATH "An FFmpeg source tree to build instead of cloning one")
set(GOLDBERRY_DAV1D_SOURCE_DIR "" CACHE PATH "A dav1d source tree to build instead of cloning one")

# The VP9/AV1 hardware decoders. On by default where the
# hwaccel needs only what the OS ships: VideoToolbox on macOS and D3D11 on
# Windows are system frameworks. Off by default on Linux: VAAPI is libva, which
# libavutil would then link directly, so a machine without libva could not load
# FFmpeg at all, not even to play audio. `:media:ffmpegConfigure` passes the
# value explicitly (-Pgoldberry.media.hwaccel), so a cached OFF from an older
# build does not linger.
if(APPLE OR WIN32)
    set(_hwaccel_default ON)
else()
    set(_hwaccel_default OFF)
endif()
option(GOLDBERRY_MEDIA_HWACCEL "Build the VP9/AV1 hardware decoders (phase 5)" ${_hwaccel_default})

# Every library's name carries this suffix: libavcodec-goldberry.so.62,
# not libavcodec.so.62. A distribution's FFmpeg of the same major has the plain
# names, and a process that loads both would otherwise get one of them twice:
# glibc satisfies a DT_NEEDED with any library already loaded under that soname,
# so GStreamer's gst-libav, loaded after this FFmpeg, linked against it and lost
# its H.264 decoder. FfmpegPlatform.BUILD_SUFFIX is the other copy, and
# FfmpegSuperbuildTest holds the two together.
set(GOLDBERRY_FFMPEG_BUILD_SUFFIX "-goldberry")

# The size gate: target 6 MB, fail above 7.
set(GOLDBERRY_MEDIA_SIZE_LIMIT 7340032 CACHE STRING "Largest total size of the five stripped libraries, in bytes")

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
            if(_value MATCHES "^(master|main|HEAD|latest|trunk)$")
                message(FATAL_ERROR "gradle/libs.versions.toml pins ${_key} to '${_value}', which floats; pin a tag or a commit.")
            endif()
            set(${_out} "${_value}" PARENT_SCOPE)
            return()
        endif()
    endforeach()
    message(FATAL_ERROR "gradle/libs.versions.toml has no `${_key} = \"...\"` under [versions].")
endfunction()

goldberry_pin(ffmpeg GOLDBERRY_FFMPEG_REF)
goldberry_pin(dav1d GOLDBERRY_DAV1D_REF)
# The commits those tags name: a clone that is not at them is refused,
# so the `ffmpeg-sources` classifier, which is refused the same way, is the
# source of what this builds.
goldberry_pin(ffmpegCommit GOLDBERRY_FFMPEG_COMMIT)
goldberry_pin(dav1dCommit GOLDBERRY_DAV1D_COMMIT)
message(STATUS "goldberry-media pins: ffmpeg ${GOLDBERRY_FFMPEG_REF}, dav1d ${GOLDBERRY_DAV1D_REF}; target ${GOLDBERRY_TARGET_ID}")

ProcessorCount(_jobs)
if(_jobs EQUAL 0)
    set(_jobs 4)
endif()

# Both upstreams install here, and the probe compiles against these headers.
set(_stage "${GOLDBERRY_MEDIA_DEPS_DIR}/stage")

# Where each upstream's source comes from: a shallow clone at the pinned tag,
# checked against the pinned commit once it is fetched, or a tree handed over.
find_package(Git QUIET)
function(goldberry_source _name _repository _ref _given _out)
    if(_given)
        if(NOT EXISTS "${_given}")
            message(FATAL_ERROR "No ${_name} source at '${_given}'.")
        endif()
        set(${_out} SOURCE_DIR "${_given}" PARENT_SCOPE)
    else()
        set(${_out}
            GIT_REPOSITORY ${_repository}
            GIT_TAG ${_ref}
            GIT_SHALLOW TRUE
            SOURCE_DIR "${GOLDBERRY_MEDIA_DEPS_DIR}/${_name}-src"
            PARENT_SCOPE)
    endif()
endfunction()

function(goldberry_verify_checkout _name _commit _given)
    if(NOT _given)
        ExternalProject_Add_Step(${_name} verify_commit
            COMMAND ${CMAKE_COMMAND} -DGIT=${GIT_EXECUTABLE} -DSOURCE_DIR=<SOURCE_DIR>
                -DNAME=${_name} -DCOMMIT=${_commit} -P "${CMAKE_CURRENT_SOURCE_DIR}/checkout.cmake"
            DEPENDEES download
            DEPENDERS configure
            COMMENT "Checking that ${_name} is at the pinned commit")
    endif()
endfunction()

goldberry_source(dav1d https://code.videolan.org/videolan/dav1d.git ${GOLDBERRY_DAV1D_REF}
    "${GOLDBERRY_DAV1D_SOURCE_DIR}" _dav1d_source)
goldberry_source(ffmpeg https://git.ffmpeg.org/ffmpeg.git ${GOLDBERRY_FFMPEG_REF}
    "${GOLDBERRY_FFMPEG_SOURCE_DIR}" _ffmpeg_source)

# ----------------------------------------------------------------------------
# dav1d: the software AV1 decoder, built static and position-independent so it
# links INTO libavcodec. One fewer file to ship and load, and BSD-2 asks nothing
# of that arrangement but the notice.
#
# -O2 rather than release's -O3, like FFmpeg below: its hot paths are
# assembly, so -O3 buys no speed and costs 255 KB on linux-x64. The buildtype
# stays release, which keeps NDEBUG and trim_dsp.
# ----------------------------------------------------------------------------
# MSVC's runtime as a DLL, as libgoldberry and FFmpeg below link it: one C
# runtime in the process, the one the JDK already carries in its bin directory.
# Stated rather than left to meson's default, because a static library built
# against the other one fails to link into FFmpeg with unresolved `__imp_`
# symbols.
if(WIN32)
    set(_dav1d_crt -Db_vscrt=md)
else()
    set(_dav1d_crt "")
endif()

ExternalProject_Add(dav1d
    ${_dav1d_source}
    PREFIX "${GOLDBERRY_MEDIA_DEPS_DIR}/dav1d"
    BINARY_DIR "${GOLDBERRY_MEDIA_DEPS_DIR}/dav1d-build"
    CONFIGURE_COMMAND ${GOLDBERRY_MESON} setup <BINARY_DIR> <SOURCE_DIR>
        --prefix=${_stage} --libdir=lib
        --buildtype=release -Doptimization=2 --default-library=static
        -Db_staticpic=true ${_dav1d_crt}
        -Denable_tools=false -Denable_tests=false -Denable_examples=false -Denable_docs=false
    BUILD_COMMAND ${GOLDBERRY_MESON} compile -C <BINARY_DIR>
    INSTALL_COMMAND ${GOLDBERRY_MESON} install -C <BINARY_DIR>
    UPDATE_DISCONNECTED TRUE)
goldberry_verify_checkout(dav1d ${GOLDBERRY_DAV1D_COMMIT} "${GOLDBERRY_DAV1D_SOURCE_DIR}")

if(WIN32)
    # meson names a static library libdav1d.a with every compiler, and FFmpeg's
    # MSVC toolchain turns pkg-config's -ldav1d into dav1d.lib. The copy is the
    # name the linker looks for.
    ExternalProject_Add_Step(dav1d msvc_library_name
        COMMAND ${CMAKE_COMMAND} -E copy_if_different "${_stage}/lib/libdav1d.a" "${_stage}/lib/dav1d.lib"
        DEPENDEES install
        COMMENT "Naming dav1d's static library the way MSVC's linker asks for it")
endif()

# ----------------------------------------------------------------------------
# FFmpeg: the configure line, in one list so that the
# NOTICE can quote exactly what was built.
#
# --disable-autodetect is what keeps the result reproducible: without it configure
# links whatever it finds on the build machine (iconv, SecureTransport, VAAPI,
# zlib) and two runners produce two different libraries.
#
# --optflags=-O2 replaces configure's -O3. GCC's -O3 unrolls and
# vectorises FFmpeg's C templates, and doubles swscale: 8976 KB on linux-x64,
# over the 7 MB gate, against 6696 KB at -O2 with dav1d at -O2 too. Decoding
# runs in hand-written assembly and measured the same at both.
# ----------------------------------------------------------------------------
set(GOLDBERRY_FFMPEG_CONFIGURE
    --disable-everything --disable-programs --disable-doc --disable-network
    --disable-avdevice --disable-avfilter --disable-autodetect
    --enable-shared --disable-static --enable-pic --disable-debug --optflags=-O2
    --build-suffix=${GOLDBERRY_FFMPEG_BUILD_SUFFIX}
    --enable-demuxer=matroska,mov,avi,ogg,flac,mp3,wav,srt,webvtt,ass
    --enable-decoder=vp8,vp9,av1,libdav1d,opus,vorbis,flac,mp3,pcm_s16le,pcm_s24le,pcm_f32le,subrip,ass,webvtt,mov_text
    --enable-parser=vp8,vp9,av1,opus,vorbis,flac,mpegaudio
    --enable-bsf=vp9_superframe,vp9_superframe_split,av1_frame_merge
    --enable-libdav1d)

if(APPLE)
    # The five libraries find each other beside themselves, wherever they are
    # unpacked, rather than at this machine's install prefix.
    list(APPEND GOLDBERRY_FFMPEG_CONFIGURE --install-name-dir=@loader_path)
    if(GOLDBERRY_MEDIA_HWACCEL)
        list(APPEND GOLDBERRY_FFMPEG_CONFIGURE --enable-videotoolbox
             --enable-hwaccel=vp9_videotoolbox,av1_videotoolbox)
    endif()
elseif(WIN32)
    # Configure runs under MSYS2's shell with MSVC as the compiler, as the Media
    # workflow sets it up. -MD is the runtime dav1d is built with above: cl's
    # default is the static one, and the two do not link together.
    list(APPEND GOLDBERRY_FFMPEG_CONFIGURE --toolchain=msvc --target-os=win64 --arch=x86_64 --extra-cflags=-MD)
    if(GOLDBERRY_MEDIA_HWACCEL)
        list(APPEND GOLDBERRY_FFMPEG_CONFIGURE --enable-d3d11va
             --enable-hwaccel=vp9_d3d11va2,av1_d3d11va2)
    endif()
else()
    # No rpath: FfmpegLibraries loads the five in dependency order, and glibc
    # resolves a DT_NEEDED against an already-loaded soname before it searches.
    if(GOLDBERRY_MEDIA_HWACCEL)
        list(APPEND GOLDBERRY_FFMPEG_CONFIGURE --enable-vaapi
             --enable-hwaccel=vp8_vaapi,vp9_vaapi,av1_vaapi)
    endif()
endif()

# Never --enable-gpl, --enable-nonfree or --enable-version3: the libraries stay
# LGPL-2.1+. Checked rather than trusted, because the list above is edited by hand.
foreach(_forbidden --enable-gpl --enable-nonfree --enable-version3)
    if(_forbidden IN_LIST GOLDBERRY_FFMPEG_CONFIGURE)
        message(FATAL_ERROR "${_forbidden} would change FFmpeg's licence away from LGPL-2.1+, which goldberry-media ships under.")
    endif()
endforeach()

# The configure script, run the way this host can run it (see GOLDBERRY_SH).
if(WIN32)
    set(_configure ${GOLDBERRY_SH} <SOURCE_DIR>/configure)
else()
    set(_configure <SOURCE_DIR>/configure)
endif()

ExternalProject_Add(ffmpeg
    DEPENDS dav1d
    ${_ffmpeg_source}
    PREFIX "${GOLDBERRY_MEDIA_DEPS_DIR}/ffmpeg"
    BINARY_DIR "${GOLDBERRY_MEDIA_DEPS_DIR}/ffmpeg-build"
    CONFIGURE_COMMAND ${CMAKE_COMMAND} -E env PKG_CONFIG_PATH=${_stage}/lib/pkgconfig
        ${_configure} --prefix=${_stage}
        --pkg-config=${GOLDBERRY_PKG_CONFIG} --pkg-config-flags=--static
        ${GOLDBERRY_FFMPEG_CONFIGURE}
    BUILD_COMMAND ${GOLDBERRY_MAKE} -j${_jobs}
    INSTALL_COMMAND ${GOLDBERRY_MAKE} install
    UPDATE_DISCONNECTED TRUE)
goldberry_verify_checkout(ffmpeg ${GOLDBERRY_FFMPEG_COMMIT} "${GOLDBERRY_FFMPEG_SOURCE_DIR}")

# ----------------------------------------------------------------------------
# The layout probe (ffmpeg_layout.c): compiled against the headers FFmpeg just
# installed, and run on this machine, since every value it prints is a
# compile-time constant of this target.
# ----------------------------------------------------------------------------
add_executable(ffmpeg_layout ffmpeg_layout.c)
add_dependencies(ffmpeg_layout ffmpeg)
target_include_directories(ffmpeg_layout PRIVATE "${_stage}/include")
set_target_properties(ffmpeg_layout PROPERTIES C_STANDARD 11 C_STANDARD_REQUIRED ON)

set(_out "${CMAKE_INSTALL_PREFIX}/lib")
string(REPLACE ";" " " _configure_line "${GOLDBERRY_FFMPEG_CONFIGURE}")

add_custom_target(ffmpeg_package ALL
    COMMAND ${CMAKE_COMMAND} -E make_directory "${_out}"
    COMMAND ffmpeg_layout "${_out}/ffmpeg-layout.properties"
    COMMAND ${CMAKE_COMMAND}
        -DSTAGE=${_stage}
        -DOUT=${_out}
        -DSYSTEM=${CMAKE_SYSTEM_NAME}
        -DSTRIP=${CMAKE_STRIP}
        -DSIZE_LIMIT=${GOLDBERRY_MEDIA_SIZE_LIMIT}
        -DBUILD_SUFFIX=${GOLDBERRY_FFMPEG_BUILD_SUFFIX}
        -DFFMPEG_REF=${GOLDBERRY_FFMPEG_REF}
        -DDAV1D_REF=${GOLDBERRY_DAV1D_REF}
        -DFFMPEG_COMMIT=${GOLDBERRY_FFMPEG_COMMIT}
        -DDAV1D_COMMIT=${GOLDBERRY_DAV1D_COMMIT}
        "-DCONFIGURE_LINE=${_configure_line}"
        -DTARGET_ID=${GOLDBERRY_TARGET_ID}
        -P "${CMAKE_CURRENT_SOURCE_DIR}/package.cmake"
    DEPENDS ffmpeg ffmpeg_layout
    VERBATIM
    COMMENT "Packaging FFmpeg for ${GOLDBERRY_TARGET_ID}")

# `cmake --build --target install` is what Gradle runs; the package target above
# already wrote everything into the install prefix, so install() has nothing left
# to do but exist.
install(CODE "message(STATUS \"FFmpeg for ${GOLDBERRY_TARGET_ID} is in ${_out}\")")
