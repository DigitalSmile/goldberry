package dev.goldberry.natives.webview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.natives.NativeLibrary;
import dev.goldberry.natives.NativePlatform;

/// A page signs in, and the application gets what it needs out of the engine:
/// the HttpOnly session cookie no script can read, and the custom-scheme
/// redirect no engine can load.
///
/// A real page against a real server — `com.sun.net.httpserver` on loopback —
/// in a child JVM ([WebviewSignInProbe]). It needs a display and WebKitGTK, and
/// skips without either, the way [WebviewExitTest] does.
@DisplayName("a page that signs in")
class WebviewSignInTest {

    @Test
    @DisplayName("hands over its HttpOnly cookie and its custom-scheme redirect, and cancels what it is told to")
    void signsIn() throws IOException, InterruptedException {
        assumeTrue(
                NativePlatform.current().os() == NativePlatform.OperatingSystem.LINUX,
                "the probe pumps GLib itself, which is the Linux engine's loop");
        assumeTrue(
                System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null, "a page needs a display");
        var library = System.getProperty(NativeLibrary.LIBRARY_PATH_PROPERTY);
        assumeTrue(library != null && !library.isBlank(), "no libgoldberry for this run");

        var requests = new CopyOnWriteArrayList<String>();
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestURI().getPath());
            if (exchange.getRequestURI().getPath().equals("/login")) {
                exchange.getResponseHeaders().add("Set-Cookie", "session=s3cr3t; Path=/; HttpOnly");
                exchange.getResponseHeaders().add("Set-Cookie", "theme=dark; Path=/");
                exchange.getResponseHeaders().add("Location", "myapp://callback?code=x");
                exchange.sendResponseHeaders(302, -1);
            } else {
                var body = "<!doctype html><title>home</title>".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
        List<String> output;
        int exit;
        try {
            var base = "http://127.0.0.1:" + server.getAddress().getPort();
            var java = ProcessHandle.current().info().command().orElseThrow();
            var command = new ArrayList<String>();
            command.add(java);
            command.add("--enable-native-access=ALL-UNNAMED");
            command.add("-D" + NativeLibrary.LIBRARY_PATH_PROPERTY + "=" + library);
            command.add("-cp");
            command.add(System.getProperty("java.class.path"));
            command.add(WebviewSignInProbe.class.getName());
            command.add(base);
            var process = new ProcessBuilder(command).redirectErrorStream(true).start();
            var finished = process.waitFor(90, TimeUnit.SECONDS);
            output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .toList();
            if (!finished) {
                process.destroyForcibly();
            }
            assertTrue(finished, "the probe did not finish:\n" + String.join("\n", output));
            exit = process.exitValue();
        } finally {
            server.stop(0);
        }
        var text = String.join("\n", output);
        assumeFalse(exit == WebviewSignInProbe.NO_PAGE, "no web view on this machine");
        assertEquals(0, exit, text);

        assertTrue(output.contains("hooked true"), text);
        assertTrue(output.contains("callback myapp://callback?code=x"), text);
        assertTrue(output.contains("cookie session=s3cr3t httponly=true path=/"), text);
        assertTrue(output.contains("cookie theme=dark httponly=false path=/"), text);
        assertTrue(output.stream().anyMatch(line -> line.startsWith("navigate ") && line.endsWith("/blocked")), text);
        assertTrue(output.contains("done"), text);
        assertTrue(requests.contains("/login"), requests.toString());
        assertTrue(!requests.contains("/blocked"), "the refused navigation reached the server: " + requests);
    }
}
