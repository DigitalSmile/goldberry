package dev.goldberry.media.ffi;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import dev.goldberry.media.io.MediaIO;

/// A custom `AVIOContext` over a [MediaIO]: how every byte reaches FFmpeg.
///
/// Two upcall stubs per context, `read_packet` and, for a seekable source,
/// `seek`, both bound to this context's [IoCallbacks]. That is the rule the
/// toolkit's Yoga measure functions follow, applied here: a stub is bound to
/// the object it serves. A callback already belongs to exactly
/// one stream, and dispatching one shared stub on the `opaque` pointer would mean
/// a table from pointers to Java objects, plus the leak when an entry is
/// forgotten. `opaque` is null.
///
/// The stubs live in a shared arena, because FFmpeg calls them from whichever
/// thread is demuxing. The arena is closed only by [#close()], after the demuxer
/// is closed and cannot call again.
///
/// The bridge does **not** own the `MediaIO`. The code that opened it closes it.
final class AvioBridge implements AutoCloseable {

    /// The I/O buffer: FFmpeg reads in blocks this size, so it is also the unit an
    /// upcall moves. 32 KB is FFmpeg's own default for `file:`.
    static final int BUFFER_SIZE = 32 * 1024;

    /// `int (*read_packet)(void *opaque, uint8_t *buf, int buf_size)`
    static final FunctionDescriptor READ_PACKET = FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT);

    /// `int64_t (*seek)(void *opaque, int64_t offset, int whence)`
    static final FunctionDescriptor SEEK = FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_LONG, JAVA_INT);

    private static final MethodHandle READ_HANDLE;
    private static final MethodHandle SEEK_HANDLE;

    static {
        try {
            var lookup = MethodHandles.lookup();
            READ_HANDLE = lookup.findVirtual(IoCallbacks.class, "read", READ_PACKET.toMethodType());
            SEEK_HANDLE = lookup.findVirtual(IoCallbacks.class, "seek", SEEK.toMethodType());
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final Ffmpeg ffmpeg;
    private final IoCallbacks callbacks;
    private final Arena arena;
    private final MemorySegment context;
    private boolean closed;

    private AvioBridge(Ffmpeg ffmpeg, IoCallbacks callbacks, Arena arena, MemorySegment context) {
        this.ffmpeg = ffmpeg;
        this.callbacks = callbacks;
        this.arena = arena;
        this.context = context;
    }

    /// A context reading `io`. It is seekable exactly when `io` is.
    @SuppressWarnings("restricted")
    static AvioBridge open(Ffmpeg ffmpeg, MediaIO io) {
        var callbacks = new IoCallbacks(io, ffmpeg.constants());
        var arena = Arena.ofShared();
        MemorySegment buffer = MemorySegment.NULL;
        try {
            var linker = Linker.nativeLinker();
            var read = linker.upcallStub(READ_HANDLE.bindTo(callbacks), READ_PACKET, arena);
            var seek = io.isSeekable()
                    ? linker.upcallStub(SEEK_HANDLE.bindTo(callbacks), SEEK, arena)
                    : MemorySegment.NULL;
            buffer = ffmpeg.malloc(BUFFER_SIZE);
            var context = ffmpeg.format()
                    .ioAllocContext()
                    .call(buffer, BUFFER_SIZE, 0, MemorySegment.NULL, read, MemorySegment.NULL, seek);
            if (context.equals(MemorySegment.NULL)) {
                throw new OutOfMemoryError("avio_alloc_context failed");
            }
            return new AvioBridge(ffmpeg, callbacks, arena, AvIoContextView.of(context));
        } catch (RuntimeException | Error e) {
            ffmpeg.free(buffer);
            arena.close();
            throw e;
        }
    }

    /// The `AVIOContext*`, for `AVFormatContext.pb`.
    MemorySegment context() {
        return context;
    }

    /// The callbacks, for what they recorded: an abort, or the first I/O failure.
    IoCallbacks callbacks() {
        return callbacks;
    }

    /// Frees the buffer the context holds now, then the context, then the stubs.
    ///
    /// Only after the demuxer that read through it is closed: a stub that FFmpeg
    /// calls after its arena is closed is a crash.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try (var scratch = Arena.ofConfined()) {
            ffmpeg.free(AvIoContextView.buffer(context));
            var holder = scratch.allocate(ADDRESS);
            holder.set(ADDRESS, 0, context);
            ffmpeg.format().ioContextFree().call(holder);
        } finally {
            arena.close();
        }
    }
}
