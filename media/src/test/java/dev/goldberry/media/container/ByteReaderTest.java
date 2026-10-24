package dev.goldberry.media.container;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.foreign.Arena;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.io.MemoryIO;

/// The reader under the demuxers, held to what the robustness tests found:
/// a size read from a damaged file is never trusted with an allocation, and
/// a negative one is the file's fault, not the caller's.
@DisplayName("ByteReader")
class ByteReaderTest {

    private static ByteReader over(int length, boolean sizeKnown) {
        var bytes = new byte[length];
        Arrays.fill(bytes, (byte) 7);
        var io = new MemoryIO(bytes);
        io.sizeKnown = sizeKnown;
        return new ByteReader(io);
    }

    private static void assertInvalidData(Executable executable) {
        var e = assertThrows(MediaException.class, executable);
        assertInstanceOf(MediaError.InvalidData.class, e.error());
    }

    @Test
    @DisplayName("a count beyond what a stream of known size holds fails before anything is allocated")
    void countBeyondKnownSize() {
        var in = over(16, true);
        // An allocation first would be an OutOfMemoryError, not this.
        assertInvalidData(() -> in.bytes(Integer.MAX_VALUE - 8));
        try (var arena = Arena.ofConfined()) {
            assertInvalidData(() -> in.segment(arena, 1L << 40));
        }
        assertEquals(0, in.position(), "nothing was read");
        assertArrayEquals(new byte[] {7, 7, 7, 7}, in.bytes(4));
    }

    @Test
    @DisplayName("on a stream of unknown size, a count it does not hold fails at its end, not on the heap")
    void countBeyondUnknownSize() {
        var in = over(200_000, false);
        assertInvalidData(() -> in.bytes(Integer.MAX_VALUE - 8));
        var again = over(200_000, false);
        try (var arena = Arena.ofConfined()) {
            assertInvalidData(() -> again.segment(arena, Integer.MAX_VALUE - 8));
        }
    }

    @Test
    @DisplayName("a long count on a stream of unknown size reads whole, gathered as it arrives")
    void longCountOfUnknownSize() {
        var in = over(200_000, false);
        var bytes = in.bytes(150_000);
        assertEquals(150_000, bytes.length);
        assertEquals(7, bytes[149_999]);
        try (var arena = Arena.ofConfined()) {
            assertEquals(50_000, in.segment(arena, 50_000).byteSize());
        }
    }

    @Test
    @DisplayName("a negative count, offset or skip is invalid data, which only a damaged size makes")
    void negative() {
        var in = over(16, true);
        assertInvalidData(() -> in.bytes(-23_542_674));
        assertInvalidData(() -> in.skip(-1_890_553_129L));
        assertInvalidData(() -> in.seek(-1));
        try (var arena = Arena.ofConfined()) {
            assertInvalidData(() -> in.segment(arena, -1));
        }
    }
}
