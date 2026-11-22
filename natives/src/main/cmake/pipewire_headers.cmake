# PipeWire's headers for SDL, staged where the system's are older than SDL asks.
#
# SDL compiles its PipeWire audio and camera drivers from one pkg-config check,
# libpipewire-0.3 >= 0.3.44, and dlopen()s the library at run time, so a build
# needs PipeWire's headers and the name of its shared library, never the library
# itself. AlmaLinux 8 -- the manylinux_2_28 container the release is built in,
# for its glibc 2.28 floor -- ships PipeWire 0.3.6, and with it SDL compiles both
# drivers out.
#
# goldberry_stage_pipewire_headers() lays a release's headers out the way
# PipeWire's own install does (include/spa-0.2, include/pipewire-0.3 with the
# generated version.h) under the build directory, and writes libpipewire-0.3.pc
# and libspa-0.2.pc for them. The libpipewire .pc keeps the SYSTEM's libdir, so
# SDL's FindLibraryAndSONAME still finds the system's libpipewire-0.3.so and
# loads it by its SONAME at run time: the headers are new, the library is
# whatever the user's machine has.

# The version a PipeWire source tree says it is, from its top-level meson.build.
# Read rather than taken from the pin, which may be a commit.
function(goldberry_pipewire_source_version _source_dir _out)
    file(STRINGS "${_source_dir}/meson.build" _lines LIMIT_COUNT 8 REGEX "version[ \t]*:")
    foreach(_line IN LISTS _lines)
        if(_line MATCHES "version[ \t]*:[ \t]*'([0-9]+\\.[0-9]+\\.[0-9]+)'")
            set(${_out} "${CMAKE_MATCH_1}" PARENT_SCOPE)
            return()
        endif()
    endforeach()
    message(FATAL_ERROR
        "${_source_dir}/meson.build names no `version : 'X.Y.Z'` in its project() call.\n"
        "The PipeWire headers' version.h is generated from it; a source tree that "
        "moved it is a build failure here rather than headers claiming no version.")
endfunction()

# Stages the headers in `SOURCE_DIR` under `DESTINATION`, writes the two .pc
# files to DESTINATION/lib/pkgconfig with libpipewire's libdir set to
# `SYSTEM_LIBDIR`, and sets `OUT_VERSION` to the headers' version.
function(goldberry_stage_pipewire_headers)
    cmake_parse_arguments(PARSE_ARGV 0 GSP "" "SOURCE_DIR;DESTINATION;SYSTEM_LIBDIR;OUT_VERSION" "")
    foreach(_required IN ITEMS SOURCE_DIR DESTINATION SYSTEM_LIBDIR OUT_VERSION)
        if(NOT GSP_${_required})
            message(FATAL_ERROR "goldberry_stage_pipewire_headers: ${_required} is required")
        endif()
    endforeach()

    set(_src "${GSP_SOURCE_DIR}")
    foreach(_expected IN ITEMS spa/include/spa src/pipewire/version.h.in src/pipewire/pipewire.h)
        if(NOT EXISTS "${_src}/${_expected}")
            message(FATAL_ERROR
                "${_src} has no ${_expected}.\n"
                "The PipeWire headers are copied the way PipeWire's install lays them out; "
                "a re-organised tree is a build failure here rather than an SDL that "
                "quietly compiles its PipeWire drivers out.")
        endif()
    endforeach()

    goldberry_pipewire_source_version("${_src}" _version)
    string(REPLACE "." ";" _parts "${_version}")
    list(GET _parts 0 PIPEWIRE_VERSION_MAJOR)
    list(GET _parts 1 PIPEWIRE_VERSION_MINOR)
    list(GET _parts 2 PIPEWIRE_VERSION_MICRO)
    # meson's set_quoted, as src/pipewire/meson.build configures it.
    set(PIPEWIRE_API_VERSION "\"0.3\"")

    # A different release staged earlier is removed whole, so a header the new
    # release dropped cannot linger. The same release is left alone: file(COPY)
    # keeps the timestamps, and SDL's PipeWire drivers are not rebuilt on every
    # configure.
    set(_root "${GSP_DESTINATION}")
    set(_stamp "${_root}/goldberry-pipewire-version")
    if(EXISTS "${_stamp}")
        file(READ "${_stamp}" _staged)
        if(NOT _staged STREQUAL _version)
            file(REMOVE_RECURSE "${_root}")
        endif()
    endif()

    set(_include "${_root}/include")
    file(COPY "${_src}/spa/include/spa"
        DESTINATION "${_include}/spa-0.2"
        FILES_MATCHING PATTERN "*.h")
    file(GLOB _pipewire_headers "${_src}/src/pipewire/*.h")
    file(COPY ${_pipewire_headers} DESTINATION "${_include}/pipewire-0.3/pipewire")
    file(COPY "${_src}/src/pipewire/extensions"
        DESTINATION "${_include}/pipewire-0.3/pipewire"
        FILES_MATCHING PATTERN "*.h")
    configure_file("${_src}/src/pipewire/version.h.in"
        "${_include}/pipewire-0.3/pipewire/version.h" @ONLY)

    # The two .pc files PipeWire installs, with the same names, versions and
    # flags. libspa's version is its API version, as upstream writes it.
    set(_pkgconfig "${_root}/lib/pkgconfig")
    file(MAKE_DIRECTORY "${_pkgconfig}")
    file(WRITE "${_pkgconfig}/libspa-0.2.pc"
        "prefix=${_root}\n"
        "includedir=\${prefix}/include\n"
        "\n"
        "Name: libspa\n"
        "Description: Simple Plugin API (headers of PipeWire ${_version}, staged by Goldberry)\n"
        "Version: 0.2\n"
        "Cflags: -I\${includedir}/spa-0.2 -D_REENTRANT -fno-strict-aliasing -fno-strict-overflow\n")
    file(WRITE "${_pkgconfig}/libpipewire-0.3.pc"
        "prefix=${_root}\n"
        "includedir=\${prefix}/include\n"
        "libdir=${GSP_SYSTEM_LIBDIR}\n"
        "\n"
        "Name: libpipewire\n"
        "Description: PipeWire Interface (headers of ${_version}, staged by Goldberry; the system's library)\n"
        "Version: ${_version}\n"
        "Requires: libspa-0.2\n"
        "Libs: -L\${libdir} -lpipewire-0.3\n"
        "Cflags: -I\${includedir}/pipewire-0.3 -D_REENTRANT\n")
    file(WRITE "${_stamp}" "${_version}")

    set(${GSP_OUT_VERSION} "${_version}" PARENT_SCOPE)
endfunction()
