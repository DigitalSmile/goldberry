package dev.goldberry.natives.wayland;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The descriptors a Wayland client hands the compositor, read back through
/// `/proc/self/fd`, which reopens an anonymous file from its start.
@DisplayName("the descriptors a Wayland client writes")
class DescriptorsTest {

    @BeforeEach
    void linux() {
        assumeTrue(Descriptors.isAvailable() && Files.isDirectory(Path.of("/proc/self/fd")), "Linux only");
    }

    @Test
    @DisplayName("makes an anonymous file holding the bytes")
    void anonymousFile() throws Exception {
        var bytes = "pixels".getBytes(StandardCharsets.UTF_8);
        var fd = Descriptors.anonymousFile("goldberry-test", bytes).orElseThrow();
        try {
            assertArrayEquals(bytes, Files.readAllBytes(Path.of("/proc/self/fd/" + fd)));
        } finally {
            assertTrue(Descriptors.close(fd));
        }
    }

    @Test
    @DisplayName("writes all of a drag's bytes and closes the descriptor")
    void writeAndClose() throws Exception {
        var fd = Descriptors.anonymousFile("goldberry-test", new byte[0]).orElseThrow();
        var bytes = new byte[300_000];
        for (var i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) i;
        }
        // Held open across the close, so the file outlives the descriptor.
        try (var channel = Files.newByteChannel(Path.of("/proc/self/fd/" + fd))) {
            assertTrue(Descriptors.writeAndClose(fd, bytes));
            var read = ByteBuffer.allocate(bytes.length);
            while (read.hasRemaining() && channel.read(read) > 0) {
                // Until it is all there.
            }
            assertArrayEquals(bytes, read.array());
        }
    }

    @Test
    @DisplayName("blocks SIGPIPE on a platform thread of its own")
    void brokenPipes() throws Exception {
        var blocked = new AtomicBoolean();
        var thread = Thread.ofPlatform().start(() -> blocked.set(Descriptors.ignoreBrokenPipes()));
        thread.join();

        assertTrue(blocked.get());
    }
}
