package dev.goldberry.media.ffi;

import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.util.OptionalLong;

import org.junit.jupiter.api.Test;

import dev.goldberry.media.io.MediaIO;

/// What one `read_packet` upcall costs: the crossing every byte FFmpeg reads
/// pays.
///
/// FFmpeg calls the stub from C. Here it is called from Java through a downcall
/// handle bound to the stub's address, so each call pays the crossing twice, Java
/// to C and C to Java. That is an upper bound on what the demuxer pays. The
/// comparison is the same 32 KB copy with no crossing at all: the
/// [MediaIO] read into a `ByteBuffer` over native memory.
///
/// **A benchmark, so `check` compiles it and never runs it.** Run with
/// `./gradlew :media:benchmark`. Needs no FFmpeg.
class ReadPacketBenchmark {

    private static final int BLOCK = AvioBridge.BUFFER_SIZE;
    private static final int WARMUP = 200_000;
    private static final int CALLS = 1_000_000;

    /// An endless source: every read fills the buffer, and nothing is ever at the
    /// end. The copy is the one a `FileIO` over a page-cached file does.
    static final class Endless implements MediaIO {
        private final ByteBuffer data = ByteBuffer.allocateDirect(BLOCK);

        @Override
        public int read(ByteBuffer target) {
            var count = Math.min(target.remaining(), BLOCK);
            target.put(target.position(), data, 0, count);
            target.position(target.position() + count);
            return count;
        }

        @Override
        public void seek(long position) {}

        @Override
        public long position() {
            return 0;
        }

        @Override
        public OptionalLong size() {
            return OptionalLong.empty();
        }

        @Override
        public void close() {}
    }

    @Test
    @SuppressWarnings("restricted")
    void perBlock() throws Throwable {
        var callbacks = new IoCallbacks(new Endless(), LayoutFixture.constants());
        var read = MethodHandles.lookup()
                .findVirtual(IoCallbacks.class, "read", AvioBridge.READ_PACKET.toMethodType())
                .bindTo(callbacks);
        try (var arena = Arena.ofConfined()) {
            var linker = Linker.nativeLinker();
            var stub = linker.upcallStub(read, AvioBridge.READ_PACKET, arena);
            var call = linker.downcallHandle(stub, AvioBridge.READ_PACKET);
            var buffer = arena.allocate(BLOCK);
            var unsized = MemorySegment.ofAddress(buffer.address());

            // What the stub reports reading, summed and checked, so the calls are
            // a result somebody reads and not code the JIT may drop.
            var through = 0L;
            for (var i = 0; i < WARMUP; i++) {
                through += (int) call.invokeExact(MemorySegment.NULL, unsized, BLOCK);
                callbacks.read(MemorySegment.NULL, unsized, BLOCK);
            }

            var started = System.nanoTime();
            for (var i = 0; i < CALLS; i++) {
                through += (int) call.invokeExact(MemorySegment.NULL, unsized, BLOCK);
            }
            var crossing = (System.nanoTime() - started) / (double) CALLS;
            if (through <= 0) {
                throw new AssertionError("the stub read nothing in " + (WARMUP + CALLS) + " calls");
            }

            started = System.nanoTime();
            for (var i = 0; i < CALLS; i++) {
                callbacks.read(MemorySegment.NULL, unsized, BLOCK);
            }
            var direct = (System.nanoTime() - started) / (double) CALLS;

            var gbPerSecond = BLOCK / crossing;
            System.out.printf(
                    "read_packet, %d KB blocks: %.0f ns through the stub, %.0f ns called directly;"
                            + " %.0f ns per block for the crossing, %.1f GB/s through the stub%n",
                    BLOCK / 1024, crossing, direct, crossing - direct, gbPerSecond);
        }
    }
}
