package dev.goldberry.natives;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The patches the superbuild puts on SDL, read as text. The camera patch is declared for
/// Linux, it does the three things it is there for, and the SDL this machine
/// fetched has it applied.
///
/// SDL 3.4's PipeWire camera never sends PipeWire buffer parameters, so
/// PipeWire 1.6 refuses its stream and the camera's permission is never
/// granted. Without the patch every camera on a PipeWire desktop would wait for
/// ever, since cameras try PipeWire first; that is worth a guard a careless
/// edit to the declaration cannot slip past.
@DisplayName("the SDL platform patches")
class SdlPatchTest {

    private static final String PATCH = "patches/sdl3-pipewire-camera.patch";

    private final Path cmakeDir = locateCmakeDir();

    /// `:natives/src/main/cmake`, from wherever the tests were started, as
    /// [SuperbuildTest] finds it.
    private static Path locateCmakeDir() {
        var working = Path.of(System.getProperty("user.dir"));
        var own = working.resolve("src/main/cmake");
        return Files.isRegularFile(own.resolve("CMakeLists.txt")) ? own : working.resolve("natives/src/main/cmake");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    @DisplayName("is applied to SDL on Linux, by a script that resets what it touches first")
    void declared() {
        var cmakeLists = read(cmakeDir.resolve("CMakeLists.txt"));
        var start = cmakeLists.indexOf("set(_sdl3_patch_arguments");
        assertTrue(start >= 0, "the superbuild does not patch SDL");
        var declaration = cmakeLists.substring(start, cmakeLists.indexOf("FetchContent_Declare(sdl3", start) + 200);
        var script = read(cmakeDir.resolve("apply_patch.cmake"));
        assertAll(
                () -> assertTrue(declaration.contains("CMAKE_SYSTEM_NAME STREQUAL \"Linux\""), declaration),
                () -> assertTrue(declaration.contains(PATCH), declaration),
                () -> assertTrue(declaration.contains("${_sdl3_patch_arguments}"), "the declaration takes the patch"),
                () -> assertTrue(
                        declaration.contains("file(SHA256"), "a changed patch must change the declaration to reapply"),
                () -> assertTrue(script.contains("checkout --"), "the script resets before it applies"),
                () -> assertTrue(script.contains("FATAL_ERROR"), "a patch that no longer applies stops the build"));
    }

    @Test
    @DisplayName("on macOS SDL inserts its Metal view beneath other content subviews")
    void cocoaStacking() {
        var cmakeLists = read(cmakeDir.resolve("CMakeLists.txt"));
        var patch = read(cmakeDir.resolve("patches/sdl3-cocoa-metal-stacking.patch"));
        assertAll(
                () -> assertTrue(cmakeLists.contains("""
                        elseif(CMAKE_SYSTEM_NAME STREQUAL "Darwin")
                            set(_sdl3_patch "${CMAKE_CURRENT_LIST_DIR}/patches/sdl3-cocoa-metal-stacking.patch")
                        endif()
                        """)),
                () -> assertTrue(patch.contains("+++ b/src/video/cocoa/SDL_cocoametalview.m")),
                () -> assertTrue(
                        patch.contains("+        [view addSubview:newview positioned:NSWindowBelow relativeTo:nil];")),
                () -> assertTrue(patch.contains("Goldberry patch")));
    }

    @Test
    @DisplayName("sends buffer parameters, waits with a timeout, and approves a camera once")
    void content() {
        var patch = read(cmakeDir.resolve(PATCH));
        assertAll(
                () -> assertTrue(patch.contains("+++ b/src/camera/pipewire/SDL_camera_pipewire.c"), "one file"),
                () -> assertTrue(patch.contains("+    PIPEWIRE_pw_stream_update_params(")),
                () -> assertTrue(patch.contains("SPA_PARAM_Buffers")),
                () -> assertTrue(
                        patch.contains("+    SDL_PIPEWIRE_SYM(pw_stream_update_params);"), "loaded at run time"),
                () -> assertTrue(patch.contains("+    PIPEWIRE_pw_thread_loop_timed_wait(hotplug.loop, 1);")),
                () -> assertTrue(patch.contains("+    SDL_PIPEWIRE_SYM(pw_thread_loop_timed_wait);")),
                () -> assertTrue(patch.contains("device->permission == SDL_CAMERA_PERMISSION_STATE_PENDING")),
                () -> assertTrue(
                        patch.contains("Goldberry patch"), "the zlib licence asks an altered source to say so"));
    }

    @Test
    @DisplayName("is in the SDL this machine fetched for Linux")
    void applied() {
        var source = cmakeDir.resolve("../../../.deps")
                .normalize()
                .resolve("linux-x64/sdl3-src/src/camera/pipewire/SDL_camera_pipewire.c");
        assumeTrue(Files.isRegularFile(source), "no Linux SDL has been fetched here");
        var text = read(source);
        assertTrue(
                text.contains("PIPEWIRE_pw_stream_update_params(device->hidden->stream"),
                source + " is not patched: configure again");
    }
}
