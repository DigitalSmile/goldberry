# ============================================================================
# Refuses a library that does not define every symbol on the export list: run
# with `cmake -P` by the superbuild's media_package target, after the link.
#
# A symbol listed in exports/goldberry-media.symbols that no archive defines is
# not a link error: `-u` leaves it undefined without complaint, and a version
# script names it without checking. The Java loader would find it missing at
# run time, by name; this finds it here, where the fix is.
#
# In: LIBRARY, SYMBOLS, NM, SYSTEM.
# ============================================================================
cmake_minimum_required(VERSION 3.28)

if(NOT NM)
    message(STATUS "no nm on this toolchain; the export list is checked by the Java loader instead")
    return()
endif()

file(STRINGS "${SYMBOLS}" _lines)
set(_wanted "")
foreach(_line IN LISTS _lines)
    string(STRIP "${_line}" _line)
    if(NOT _line STREQUAL "" AND NOT _line MATCHES "^#")
        list(APPEND _wanted "${_line}")
    endif()
endforeach()

if(SYSTEM STREQUAL "Windows")
    # A DLL's export table, through objdump -p, which lives beside nm.
    string(REPLACE "nm" "objdump" _objdump "${NM}")
    execute_process(COMMAND "${_objdump}" -p "${LIBRARY}" OUTPUT_VARIABLE _dump COMMAND_ERROR_IS_FATAL ANY)
    string(REGEX MATCHALL "\\[ *[0-9]+\\] [A-Za-z_][A-Za-z0-9_]*" _entries "${_dump}")
    set(_defined "")
    foreach(_entry IN LISTS _entries)
        string(REGEX REPLACE ".* " "" _entry "${_entry}")
        list(APPEND _defined "${_entry}")
    endforeach()
else()
    execute_process(COMMAND "${NM}" -g --defined-only "${LIBRARY}" OUTPUT_VARIABLE _dump COMMAND_ERROR_IS_FATAL ANY)
    string(REGEX MATCHALL "[^\n]+" _entries "${_dump}")
    set(_defined "")
    foreach(_entry IN LISTS _entries)
        string(REGEX REPLACE ".* " "" _entry "${_entry}")
        # ELF's version suffix, and Mach-O's leading underscore.
        string(REGEX REPLACE "@@.*$" "" _entry "${_entry}")
        if(SYSTEM STREQUAL "Darwin")
            string(REGEX REPLACE "^_" "" _entry "${_entry}")
        endif()
        list(APPEND _defined "${_entry}")
    endforeach()
endif()

set(_missing "")
foreach(_symbol IN LISTS _wanted)
    if(NOT _symbol IN_LIST _defined)
        list(APPEND _missing "${_symbol}")
    endif()
endforeach()
if(_missing)
    string(REPLACE ";" "\n  " _missing_lines "${_missing}")
    message(FATAL_ERROR
        "libgoldberry-media does not define these symbols of exports/goldberry-media.symbols:\n  ${_missing_lines}\n"
        "Either the name is wrong, or the upstream was built without that function.")
endif()
list(LENGTH _wanted _count)
message(STATUS "libgoldberry-media defines every one of the ${_count} exported symbols")
