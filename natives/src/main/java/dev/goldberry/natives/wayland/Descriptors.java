package dev.goldberry.natives.wayland;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.goldberry.natives.Downcalls;

/// The file descriptors a Wayland client writes into: an anonymous file
/// (`memfd_create`) that becomes shared memory for a buffer, and the pipe a
/// drag target hands over to be written its data.
///
/// libc on Linux, the only platform with Wayland. A writer to a pipe whose
/// reader has gone gets `SIGPIPE`; the thread that writes blocks it first, so
/// the write fails with `EPIPE` instead of the signal reaching a handler.
public final class Descriptors {

    /// `MFD_CLOEXEC`.
    private static final int MFD_CLOEXEC = 1;

    /// `SIGPIPE`, and `SIG_BLOCK`, on Linux.
    private static final int SIGPIPE = 13;

    private static final int SIG_BLOCK = 0;

    /// glibc's and musl's `sigset_t` both fit in this.
    private static final long SIGSET_BYTES = 128;

    private static final FunctionDescriptor I_PI =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
    private static final FunctionDescriptor WRITE =
            Downcalls.describe(FunctionDescriptor.of(JAVA_LONG, JAVA_INT, ADDRESS, JAVA_LONG));
    private static final FunctionDescriptor I_I = Downcalls.describe(FunctionDescriptor.of(JAVA_INT, JAVA_INT));
    private static final FunctionDescriptor I_P = Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final FunctionDescriptor SIGMASK =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));

    private record Libc(
            MethodHandle memfdCreate,
            MethodHandle write,
            MethodHandle close,
            MethodHandle sigemptyset,
            MethodHandle sigaddset,
            MethodHandle pthreadSigmask) {

        @SuppressWarnings("restricted")
        static @Nullable Libc find() {
            var lookup = Linker.nativeLinker().defaultLookup();
            var memfd = lookup.find("memfd_create");
            var write = lookup.find("write");
            var close = lookup.find("close");
            var empty = lookup.find("sigemptyset");
            var add = lookup.find("sigaddset");
            var mask = lookup.find("pthread_sigmask");
            if (memfd.isEmpty()
                    || write.isEmpty()
                    || close.isEmpty()
                    || empty.isEmpty()
                    || add.isEmpty()
                    || mask.isEmpty()) {
                return null;
            }
            return new Libc(
                    Downcalls.link(I_PI).bindTo(memfd.get()),
                    Downcalls.link(WRITE).bindTo(write.get()),
                    Downcalls.link(I_I).bindTo(close.get()),
                    Downcalls.link(I_P).bindTo(empty.get()),
                    Downcalls.link(I_PI).bindTo(add.get()),
                    Downcalls.link(SIGMASK).bindTo(mask.get()));
        }
    }

    private static final class Holder {
        private static final @Nullable Libc LIBC = Libc.find();
    }

    private Descriptors() {}

    /// Whether this libc has everything here.
    public static boolean isAvailable() {
        return Holder.LIBC != null;
    }

    /// An anonymous file holding `bytes`, or empty where it cannot be made.
    /// The caller closes the descriptor.
    public static Optional<Integer> anonymousFile(String name, byte[] bytes) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(bytes, "bytes");
        var libc = Holder.LIBC;
        if (libc == null) {
            return Optional.empty();
        }
        try (var arena = Arena.ofConfined()) {
            var fd = (int) libc.memfdCreate().invokeExact(arena.allocateFrom(name), MFD_CLOEXEC);
            if (fd < 0) {
                return Optional.empty();
            }
            if (!writeAll(libc, fd, bytes)) {
                close(fd);
                return Optional.empty();
            }
            return Optional.of(fd);
        } catch (Throwable t) {
            throw Downcalls.failure("memfd_create", t);
        }
    }

    /// Writes all of `bytes` to `fd` and closes it, answering whether every
    /// byte went. Blocks until the reader has taken them; meant for a thread
    /// that has called [#ignoreBrokenPipes] first.
    public static boolean writeAndClose(int fd, byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        var libc = Holder.LIBC;
        if (libc == null) {
            return false;
        }
        try {
            return writeAll(libc, fd, bytes);
        } finally {
            close(fd);
        }
    }

    /// Closes `fd`, answering whether it closed.
    public static boolean close(int fd) {
        var libc = Holder.LIBC;
        if (libc == null || fd < 0) {
            return false;
        }
        try {
            return (int) libc.close().invokeExact(fd) == 0;
        } catch (Throwable t) {
            throw Downcalls.failure("close", t);
        }
    }

    /// Blocks `SIGPIPE` on the calling thread, which must be a platform
    /// thread, so that a write to a pipe nobody reads any more fails instead.
    ///
    /// @return whether it is blocked
    public static boolean ignoreBrokenPipes() {
        var libc = Holder.LIBC;
        if (libc == null) {
            return false;
        }
        try (var arena = Arena.ofConfined()) {
            var set = arena.allocate(SIGSET_BYTES, 8);
            var emptied = (int) libc.sigemptyset().invokeExact(set);
            var added = (int) libc.sigaddset().invokeExact(set, SIGPIPE);
            var masked = (int) libc.pthreadSigmask().invokeExact(SIG_BLOCK, set, MemorySegment.NULL);
            return emptied == 0 && added == 0 && masked == 0;
        } catch (Throwable t) {
            throw Downcalls.failure("pthread_sigmask", t);
        }
    }

    private static boolean writeAll(Libc libc, int fd, byte[] bytes) {
        try (var arena = Arena.ofConfined()) {
            var buffer = arena.allocate(Math.max(1, bytes.length));
            MemorySegment.copy(bytes, 0, buffer, JAVA_BYTE, 0, bytes.length);
            long written = 0;
            while (written < bytes.length) {
                var wrote = (long) libc.write().invokeExact(fd, buffer.asSlice(written), bytes.length - written);
                if (wrote <= 0) {
                    return false;
                }
                written += wrote;
            }
            return true;
        } catch (Throwable t) {
            throw Downcalls.failure("write", t);
        }
    }
}
