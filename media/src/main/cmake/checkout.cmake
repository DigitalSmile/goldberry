# ============================================================================
# Refuses an upstream checkout that is not at its pinned commit: run with
# `cmake -P` by the superbuild after each clone, so the source published is the
# source built.
#
# ExternalProject clones by tag, and a tag can be moved. The `ffmpeg-sources`
# classifier is refused on the same pin, so the two agreeing here is what makes
# the published source the source of the published libraries.
#
# In: GIT, SOURCE_DIR, NAME, COMMIT.
# ============================================================================
cmake_minimum_required(VERSION 3.28)

execute_process(COMMAND "${GIT}" -C "${SOURCE_DIR}" rev-parse HEAD
    OUTPUT_VARIABLE _head
    OUTPUT_STRIP_TRAILING_WHITESPACE
    COMMAND_ERROR_IS_FATAL ANY)
if(NOT _head STREQUAL COMMIT)
    message(FATAL_ERROR
        "${NAME} in ${SOURCE_DIR} is at ${_head}, not the pinned ${COMMIT}.\n"
        "Either its tag was moved upstream or the commit in gradle/libs.versions.toml is wrong; "
        "re-pin both after checking which.")
endif()
