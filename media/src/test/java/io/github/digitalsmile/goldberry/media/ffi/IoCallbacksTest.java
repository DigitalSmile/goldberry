package io.github.digitalsmile.goldberry.media.ffi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.media.io.MemoryIO;

/// The callback rules, with no FFmpeg: the buffer is native memory from a Java
/// arena, handed over the way FFmpeg hands it, zero-length.
@DisplayName("IoCallbacks")
class IoCallbacksTest {

    private static final FfmpegConstants C = LayoutFixture.constants();
    private static final byte[] DATA = {10, 20, 30, 40, 50};

    private final Arena arena = Arena.ofConfined();

    @AfterEach
    void close() {
        arena.close();
    }

    /// A buffer as FFmpeg passes it: the address, with no size attached.
    private MemorySegment buffer(int size) {
        return MemorySegment.ofAddress(arena.allocate(size).address());
    }

    @SuppressWarnings("restricted")
    private byte[] bytes(MemorySegment buffer, int count) {
        return MemorySegment.ofAddress(buffer.address()).reinterpret(count).toArray(ValueLayout.JAVA_BYTE);
    }

    @Nested
    @DisplayName("read_packet")
    class Read {

        @Test
        @DisplayName("copies into FFmpeg's buffer and answers the count, then AVERROR_EOF")
        void reads() {
            var callbacks = new IoCallbacks(new MemoryIO(DATA), C);
            var buffer = buffer(16);
            assertEquals(5, callbacks.read(MemorySegment.NULL, buffer, 16));
            assertArrayEquals(DATA, bytes(buffer, 5));
            assertEquals(C.averrorEof(), callbacks.read(MemorySegment.NULL, buffer, 16));
        }

        @Test
        @DisplayName("passes a short read through; FFmpeg asks again")
        void shortRead() {
            var io = new MemoryIO(DATA);
            io.chunk = 2;
            var callbacks = new IoCallbacks(io, C);
            assertEquals(2, callbacks.read(MemorySegment.NULL, buffer(16), 16));
        }

        @Test
        @DisplayName("never answers 0: a read of nothing is asked again")
        void emptyReads() {
            var io = new MemoryIO(DATA);
            io.emptyReads = 3;
            assertEquals(5, new IoCallbacks(io, C).read(MemorySegment.NULL, buffer(16), 16));
        }

        @Test
        @DisplayName("a MediaIO that only ever reads nothing is a failure, not a spin")
        void endlessEmptyReads() {
            var io = new MemoryIO(DATA);
            io.emptyReads = Integer.MAX_VALUE;
            var callbacks = new IoCallbacks(io, C);
            assertEquals(C.averrorEio(), callbacks.read(MemorySegment.NULL, buffer(16), 16));
            assertTrue(callbacks.failure().getMessage().contains("returned 0 bytes"));
        }

        @Test
        @DisplayName("an I/O failure is AVERROR(EIO), and the first one is kept for the error")
        void failure() {
            var io = new MemoryIO(DATA);
            var first = new IOException("connection reset");
            io.readFailure = first;
            var callbacks = new IoCallbacks(io, C);
            assertEquals(C.averrorEio(), callbacks.read(MemorySegment.NULL, buffer(16), 16));
            io.readFailure = new IOException("second");
            callbacks.read(MemorySegment.NULL, buffer(16), 16);
            assertSame(first, callbacks.failure());
        }

        @Test
        @DisplayName("a bug in the MediaIO does not escape into C")
        void bug() {
            var io = new MemoryIO(DATA);
            io.readBug = new IllegalStateException("oops");
            var callbacks = new IoCallbacks(io, C);
            assertEquals(C.averrorEio(), callbacks.read(MemorySegment.NULL, buffer(16), 16));
            assertInstanceOf(IllegalStateException.class, callbacks.failure().getCause());
        }

        @Test
        @DisplayName("a stream closed under the read is an abort, AVERROR_EXIT")
        void closedUnder() {
            var io = new MemoryIO(DATA);
            io.close();
            var callbacks = new IoCallbacks(io, C);
            assertEquals(C.averrorExit(), callbacks.read(MemorySegment.NULL, buffer(16), 16));
            assertTrue(callbacks.aborted());
            assertNull(callbacks.failure());
        }
    }

    @Nested
    @DisplayName("seek")
    class Seek {

        @Test
        @DisplayName("SEEK_SET, SEEK_CUR and SEEK_END become absolute positions")
        void whence() throws IOException {
            var io = new MemoryIO(DATA);
            var callbacks = new IoCallbacks(io, C);
            assertEquals(2, callbacks.seek(MemorySegment.NULL, 2, IoCallbacks.SEEK_SET));
            assertEquals(3, callbacks.seek(MemorySegment.NULL, 1, IoCallbacks.SEEK_CUR));
            assertEquals(4, callbacks.seek(MemorySegment.NULL, -1, IoCallbacks.SEEK_END));
            assertEquals(4, io.position());
        }

        @Test
        @DisplayName("ignores AVSEEK_FORCE")
        void force() {
            var callbacks = new IoCallbacks(new MemoryIO(DATA), C);
            assertEquals(1, callbacks.seek(MemorySegment.NULL, 1, IoCallbacks.SEEK_SET | C.avseekForce()));
        }

        @Test
        @DisplayName("answers AVSEEK_SIZE with the size, and with an error when it is unknown")
        void size() {
            var io = new MemoryIO(DATA);
            var callbacks = new IoCallbacks(io, C);
            assertEquals(5, callbacks.seek(MemorySegment.NULL, 0, C.avseekSize()));
            io.sizeKnown = false;
            assertTrue(callbacks.seek(MemorySegment.NULL, 0, C.avseekSize()) < 0);
            assertTrue(callbacks.seek(MemorySegment.NULL, 0, IoCallbacks.SEEK_END) < 0);
        }

        @Test
        @DisplayName("refuses a position before the start and a whence it does not know")
        void refuses() {
            var callbacks = new IoCallbacks(new MemoryIO(DATA), C);
            assertEquals(C.averrorEio(), callbacks.seek(MemorySegment.NULL, -1, IoCallbacks.SEEK_SET));
            assertEquals(C.averrorEio(), callbacks.seek(MemorySegment.NULL, 0, 7));
        }
    }

    @Test
    @DisplayName("abort closes the MediaIO and every callback after it answers AVERROR_EXIT")
    void abort() {
        var io = new MemoryIO(DATA);
        var callbacks = new IoCallbacks(io, C);
        callbacks.abort();
        assertEquals(1, io.closes);
        assertEquals(C.averrorExit(), callbacks.read(MemorySegment.NULL, buffer(16), 16));
        assertEquals(C.averrorExit(), callbacks.seek(MemorySegment.NULL, 0, IoCallbacks.SEEK_SET));
    }
}
