# ============================================================================
# Refuses a library that does not define every symbol on the export list: run
# with `cmake -P` by the superbuild's media_package target, after the link.
#
# A symbol listed in exports/goldberry-media.symbols that no archive defines is
# not a link error: `-u` leaves it undefined without complaint, and a version
# script names it without checking. The Java loader would find it missing at
# run time, by name; this finds it here, where the fix is.
#
# In: LIBRARY, SYMBOLS, NM, SYSTEM; DUMP, a file holding what nm (objdump -p on
# Windows) printed for the library, read instead of running the tool, which is
# how ExportCheckTest holds the parsing to each toolchain's output.
# ============================================================================
cmake_minimum_required(VERSION 3.28)

if(NOT NM AND NOT DUMP)
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

if(DUMP)
    file(READ "${DUMP}" _dump)
elseif(SYSTEM STREQUAL "Windows")
    # A DLL's export table, through objdump -p, which lives beside nm.
    string(REGEX REPLACE "nm(\\.exe)?$" "objdump\\1" _objdump "${NM}")
    execute_process(COMMAND "${_objdump}" -p "${LIBRARY}" OUTPUT_VARIABLE _dump COMMAND_ERROR_IS_FATAL ANY)
else()
    execute_process(COMMAND "${NM}" -g --defined-only "${LIBRARY}" OUTPUT_VARIABLE _dump COMMAND_ERROR_IS_FATAL ANY)
endif()

set(_defined "")
if(SYSTEM STREQUAL "Windows")
    # The names are the last word of each line of the name pointer table, which
    # ends at the first blank line. Binutils before 2.39 prints `[   0] name`,
    # later ones `[   0] +base[   1]  0000 name` under a column header.
    string(FIND "${_dump}" "[Ordinal/Name Pointer] Table" _at)
    if(_at GREATER_EQUAL 0)
        string(SUBSTRING "${_dump}" ${_at} -1 _table)
        string(REGEX REPLACE "\n[ \t\r]*\n.*$" "" _table "${_table}")
        string(REGEX MATCHALL "\\[ *[0-9]+\\][^\n]*" _entries "${_table}")
        foreach(_entry IN LISTS _entries)
            string(STRIP "${_entry}" _entry)
            string(REGEX REPLACE ".*[ \t]" "" _entry "${_entry}")
            list(APPEND _defined "${_entry}")
        endforeach()
    endif()
else()
    string(REGEX MATCHALL "[^\n]+" _entries "${_dump}")
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
