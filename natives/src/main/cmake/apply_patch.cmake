# Applies one patch to a fetched upstream, run as FetchContent's PATCH_COMMAND
# in the upstream's source directory:
#
#   cmake -DGIT=<git> -DPATCH=<file.patch> -P apply_patch.cmake
#
# Idempotent, and it follows a changed patch: every file the patch touches is
# first put back as the pinned ref has it, then the patch is applied. So a
# configure over a source already patched, or patched by an older version of
# the patch, ends with exactly this patch applied. A patch that no longer
# applies -- an upstream bump moved the lines -- stops the configure and says
# which, rather than building an unpatched library.
cmake_minimum_required(VERSION 3.28)

foreach(_required IN ITEMS GIT PATCH)
    if(NOT DEFINED ${_required})
        message(FATAL_ERROR "apply_patch.cmake needs -D${_required}=...")
    endif()
endforeach()

# The files the patch touches, from its `+++ b/<path>` lines.
file(STRINGS "${PATCH}" _targets REGEX "^\\+\\+\\+ b/")
list(TRANSFORM _targets REPLACE "^\\+\\+\\+ b/" "")
if(NOT _targets)
    message(FATAL_ERROR "${PATCH} touches no file")
endif()

execute_process(
    COMMAND "${GIT}" checkout -- ${_targets}
    RESULT_VARIABLE _reset
    ERROR_VARIABLE _reset_error)
if(NOT _reset EQUAL 0)
    message(FATAL_ERROR "could not reset ${_targets} before patching:\n${_reset_error}")
endif()

execute_process(
    COMMAND "${GIT}" apply --whitespace=nowarn "${PATCH}"
    RESULT_VARIABLE _applied
    ERROR_VARIABLE _apply_error)
if(NOT _applied EQUAL 0)
    message(FATAL_ERROR
        "${PATCH} no longer applies:\n${_apply_error}\n"
        "The pinned upstream changed under it. Rebase the patch on the new ref, "
        "or drop it if upstream now does the same.")
endif()
get_filename_component(_name "${PATCH}" NAME)
message(STATUS "Patched with ${_name}: ${_targets}")
