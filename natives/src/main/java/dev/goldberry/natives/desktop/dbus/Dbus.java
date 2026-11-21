package dev.goldberry.natives.desktop.dbus;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import java.io.ByteArrayOutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.natives.Downcalls;

/// A private D-Bus connection, through libdbus loaded at run time: method
/// calls with their replies or errors, signals both ways, and the serving side
/// a test's fake service needs.
///
/// The settings portal reads one value and needs four calls; a notification
/// is a call with arrays and a dictionary in it; a secret is a struct of an
/// object path and byte arrays, and a locked keyring answers with a prompt
/// whose result is a signal. So this is the iterator API in full, behind a
/// [Writer] and a reader that turns a message into plain Java values (see
/// [#values]), rather than one more handle per call.
///
/// ## A private connection
///
/// `dbus_bus_get_private`, not the shared `dbus_bus_get` the portal query
/// uses, and for two reasons. Signals are read here by popping the
/// connection's queue, and on the shared connection that would take messages
/// another part of the process was waiting for. And a private connection can
/// be told not to `_exit()` the process when the bus goes away, which is
/// libdbus' default for every connection it opens to a bus.
///
/// Confined to one thread at a time. libdbus is thread-safe; this class's
/// buffers are not.
///
/// Its package is not exported: the notifier and the keychain are, and they
/// put their own words on what crosses here.
public final class Dbus implements AutoCloseable {

    /// `DBUS_BUS_SESSION`.
    private static final int SESSION_BUS = 0;

    /// D-Bus type codes, which are the ASCII letters the specification names
    /// them by.
    public static final int BYTE = 'y';

    public static final int BOOLEAN = 'b';
    public static final int INT16 = 'n';
    public static final int UINT16 = 'q';
    public static final int INT32 = 'i';
    public static final int UINT32 = 'u';
    public static final int INT64 = 'x';
    public static final int UINT64 = 't';
    public static final int DOUBLE = 'd';
    public static final int STRING = 's';
    public static final int OBJECT_PATH = 'o';
    public static final int SIGNATURE = 'g';
    public static final int ARRAY = 'a';
    public static final int VARIANT = 'v';
    public static final int STRUCT = 'r';
    public static final int DICT_ENTRY = 'e';
    public static final int INVALID = 0;

    /// `DBUS_MESSAGE_TYPE_*`.
    private static final int METHOD_CALL = 1;

    private static final int SIGNAL = 4;

    /// `DBUS_NAME_FLAG_DO_NOT_QUEUE`, and the answer that says the name is ours.
    private static final int DO_NOT_QUEUE = 4;

    private static final int PRIMARY_OWNER = 1;

    /// Room for a `DBusMessageIter` and a `DBusError`. Both are private to
    /// libdbus; these are sizes nobody will grow past rather than ones read off a
    /// header this build does not have. A `DBusError` starts with its name and
    /// its message, two `const char *`, which is all that is read of it.
    private static final long ITERATOR_BYTES = 256;

    private static final long ERROR_BYTES = 64;

    // The shapes, as constants: recorded for a native image wherever it is
    // built, whether or not that machine has libdbus. See Downcalls.describe.
    private static final FunctionDescriptor P_P = Downcalls.describe(FunctionDescriptor.of(ADDRESS, ADDRESS));
    private static final FunctionDescriptor P_IP =
            Downcalls.describe(FunctionDescriptor.of(ADDRESS, JAVA_INT, ADDRESS));
    private static final FunctionDescriptor P_PP = Downcalls.describe(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
    private static final FunctionDescriptor P_PPP =
            Downcalls.describe(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    private static final FunctionDescriptor P_PPPP =
            Downcalls.describe(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    private static final FunctionDescriptor P_PPIP =
            Downcalls.describe(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));
    private static final FunctionDescriptor V_P = Downcalls.describe(FunctionDescriptor.ofVoid(ADDRESS));
    private static final FunctionDescriptor V_PP = Downcalls.describe(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
    private static final FunctionDescriptor V_PI = Downcalls.describe(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));
    private static final FunctionDescriptor V_PPP =
            Downcalls.describe(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS));
    private static final FunctionDescriptor I_P = Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final FunctionDescriptor I_PI =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
    private static final FunctionDescriptor I_PP =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    private static final FunctionDescriptor I_PPP =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
    private static final FunctionDescriptor I_PIP =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
    private static final FunctionDescriptor I_PIPP =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));
    private static final FunctionDescriptor I_PPIP =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));

    /// The bound functions, or empty where there is no libdbus.
    private static final class Holder {
        private static final @Nullable Library LIBRARY = Library.find();
    }

    /// Whether this process can speak D-Bus at all.
    public static boolean isAvailable() {
        return Holder.LIBRARY != null;
    }

    private final Library lib;
    private final MemorySegment connection;
    private boolean closed;

    private Dbus(Library lib, MemorySegment connection) {
        this.lib = lib;
        this.connection = connection;
    }

    /// A private connection to the session bus, or empty where there is no
    /// libdbus or no bus.
    public static Optional<Dbus> session() {
        var lib = Holder.LIBRARY;
        if (lib == null) {
            return Optional.empty();
        }
        try (var arena = Arena.ofConfined()) {
            var error = lib.error(arena);
            var connection = (MemorySegment) lib.call(lib.busGetPrivate, SESSION_BUS, error);
            lib.errorFree(error);
            return opened(lib, connection);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /// A private connection to the bus at `address`, registered on it — for a
    /// test with a bus of its own.
    public static Optional<Dbus> at(String address) {
        var lib = Holder.LIBRARY;
        if (lib == null) {
            return Optional.empty();
        }
        try (var arena = Arena.ofConfined()) {
            var error = lib.error(arena);
            var connection = (MemorySegment) lib.call(lib.connectionOpenPrivate, arena.allocateFrom(address), error);
            if (connection.address() == 0) {
                lib.errorFree(error);
                return Optional.empty();
            }
            if ((int) lib.call(lib.busRegister, connection, error) == 0) {
                lib.errorFree(error);
                lib.call(lib.connectionClose, connection);
                lib.call(lib.connectionUnref, connection);
                return Optional.empty();
            }
            lib.errorFree(error);
            return opened(lib, connection);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static Optional<Dbus> opened(Library lib, MemorySegment connection) {
        if (connection.address() == 0) {
            return Optional.empty();
        }
        // Never `_exit()` the process because the session bus went away, which
        // is what libdbus does by default for a bus connection.
        lib.call(lib.setExitOnDisconnect, connection, 0);
        return Optional.of(new Dbus(lib, connection));
    }

    /// What a method call came back with.
    public sealed interface Reply {

        /// A method return, with its arguments read by [Dbus#values].
        record Returned(List<Object> values) implements Reply {
            public Returned {
                values = List.copyOf(values);
            }
        }

        /// An error reply: `org.freedesktop.DBus.Error.ServiceUnknown` for
        /// nobody owning the name, the service's own name otherwise.
        record Failed(String name, String message) implements Reply {}

        /// Nothing came back in time, or the call could not be sent.
        record Unanswered() implements Reply {}
    }

    /// Calls `member` and waits up to `timeout` for its reply.
    public Reply invoke(
            String destination,
            String path,
            String iface,
            String member,
            Consumer<Writer> arguments,
            Duration timeout) {
        try (var arena = Arena.ofConfined()) {
            var message = (MemorySegment) lib.call(
                    lib.newMethodCall,
                    arena.allocateFrom(destination),
                    arena.allocateFrom(path),
                    arena.allocateFrom(iface),
                    arena.allocateFrom(member));
            if (message.address() == 0) {
                return new Reply.Unanswered();
            }
            try {
                arguments.accept(Writer.appending(lib, arena, message));
                var error = lib.error(arena);
                var answer = (MemorySegment)
                        lib.call(lib.sendWithReplyAndBlock, connection, message, millis(timeout), error);
                if (answer.address() == 0) {
                    var name = Library.string(error.get(ADDRESS, 0));
                    var text = Library.string(error.get(ADDRESS, ADDRESS.byteSize()));
                    lib.errorFree(error);
                    return name == null
                                    || name.equals("org.freedesktop.DBus.Error.NoReply")
                                    || name.equals("org.freedesktop.DBus.Error.Timeout")
                            ? new Reply.Unanswered()
                            : new Reply.Failed(name, text == null ? "" : text);
                }
                lib.errorFree(error);
                try {
                    return new Reply.Returned(values(lib, arena, answer));
                } finally {
                    lib.call(lib.messageUnref, answer);
                }
            } finally {
                lib.call(lib.messageUnref, message);
            }
        }
    }

    /// Calls `member` and waits up to `timeoutMillis` for the reply, which
    /// `reply` reads as a list of values. False when the call failed or timed
    /// out.
    public boolean call(
            String destination,
            String path,
            String iface,
            String member,
            Consumer<Writer> arguments,
            int timeoutMillis,
            Consumer<List<Object>> reply) {
        var answer = invoke(destination, path, iface, member, arguments, Duration.ofMillis(timeoutMillis));
        if (answer instanceof Reply.Returned(var values)) {
            reply.accept(values);
            return true;
        }
        return false;
    }

    /// Emits a signal. False when it could not be queued.
    public boolean signal(String path, String iface, String member, Consumer<Writer> arguments) {
        try (var arena = Arena.ofConfined()) {
            var message = (MemorySegment) lib.call(
                    lib.newSignal, arena.allocateFrom(path), arena.allocateFrom(iface), arena.allocateFrom(member));
            if (message.address() == 0) {
                return false;
            }
            try {
                arguments.accept(Writer.appending(lib, arena, message));
                return send(message);
            } finally {
                lib.call(lib.messageUnref, message);
            }
        }
    }

    /// Asks the bus to route signals matching `rule` to this connection.
    public void match(String rule) {
        try (var arena = Arena.ofConfined()) {
            var error = lib.error(arena);
            lib.call(lib.addMatch, connection, arena.allocateFrom(rule), error);
            lib.errorFree(error);
        }
    }

    /// Takes `name` on the bus, now or not at all. True when this connection
    /// owns it.
    public boolean requestName(String name) {
        try (var arena = Arena.ofConfined()) {
            var error = lib.error(arena);
            var answer = (int) lib.call(lib.requestName, connection, arena.allocateFrom(name), DO_NOT_QUEUE, error);
            lib.errorFree(error);
            return answer == PRIMARY_OWNER;
        }
    }

    /// One signal that arrived: who sent it as, and its arguments.
    public record Received(String path, String iface, String member, List<Object> arguments) {
        public Received {
            arguments = List.copyOf(arguments);
        }
    }

    /// Reads whatever has arrived, without waiting, and answers the signals of
    /// `iface`. Everything else is dropped.
    public List<Received> drain(String iface) {
        var found = new ArrayList<Received>();
        receive(0, incoming -> {
            if (!incoming.isCall() && iface.equals(incoming.iface())) {
                found.add(incoming.received());
            }
        });
        return found;
    }

    /// Waits up to `timeout` for `member` of `iface` emitted at `path`, and
    /// answers its arguments. Whatever else arrives meanwhile is dropped, so
    /// this is for a connection kept for one conversation, with a match rule
    /// added before the call that causes the signal.
    public Optional<List<Object>> awaitSignal(String iface, String member, String path, Duration timeout) {
        var deadline = System.nanoTime() + timeout.toNanos();
        var found = new ArrayList<List<Object>>(1);
        while (found.isEmpty()) {
            var left = Duration.ofNanos(deadline - System.nanoTime());
            if (left.isNegative()) {
                return Optional.empty();
            }
            receive(Math.max(1, millis(left)), incoming -> {
                if (found.isEmpty()
                        && !incoming.isCall()
                        && iface.equals(incoming.iface())
                        && member.equals(incoming.member())
                        && path.equals(incoming.path())) {
                    found.add(incoming.arguments());
                }
            });
        }
        return Optional.of(found.getFirst());
    }

    /// Waits up to `timeoutMillis` for something to arrive, then hands every
    /// message queued to `handler` — calls to be answered, and signals. A call
    /// the handler neither answers nor fails is left for its caller to time
    /// out on.
    public void receive(int timeoutMillis, Consumer<Incoming> handler) {
        lib.call(lib.readWrite, connection, timeoutMillis);
        while (true) {
            var message = (MemorySegment) lib.call(lib.popMessage, connection);
            if (message.address() == 0) {
                return;
            }
            try (var arena = Arena.ofConfined()) {
                var type = (int) lib.call(lib.getType, message);
                if (type == METHOD_CALL || type == SIGNAL) {
                    handler.accept(new Incoming(this, arena, message, type == METHOD_CALL));
                }
            } finally {
                lib.call(lib.messageUnref, message);
            }
        }
    }

    /// A call or a signal being read in [Dbus#receive]. Valid only inside the
    /// handler it was given to.
    public static final class Incoming {

        private final Dbus bus;
        private final Arena arena;
        private final MemorySegment message;
        private final boolean call;
        private final String path;
        private final String iface;
        private final String member;
        private final List<Object> arguments;

        private Incoming(Dbus bus, Arena arena, MemorySegment message, boolean call) {
            this.bus = bus;
            this.arena = arena;
            this.message = message;
            this.call = call;
            var lib = bus.lib;
            this.path = Objects.requireNonNullElse(Library.string((MemorySegment) lib.call(lib.getPath, message)), "");
            this.iface =
                    Objects.requireNonNullElse(Library.string((MemorySegment) lib.call(lib.getInterface, message)), "");
            this.member =
                    Objects.requireNonNullElse(Library.string((MemorySegment) lib.call(lib.getMember, message)), "");
            this.arguments = values(lib, arena, message);
        }

        /// A method call, which wants an answer, rather than a signal.
        public boolean isCall() {
            return call;
        }

        public String path() {
            return path;
        }

        public String iface() {
            return iface;
        }

        public String member() {
            return member;
        }

        public List<Object> arguments() {
            return arguments;
        }

        Received received() {
            return new Received(path, iface, member, arguments);
        }

        /// Answers the call with what `values` writes.
        public void reply(Consumer<Writer> values) {
            var lib = bus.lib;
            var reply = (MemorySegment) lib.call(lib.newMethodReturn, message);
            try {
                values.accept(Writer.appending(lib, arena, reply));
                bus.send(reply);
            } finally {
                lib.call(lib.messageUnref, reply);
            }
        }

        /// Answers the call with the error `name`.
        public void fail(String name, String text) {
            var lib = bus.lib;
            var reply =
                    (MemorySegment) lib.call(lib.newError, message, arena.allocateFrom(name), arena.allocateFrom(text));
            try {
                bus.send(reply);
            } finally {
                lib.call(lib.messageUnref, reply);
            }
        }
    }

    /// The socket under this connection, for a thread that sleeps in `poll`
    /// until something arrives rather than in [#receive] with a timeout. Read
    /// whatever libdbus has already queued with `receive(0, …)` before each
    /// wait: a message read in along with a reply is in that queue, not on the
    /// socket.
    public OptionalInt unixFd() {
        try (var arena = Arena.ofConfined()) {
            var fd = arena.allocate(JAVA_INT);
            return (int) lib.call(lib.getUnixFd, connection, fd) == 0
                    ? OptionalInt.empty()
                    : OptionalInt.of(fd.get(JAVA_INT, 0));
        }
    }

    private boolean send(MemorySegment message) {
        var sent = (int) lib.call(lib.send, connection, message, MemorySegment.NULL) != 0;
        lib.call(lib.flush, connection);
        return sent;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            lib.call(lib.connectionClose, connection);
            lib.call(lib.connectionUnref, connection);
        }
    }

    private static int millis(Duration duration) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, duration.toMillis()));
    }

    // ---- values

    /// An object path, which is a string the bus checks the shape of.
    public record ObjectPath(String value) {

        /// The path the Secret Service and others answer for "none".
        public static final ObjectPath NONE = new ObjectPath("/");

        public ObjectPath {
            Objects.requireNonNull(value, "value");
        }

        public boolean isNone() {
            return value.equals("/");
        }
    }

    /// A struct's fields, in order.
    public record Struct(List<Object> fields) {
        public Struct {
            fields = List.copyOf(fields);
        }
    }

    /// One entry of a dictionary, which D-Bus writes as an array of these.
    public record Entry(Object key, Object value) {}

    /// A variant's value, read through.
    public record Variant(Object value) {}

    /// The arguments of `message`, in order, as plain values: a [String] for
    /// `s` and `g`, an [ObjectPath] for `o`, an [Integer] for the 32-bit and
    /// narrower integers but bytes, a [Byte] for `y`, a [Long] for the 64-bit
    /// ones, a [Boolean], a [Double], a `byte[]` for an array of bytes, a
    /// [List] for any other array (of [Entry] for a dictionary), a [Struct] and
    /// a [Variant]. A Unix file descriptor is read as nothing.
    private static List<Object> values(Library lib, Arena arena, MemorySegment message) {
        var iterator = lib.iterator(arena);
        if ((int) lib.call(lib.iterInit, message, iterator) == 0) {
            return List.of();
        }
        return readAll(lib, arena, iterator);
    }

    private static List<Object> readAll(Library lib, Arena arena, MemorySegment iterator) {
        var values = new ArrayList<Object>();
        do {
            var type = (int) lib.call(lib.iterGetArgType, iterator);
            if (type == INVALID) {
                break;
            }
            var value = read(lib, arena, iterator, type);
            if (value != null) {
                values.add(value);
            }
        } while ((int) lib.call(lib.iterNext, iterator) != 0);
        return values;
    }

    private static @Nullable Object read(Library lib, Arena arena, MemorySegment iterator, int type) {
        return switch (type) {
            case ARRAY -> {
                var inner = lib.iterator(arena);
                var element = (int) lib.call(lib.iterGetElementType, iterator);
                lib.call(lib.iterRecurse, iterator, inner);
                yield element == BYTE ? bytes(lib, arena, inner) : readAll(lib, arena, inner);
            }
            case STRUCT -> {
                var inner = lib.iterator(arena);
                lib.call(lib.iterRecurse, iterator, inner);
                yield new Struct(readAll(lib, arena, inner));
            }
            case DICT_ENTRY -> {
                var inner = lib.iterator(arena);
                lib.call(lib.iterRecurse, iterator, inner);
                var pair = readAll(lib, arena, inner);
                yield pair.size() == 2 ? new Entry(pair.get(0), pair.get(1)) : null;
            }
            case VARIANT -> {
                var inner = lib.iterator(arena);
                lib.call(lib.iterRecurse, iterator, inner);
                var held = readAll(lib, arena, inner);
                yield held.isEmpty() ? null : new Variant(held.getFirst());
            }
            // A Unix file descriptor is skipped: reading one would duplicate it.
            case STRING, SIGNATURE, OBJECT_PATH, BYTE, BOOLEAN, INT16, UINT16, INT32, UINT32, INT64, UINT64, DOUBLE ->
                basic(lib, arena, iterator, type);
            default -> null;
        };
    }

    private static @Nullable Object basic(Library lib, Arena arena, MemorySegment iterator, int type) {
        var slot = arena.allocate(JAVA_LONG);
        slot.set(JAVA_LONG, 0, 0);
        lib.call(lib.iterGetBasic, iterator, slot);
        return switch (type) {
            case STRING, SIGNATURE -> Library.string(slot.get(ADDRESS, 0));
            case OBJECT_PATH -> {
                var text = Library.string(slot.get(ADDRESS, 0));
                yield text == null ? null : new ObjectPath(text);
            }
            case BYTE -> slot.get(JAVA_BYTE, 0);
            case BOOLEAN -> slot.get(JAVA_INT, 0) != 0;
            case INT16 -> (int) slot.get(JAVA_SHORT, 0);
            case UINT16 -> Short.toUnsignedInt(slot.get(JAVA_SHORT, 0));
            case INT32, UINT32 -> slot.get(JAVA_INT, 0);
            case INT64, UINT64 -> slot.get(JAVA_LONG, 0);
            case DOUBLE -> slot.get(JAVA_DOUBLE, 0);
            default -> null;
        };
    }

    private static byte[] bytes(Library lib, Arena arena, MemorySegment iterator) {
        var out = new ByteArrayOutputStream();
        var slot = arena.allocate(JAVA_LONG);
        while ((int) lib.call(lib.iterGetArgType, iterator) == BYTE) {
            lib.call(lib.iterGetBasic, iterator, slot);
            out.write(slot.get(JAVA_BYTE, 0));
            if ((int) lib.call(lib.iterNext, iterator) == 0) {
                break;
            }
        }
        slot.fill((byte) 0);
        return out.toByteArray();
    }

    /// Appends arguments to a message being built.
    public static final class Writer {

        private final Library lib;
        private final Arena arena;
        private final MemorySegment iterator;

        /// A writer appending to the end of `message`.
        static Writer appending(Library lib, Arena arena, MemorySegment message) {
            var writer = new Writer(lib, arena, lib.iterator(arena));
            lib.call(lib.iterInitAppend, message, writer.iterator);
            return writer;
        }

        private Writer(Library lib, Arena arena, MemorySegment iterator) {
            this.lib = lib;
            this.arena = arena;
            this.iterator = iterator;
        }

        public Writer string(String value) {
            return text(STRING, value);
        }

        public Writer objectPath(String value) {
            return text(OBJECT_PATH, value);
        }

        public Writer uint32(int value) {
            return basic(UINT32, arena.allocateFrom(JAVA_INT, value));
        }

        public Writer int32(int value) {
            return basic(INT32, arena.allocateFrom(JAVA_INT, value));
        }

        public Writer int64(long value) {
            return basic(INT64, arena.allocateFrom(JAVA_LONG, value));
        }

        public Writer bool(boolean value) {
            // A dbus_bool_t is four bytes, whatever C's bool is.
            return basic(BOOLEAN, arena.allocateFrom(JAVA_INT, value ? 1 : 0));
        }

        /// An `ay`. The one slot each byte passes through is cleared after, so
        /// a secret is not left in it; libdbus keeps its own copy until the
        /// message is freed.
        public Writer bytes(byte[] value) {
            return container(ARRAY, "y", array -> {
                var slot = arena.allocate(JAVA_LONG);
                for (var b : value) {
                    slot.set(JAVA_BYTE, 0, b);
                    array.basic(BYTE, slot);
                }
                slot.fill((byte) 0);
            });
        }

        /// An array of `signature`, filled by `contents`.
        public Writer array(String signature, Consumer<Writer> contents) {
            return container(ARRAY, signature, contents);
        }

        /// A struct, whose fields `contents` writes in order.
        public Writer struct(Consumer<Writer> contents) {
            return container(STRUCT, null, contents);
        }

        /// A variant of `signature`, whose one value `value` writes.
        public Writer variant(String signature, Consumer<Writer> value) {
            return container(VARIANT, signature, value);
        }

        /// One `{key: variant}` entry of an `a{sv}`.
        public Writer entry(String key, String variantSignature, Consumer<Writer> value) {
            return container(DICT_ENTRY, null, entry -> {
                entry.string(key);
                entry.container(VARIANT, variantSignature, value);
            });
        }

        /// One `{key: value}` entry of an `a{ss}`.
        public Writer entry(String key, String value) {
            return container(DICT_ENTRY, null, entry -> entry.string(key).string(value));
        }

        private Writer container(int type, @Nullable String signature, Consumer<Writer> contents) {
            var inner = new Writer(lib, arena, lib.iterator(arena));
            var opened = (int) lib.call(
                    lib.iterOpenContainer,
                    iterator,
                    type,
                    signature == null ? MemorySegment.NULL : arena.allocateFrom(signature),
                    inner.iterator);
            if (opened == 0) {
                throw new IllegalStateException("libdbus could not open a container");
            }
            contents.accept(inner);
            lib.call(lib.iterCloseContainer, iterator, inner.iterator);
            return this;
        }

        private Writer text(int type, String value) {
            var pointer = arena.allocate(ADDRESS);
            pointer.set(ADDRESS, 0, arena.allocateFrom(value));
            return basic(type, pointer);
        }

        private Writer basic(int type, MemorySegment value) {
            if ((int) lib.call(lib.iterAppendBasic, iterator, type, value) == 0) {
                throw new IllegalStateException("libdbus is out of memory");
            }
            return this;
        }
    }

    /// The functions, bound once.
    static final class Library {

        final MethodHandle busGetPrivate;
        final MethodHandle connectionOpenPrivate;
        final MethodHandle busRegister;
        final MethodHandle setExitOnDisconnect;
        final MethodHandle connectionClose;
        final MethodHandle connectionUnref;
        final MethodHandle newMethodCall;
        final MethodHandle newSignal;
        final MethodHandle newMethodReturn;
        final MethodHandle newError;
        final MethodHandle messageUnref;
        final MethodHandle iterInitAppend;
        final MethodHandle iterAppendBasic;
        final MethodHandle iterOpenContainer;
        final MethodHandle iterCloseContainer;
        final MethodHandle iterInit;
        final MethodHandle iterGetArgType;
        final MethodHandle iterGetElementType;
        final MethodHandle iterGetBasic;
        final MethodHandle iterNext;
        final MethodHandle iterRecurse;
        final MethodHandle sendWithReplyAndBlock;
        final MethodHandle send;
        final MethodHandle flush;
        final MethodHandle addMatch;
        final MethodHandle readWrite;
        final MethodHandle popMessage;
        final MethodHandle getType;
        final MethodHandle getPath;
        final MethodHandle getInterface;
        final MethodHandle getMember;
        final MethodHandle requestName;
        final MethodHandle getUnixFd;
        final MethodHandle errorInit;
        final MethodHandle errorFreeHandle;

        @SuppressWarnings("restricted")
        private Library(SymbolLookup lookup) {
            busGetPrivate = bind(lookup, "dbus_bus_get_private", P_IP);
            connectionOpenPrivate = bind(lookup, "dbus_connection_open_private", P_PP);
            busRegister = bind(lookup, "dbus_bus_register", I_PP);
            setExitOnDisconnect = bind(lookup, "dbus_connection_set_exit_on_disconnect", V_PI);
            connectionClose = bind(lookup, "dbus_connection_close", V_P);
            connectionUnref = bind(lookup, "dbus_connection_unref", V_P);
            newMethodCall = bind(lookup, "dbus_message_new_method_call", P_PPPP);
            newSignal = bind(lookup, "dbus_message_new_signal", P_PPP);
            newMethodReturn = bind(lookup, "dbus_message_new_method_return", P_P);
            newError = bind(lookup, "dbus_message_new_error", P_PPP);
            messageUnref = bind(lookup, "dbus_message_unref", V_P);
            iterInitAppend = bind(lookup, "dbus_message_iter_init_append", V_PP);
            iterAppendBasic = bind(lookup, "dbus_message_iter_append_basic", I_PIP);
            iterOpenContainer = bind(lookup, "dbus_message_iter_open_container", I_PIPP);
            iterCloseContainer = bind(lookup, "dbus_message_iter_close_container", I_PP);
            iterInit = bind(lookup, "dbus_message_iter_init", I_PP);
            iterGetArgType = bind(lookup, "dbus_message_iter_get_arg_type", I_P);
            iterGetElementType = bind(lookup, "dbus_message_iter_get_element_type", I_P);
            iterGetBasic = bind(lookup, "dbus_message_iter_get_basic", V_PP);
            iterNext = bind(lookup, "dbus_message_iter_next", I_P);
            iterRecurse = bind(lookup, "dbus_message_iter_recurse", V_PP);
            sendWithReplyAndBlock = bind(lookup, "dbus_connection_send_with_reply_and_block", P_PPIP);
            send = bind(lookup, "dbus_connection_send", I_PPP);
            flush = bind(lookup, "dbus_connection_flush", V_P);
            addMatch = bind(lookup, "dbus_bus_add_match", V_PPP);
            readWrite = bind(lookup, "dbus_connection_read_write", I_PI);
            popMessage = bind(lookup, "dbus_connection_pop_message", P_P);
            getType = bind(lookup, "dbus_message_get_type", I_P);
            getPath = bind(lookup, "dbus_message_get_path", P_P);
            getInterface = bind(lookup, "dbus_message_get_interface", P_P);
            getMember = bind(lookup, "dbus_message_get_member", P_P);
            requestName = bind(lookup, "dbus_bus_request_name", I_PPIP);
            getUnixFd = bind(lookup, "dbus_connection_get_unix_fd", I_PP);
            errorInit = bind(lookup, "dbus_error_init", V_P);
            errorFreeHandle = bind(lookup, "dbus_error_free", V_P);
        }

        private static MethodHandle bind(SymbolLookup lookup, String name, FunctionDescriptor shape) {
            var address = lookup.find(name)
                    .orElseThrow(() -> new UnsatisfiedLinkError("this libdbus does not export " + name));
            return Downcalls.link(shape).bindTo(address);
        }

        /// Binds against whichever libdbus this machine has, or null.
        @SuppressWarnings("restricted")
        static @Nullable Library find() {
            for (var name : new String[] {"libdbus-1.so.3", "libdbus-1.so"}) {
                try {
                    return new Library(SymbolLookup.libraryLookup(name, Arena.global()));
                } catch (IllegalArgumentException | UnsatisfiedLinkError e) {
                    // The next name, and then no D-Bus at all.
                }
            }
            return null;
        }

        /// Calls a bound function. A crossing that throws is a desktop that
        /// does not answer, never a reason to stop.
        Object call(MethodHandle handle, Object... arguments) {
            try {
                var result = handle.invokeWithArguments(arguments);
                return result == null ? Boolean.TRUE : result;
            } catch (Throwable e) {
                throw new IllegalStateException("a D-Bus call failed", e);
            }
        }

        MemorySegment error(Arena arena) {
            var error = arena.allocate(ERROR_BYTES);
            error.fill((byte) 0);
            call(errorInit, error);
            return error;
        }

        void errorFree(MemorySegment error) {
            call(errorFreeHandle, error);
        }

        MemorySegment iterator(Arena arena) {
            var iterator = arena.allocate(ITERATOR_BYTES);
            iterator.fill((byte) 0);
            return iterator;
        }

        /// A C string libdbus owns, or null.
        @SuppressWarnings("restricted")
        static @Nullable String string(MemorySegment pointer) {
            if (pointer.address() == 0) {
                return null;
            }
            return pointer.reinterpret(Integer.MAX_VALUE).getString(0);
        }
    }
}
