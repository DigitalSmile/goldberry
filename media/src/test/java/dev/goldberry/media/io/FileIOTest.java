package dev.goldberry.media.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.OptionalLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("FileIO")
class FileIOTest {

    @TempDir
    Path directory;

    private Path file(byte... bytes) throws IOException {
        return Files.write(directory.resolve("data.bin"), bytes);
    }

    @Test
    @DisplayName("reads the file front to back, then answers -1")
    void reads() throws IOException {
        try (var io = FileIO.open(file((byte) 1, (byte) 2, (byte) 3))) {
            var buffer = ByteBuffer.allocate(8);
            assertEquals(3, io.read(buffer));
            assertArrayEquals(new byte[] {1, 2, 3}, java.util.Arrays.copyOf(buffer.array(), 3));
            assertEquals(-1, io.read(ByteBuffer.allocate(8)));
            assertEquals(3, io.position());
        }
    }

    @Test
    @DisplayName("seeks to an absolute position, and past the end reads as the end")
    void seeks() throws IOException {
        try (var io = FileIO.open(file((byte) 1, (byte) 2, (byte) 3))) {
            io.seek(2);
            var buffer = ByteBuffer.allocate(1);
            assertEquals(1, io.read(buffer));
            assertEquals(3, buffer.get(0));
            io.seek(10);
            assertEquals(-1, io.read(ByteBuffer.allocate(1)));
            assertThrows(IOException.class, () -> io.seek(-1));
        }
    }

    @Test
    @DisplayName("knows its size and is seekable")
    void size() throws IOException {
        try (var io = FileIO.open(file(new byte[1234]))) {
            assertEquals(OptionalLong.of(1234), io.size());
            assertTrue(io.isSeekable());
        }
    }

    @Test
    @DisplayName("a read after close fails as closed, which is how an abort is recognised")
    void closed() throws IOException {
        var io = FileIO.open(file((byte) 1));
        io.close();
        assertThrows(ClosedChannelException.class, () -> io.read(ByteBuffer.allocate(1)));
        assertEquals(-1, io.position());
    }

    @Test
    @DisplayName("fails to open a file that is not there")
    void missing() {
        assertThrows(NoSuchFileException.class, () -> FileIO.open(directory.resolve("absent")));
    }
}
