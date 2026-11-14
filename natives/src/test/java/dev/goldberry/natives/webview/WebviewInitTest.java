package dev.goldberry.natives.webview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.natives.NativeLibrary;
import dev.goldberry.natives.NativePlatform;

/// An application's script runs before a page's own: the page's first inline
/// script already sees what it defined. A real page in a child JVM
/// ([WebviewInitProbe]), skipped without a display or WebKitGTK, as
/// [WebviewSignInTest] is.
@DisplayName("a page's init script")
class WebviewInitTest {

    @Test
    @DisplayName("runs before the document's own scripts, and eval reaches a binding")
    void runsFirst() throws IOException, InterruptedException {
        assumeTrue(
                NativePlatform.current().os() == NativePlatform.OperatingSystem.LINUX,
                "the probe pumps GLib itself, which is the Linux engine's loop");
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
        command.add(WebviewInitProbe.class.getName());
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        var finished = process.waitFor(90, TimeUnit.SECONDS);
        List<String> output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .lines()
                .toList();
        if (!finished) {
            process.destroyForcibly();
        }
        var text = String.join("\n", output);
        assertTrue(finished, "the probe did not finish:\n" + text);
        assumeFalse(process.exitValue() == WebviewInitProbe.NO_PAGE, "no web view on this machine");
        assertEquals(0, process.exitValue(), text);

        assertTrue(output.contains("report [\"gb-9\"]"), "the document saw the init script's global:\n" + text);
        assertTrue(output.contains("report [\"from eval\"]"), text);
        assertTrue(output.contains("done"), text);
    }
}
