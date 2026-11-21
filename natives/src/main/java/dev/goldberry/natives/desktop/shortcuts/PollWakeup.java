package dev.goldberry.natives.desktop.shortcuts;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.goldberry.natives.Downcalls;

/// Waiting on a connection's file descriptor until it is readable or another
/// thread wakes the wait, through libc's `poll` and a pipe.
///
/// Xlib and libdbus each give their connection's descriptor, so a worker can
/// sleep until something arrives rather than wake on a timer to look. The
/// pipe is the other half: [#wake] writes one byte to it, which makes the
/// `poll` return, and the wait reads it back out.
///
/// Linux and the BSDs. The pipe is non-blocking at both ends, so a wake when
/// the pipe is full (64 KiB of wakes nobody read) drops the byte rather than
/// blocking the thread that woke it.
final class PollWakeup implements AutoCloseable {

    /// `int pipe2(int fds[2], int flags)`.
    private static final FunctionDescriptor PIPE2 =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    /// `int poll(struct pollfd *, nfds_t, int timeout)`.
    private static final FunctionDescriptor POLL =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, JAVA_INT));

    /// `ssize_t read(int, void *, size_t)` and `write`, which share it.
    private static final FunctionDescriptor READ_WRITE =
            Downcalls.describe(FunctionDescriptor.of(JAVA_LONG, JAVA_INT, ADDRESS, JAVA_LONG));

    /// `int close(int)`.
    private static final FunctionDescriptor CLOSE = Downcalls.describe(FunctionDescriptor.of(JAVA_INT, JAVA_INT));

    /// `POLLIN`.
    private static final short POLL_IN = 1;

    /// `struct pollfd`: an `int` and two `short`s.
    static final long POLL_FD_BYTES = 8;

    private record Libc(
            MethodHandle pipe2, MethodHandle poll, MethodHandle read, MethodHandle write, MethodHandle close) {

        @SuppressWarnings("restricted")
        static @Nullable Libc find() {
            var lookup = Linker.nativeLinker().defaultLookup();
            var pipe2 = lookup.find("pipe2");
            var poll = lookup.find("poll");
            var read = lookup.find("read");
            var write = lookup.find("write");
            var close = lookup.find("close");
            if (pipe2.isEmpty() || poll.isEmpty() || read.isEmpty() || write.isEmpty() || close.isEmpty()) {
                return null;
            }
            return new Libc(
                    Downcalls.link(PIPE2).bindTo(pipe2.get()),
                    Downcalls.link(POLL).bindTo(poll.get()),
                    Downcalls.link(READ_WRITE).bindTo(read.get()),
                    Downcalls.link(READ_WRITE).bindTo(write.get()),
                    Downcalls.link(CLOSE).bindTo(close.get()));
        }
    }

    private static final class Holder {
        private static final @Nullable Libc LIBC = Libc.find();
    }

    private final Libc libc;
    private final int readEnd;
    private final int writeEnd;
    private final Arena arena = Arena.ofShared();
    private final MemorySegment fds;
    private final MemorySegment wakeByte;
    private final MemorySegment drain;
    private boolean closed;

    private PollWakeup(Libc libc, int readEnd, int writeEnd) {
        this.libc = libc;
        this.readEnd = readEnd;
        this.writeEnd = writeEnd;
        this.fds = arena.allocate(POLL_FD_BYTES * 2, 4);
        this.wakeByte = arena.allocate(1);
        this.drain = arena.allocate(64);
    }

    /// A pipe to wake a wait with, or empty where libc has no `pipe2`.
    static Optional<PollWakeup> open() {
        var libc = Holder.LIBC;
        if (libc == null) {
            return Optional.empty();
        }
        try (var scratch = Arena.ofConfined()) {
            var pair = scratch.allocate(JAVA_INT, 2);
            if ((int) call(libc.pipe2(), pair, nonBlockingFlags()) != 0) {
                return Optional.empty();
            }
            return Optional.of(new PollWakeup(libc, pair.getAtIndex(JAVA_INT, 0), pair.getAtIndex(JAVA_INT, 1)));
        }
    }

    /// `O_NONBLOCK | O_CLOEXEC`, which differ between Linux and the BSDs.
    static int nonBlockingFlags() {
        var os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("linux") ? 0x800 | 0x80000 : 0x4 | 0x100000;
    }

    /// Waits until `fd` is readable, the pipe is woken, or `timeoutMillis`
    /// passes (-1 for no limit).
    ///
    /// @return whether `fd` is readable
    boolean await(int fd, int timeoutMillis) {
        fds.set(JAVA_INT, 0, fd);
        fds.set(JAVA_SHORT, 4, POLL_IN);
        fds.set(JAVA_SHORT, 6, (short) 0);
        fds.set(JAVA_INT, POLL_FD_BYTES, readEnd);
        fds.set(JAVA_SHORT, POLL_FD_BYTES + 4, POLL_IN);
        fds.set(JAVA_SHORT, POLL_FD_BYTES + 6, (short) 0);
        var ready = (int) call(libc.poll(), fds, 2L, timeoutMillis);
        if (ready <= 0) {
            return false;
        }
        if ((fds.get(JAVA_SHORT, POLL_FD_BYTES + 6) & POLL_IN) != 0) {
            // Everything the pipe holds: several wakes are one.
            while ((long) call(libc.read(), readEnd, drain, drain.byteSize()) > 0) {
                // Read until it would block.
            }
        }
        return (fds.get(JAVA_SHORT, 6) & POLL_IN) != 0;
    }

    /// Makes a wait return. Safe from any thread.
    synchronized void wake() {
        if (!closed) {
            call(libc.write(), writeEnd, wakeByte, 1L);
        }
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            call(libc.close(), readEnd);
            call(libc.close(), writeEnd);
            arena.close();
        }
    }

    private static Object call(MethodHandle handle, Object... arguments) {
        try {
            var result = handle.invokeWithArguments(arguments);
            return result == null ? Boolean.TRUE : result;
        } catch (Throwable e) {
            throw new IllegalStateException("a libc call failed", e);
        }
    }
}
