package dev.goldberry.natives.desktop.notify;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.natives.Downcalls;

/// A private D-Bus connection, through libdbus loaded at run time — enough of
/// it to send a method call, emit a signal and read the signals that come back.
///
/// The settings portal reads one value and needs four calls; a notification
/// is a call with arrays and a dictionary in it, and its answer — the user
/// clicked it — is a signal that arrives later. So this is the iterator API in
/// full, behind a writer and a reader, rather than four more handles.
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
/// UI-thread confined. libdbus is thread-safe; this class's buffers are not.
final class Dbus implements AutoCloseable {

    /// `DBUS_BUS_SESSION`.
    private static final int SESSION_BUS = 0;

    /// D-Bus type codes, which are the ASCII letters the specification names
    /// them by.
    static final int STRING = 's';

    static final int UINT32 = 'u';
    static final int INT32 = 'i';
    static final int INT64 = 'x';
    static final int BOOLEAN = 'b';
    static final int ARRAY = 'a';
    static final int VARIANT = 'v';
    static final int DICT_ENTRY = 'e';
    static final int INVALID = 0;

    /// Room for a `DBusMessageIter` and a `DBusError`. Both are private to
    /// libdbus; these are sizes nobody will grow past rather than ones read off a
    /// header this build does not have.
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
    static boolean isAvailable() {
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
    static Optional<Dbus> session() {
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
    static Optional<Dbus> at(String address) {
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

    /// Calls `member` and waits up to `timeoutMillis` for the reply, which
    /// `reply` reads. False when the call failed or timed out.
    boolean call(
            String destination,
            String path,
            String iface,
            String member,
            Consumer<Writer> arguments,
            int timeoutMillis,
            Consumer<Reader> reply) {
        try (var arena = Arena.ofConfined()) {
            var message = (MemorySegment) lib.call(
                    lib.newMethodCall,
                    arena.allocateFrom(destination),
                    arena.allocateFrom(path),
                    arena.allocateFrom(iface),
                    arena.allocateFrom(member));
            if (message.address() == 0) {
                return false;
            }
            try {
                arguments.accept(Writer.appending(lib, arena, message));
                var error = lib.error(arena);
                var answer =
                        (MemorySegment) lib.call(lib.sendWithReplyAndBlock, connection, message, timeoutMillis, error);
                lib.errorFree(error);
                if (answer.address() == 0) {
                    return false;
                }
                try {
                    reply.accept(new Reader(lib, arena, answer));
                } finally {
                    lib.call(lib.messageUnref, answer);
                }
                return true;
            } finally {
                lib.call(lib.messageUnref, message);
            }
        }
    }

    /// Emits a signal. False when it could not be queued.
    boolean signal(String path, String iface, String member, Consumer<Writer> arguments) {
        try (var arena = Arena.ofConfined()) {
            var message = (MemorySegment) lib.call(
                    lib.newSignal, arena.allocateFrom(path), arena.allocateFrom(iface), arena.allocateFrom(member));
            if (message.address() == 0) {
                return false;
            }
            try {
                arguments.accept(Writer.appending(lib, arena, message));
                var sent = (int) lib.call(lib.send, connection, message, MemorySegment.NULL) != 0;
                lib.call(lib.flush, connection);
                return sent;
            } finally {
                lib.call(lib.messageUnref, message);
            }
        }
    }

    /// Asks the bus to route signals matching `rule` to this connection.
    void match(String rule) {
        try (var arena = Arena.ofConfined()) {
            var error = lib.error(arena);
            lib.call(lib.addMatch, connection, arena.allocateFrom(rule), error);
            lib.errorFree(error);
        }
    }

    /// One signal or call that arrived: who it is for, and its arguments read
    /// as far as `read` cares to.
    record Incoming(String iface, String member, List<Object> arguments) {}

    /// Reads whatever has arrived, without waiting, and answers the messages
    /// of `iface` with their basic arguments — strings, numbers, booleans —
    /// in order. Everything else is dropped.
    List<Incoming> drain(String iface) {
        var found = new ArrayList<Incoming>();
        lib.call(lib.readWrite, connection, 0);
        while (true) {
            var message = (MemorySegment) lib.call(lib.popMessage, connection);
            if (message.address() == 0) {
                return found;
            }
            try (var arena = Arena.ofConfined()) {
                var messageIface = Library.string((MemorySegment) lib.call(lib.getInterface, message));
                var member = Library.string((MemorySegment) lib.call(lib.getMember, message));
                if (iface.equals(messageIface) && member != null) {
                    found.add(new Incoming(iface, member, new Reader(lib, arena, message).basics()));
                }
            } finally {
                lib.call(lib.messageUnref, message);
            }
        }
    }

    /// The raw connection, for a test's fake service, which needs calls this
    /// class does not wrap.
    MemorySegment connection() {
        return connection;
    }

    /// The bindings, for the same test.
    static @Nullable Library library() {
        return Holder.LIBRARY;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            lib.call(lib.connectionClose, connection);
            lib.call(lib.connectionUnref, connection);
        }
    }

    /// Appends arguments to a message being built.
    static final class Writer {

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

        Writer string(String value) {
            var pointer = arena.allocate(ADDRESS);
            pointer.set(ADDRESS, 0, arena.allocateFrom(value));
            return basic(STRING, pointer);
        }

        Writer uint32(int value) {
            return basic(UINT32, arena.allocateFrom(JAVA_INT, value));
        }

        Writer int32(int value) {
            return basic(INT32, arena.allocateFrom(JAVA_INT, value));
        }

        Writer int64(long value) {
            return basic(INT64, arena.allocateFrom(JAVA_LONG, value));
        }

        Writer bool(boolean value) {
            // A dbus_bool_t is four bytes, whatever C's bool is.
            return basic(BOOLEAN, arena.allocateFrom(JAVA_INT, value ? 1 : 0));
        }

        /// An array of `signature`, filled by `contents`.
        Writer array(String signature, Consumer<Writer> contents) {
            return container(ARRAY, signature, contents);
        }

        /// One `{key: variant}` entry of an `a{sv}`.
        Writer entry(String key, String variantSignature, Consumer<Writer> value) {
            return container(DICT_ENTRY, null, entry -> {
                entry.string(key);
                entry.container(VARIANT, variantSignature, value);
            });
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

        private Writer basic(int type, MemorySegment value) {
            if ((int) lib.call(lib.iterAppendBasic, iterator, type, value) == 0) {
                throw new IllegalStateException("libdbus is out of memory");
            }
            return this;
        }
    }

    /// Reads a message's arguments.
    static final class Reader {

        private final Library lib;
        private final Arena arena;
        private final MemorySegment iterator;
        private final boolean any;

        Reader(Library lib, Arena arena, MemorySegment message) {
            this.lib = lib;
            this.arena = arena;
            this.iterator = lib.iterator(arena);
            this.any = (int) lib.call(lib.iterInit, message, iterator) != 0;
        }

        /// The basic arguments at the top level, in order: a [String], an
        /// [Integer] for `u` and `i`, a [Long] for `x`, a [Boolean] for `b`.
        /// Containers are skipped.
        List<Object> basics() {
            var values = new ArrayList<Object>();
            if (!any) {
                return values;
            }
            do {
                var type = (int) lib.call(lib.iterGetArgType, iterator);
                var slot = arena.allocate(JAVA_LONG);
                slot.set(JAVA_LONG, 0, 0);
                switch (type) {
                    case STRING -> {
                        lib.call(lib.iterGetBasic, iterator, slot);
                        values.add(Library.string(slot.get(ADDRESS, 0)));
                    }
                    case UINT32, INT32 -> {
                        lib.call(lib.iterGetBasic, iterator, slot);
                        values.add(slot.get(JAVA_INT, 0));
                    }
                    case INT64 -> {
                        lib.call(lib.iterGetBasic, iterator, slot);
                        values.add(slot.get(JAVA_LONG, 0));
                    }
                    case BOOLEAN -> {
                        lib.call(lib.iterGetBasic, iterator, slot);
                        values.add(slot.get(JAVA_INT, 0) != 0);
                    }
                    case INVALID -> {
                        return values;
                    }
                    default -> values.add(new Skipped((char) type));
                }
            } while ((int) lib.call(lib.iterNext, iterator) != 0);
            return values;
        }

        /// The first argument, as a `uint32`, or -1 when it is not one.
        long firstUint32() {
            var values = basics();
            return !values.isEmpty() && values.getFirst() instanceof Integer value ? Integer.toUnsignedLong(value) : -1;
        }
    }

    /// An argument [Reader#basics] did not read: a container, with its type.
    record Skipped(char type) {}

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
        final MethodHandle messageUnref;
        final MethodHandle iterInitAppend;
        final MethodHandle iterAppendBasic;
        final MethodHandle iterOpenContainer;
        final MethodHandle iterCloseContainer;
        final MethodHandle iterInit;
        final MethodHandle iterGetArgType;
        final MethodHandle iterGetBasic;
        final MethodHandle iterNext;
        final MethodHandle iterRecurse;
        final MethodHandle sendWithReplyAndBlock;
        final MethodHandle send;
        final MethodHandle flush;
        final MethodHandle addMatch;
        final MethodHandle readWrite;
        final MethodHandle popMessage;
        final MethodHandle getInterface;
        final MethodHandle getMember;
        final MethodHandle requestName;
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
            messageUnref = bind(lookup, "dbus_message_unref", V_P);
            iterInitAppend = bind(lookup, "dbus_message_iter_init_append", V_PP);
            iterAppendBasic = bind(lookup, "dbus_message_iter_append_basic", I_PIP);
            iterOpenContainer = bind(lookup, "dbus_message_iter_open_container", I_PIPP);
            iterCloseContainer = bind(lookup, "dbus_message_iter_close_container", I_PP);
            iterInit = bind(lookup, "dbus_message_iter_init", I_PP);
            iterGetArgType = bind(lookup, "dbus_message_iter_get_arg_type", I_P);
            iterGetBasic = bind(lookup, "dbus_message_iter_get_basic", V_PP);
            iterNext = bind(lookup, "dbus_message_iter_next", I_P);
            iterRecurse = bind(lookup, "dbus_message_iter_recurse", V_PP);
            sendWithReplyAndBlock = bind(lookup, "dbus_connection_send_with_reply_and_block", P_PPIP);
            send = bind(lookup, "dbus_connection_send", I_PPP);
            flush = bind(lookup, "dbus_connection_flush", V_P);
            addMatch = bind(lookup, "dbus_bus_add_match", V_PPP);
            readWrite = bind(lookup, "dbus_connection_read_write", I_PI);
            popMessage = bind(lookup, "dbus_connection_pop_message", P_P);
            getInterface = bind(lookup, "dbus_message_get_interface", P_P);
            getMember = bind(lookup, "dbus_message_get_member", P_P);
            requestName = bind(lookup, "dbus_bus_request_name", I_PPIP);
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
