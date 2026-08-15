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
# STATUS: builds and links on both Linux targets and both macOS targets, so the
# upstream refs, the CMake target names, and the ELF version-script and Mach-O
# -exported_symbols_list branches of the export model below are all confirmed
# against the pinned revisions. Windows has not been built yet -- the MSVC branch
# (/INCLUDE:, .def) is still unexercised.

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

# Upstream refs are pinned in gradle/libs.versions.toml and passed in by Gradle.
# Defaults exist only so a bare `cmake -S ...` is not immediately broken.
set(GOLDBERRY_BLEND2D_REF "master" CACHE STRING "Blend2D git ref")
set(GOLDBERRY_ASMJIT_REF "master" CACHE STRING "AsmJit git ref (must suit the Blend2D ref)")
set(GOLDBERRY_YOGA_REF "v3.1.0" CACHE STRING "Yoga git ref")
set(GOLDBERRY_HARFBUZZ_REF "8.5.0" CACHE STRING "HarfBuzz git ref")
set(GOLDBERRY_SDL3_REF "release-3.2.0" CACHE STRING "SDL3 git ref")
set(GOLDBERRY_XKBCOMMON_REF "xkbcommon-1.6.0" CACHE STRING "libxkbcommon git ref")
set(GOLDBERRY_TARGET_ID "host" CACHE STRING "Distribution target id, e.g. linux-x64")

message(STATUS "Goldberry target: ${GOLDBERRY_TARGET_ID} (${CMAKE_SYSTEM_NAME}/${CMAKE_SYSTEM_PROCESSOR})")

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
FetchContent_Declare(asmjit
    GIT_REPOSITORY https://github.com/asmjit/asmjit.git
    GIT_TAG ${GOLDBERRY_ASMJIT_REF}
    GIT_SHALLOW TRUE
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

FetchContent_Declare(blend2d
    GIT_REPOSITORY https://github.com/blend2d/blend2d.git
    GIT_TAG ${GOLDBERRY_BLEND2D_REF}
    GIT_SHALLOW TRUE)

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
    GIT_SHALLOW TRUE)

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
    GIT_SHALLOW TRUE)

set(SDL_STATIC ON CACHE BOOL "" FORCE)
set(SDL_SHARED OFF CACHE BOOL "" FORCE)
set(SDL_TEST_LIBRARY OFF CACHE BOOL "" FORCE)
set(SDL_TESTS OFF CACHE BOOL "" FORCE)
set(SDL_EXAMPLES OFF CACHE BOOL "" FORCE)
set(SDL_INSTALL OFF CACHE BOOL "" FORCE)

# asmjit is deliberately absent: it was populated above and is added by Blend2D.
FetchContent_MakeAvailable(blend2d yoga harfbuzz sdl3)

# ============================================================================
# libxkbcommon -- keyboard translation (§3, §7.1)
#
# Linux and Scarlet backends only, and an implementation detail behind the
# Backend SPI -- it is never part of Goldberry's public input model.
#
# Upstream builds with meson rather than CMake, so it is an ExternalProject.
# The build is native, so meson needs no cross file.
# ============================================================================
if(CMAKE_SYSTEM_NAME STREQUAL "Linux")
    ExternalProject_Add(xkbcommon
        GIT_REPOSITORY https://github.com/xkbcommon/libxkbcommon.git
        GIT_TAG ${GOLDBERRY_XKBCOMMON_REF}
        GIT_SHALLOW TRUE
        # Core library only. `enable-x11` builds libxkbcommon-x11, which fetches a
        # keymap over an xcb connection, and `enable-wayland` builds an interactive
        # demo tool -- Goldberry needs neither. Per §7.1 it uses xkb_state and
        # xkb_compose to translate keys from a keymap the backend hands it: SDL3
        # owns the X11 connection, and the Scarlet compositor delivers its keymap
        # as an fd. Turning these off also drops the xcb-xkb and wayland-protocols
        # build dependencies, which is a consequence rather than the reason.
        CONFIGURE_COMMAND meson setup <BINARY_DIR> <SOURCE_DIR>
            --prefix <INSTALL_DIR>
            # Meson's default libdir is arch-qualified (lib/x86_64-linux-gnu on
            # Debian-ish hosts, lib64 on others), so the install path would vary
            # by distribution. Pin it so the link line below is always right.
            --libdir lib
            --default-library static
            -Denable-x11=false
            -Denable-wayland=false
            -Denable-docs=false
            -Denable-tools=false
        BUILD_COMMAND meson compile -C <BINARY_DIR>
        INSTALL_COMMAND meson install -C <BINARY_DIR>
        BUILD_BYPRODUCTS <INSTALL_DIR>/lib/libxkbcommon.a)

    ExternalProject_Get_Property(xkbcommon INSTALL_DIR)
    set(XKBCOMMON_INSTALL_DIR "${INSTALL_DIR}")
endif()

# ============================================================================
# The one artifact
#
# goldberry_shim.c holds two things and nothing else: the ABI probe, and the
# layout table that ADR-0010 makes the safety argument for hand-written
# bindings. Everything else crosses the boundary through FFM downcalls.
# ============================================================================
add_library(goldberry SHARED goldberry_shim.c)

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
    SDL3::SDL3-static)

if(CMAKE_SYSTEM_NAME STREQUAL "Linux")
    add_dependencies(goldberry xkbcommon)
    target_include_directories(goldberry PRIVATE "${XKBCOMMON_INSTALL_DIR}/include")
    target_link_libraries(goldberry PRIVATE "${XKBCOMMON_INSTALL_DIR}/lib/libxkbcommon.a")
endif()

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
    set(_version_script "${CMAKE_BINARY_DIR}/goldberry.map")
    string(JOIN ";\n    " _sym_block ${GOLDBERRY_EXPORTED_SYMBOLS})
    file(WRITE "${_version_script}" "GOLDBERRY_0 {\n  global:\n    ${_sym_block};\n  local:\n    *;\n};\n")
    target_link_options(goldberry PRIVATE
        "LINKER:--version-script=${_version_script}"
        "LINKER:--exclude-libs,ALL"
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
