package dev.goldberry.media.ffi;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.channels.ClosedChannelException;
import java.util.Arrays;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.media.io.MediaIO;

/// What FFmpeg's `read_packet` and `seek` callbacks do, over one [MediaIO].
///
/// Kept apart from [AvioBridge], which turns these two methods into upcall stubs,
/// so the rules can be tested without FFmpeg. The rules:
///
/// - **Nothing is thrown into C.** An exception that escapes an upcall ends the
///   JVM. Every failure becomes an `AVERROR`. The first I/O exception is kept,
///   so the error the application sees names what the `MediaIO` said and not
///   FFmpeg's "I/O error".
/// - **End of stream is `AVERROR_EOF`**, never 0. FFmpeg 7 stopped accepting 0
///   from `read_packet` as an end, so a `MediaIO` that returns 0 is asked again.
/// - **An abort is `AVERROR_EXIT`.** [#abort()] closes the `MediaIO`, and the read
///   blocked in it ends with an asynchronous-close exception. From then on every
///   callback answers `AVERROR_EXIT`, and FFmpeg unwinds whatever it was doing.
/// - **Relative seeks are resolved here.** `SEEK_CUR` and `SEEK_END` become
///   absolute positions, so a `MediaIO` implements one kind of seek.
///   `AVSEEK_SIZE` asks for the size, and `AVSEEK_FORCE` is a hint that is
///   ignored.
final class IoCallbacks {

    /// `SEEK_SET`, `SEEK_CUR` and `SEEK_END`. These are C's, the same on every
    /// target.
    static final int SEEK_SET = 0;

    static final int SEEK_CUR = 1;
    static final int SEEK_END = 2;

    /// How many times a read that returns 0 bytes is retried before it is treated
    /// as a failure. A `MediaIO` is told to block rather than return 0, and one
    /// that keeps returning 0 would spin FFmpeg forever.
    private static final int EMPTY_READ_RETRIES = 64;

    private final MediaIO io;
    private final FfmpegConstants constants;
    private volatile boolean aborted;
    private volatile @Nullable IOException failure;
    /// The source's first bytes, as FFmpeg read them, for naming a container
    /// no demuxer recognised ([ContainerSniffer]).
    private final byte[] head = new byte[ContainerSniffer.HEAD_BYTES];
    private int headLength;

    IoCallbacks(MediaIO io, FfmpegConstants constants) {
        this.io = Objects.requireNonNull(io, "io");
        this.constants = Objects.requireNonNull(constants, "constants");
    }

    /// `int read_packet(void *opaque, uint8_t *buf, int buf_size)`.
    ///
    /// @param opaque unused. The stub is bound to this object, so there is no
    ///               context pointer to dispatch on (ADR-0017)
    /// @param buffer where to read to. Zero-length as FFmpeg hands it over, and
    ///               sized here
    /// @param size   how many bytes FFmpeg can take
    /// @return bytes read, `AVERROR_EOF`, `AVERROR_EXIT` or `AVERROR(EIO)`
    @SuppressWarnings({"restricted", "unused"})
    int read(MemorySegment opaque, MemorySegment buffer, int size) {
        if (aborted) {
            return constants.averrorExit();
        }
        try {
            var segment = buffer.reinterpret(size);
            var target = segment.asByteBuffer();
            var at = io.position();
            for (var attempt = 0; attempt < EMPTY_READ_RETRIES; attempt++) {
                var read = io.read(target);
                if (read > 0) {
                    keepHead(at, segment, read);
                    return read;
                }
                if (read < 0) {
                    return constants.averrorEof();
                }
            }
            return fail(new IOException(io + " returned 0 bytes " + EMPTY_READ_RETRIES + " times in a row"));
        } catch (ClosedChannelException e) {
            // Closed under us (AsynchronousCloseException is one of these): the
            // abort path, or a close that raced one.
            aborted = true;
            return constants.averrorExit();
        } catch (IOException e) {
            return aborted ? constants.averrorExit() : fail(e);
        } catch (RuntimeException | Error e) {
            // Anything at all rather than crash the JVM from inside C.
            return fail(new IOException(e));
        }
    }

    /// `int64_t seek(void *opaque, int64_t offset, int whence)`.
    ///
    /// @return the new position; the size for `AVSEEK_SIZE`; or a negative `AVERROR`
    @SuppressWarnings("unused")
    long seek(MemorySegment opaque, long offset, int whence) {
        if (aborted) {
            return constants.averrorExit();
        }
        try {
            if ((whence & constants.avseekSize()) != 0) {
                var size = io.size();
                // A negative answer tells FFmpeg the size is unknown, and it carries on.
                return size.isPresent() ? size.getAsLong() : constants.averrorEio();
            }
            var mode = whence & ~constants.avseekForce();
            var target =
                    switch (mode) {
                        case SEEK_SET -> offset;
                        case SEEK_CUR -> io.position() + offset;
                        case SEEK_END -> {
                            var size = io.size();
                            if (size.isEmpty()) {
                                yield -1L;
                            }
                            yield size.getAsLong() + offset;
                        }
                        default -> -1L;
                    };
            if (target < 0) {
                return constants.averrorEio();
            }
            io.seek(target);
            return target;
        } catch (ClosedChannelException e) {
            aborted = true;
            return constants.averrorExit();
        } catch (IOException e) {
            return aborted ? constants.averrorExit() : fail(e);
        } catch (RuntimeException | Error e) {
            return fail(new IOException(e));
        }
    }

    /// Keeps what a read from `at` brought in, when it continues the source's first
    /// bytes. Reads elsewhere (a probe that seeks to the end) are not the head.
    private void keepHead(long at, MemorySegment segment, int read) {
        if (headLength < head.length && at == headLength) {
            var count = Math.min(read, head.length - headLength);
            MemorySegment.copy(segment, JAVA_BYTE, 0, head, headLength, count);
            headLength += count;
        }
    }

    /// The source's first bytes as far as FFmpeg read them, up to
    /// [ContainerSniffer#HEAD_BYTES].
    byte[] head() {
        return Arrays.copyOf(head, headLength);
    }

    /// Makes every pending and future callback answer `AVERROR_EXIT`, and closes
    /// the `MediaIO` so that a read blocked in it ends. Safe from any thread.
    void abort() {
        aborted = true;
        try {
            io.close();
        } catch (IOException e) {
            // Closing to abort: the stream is being abandoned either way.
            if (failure == null) {
                failure = e;
            }
        }
    }

    /// Whether [#abort()] was called, or the stream was closed under a read.
    boolean aborted() {
        return aborted;
    }

    /// The first I/O failure a callback turned into an `AVERROR`, if any.
    @Nullable
    IOException failure() {
        return failure;
    }

    private int fail(IOException e) {
        if (failure == null) {
            failure = e;
        }
        return constants.averrorEio();
    }
}
