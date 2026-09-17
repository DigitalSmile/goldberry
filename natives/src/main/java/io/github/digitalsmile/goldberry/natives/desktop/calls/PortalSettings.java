package io.github.digitalsmile.goldberry.natives.desktop.calls;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

import io.github.digitalsmile.goldberry.natives.desktop.MotionPreference;

/// One question to the XDG settings portal, over D-Bus, on Linux.
///
/// ## Why libdbus and not SDL
///
/// SDL answers `SDL_GetSystemTheme` and nothing else about the desktop's
/// preferences; there is no `SDL_GetReducedMotion` and no request open for one.
/// The portal has the answer — `org.freedesktop.portal.Desktop`, the interface
/// every sandboxed application on Linux already reads its settings through — and
/// reaching it means speaking D-Bus.
///
/// **libdbus is loaded at run time and is not a build dependency.** There is
/// nothing to compile, nothing to ship and nothing added to the superbuild: the
/// library is `dlopen`ed by name, and a machine without it answers
/// [MotionPreference#UNKNOWN] like a machine without a portal. That is the same
/// shape SDL itself uses for D-Bus, and it is why this costs no native build
/// ([ADR-0383]).
///
/// ## Why the calls are the ones they are
///
/// `dbus_message_append_args` is variadic, and the two arguments this sends are
/// strings — so the iterator API says the same thing without a variadic
/// signature: `dbus_message_iter_init_append` then two
/// `dbus_message_iter_append_basic` calls. Reading the reply is the mirror of
/// that, with one wrinkle: `Read` returns a variant, and every portal
/// implementation wraps the value in a *second* variant, so the reader recurses
/// through as many as it finds.
public final class PortalSettings {

    /// The three names that make a portal call, and a key that is worth two
    /// tries: the freedesktop namespace is the specified one, and GNOME's own
    /// key is what a desktop that predates it answers.
    private static final String SERVICE = "org.freedesktop.portal.Desktop";

    private static final String PATH = "/org/freedesktop/portal/desktop";

    private static final String INTERFACE = "org.freedesktop.portal.Settings";

    private static final String METHOD = "Read";

    /// How long to wait for the portal, in milliseconds.
    ///
    /// Short on purpose. This is asked once, on the way to the first frame, and
    /// a desktop whose portal is wedged must cost a fifth of a second rather
    /// than a second and a half — the answer is a preference, and the default is
    /// already correct.
    private static final int TIMEOUT_MILLIS = 200;

    /// `DBUS_BUS_SESSION`.
    private static final int SESSION_BUS = 0;

    /// The D-Bus type codes this reads, which are the ASCII letters the
    /// specification names them by.
    private static final int TYPE_STRING = 's';

    private static final int TYPE_VARIANT = 'v';

    private static final int TYPE_BOOLEAN = 'b';

    private static final int TYPE_UINT32 = 'u';

    /// Room for a `DBusMessageIter`, which is opaque and about eighty bytes of
    /// `dummy` fields. Rounded up generously: the struct is private to libdbus,
    /// so the safe size is one nobody will grow past rather than one read off a
    /// header this build does not have.
    private static final long ITERATOR_BYTES = 256;

    /// The same for a `DBusError`, which is two pointers, a bitfield and some
    /// padding.
    private static final long ERROR_BYTES = 64;

    private PortalSettings() {}

    /// The handles, looked up once. Null when there is no libdbus, which is what
    /// makes every method here a no-op on a machine without one.
    private static final Calls CALLS = Calls.find();

    /// Whether this can ask at all.
    public static boolean isAvailable() {
        return CALLS != null;
    }

    /// Reads `namespace`/`key` and answers what it says about motion, or
    /// [MotionPreference#UNKNOWN] for anything that did not work.
    public static MotionPreference read(String namespace, String key, boolean trueMeansFull) {
        if (CALLS == null) {
            return MotionPreference.UNKNOWN;
        }
        try (var arena = Arena.ofConfined()) {
            var error = arena.allocate(ERROR_BYTES);
            error.fill((byte) 0);
            CALLS.errorInit.call(error);
            var connection = CALLS.busGet.call(SESSION_BUS, error);
            if (connection.address() == 0) {
                CALLS.errorFree.call(error);
                return MotionPreference.UNKNOWN;
            }
            var message = CALLS.newMethodCall.call(
                    arena.allocateFrom(SERVICE),
                    arena.allocateFrom(PATH),
                    arena.allocateFrom(INTERFACE),
                    arena.allocateFrom(METHOD));
            if (message.address() == 0) {
                CALLS.errorFree.call(error);
                return MotionPreference.UNKNOWN;
            }
            try {
                append(arena, message, namespace, key);
                var reply = CALLS.sendWithReplyAndBlock.call(connection, message, TIMEOUT_MILLIS, error);
                if (reply.address() == 0) {
                    return MotionPreference.UNKNOWN;
                }
                try {
                    return value(arena, reply, trueMeansFull);
                } finally {
                    CALLS.unref.call(reply);
                }
            } finally {
                CALLS.unref.call(message);
                CALLS.errorFree.call(error);
            }
        } catch (RuntimeException e) {
            // A portal that answers something unexpected is a desktop with no
            // answer, not a reason to stop opening windows.
            return MotionPreference.UNKNOWN;
        }
    }

    /// Appends the two strings the `Read` method takes.
    private static void append(Arena arena, MemorySegment message, String namespace, String key) {
        var iterator = arena.allocate(ITERATOR_BYTES);
        iterator.fill((byte) 0);
        CALLS.iterInitAppend.call(message, iterator);
        for (var text : new String[] {namespace, key}) {
            // A `const char **`: the call takes the address *of* the pointer,
            // which is what makes this the non-variadic half of the API.
            var pointer = arena.allocate(ValueLayout.ADDRESS);
            pointer.set(ValueLayout.ADDRESS, 0, arena.allocateFrom(text));
            CALLS.iterAppendBasic.call(iterator, TYPE_STRING, pointer);
        }
    }

    /// Reads the reply: a variant, usually wrapping another variant, wrapping a
    /// boolean or a `uint32`.
    private static MotionPreference value(Arena arena, MemorySegment reply, boolean trueMeansFull) {
        var iterator = arena.allocate(ITERATOR_BYTES);
        iterator.fill((byte) 0);
        if (CALLS.iterInit.call(reply, iterator) == 0) {
            return MotionPreference.UNKNOWN;
        }
        var current = iterator;
        // Through as many variants as there are. GNOME wraps the value twice and
        // the specification promises one; counting them would be reading a
        // desktop's mind, and unwrapping until something is not a variant is the
        // same answer either way.
        for (var depth = 0; depth < 4 && CALLS.iterGetArgType.call(current) == TYPE_VARIANT; depth++) {
            var inner = arena.allocate(ITERATOR_BYTES);
            inner.fill((byte) 0);
            CALLS.iterRecurse.call(current, inner);
            current = inner;
        }
        var type = CALLS.iterGetArgType.call(current);
        var slot = arena.allocate(ValueLayout.JAVA_LONG);
        slot.set(ValueLayout.JAVA_LONG, 0, 0);
        if (type == TYPE_BOOLEAN) {
            CALLS.iterGetBasic.call(current, slot);
            var on = slot.get(ValueLayout.JAVA_INT, 0) != 0;
            return on == trueMeansFull ? MotionPreference.FULL : MotionPreference.REDUCED;
        }
        if (type == TYPE_UINT32) {
            CALLS.iterGetBasic.call(current, slot);
            // `org.freedesktop.appearance` spells its preferences as an
            // enumeration: 0 is "no preference", and anything else is the
            // preference being expressed.
            return slot.get(ValueLayout.JAVA_INT, 0) == 0 ? MotionPreference.FULL : MotionPreference.REDUCED;
        }
        return MotionPreference.UNKNOWN;
    }

    /// The handful of libdbus functions this needs, bound once.
    private record Calls(
            Call2 busGet,
            Call4 newMethodCall,
            Call2Void iterInitAppend,
            Call3Int iterAppendBasic,
            Call2Int iterInit,
            Call1Int iterGetArgType,
            Call2Void iterRecurse,
            Call2Void iterGetBasic,
            Call4Reply sendWithReplyAndBlock,
            Call1Void unref,
            Call1Void errorInit,
            Call1Void errorFree) {

        /// Binds against whichever libdbus this machine has, or null.
        static Calls find() {
            var lookup = library();
            if (lookup == null) {
                return null;
            }
            try {
                return new Calls(
                        new Call2(lookup, "dbus_bus_get"),
                        new Call4(lookup, "dbus_message_new_method_call"),
                        new Call2Void(lookup, "dbus_message_iter_init_append"),
                        new Call3Int(lookup, "dbus_message_iter_append_basic"),
                        new Call2Int(lookup, "dbus_message_iter_init"),
                        new Call1Int(lookup, "dbus_message_iter_get_arg_type"),
                        new Call2Void(lookup, "dbus_message_iter_recurse"),
                        new Call2Void(lookup, "dbus_message_iter_get_basic"),
                        new Call4Reply(lookup, "dbus_connection_send_with_reply_and_block"),
                        new Call1Void(lookup, "dbus_message_unref"),
                        new Call1Void(lookup, "dbus_error_init"),
                        new Call1Void(lookup, "dbus_error_free"));
            } catch (RuntimeException | UnsatisfiedLinkError e) {
                // A libdbus that does not export one of these is not a libdbus
                // this knows how to talk to.
                return null;
            }
        }

        /// The library, by the two names it ships under.
        @SuppressWarnings("restricted")
        private static SymbolLookup library() {
            for (var name : new String[] {"libdbus-1.so.3", "libdbus-1.so"}) {
                try {
                    return SymbolLookup.libraryLookup(name, Arena.global());
                } catch (IllegalArgumentException | UnsatisfiedLinkError e) {
                    // The next name, and then no D-Bus at all.
                }
            }
            return null;
        }
    }

    // --- the signatures, one holder per shape (ADR-0173's pattern) -----------

    private record Call2(MethodHandle handle, MemorySegment address) {

        Call2(SymbolLookup lookup, String symbol) {
            this(
                    Bindings.link(
                            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS)),
                    Bindings.symbol(lookup, symbol));
        }

        MemorySegment call(int first, MemorySegment second) {
            return Bindings.address(handle, address, first, second);
        }
    }

    private record Call4(MethodHandle handle, MemorySegment address) {

        Call4(SymbolLookup lookup, String symbol) {
            this(
                    Bindings.link(FunctionDescriptor.of(
                            ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS)),
                    Bindings.symbol(lookup, symbol));
        }

        MemorySegment call(MemorySegment a, MemorySegment b, MemorySegment c, MemorySegment d) {
            return Bindings.address(handle, address, a, b, c, d);
        }
    }

    private record Call4Reply(MethodHandle handle, MemorySegment address) {

        Call4Reply(SymbolLookup lookup, String symbol) {
            this(
                    Bindings.link(FunctionDescriptor.of(
                            ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS,
                            ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS)),
                    Bindings.symbol(lookup, symbol));
        }

        MemorySegment call(MemorySegment a, MemorySegment b, int timeout, MemorySegment error) {
            return Bindings.address(handle, address, a, b, timeout, error);
        }
    }

    private record Call1Void(MethodHandle handle, MemorySegment address) {

        Call1Void(SymbolLookup lookup, String symbol) {
            this(Bindings.link(FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)), Bindings.symbol(lookup, symbol));
        }

        void call(MemorySegment a) {
            Bindings.nothing(handle, address, a);
        }
    }

    private record Call2Void(MethodHandle handle, MemorySegment address) {

        Call2Void(SymbolLookup lookup, String symbol) {
            this(
                    Bindings.link(FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS)),
                    Bindings.symbol(lookup, symbol));
        }

        void call(MemorySegment a, MemorySegment b) {
            Bindings.nothing(handle, address, a, b);
        }
    }

    private record Call1Int(MethodHandle handle, MemorySegment address) {

        Call1Int(SymbolLookup lookup, String symbol) {
            this(
                    Bindings.link(FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)),
                    Bindings.symbol(lookup, symbol));
        }

        int call(MemorySegment a) {
            return Bindings.integer(handle, address, a);
        }
    }

    private record Call2Int(MethodHandle handle, MemorySegment address) {

        Call2Int(SymbolLookup lookup, String symbol) {
            this(
                    Bindings.link(
                            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)),
                    Bindings.symbol(lookup, symbol));
        }

        int call(MemorySegment a, MemorySegment b) {
            return Bindings.integer(handle, address, a, b);
        }
    }

    private record Call3Int(MethodHandle handle, MemorySegment address) {

        Call3Int(SymbolLookup lookup, String symbol) {
            this(
                    Bindings.link(FunctionDescriptor.of(
                            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS)),
                    Bindings.symbol(lookup, symbol));
        }

        int call(MemorySegment a, int type, MemorySegment b) {
            return Bindings.integer(handle, address, a, type, b);
        }
    }
}
