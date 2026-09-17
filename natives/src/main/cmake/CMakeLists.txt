# libgoldberry -- the single native artifact (docs/ARCHITECTURE.md §3.2)
#
# Statically links every native dependency into ONE shared library, so
# distribution is a single file per platform/arch, following the LWJGL model.
# Built natively on each target's own runner (ADR-0012) -- there is no
# cross-compilation toolchain. Two runners cross-target within their own native
# toolchain: MSVC produces ARM64 from an x64 host with `-A ARM64`, and Xcode on
# Apple Silicon produces x86_64 via CMAKE_OSX_ARCHITECTURES.
#
# On Linux the build runs inside a manylinux_2_28 container, which is what pins
# the glibc floor at 2.28. A library built outside that container links against
# the host's glibc and is therefore NOT the publishable artifact.
#
# STATUS: builds and links on all four targets, so the upstream refs, the CMake
# target names, and all three branches of the export model below -- the ELF
# version script, Mach-O's -exported_symbols_list, and MSVC's /INCLUDE: and .def
# -- are confirmed against the pinned revisions rather than argued about. The
# Windows leg is also the only one that could catch Win64's 4-byte `long`, and
# the layout table passes there.

cmake_minimum_required(VERSION 3.28)

project(goldberry
    VERSION 0.1.0
    DESCRIPTION "Goldberry native core"
    LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Everything is compiled PIC because it all ends up inside one shared library.
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Static dependencies, one shared output. Hide everything that is not ours so
# statically linked upstreams do not leak symbols into the host process -- an
# app embedding its own SDL or HarfBuzz must not collide with ours.
set(BUILD_SHARED_LIBS OFF)
set(CMAKE_C_VISIBILITY_PRESET hidden)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_VISIBILITY_INLINES_HIDDEN ON)

include(FetchContent)
include(ExternalProject)

# ============================================================================
# Pinned upstream refs (ADR-0030, ADR-0035)
#
# Read straight out of gradle/libs.versions.toml, which is the ONLY place a ref
# is written down. They used to be duplicated here and in every CI workflow,
# because the manylinux container has no JDK to read the catalog with -- but the
# JDK was never the thing that could read it. CMake can, and already parses a
# text file this way for the export list below.
#
# So there is no default to drift: a ref this build does not find in the catalog
# is a hard error rather than a stale copy that quietly builds something else.
# `./gradlew :natives:checkPinnedRefs` now checks that no copy has come back.
# ============================================================================
set(GOLDBERRY_VERSION_CATALOG "${CMAKE_CURRENT_SOURCE_DIR}/../../../../gradle/libs.versions.toml"
    CACHE FILEPATH "gradle/libs.versions.toml -- the one place upstream refs are pinned")

if(NOT EXISTS "${GOLDBERRY_VERSION_CATALOG}")
    message(FATAL_ERROR
        "The version catalog is not at '${GOLDBERRY_VERSION_CATALOG}'.\n"
        "Every upstream ref is pinned there and nowhere else (ADR-0035). Configuring "
        "from outside a full checkout needs -DGOLDBERRY_VERSION_CATALOG=<path>.")
endif()

# Editing the catalog re-runs configure, so a bumped ref is re-fetched rather
# than silently kept from the last build's _deps.
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${GOLDBERRY_VERSION_CATALOG}")

file(STRINGS "${GOLDBERRY_VERSION_CATALOG}" _catalog_lines)

# Reads `key = "value"` out of the catalog's [versions] table.
#
# Anchored at the start of the line and requiring a quoted scalar, so a
# [libraries] entry that merely mentions the same word -- `version.ref = "yoga"`
# inside a braced table -- cannot match.
function(goldberry_pin _key _out)
    foreach(_line IN LISTS _catalog_lines)
        if(_line MATCHES "^[ \t]*${_key}[ \t]*=[ \t]*\"([^\"]*)\"")
            set(_value "${CMAKE_MATCH_1}")

            # A pin, not a range (ADR-0030). A floating ref makes the artifact
            # irreproducible, and it is invisible: the build succeeds, against
            # whatever upstream happened to have committed that morning.
            if(_value MATCHES "^(master|main|HEAD|latest|trunk)$")
                message(FATAL_ERROR
                    "gradle/libs.versions.toml pins ${_key} to '${_value}', which floats.\n"
                    "Upstream refs must be a tag or a commit SHA so the artifact is "
                    "reproducible. See ADR-0030.")
            endif()

            set(${_out} "${_value}" PARENT_SCOPE)
            return()
        endif()
    endforeach()
    message(FATAL_ERROR
        "gradle/libs.versions.toml has no `${_key} = \"...\"` under [versions].\n"
        "Every upstream this build fetches is pinned there (ADR-0035).")
endfunction()

goldberry_pin(blend2d GOLDBERRY_BLEND2D_REF)
goldberry_pin(asmjit GOLDBERRY_ASMJIT_REF)
goldberry_pin(yoga GOLDBERRY_YOGA_REF)
goldberry_pin(harfbuzz GOLDBERRY_HARFBUZZ_REF)
goldberry_pin(sdl3 GOLDBERRY_SDL3_REF)
goldberry_pin(md4c GOLDBERRY_MD4C_REF)
goldberry_pin(libwebp GOLDBERRY_LIBWEBP_REF)

message(STATUS "Goldberry pins: blend2d ${GOLDBERRY_BLEND2D_REF}, asmjit ${GOLDBERRY_ASMJIT_REF}, "
    "yoga ${GOLDBERRY_YOGA_REF}, harfbuzz ${GOLDBERRY_HARFBUZZ_REF}, "
    "sdl3 ${GOLDBERRY_SDL3_REF}, md4c ${GOLDBERRY_MD4C_REF}, "
    "libwebp ${GOLDBERRY_LIBWEBP_REF}")

set(GOLDBERRY_TARGET_ID "host" CACHE STRING "Distribution target id, e.g. linux-x64")

message(STATUS "Goldberry target: ${GOLDBERRY_TARGET_ID} (${CMAKE_SYSTEM_NAME}/${CMAKE_SYSTEM_PROCESSOR})")

# ============================================================================
# The desktop integrations, asked for on purpose (docs/gaps.md G32, ADR-0325)
#
# On Linux, three of the things this toolkit ships an API for are compiled into
# SDL only if a development package happened to be installed on the machine that
# built it, and are compiled OUT -- silently, into a call that succeeds and
# answers nothing -- if it was not:
#
#   dbus-1    SDL_GetSystemTheme() and its change event, the XDG portal file
#             dialog, and the screensaver inhibit. `src/core/linux/
#             SDL_system_theme.c` is the whole of SDL's theme detection on Linux
#             and every line of it is behind SDL_USE_LIBDBUS, which SDL #defines
#             from HAVE_DBUS_DBUS_H -- a BUILD-time probe for the headers. SDL
#             dlopen()s libdbus-1.so at run time, so the shipped binary needs no
#             D-Bus installed to USE it; it needed the headers to be able to.
#   ibus-1.0  the input method on X11. Not on Wayland, where SDL drives
#             zwp_text_input_v3 from the compositor and needs neither IBus nor
#             Fcitx -- which is why this one hides: the machine that built it and
#             the machine that found the bug can both be composing text fine.
#   libudev   input-device hotplug.
#
# All three defaulted to ON already and all three were off in the published
# library, because "ON" in SDL's CMake means "use it if the headers are there".
# Nothing said so: not the configure, not the build log, not the run. G32 is that
# entry, and it was found by an application asking why the toolkit thought a
# dark desktop had no colour-scheme setting.
#
# So the headers are a declared dependency of this superbuild. It probes for the
# same pkg-config modules SDL probes for, stops rather than shipping a library
# that cannot ask the desktop anything, and records what it found in the binary
# itself -- `goldberry_platform_capabilities`, which is what
# `Goldberry.capabilities()` reads back. A build may still be made without them,
# on purpose, with -DGOLDBERRY_REQUIRE_PLATFORM_INTEGRATION=OFF; what it may no
# longer do is claim to be the other kind.
# ============================================================================
# Probed here, before a single byte is downloaded, so "install this package" is
# the first thing a configure says rather than the thing it says after cloning
# 330 MB. The SDL_* options below are set here for the same reason they are set
# at all -- they have to be in the cache before SDL configures, which is further
# down.
option(GOLDBERRY_REQUIRE_PLATFORM_INTEGRATION
    "Stop the configure when a desktop integration's headers are absent" ON)

# Explicit rather than inherited. Each of these is ON by default in SDL, and
# writing them down is what makes the intent reviewable: the toolkit wants all
# three, and a future SDL that flips a default cannot take one away quietly.
set(SDL_DBUS ON CACHE BOOL "" FORCE)
set(SDL_IBUS ON CACHE BOOL "" FORCE)
set(SDL_LIBUDEV ON CACHE BOOL "" FORCE)

set(GOLDBERRY_PLATFORM_DEFINITIONS "")

# Probes for one integration's headers and records the answer.
#
# MODULES is a list because SDL's own probe is `pkg_search_module(DBUS dbus-1
# dbus)`: distributions disagree about the .pc file's name, and a probe that
# asks for fewer names than SDL does would report absent on a machine SDL builds
# fine on -- a false alarm is as bad as a silent downgrade, because the next
# person turns the check off.
#
# DEFINE is what the shim compiles against; REQUIRED says whether its absence
# stops the build. SDL_BUILD_CONFIG is the #define in SDL's generated
# SDL_build_config.h that this probe is a prediction of -- cross-checked below,
# once SDL has actually configured.
function(goldberry_desktop_integration)
    cmake_parse_arguments(GDI "REQUIRED" "NAME;DEFINE;SDL_BUILD_CONFIG;PURPOSE;APT;DNF" "MODULES" ${ARGN})

    set(_found OFF)
    if(PKG_CONFIG_FOUND)
        # Finds the first of MODULES that exists, like SDL's own search.
        pkg_search_module(GOLDBERRY_PKG_${GDI_NAME} QUIET ${GDI_MODULES})
        if(GOLDBERRY_PKG_${GDI_NAME}_FOUND)
            set(_found ON)
        endif()
    endif()

    if(_found)
        message(STATUS "Goldberry desktop integration: ${GDI_NAME} -- ${GDI_PURPOSE}")
        # list(APPEND) rather than a ";" join: the list starts empty, and joining
        # onto an empty string leaves a leading separator -- an empty element that
        # reaches the compiler as `-D`.
        set(_definitions ${GOLDBERRY_PLATFORM_DEFINITIONS})
        list(APPEND _definitions ${GDI_DEFINE})
        set(GOLDBERRY_PLATFORM_DEFINITIONS "${_definitions}" PARENT_SCOPE)
        set(GOLDBERRY_EXPECT_${GDI_SDL_BUILD_CONFIG} ON PARENT_SCOPE)
        return()
    endif()

    set(_how_to_install
        "  Debian/Ubuntu:       sudo apt install ${GDI_APT}\n"
        "  RHEL/Fedora/Alma:    sudo dnf install -y ${GDI_DNF}\n")

    if(GDI_REQUIRED AND GOLDBERRY_REQUIRE_PLATFORM_INTEGRATION)
        message(FATAL_ERROR
            "No development headers for ${GDI_NAME} (pkg-config: ${GDI_MODULES}).\n"
            "Without them SDL compiles out ${GDI_PURPOSE}, and the calls that back it "
            "answer 'the desktop does not say' on a desktop that does -- on every Linux "
            "session, forever, with no error anywhere. That is docs/gaps.md G32, and this "
            "is the stop it asked for.\n"
            ${_how_to_install}
            "Or configure with -DGOLDBERRY_REQUIRE_PLATFORM_INTEGRATION=OFF to build "
            "without it on purpose: the library then reports the capability as absent "
            "through Goldberry.capabilities() rather than pretending to have it.")
    endif()

    # Not required, or required and waived. Either way it is said out loud, in
    # the build log, naming the package -- which is the whole complaint in G32.
    message(WARNING
        "Goldberry: no headers for ${GDI_NAME} (pkg-config: ${GDI_MODULES}).\n"
        "This libgoldberry will be built WITHOUT ${GDI_PURPOSE}, and will report it as "
        "absent through Goldberry.capabilities().\n"
        ${_how_to_install})
endfunction()

if(CMAKE_SYSTEM_NAME STREQUAL "Linux")
    find_package(PkgConfig QUIET)
    if(NOT PKG_CONFIG_FOUND)
        message(WARNING
            "Goldberry: pkg-config is not installed, so the desktop integrations below "
            "cannot be probed for. SDL will make the same decision from the same absent "
            "tool and compile all of them out.")
    endif()

    goldberry_desktop_integration(
        NAME "D-Bus"
        MODULES dbus-1 dbus
        DEFINE GOLDBERRY_PLATFORM_DBUS
        SDL_BUILD_CONFIG HAVE_DBUS_DBUS_H
        PURPOSE "the desktop's light-or-dark setting, the portal file dialog and screensaver inhibition"
        APT libdbus-1-dev
        DNF dbus-devel
        REQUIRED)

    goldberry_desktop_integration(
        NAME "IBus"
        MODULES ibus-1.0
        DEFINE GOLDBERRY_PLATFORM_IBUS
        SDL_BUILD_CONFIG HAVE_IBUS_IBUS_H
        PURPOSE "the input method on X11 (Wayland needs none)"
        APT libibus-1.0-dev
        DNF ibus-devel)

    goldberry_desktop_integration(
        NAME "udev"
        MODULES libudev
        DEFINE GOLDBERRY_PLATFORM_UDEV
        SDL_BUILD_CONFIG HAVE_LIBUDEV_H
        PURPOSE "input-device hotplug"
        APT libudev-dev
        DNF systemd-devel)
endif()

# --- The download phase must be audible (ADR-0029) ---------------------------
#
# FetchContent clones five repositories totalling roughly 330 MB. Left to its
# defaults that is several silent minutes: FETCHCONTENT_QUIET swallows the
# populate step's output, and git without --progress writes nothing at all when
# stdout is not a terminal -- which it never is under Gradle's Exec. The build
# was routinely reported as hung during exactly this window.
#
# So: unmute the populate step, and pass --progress to git (GIT_PROGRESS on each
# declaration below) so it reports even into a pipe. The configure step gets
# noisier; that is the point. A build that is doing something must look like it.
set(FETCHCONTENT_QUIET FALSE)

# Where the clones live. Gradle points this at natives/.deps/<target-id>, which
# is OUTSIDE build/ on purpose -- see ADR-0029. Inside build/, `./gradlew clean`
# discards all 330 MB and the next build re-clones it.
#
# Per target rather than one shared directory: FETCHCONTENT_BASE_DIR holds the
# generator-specific `<name>-build` and `<name>-subbuild` trees beside the
# sources, so two targets sharing one base directory would fight over them.
if(FETCHCONTENT_BASE_DIR)
    message(STATUS "Goldberry: upstream sources cached in ${FETCHCONTENT_BASE_DIR}")
endif()

# ============================================================================
# Blend2D -- 2D vector rasterization (§3, ADR-0002)
#
# JIT pipelines via AsmJit, banded multithreading, built-in PNG/JPEG/QOI codecs.
# Renders glyph runs straight from font outlines, which is why there is no
# FreeType here.
#
# NOTE (ADR-0002): AsmJit allocates executable memory at runtime. On Apple
# Silicon that needs W^X handling (MAP_JIT + pthread_jit_write_protect_np).
# Verify on the macOS leg before trusting it.
# ============================================================================
# Blend2D does not consume asmjit as a CMake target -- it does
# `add_subdirectory("${ASMJIT_DIR}" asmjit)` with ASMJIT_EMBED, compiling asmjit
# straight into itself so the shared library avoids linking libstdc++. So it
# needs asmjit's SOURCE DIRECTORY, not a target.
#
# SOURCE_SUBDIR therefore points at a directory that does not exist: that makes
# FetchContent download asmjit without calling add_subdirectory() on it, leaving
# Blend2D to add it exactly once, its own way. Declaring it normally produces a
# rival asmjit subproject and duplicate targets.
#
# No GIT_SHALLOW here, and none on Blend2D below, because both are pinned to a
# commit SHA (ADR-0030) and CMake's own documentation says GIT_SHALLOW "works
# only with branch names and tags. A commit hash is not allowed."
#
# It would appear to work anyway today: a shallow clone fetches `--depth 1
# --no-single-branch`, so it contains every branch *tip*, and these SHAs are the
# tips of master right now. The checkout would start failing the day upstream
# commits anything -- on a clean clone, in CI, with "Failed to checkout tag" and
# nothing pointing at the cause. Paying for the full history is the cheaper of
# the two.
FetchContent_Declare(asmjit
    GIT_REPOSITORY https://github.com/asmjit/asmjit.git
    GIT_TAG ${GOLDBERRY_ASMJIT_REF}
    # GIT_PROGRESS but NOT GIT_SHALLOW -- see the note above. Progress is the
    # whole point of ADR-0038; shallow is what a SHA pin forbids (ADR-0030).
    GIT_PROGRESS TRUE
    SOURCE_SUBDIR goldberry-do-not-add-subdirectory)

FetchContent_MakeAvailable(asmjit)

set(ASMJIT_DIR "${asmjit_SOURCE_DIR}" CACHE PATH "Location of asmjit" FORCE)

# Upstream's own guard for this cannot fire: blend2d/CMakeLists.txt writes
# `message(FATAL ...)`, and FATAL is not a CMake message mode. CMake treats the
# word as message text, prints it as a notice, and CARRIES ON -- straight into
# `add_subdirectory("")`, which resolves to Blend2D's own directory and recurses
# until the 1000-frame limit. The real error is then unrecognisable.
#
# So check it here, where it can still fail usefully.
if(NOT EXISTS "${ASMJIT_DIR}/CMakeLists.txt")
    message(FATAL_ERROR
        "asmjit was not populated at '${ASMJIT_DIR}'.\n"
        "Blend2D needs asmjit's source directory. Without it Blend2D recurses into "
        "itself and fails with 'Maximum recursion depth of 1000 exceeded'.")
endif()
message(STATUS "Goldberry: asmjit for Blend2D at ${ASMJIT_DIR}")

# No GIT_SHALLOW -- see the note on asmjit above.
FetchContent_Declare(blend2d
    GIT_REPOSITORY https://github.com/blend2d/blend2d.git
    GIT_TAG ${GOLDBERRY_BLEND2D_REF}
    GIT_PROGRESS TRUE)

set(BLEND2D_STATIC TRUE CACHE BOOL "" FORCE)
set(BLEND2D_TEST FALSE CACHE BOOL "" FORCE)

# ============================================================================
# Yoga -- flexbox layout (§3)
#
# Hidden behind an internal interface on the Java side so Taffy or a grid engine
# stays swappable later.
# ============================================================================
# SOURCE_SUBDIR yoga, so only the library is configured.
#
# Yoga's top-level CMakeLists.txt calls add_subdirectory(tests) unconditionally
# -- there is no option to turn it off -- which fetches googletest, builds
# yogatests, and then runs gtest_discover_tests. That last step EXECUTES the test
# binary at configure time, which is fatal on a cross-targeting runner: MSVC
# building ARM64 on an x64 host produces a yogatests.exe the host cannot run, and
# CMake reports it as "Error running test executable ... Result: unknown error"
# after five minutes of compiling.
#
# yoga/CMakeLists.txt is self-contained: it defines yogacore, includes Yoga's own
# project-defaults, and exports the include directory that makes <yoga/Yoga.h>
# resolve. Pointing at it skips the tests, the googletest dependency, and the
# gtest/gmock archives that were being built on every target for nothing.
FetchContent_Declare(yoga
    GIT_REPOSITORY https://github.com/facebook/yoga.git
    GIT_TAG ${GOLDBERRY_YOGA_REF}
    GIT_SHALLOW TRUE
    GIT_PROGRESS TRUE
    SOURCE_SUBDIR yoga)

# ============================================================================
# HarfBuzz -- text shaping (§6)
#
# UTF-16 in (matches Java strings), glyph IDs and positions out. Every optional
# integration is off: Goldberry segments with java.text.Bidi and BreakIterator,
# and rasterizes with Blend2D, so HarfBuzz needs neither ICU, GLib, nor FreeType.
# ============================================================================
FetchContent_Declare(harfbuzz
    GIT_REPOSITORY https://github.com/harfbuzz/harfbuzz.git
    GIT_TAG ${GOLDBERRY_HARFBUZZ_REF}
    GIT_SHALLOW TRUE
    GIT_PROGRESS TRUE)

set(HB_HAVE_FREETYPE OFF CACHE BOOL "" FORCE)
set(HB_HAVE_GLIB OFF CACHE BOOL "" FORCE)
set(HB_HAVE_ICU OFF CACHE BOOL "" FORCE)
set(HB_HAVE_CORETEXT OFF CACHE BOOL "" FORCE)
set(HB_HAVE_DIRECTWRITE OFF CACHE BOOL "" FORCE)
set(HB_BUILD_SUBSET OFF CACHE BOOL "" FORCE)
set(HB_BUILD_UTILS OFF CACHE BOOL "" FORCE)
set(HB_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(SKIP_INSTALL_ALL ON CACHE BOOL "" FORCE)

# ============================================================================
# SDL3 -- the desktop backend (§3, §4, ADR-0003)
#
# Windowing, input, per-monitor DPI, clipboard, cursors, popups, tray, SDL_GPU.
# ============================================================================
FetchContent_Declare(sdl3
    GIT_REPOSITORY https://github.com/libsdl-org/SDL.git
    GIT_TAG ${GOLDBERRY_SDL3_REF}
    GIT_SHALLOW TRUE
    GIT_PROGRESS TRUE)

set(SDL_STATIC ON CACHE BOOL "" FORCE)
set(SDL_SHARED OFF CACHE BOOL "" FORCE)
set(SDL_TEST_LIBRARY OFF CACHE BOOL "" FORCE)
set(SDL_TESTS OFF CACHE BOOL "" FORCE)
set(SDL_EXAMPLES OFF CACHE BOOL "" FORCE)
set(SDL_INSTALL OFF CACHE BOOL "" FORCE)


# ============================================================================
# md4c -- the Markdown parser (ADR-0294)
#
# The smallest upstream here by three orders of magnitude: one C file and a
# header, MIT, CommonMark plus tables, strikethrough, task lists and permissive
# autolinks. It is `goldberry-html`'s markdown half, and it is compiled INTO this
# library rather than into a native of its own -- ADR-0190 quarantines a content
# module's natives, and ADR-0294 is where that rule was measured against a parser
# this size and found to cost more than it isolates.
#
# SOURCE_SUBDIR names a directory that does not exist, which is the trick asmjit
# uses above: FetchContent downloads md4c without calling add_subdirectory() on
# it, so its own CMake project -- which builds md4c-html, the md2html executable
# and a pkg-config file, and installs all three -- never runs. What this build
# wants is two translation units, added to the shim's target below.
FetchContent_Declare(md4c
    GIT_REPOSITORY https://github.com/mity/md4c.git
    GIT_TAG ${GOLDBERRY_MD4C_REF}
    GIT_SHALLOW TRUE
    GIT_PROGRESS TRUE
    SOURCE_SUBDIR do-not-add-this-subdirectory)

# ============================================================================
# libwebp -- the WebP decoder (docs/gaps.md G35a, ADR-0329)
#
# Blend2D is compiled with the PNG, JPEG and QOI codecs and no others, so a WebP
# -- which is what most screenshot tools now write -- does not decode at all. The
# format is VP8, which is a video codec; there is no pure-Java answer worth
# writing, and the reference decoder is BSD and small.
#
# **`webp` and `webpdemux`** (ADR-0385). This used to link `webpdecoder` alone --
# the decoder-only library -- on the grounds that Goldberry reads one still frame
# and writes PNG, so an encoder would be several hundred kilobytes nothing could
# reach. Two things changed: an application that photographs a canvas wants to
# *write* a WebP, and an animated WebP was what was left of the "one frame only"
# entry once animated GIF was built. Both live in targets upstream already
# builds -- `webp` is the decoder plus the encoder, `webpdemux` is the animation
# reader over it -- so this is a link line and an export list rather than new
# code.
#
# The command-line tools, the muxer (*writing* an animation) and the extras stay
# off: nothing here assembles a WebP animation, and switching on nine binaries
# to reach a library is how a build gets slow.
#
# Its headers mark the API `visibility("default")` on GCC and Clang -- unlike
# Blend2D's and HarfBuzz's, which do not when built statically -- so it needs no
# entry in the visibility fix-up below. The export list is still what decides
# what reaches Java.
FetchContent_Declare(libwebp
    GIT_REPOSITORY https://github.com/webmproject/libwebp.git
    GIT_TAG ${GOLDBERRY_LIBWEBP_REF}
    GIT_SHALLOW TRUE
    GIT_PROGRESS TRUE)

set(WEBP_BUILD_ANIM_UTILS OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_CWEBP OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_DWEBP OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_GIF2WEBP OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_IMG2WEBP OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_VWEBP OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_WEBPINFO OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_LIBWEBPMUX OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_WEBPMUX OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_EXTRAS OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_FUZZTEST OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_WEBP_JS OFF CACHE BOOL "" FORCE)

# asmjit is deliberately absent: it was populated above and is added by Blend2D.
FetchContent_MakeAvailable(blend2d yoga harfbuzz sdl3 md4c libwebp)

foreach(webp_target webp webpdemux)
    if(NOT TARGET ${webp_target})
        message(FATAL_ERROR
            "libwebp was fetched but defines no `${webp_target}` target.\n"
            "Those two are the codec and the animation reader this build links -- see "
            "the FetchContent block above, ADR-0329 and ADR-0385. An upstream that "
            "renamed one is a build failure here rather than a missing symbol at load "
            "time.")
    endif()
endforeach()

# --- What SDL actually decided (docs/gaps.md G32) ----------------------------
#
# The probe above is a *prediction* of what SDL's own CMake would find. This is
# the answer, read out of the header SDL just generated -- the same file the gap
# was diagnosed from:
#
#     $ grep DBUS .../build_config/SDL_build_config.h
#     /* #undef HAVE_DBUS_DBUS_H */
#
# Worth reading rather than trusting the prediction, because the two can differ
# and the difference is exactly the failure being fixed: a machine can have the
# headers while SDL still compiles the feature out -- a second pkg-config on the
# PATH, an SDL option flipped in a cache from an earlier configure, or an
# upstream that renamed the probe. The prediction is what produces a good error
# message; this is what makes it true.
if(CMAKE_SYSTEM_NAME STREQUAL "Linux")
    file(GLOB _sdl_build_configs
        "${sdl3_BINARY_DIR}/include-config-*/build_config/SDL_build_config.h"
        "${sdl3_BINARY_DIR}/include/build_config/SDL_build_config.h")

    if(_sdl_build_configs)
        list(GET _sdl_build_configs 0 _sdl_build_config)
        file(READ "${_sdl_build_config}" _sdl_build_config_text)

        foreach(_define IN ITEMS HAVE_DBUS_DBUS_H HAVE_IBUS_IBUS_H HAVE_LIBUDEV_H)
            set(_sdl_has OFF)
            if(_sdl_build_config_text MATCHES "\n#define ${_define}")
                set(_sdl_has ON)
            endif()
            if(GOLDBERRY_EXPECT_${_define} AND NOT _sdl_has)
                message(FATAL_ERROR
                    "The headers for ${_define} are installed, but SDL configured itself "
                    "without it:\n"
                    "  ${_sdl_build_config}\n"
                    "So this libgoldberry would report a capability it does not have, which "
                    "is the one outcome docs/gaps.md G32 rules out. Delete the build "
                    "directory and configure again; if it persists, SDL's probe and this "
                    "one have diverged and the superbuild is what needs fixing.")
            endif()
        endforeach()
    else()
        # Not fatal: an upstream that moves its generated header is a broken
        # cross-check, not a broken library, and the capability bits still say
        # what the probe found.
        message(WARNING
            "Goldberry: SDL's generated SDL_build_config.h was not found under "
            "${sdl3_BINARY_DIR}, so what SDL compiled in could not be cross-checked "
            "against this build's own probe (docs/gaps.md G32).")
    endif()
endif()

if(NOT EXISTS "${md4c_SOURCE_DIR}/src/md4c.c")
    message(FATAL_ERROR
        "md4c was fetched but ${md4c_SOURCE_DIR}/src/md4c.c is not there.\n"
        "The shim compiles md4c's sources directly rather than linking its CMake "
        "targets, so a re-organised upstream tree is a build failure here rather "
        "than a missing symbol at load time. See ADR-0294.")
endif()

# Some upstreams must be compiled with DEFAULT symbol visibility (ADR-0031).
#
# This is ADR-0018's lesson in its third costume: a version script cannot
# promote a symbol that is already hidden. There, --exclude-libs,ALL did the
# hiding. Here it is the upstream headers, in two different ways.
#
# Blend2D:
#     #if !defined(BL_STATIC)
#       ... #define BL_API __attribute__((visibility("default")))
#     #endif
#     #ifndef BL_API
#       #define BL_API          <-- a static build gets nothing at all
#     #endif
#
# HarfBuzz, more bluntly still:
#     #ifndef HB_EXTERN
#     #define HB_EXTERN extern   <-- no visibility attribute, ever
#     #endif
#
# Neither decorates its API when built statically, so the
# CMAKE_CXX_VISIBILITY_PRESET=hidden set at the top of this file applies to
# every one of their functions. The failure is quiet and specific: the symbols
# link in perfectly well -- `-u` pulls each one out of its archive -- and arrive
# in libgoldberry.so as LOCAL, where nothing can call them. `nm -D` shows none
# of them while plain `nm` shows them all as lowercase `t`, and the exported
# count silently sits below the length of the export list.
#
# Yoga and SDL do not have this problem: YG_EXPORT and SDL_DECLSPEC are
# unconditionally visibility("default"), static build or not.
#
# Turning visibility back on for these targets does NOT leak their internals:
# the version script's `local: *` still hides everything that is not on the
# export list, which is the same argument ADR-0018 settled. asmjit is compiled
# into the blend2d target rather than linked, so it is covered with it.
foreach(_target IN ITEMS blend2d harfbuzz)
    if(NOT TARGET ${_target})
        message(FATAL_ERROR
            "Expected a target named `${_target}`. Without it the visibility fix "
            "below does not apply and its symbols fail to export -- silently, as "
            "local symbols. See ADR-0031.")
    endif()
    set_target_properties(${_target} PROPERTIES
        C_VISIBILITY_PRESET default
        CXX_VISIBILITY_PRESET default
        VISIBILITY_INLINES_HIDDEN OFF)
endforeach()


# ============================================================================
# The one artifact
#
# goldberry_shim.c holds two things and nothing else: the ABI probe, and the
# layout table that ADR-0010 makes the safety argument for hand-written
# bindings. Everything else crosses the boundary through FFM downcalls.
# ============================================================================
add_library(goldberry SHARED goldberry_shim.c)

# md4c's two translation units, compiled into the shim rather than linked as a
# library (see its FetchContent block above). `entity.c` is the HTML5 named
# entity table -- 2125 names -- which `goldberry_md_entity` hands to Java rather
# than Java carrying a second copy of it.
#
# Hidden visibility and no export-list entry of their own: nothing in Java binds
# `md_parse` directly. The four `goldberry_md_*` functions are the whole surface,
# because a document's event stream crosses the boundary as one buffer rather
# than as thousands of upcalls (ADR-0190's hot-path rule, ADR-0294).
target_sources(goldberry PRIVATE
    "${md4c_SOURCE_DIR}/src/md4c.c"
    "${md4c_SOURCE_DIR}/src/entity.c")
target_include_directories(goldberry PRIVATE "${md4c_SOURCE_DIR}/src")

# What the probe above found, compiled into the library so it can be asked
# (docs/gaps.md G32, ADR-0325). Empty on macOS and Windows, where the shim needs
# no flag: every one of these is a system framework there rather than an optional
# third-party header, and `goldberry_platform_capabilities` says so itself.
target_compile_definitions(goldberry PRIVATE ${GOLDBERRY_PLATFORM_DEFINITIONS})

set_target_properties(goldberry PROPERTIES
    C_VISIBILITY_PRESET hidden
    OUTPUT_NAME goldberry)

# The shim includes upstream headers to compute sizeof/offsetof, so it needs the
# include paths -- but only PRIVATE ones. No upstream header is part of
# Goldberry's interface.
# Target names confirmed against the pinned revisions. Blend2D's is lowercase --
# `Blend2D::Blend2D` does not exist, whatever the project's own capitalisation
# suggests.
target_link_libraries(goldberry PRIVATE
    blend2d::blend2d
    yogacore
    harfbuzz
    SDL3::SDL3-static
    # The demuxer first: it links `webp`, and a static link line is read left to
    # right.
    webpdemux
    webp)

# ============================================================================
# Exported surface
#
# Goldberry calls upstream functions -- SDL_Init, hb_shape, YGNodeNew -- directly
# through FFM downcalls, with no C glue in between (§3.1). For that to work each
# bound symbol has to be an exported dynamic symbol of libgoldberry. But
# everything is built with hidden visibility, and the upstream archives are
# statically linked, so by default those symbols are both hidden and dropped by
# the linker as unreferenced.
#
# exports/goldberry.symbols is the single, explicit list of what crosses the
# boundary. For each symbol the build does two things: forces the linker to pull
# it out of its static archive (-u), and marks it exported in the platform's own
# dialect. Nothing else escapes -- an app embedding its own SDL or HarfBuzz sees
# no collision with ours.
#
# A narrow, enumerable export list is only possible because the bindings are
# hand-written (ADR-0010). A generated whole-header surface could not be listed.
# ============================================================================
set(_symbols_file "${CMAKE_CURRENT_SOURCE_DIR}/exports/goldberry.symbols")
file(STRINGS "${_symbols_file}" _symbol_lines)
set(GOLDBERRY_EXPORTED_SYMBOLS "")
foreach(_line IN LISTS _symbol_lines)
    string(STRIP "${_line}" _line)
    if(_line STREQUAL "" OR _line MATCHES "^#")
        continue()
    endif()
    # Anything surviving the comment filter must be a C identifier. Without this
    # check, junk reaches the linker as `-u <junk>` and fails much later with a
    # version-script syntax error that names neither this file nor the offending
    # line. file(STRINGS) splitting a comment on a non-ASCII byte is exactly how
    # that happened once.
    if(NOT _line MATCHES "^[A-Za-z_][A-Za-z0-9_]*$")
        message(FATAL_ERROR
            "exports/goldberry.symbols: '${_line}' is not a valid symbol name.\n"
            "Every non-comment line must be a C identifier. If this looks like part "
            "of a comment, the comment probably contains a non-ASCII character.")
    endif()
    list(APPEND GOLDBERRY_EXPORTED_SYMBOLS "${_line}")
endforeach()
list(LENGTH GOLDBERRY_EXPORTED_SYMBOLS _symbol_count)
message(STATUS "Goldberry exports ${_symbol_count} native symbols")

set_property(TARGET goldberry APPEND PROPERTY LINK_DEPENDS "${_symbols_file}")

# Force each exported symbol to be linked in even though the shim never
# references it. Without this the static archives contribute nothing.
#
# Three dialects, because each native toolchain spells this differently -- and
# Mach-O needs the leading underscore that ELF does not.
foreach(_symbol IN LISTS GOLDBERRY_EXPORTED_SYMBOLS)
    if(MSVC)
        target_link_options(goldberry PRIVATE "/INCLUDE:${_symbol}")
    elseif(CMAKE_SYSTEM_NAME STREQUAL "Darwin")
        target_link_options(goldberry PRIVATE "LINKER:-u,_${_symbol}")
    else()
        target_link_options(goldberry PRIVATE "LINKER:-u,${_symbol}")
    endif()
endforeach()

if(CMAKE_SYSTEM_NAME STREQUAL "Linux")
    # The `local: *` clause is what hides everything not listed. It is also
    # sufficient: --exclude-libs,ALL used to be here as belt-and-braces and had to
    # go, because it does not merely hide archive symbols by default -- it forces
    # them local, and a version script cannot promote a symbol the linker has
    # already been told to hide. With it in place, SDL_Init was pulled into the
    # library by -u, occupied its four megabytes, and was not exported: `nm -D`
    # reported goldberry's own four symbols and nothing else.
    #
    # That went unnoticed while every exported symbol came from goldberry_shim.c,
    # which is an object file rather than an archive member. SDL3 is the first
    # upstream symbol on the list, and it found the flaw immediately.
    set(_version_script "${CMAKE_BINARY_DIR}/goldberry.map")
    string(JOIN ";\n    " _sym_block ${GOLDBERRY_EXPORTED_SYMBOLS})
    file(WRITE "${_version_script}" "GOLDBERRY_0 {\n  global:\n    ${_sym_block};\n  local:\n    *;\n};\n")
    target_link_options(goldberry PRIVATE
        "LINKER:--version-script=${_version_script}"
        "LINKER:--no-undefined")

elseif(CMAKE_SYSTEM_NAME STREQUAL "Darwin")
    # Mach-O symbols carry a leading underscore.
    set(_export_list "${CMAKE_BINARY_DIR}/goldberry.exported_symbols")
    set(_macho_syms "")
    foreach(_symbol IN LISTS GOLDBERRY_EXPORTED_SYMBOLS)
        string(APPEND _macho_syms "_${_symbol}\n")
    endforeach()
    file(WRITE "${_export_list}" "${_macho_syms}")
    target_link_options(goldberry PRIVATE
        "LINKER:-exported_symbols_list,${_export_list}"
        "LINKER:-dead_strip")

elseif(CMAKE_SYSTEM_NAME STREQUAL "Windows")
    # A .def is the only way to export symbols that live in a static archive
    # rather than in this project's own sources -- __declspec(dllexport) cannot
    # reach them.
    set(_def_file "${CMAKE_BINARY_DIR}/goldberry.def")
    string(JOIN "\n    " _def_block ${GOLDBERRY_EXPORTED_SYMBOLS})
    file(WRITE "${_def_file}" "LIBRARY goldberry\nEXPORTS\n    ${_def_block}\n")
    if(MSVC)
        target_link_options(goldberry PRIVATE "/DEF:${_def_file}")
    else()
        target_link_options(goldberry PRIVATE "${_def_file}")
    endif()
endif()

install(TARGETS goldberry
    RUNTIME DESTINATION lib     # Windows DLLs land beside the others on purpose:
    LIBRARY DESTINATION lib     # NativeLibrary looks in one place per target.
    ARCHIVE DESTINATION lib)
