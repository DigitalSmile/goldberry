package dev.goldberry.instance;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channel;
import java.nio.channels.Channels;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;

/// One running copy of an application: the first launch holds a lock, and a
/// second launch hands its arguments to the first and exits.
///
/// ```java
/// var instance = SingleInstance.acquire("dev.example.chat", List.of(args));
/// if (instance.isEmpty()) {
///     return; // the running copy has the arguments now
/// }
/// instance.get().onArguments(ui, arguments -> openLinks(arguments));
/// ```
///
/// Plain Java: a [FileLock] on a file in a directory of the application's
/// own, and a Unix-domain socket beside it, which every desktop Java supports,
/// Windows 10 and later included. Nothing native and nothing to install.
///
/// What arrives is the second launch's command line, whole: a URL the
/// system passed (`tessera://invite/…`), file paths, flags. What they mean is
/// the application's. An application launched through [dev.goldberry.Goldberry]
/// that names an [dev.goldberry.Application#singleInstance()] id gets this done
/// for it, and hears each argument that is a URL or a file through
/// [dev.goldberry.Application#onOpenUrl].
///
/// Read more: [Windows, popups and the host](https://goldberry.dev/docs/guide/windows.html#the-low-level-path).
public final class SingleInstance implements AutoCloseable {

    private static final Logger LOG = Logs.of(SingleInstance.class);

    /// The most arguments one hand-over carries, and the longest one: a guard
    /// against a stray client, not a limit anybody meets.
    private static final int MAX_ARGUMENTS = 1024;

    private static final int MAX_ARGUMENT_BYTES = 1 << 20;

    /// The longest socket path, in bytes, kept beside the lock: under every
    /// platform's limit for a Unix-domain address (104 bytes on macOS, 108 on
    /// Linux and Windows), terminator included.
    static final int MAX_SOCKET_PATH_BYTES = 100;

    private static final Set<PosixFilePermission> OWNER_ONLY = PosixFilePermissions.fromString("rwx------");

    private final Path directory;
    private final FileChannel lockFile;
    private final FileLock lock;
    private final ServerSocketChannel server;
    private final Path socket;
    private volatile @Nullable Delivery delivery;
    private final List<List<String>> early = new ArrayList<>();
    private boolean closed;

    private record Delivery(Executor executor, Consumer<List<String>> listener) {}

    private SingleInstance(
            Path directory, FileChannel lockFile, FileLock lock, ServerSocketChannel server, Path socket) {
        this.directory = directory;
        this.lockFile = lockFile;
        this.lock = lock;
        this.server = server;
        this.socket = socket;
        Thread.ofVirtual().name("goldberry-single-instance").start(this::accept);
    }

    /// Claims the application `id`, in [#directoryFor(String)]'s directory.
    ///
    /// @param id        the application, as a reverse domain name or any name
    ///                  of letters, digits, dots, dashes and underscores
    /// @param arguments what to hand the running copy when there is one
    /// @return this process's claim when it is the first, or empty when
    ///         another copy holds it and has been handed `arguments`
    /// @throws UncheckedIOException when the directory cannot be made, or the
    ///                              running copy cannot be reached
    public static Optional<SingleInstance> acquire(String id, List<String> arguments) {
        return acquire(directoryFor(id), arguments, true);
    }

    /// Claims `directory` as the application's, for a caller that keeps its
    /// own: a test's, or an application's data directory.
    ///
    /// @throws UncheckedIOException when the directory cannot be made, or the
    ///                              running copy cannot be reached
    public static Optional<SingleInstance> acquire(Path directory, List<String> arguments) {
        return acquire(directory, arguments, false);
    }

    private static Optional<SingleInstance> acquire(Path directory, List<String> arguments, boolean privateDirectory) {
        Objects.requireNonNull(directory, "directory");
        var handed = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        try {
            if (privateDirectory && isPosix(directory)) {
                createPosixPrivateDirectory(directory);
            } else {
                Files.createDirectories(directory);
            }
            var socket = socketFor(directory);
            var socketParent = socket.getParent();
            if (socketParent != null && !socketParent.equals(directory) && isPosix(socketParent)) {
                createPosixPrivateDirectory(socketParent);
            }
            return claim(directory, socket, handed);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot claim " + directory + " for one running copy", e);
        }
    }

    private static Optional<SingleInstance> claim(Path directory, Path socket, List<String> arguments)
            throws IOException {
        var lockFile = FileChannel.open(
                directory.resolve("instance.lock"),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS);
        try {
            var lock = tryLock(lockFile);
            if (lock == null) {
                lockFile.close();
                handOver(socket, arguments);
                return Optional.empty();
            }
            // The lock is held, so a socket file left here belongs to a copy
            // that died without cleaning up.
            Files.deleteIfExists(socket);
            var server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            try {
                server.bind(UnixDomainSocketAddress.of(socket));
                return Optional.of(new SingleInstance(directory, lockFile, lock, server, socket));
            } catch (IOException | RuntimeException e) {
                closeFailedClaim(server, e);
                throw e;
            }
        } catch (IOException | RuntimeException e) {
            closeFailedClaim(lockFile, e);
            throw e;
        }
    }

    private static void closeFailedClaim(Channel channel, Throwable failure) {
        try {
            channel.close();
        } catch (IOException e) {
            failure.addSuppressed(e);
        }
    }

    /// The lock, or null when another process holds it, or this one does
    /// through another channel: the JVM refuses a second lock of its own rather
    /// than reporting it held.
    private static @Nullable FileLock tryLock(FileChannel channel) throws IOException {
        try {
            return channel.tryLock();
        } catch (OverlappingFileLockException e) {
            return null;
        }
    }

    /// The socket for the claim on `directory`: beside the lock, or, where
    /// that path is longer than a Unix-domain address may be (a long user name
    /// under Windows' temporary directory, a deep data directory), a short
    /// name in a user-specific temporary directory that only `directory` gives. Both
    /// launches work it out alike, so the second finds the first.
    static Path socketFor(Path directory) {
        var beside = directory.resolve("instance.sock");
        if (fitsSocketAddress(beside)) {
            return beside;
        }
        var name = directory.toAbsolutePath().normalize().toString();
        var temp = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath();
        var file = "goldberry-" + digest(name) + ".sock";
        if (!isPosix(temp)) {
            // Windows already places the temporary directory in the user's profile.
            return temp.resolve(file);
        }
        var userDirectory = "gb-" + digest(System.getProperty("user.home"));
        var preferred = temp.resolve(userDirectory).resolve(file);
        return fitsSocketAddress(preferred) ? preferred : Path.of("/tmp", userDirectory, file);
    }

    private static boolean fitsSocketAddress(Path path) {
        return path.toString().getBytes(StandardCharsets.UTF_8).length <= MAX_SOCKET_PATH_BYTES;
    }

    private static boolean isPosix(Path path) {
        return path.getFileSystem().supportedFileAttributeViews().contains("posix");
    }

    private static void createPosixPrivateDirectory(Path directory) throws IOException {
        Files.createDirectories(directory, PosixFilePermissions.asFileAttribute(OWNER_ONLY));
        var attributes = Files.readAttributes(directory, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        var user = directory
                .getFileSystem()
                .getUserPrincipalLookupService()
                .lookupPrincipalByName(System.getProperty("user.name"));
        if (!attributes.isDirectory() || !attributes.owner().equals(user)) {
            throw new IOException("not a directory owned by the current user: " + directory);
        }
        if (!attributes.permissions().equals(OWNER_ONLY)) {
            Files.setPosixFilePermissions(directory, OWNER_ONLY);
        }
    }

    /// The first 64 bits of `text`'s SHA-256, in hex.
    private static String digest(String text) {
        try {
            var hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every Java has SHA-256", e);
        }
    }

    /// Where an application's lock and socket live: under `XDG_RUNTIME_DIR`
    /// where the desktop has one, which is per user and cleared at log-out, and
    /// under the user's home directory otherwise.
    ///
    /// @throws IllegalArgumentException if `id` is blank or has a character a
    ///                                  file name may not
    public static Path directoryFor(String id) {
        Objects.requireNonNull(id, "id");
        if (!id.matches("[A-Za-z0-9._-]+") || id.equals(".") || id.equals("..")) {
            throw new IllegalArgumentException(
                    "an application id is letters, digits, dots, dashes and underscores, not \"" + id + "\"");
        }
        var runtime = System.getenv("XDG_RUNTIME_DIR");
        if (runtime != null && !runtime.isBlank()) {
            return Path.of(runtime, "goldberry", id);
        }
        return Path.of(System.getProperty("user.home"), ".goldberry", "instances", id);
    }

    /// Hands every later launch's arguments to `listener` on `executor`: the
    /// UI thread's, for an application that acts on them in its window. Ones
    /// that arrived before this was called are handed over now, in order.
    public void onArguments(Executor executor, Consumer<List<String>> listener) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(listener, "listener");
        List<List<String>> waiting;
        synchronized (early) {
            delivery = new Delivery(executor, listener);
            waiting = List.copyOf(early);
            early.clear();
        }
        for (var arguments : waiting) {
            executor.execute(() -> listener.accept(arguments));
        }
    }

    /// The directory the claim is in.
    public Path directory() {
        return directory;
    }

    /// Stops listening and lets the next launch be the first. Idempotent.
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            server.close();
            Files.deleteIfExists(socket);
            lock.release();
            lockFile.close();
        } catch (IOException e) {
            LOG.warn("letting go of the single-instance claim in {} failed", directory, e);
        }
    }

    /// Writes `arguments` to the running copy's socket.
    private static void handOver(Path socket, List<String> arguments) throws IOException {
        try (var channel = SocketChannel.open(UnixDomainSocketAddress.of(socket));
                var out = new DataOutputStream(Channels.newOutputStream(channel))) {
            out.writeInt(arguments.size());
            for (var argument : arguments) {
                var bytes = argument.getBytes(StandardCharsets.UTF_8);
                out.writeInt(bytes.length);
                out.write(bytes);
            }
        }
    }

    /// Takes hand-overs until closed, on its own virtual thread.
    private void accept() {
        while (true) {
            try (var client = server.accept();
                    var in = new DataInputStream(Channels.newInputStream(client))) {
                deliver(read(in));
            } catch (ClosedChannelException e) {
                return;
            } catch (IOException | RuntimeException e) {
                if (!server.isOpen()) {
                    return;
                }
                LOG.warn("a second launch's arguments could not be read", e);
            }
        }
    }

    private static List<String> read(DataInputStream in) throws IOException {
        var count = in.readInt();
        if (count < 0 || count > MAX_ARGUMENTS) {
            throw new IOException("a hand-over of " + count + " arguments");
        }
        var arguments = new ArrayList<String>(count);
        for (var i = 0; i < count; i++) {
            var length = in.readInt();
            if (length < 0 || length > MAX_ARGUMENT_BYTES) {
                throw new IOException("an argument of " + length + " bytes");
            }
            arguments.add(new String(in.readNBytes(length), StandardCharsets.UTF_8));
        }
        return List.copyOf(arguments);
    }

    private void deliver(List<String> arguments) {
        Delivery current;
        synchronized (early) {
            current = delivery;
            if (current == null) {
                early.add(arguments);
                return;
            }
        }
        current.executor().execute(() -> current.listener().accept(arguments));
    }
}
