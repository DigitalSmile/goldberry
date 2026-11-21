package dev.goldberry.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/// Two launches of one application, in one JVM over a temporary directory:
/// the first holds the claim, and the second hands its arguments over and is
/// told to exit.
@DisplayName("a single-instance claim")
class SingleInstanceTest {

    @Test
    @Timeout(20)
    @DisplayName("hands a second launch's arguments to the first, whole and in order")
    void handsOver(@TempDir Path directory) throws InterruptedException {
        var heard = new LinkedBlockingQueue<List<String>>();
        try (var first = SingleInstance.acquire(directory, List.of("--first")).orElseThrow()) {
            first.onArguments(Runnable::run, heard::add);

            var second = SingleInstance.acquire(directory, List.of("tessera://invite/42", "--minimised", "фото.png"));
            assertTrue(second.isEmpty(), "the second launch is told the first is running");
            assertEquals(List.of("tessera://invite/42", "--minimised", "фото.png"), heard.poll(10, TimeUnit.SECONDS));

            SingleInstance.acquire(directory, List.of());
            assertEquals(List.of(), heard.poll(10, TimeUnit.SECONDS), "an empty command line still arrives");
        }
    }

    @Test
    @Timeout(20)
    @DisplayName("hands over through a short socket name when the directory is too deep for a socket beside the lock")
    void deepDirectory(@TempDir Path root) throws InterruptedException {
        var directory = root.resolve("a".repeat(60)).resolve("dev.goldberry.test." + "b".repeat(36));
        var socket = SingleInstance.socketFor(directory);
        assertFalse(socket.startsWith(directory), "the socket moved out of the deep directory");
        assertTrue(socket.getFileName().toString().matches("goldberry-[0-9a-f]{16}\\.sock"), socket::toString);
        assertEquals(socket, SingleInstance.socketFor(directory), "both launches find the same socket");

        var heard = new LinkedBlockingQueue<List<String>>();
        try (var first = SingleInstance.acquire(directory, List.of()).orElseThrow()) {
            first.onArguments(Runnable::run, heard::add);
            assertTrue(SingleInstance.acquire(directory, List.of("tessera://invite/7"))
                    .isEmpty());
            assertEquals(List.of("tessera://invite/7"), heard.poll(10, TimeUnit.SECONDS));
        }
        assertFalse(Files.exists(socket), "closing removes the socket wherever it was");
    }

    @Test
    @DisplayName("keeps the socket beside the lock when the path fits")
    void shallowDirectory(@TempDir Path directory) {
        assumeTrue(directory.resolve("instance.sock").toString().length() <= SingleInstance.MAX_SOCKET_PATH_BYTES);
        assertEquals(directory.resolve("instance.sock"), SingleInstance.socketFor(directory));
    }

    @Test
    @Timeout(20)
    @DisplayName("keeps arguments that arrive before anyone listens, and hands them over when someone does")
    void early(@TempDir Path directory) throws InterruptedException {
        try (var first = SingleInstance.acquire(directory, List.of()).orElseThrow()) {
            SingleInstance.acquire(directory, List.of("one"));
            var heard = new LinkedBlockingQueue<List<String>>();
            // The hand-over is read on a thread of its own; give it a moment to land.
            Thread.sleep(200);
            first.onArguments(Runnable::run, heard::add);
            assertEquals(List.of("one"), heard.poll(10, TimeUnit.SECONDS));
        }
    }

    @Test
    @Timeout(20)
    @DisplayName("lets the next launch be the first once closed, and a dead copy's socket is no obstacle")
    void closes(@TempDir Path directory) {
        var first = SingleInstance.acquire(directory, List.of()).orElseThrow();
        first.close();
        first.close();
        var next = SingleInstance.acquire(directory, List.of());
        assertTrue(next.isPresent(), "the claim is free again");
        try (var claim = next.get()) {
            assertEquals(directory, claim.directory());
        }
    }

    @Test
    @DisplayName("names a directory per application, and refuses an id that is not a file name")
    void directories() {
        var path = SingleInstance.directoryFor("dev.example.chat");
        assertEquals("dev.example.chat", path.getFileName().toString());
        assertThrows(IllegalArgumentException.class, () -> SingleInstance.directoryFor("../escape"));
        assertThrows(IllegalArgumentException.class, () -> SingleInstance.directoryFor(" "));
    }
}
