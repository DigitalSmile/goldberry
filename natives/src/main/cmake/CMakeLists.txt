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

message(STATUS "Goldberry pins: blend2d ${GOLDBERRY_BLEND2D_REF}, asmjit ${GOLDBERRY_ASMJIT_REF}, "
    "yoga ${GOLDBERRY_YOGA_REF}, harfbuzz ${GOLDBERRY_HARFBUZZ_REF}, "
    "sdl3 ${GOLDBERRY_SDL3_REF}")

set(GOLDBERRY_TARGET_ID "host" CACHE STRING "Distribution target id, e.g. linux-x64")

message(STATUS "Goldberry target: ${GOLDBERRY_TARGET_ID} (${CMAKE_SYSTEM_NAME}/${CMAKE_SYSTEM_PROCESSOR})")

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

# asmjit is deliberately absent: it was populated above and is added by Blend2D.
FetchContent_MakeAvailable(blend2d yoga harfbuzz sdl3)

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
