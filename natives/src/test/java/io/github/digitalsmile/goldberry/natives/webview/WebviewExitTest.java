package io.github.digitalsmile.goldberry.natives.webview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import io.github.digitalsmile.goldberry.natives.NativePlatform;

/// A process that has shown a page ends cleanly (ADR-0507).
///
/// In a child JVM, because the defect is in the end of a process and nothing
/// inside one can watch its own exit handlers: WebKit dropped its default
/// context from `exit()` on the launcher's first thread, and its website data
/// store aborted the process for touching main-thread state from there — after
/// every line of the application had run.
@DisplayName("a process that showed a page")
class WebviewExitTest {

    @Test
    @DisplayName("exits with 0, and WebKit says nothing on the way out")
    void exitsCleanly() throws IOException, InterruptedException {
        assumeTrue(
                NativePlatform.current().os() == NativePlatform.OperatingSystem.LINUX,
                "the exit-handler thread is the stock launcher's on Linux");
        assumeTrue(
                System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null, "a page needs a display");
        var library = System.getProperty(NativeLibrary.LIBRARY_PATH_PROPERTY);
        assumeTrue(library != null && !library.isBlank(), "no libgoldberry for this run");

        var java = ProcessHandle.current().info().command().orElseThrow();
        var command = new ArrayList<String>();
        command.add(java);
        command.add("--enable-native-access=ALL-UNNAMED");
        command.add("-D" + NativeLibrary.LIBRARY_PATH_PROPERTY + "=" + library);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(WebviewExitProbe.class.getName());
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        var finished = process.waitFor(60, TimeUnit.SECONDS);
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!finished) {
            process.destroyForcibly();
        }

        assertTrue(finished, "the probe did not finish:\n" + output);
        assumeFalse(process.exitValue() == WebviewExitProbe.NO_PAGE, "no web view on this machine");
        assertEquals(0, process.exitValue(), "134 is the abort ADR-0507 fixed:\n" + output);
        assertFalse(output.contains("WebKit encountered an internal error"), output);
    }
}
