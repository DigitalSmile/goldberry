package dev.goldberry.natives.desktop.shortcuts;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.natives.Downcalls;
import dev.goldberry.natives.Upcalls;

/// Global shortcuts on X11: a passive grab of each key on the root window,
/// through a Display of this backend's own thread.
///
/// `XGrabKey` asks the server for a key with a set of modifiers wherever the
/// focus is. The server reports it to the grabbing client, which is the one
/// thing that makes a shortcut global on X11, and refuses with `BadAccess`
/// when another client grabbed the same combination first.
///
/// ## The lock modifiers
///
/// A grab is for an exact modifier state, and Caps Lock and Num Lock are
/// modifiers to the server. So each hotkey is grabbed four times: as asked,
/// and with Caps Lock, Num Lock or both added. Which modifier bit Num Lock is
/// depends on the keyboard map and is read from it; `Mod2` is the usual one
/// and the fallback.
///
/// ## Press and release
///
/// An X server repeats a held key as a release and a press, which a
/// push-to-talk would hear as letting go. `XkbSetDetectableAutoRepeat` turns
/// the repeats into presses alone, which [HotkeyState] drops, so the release
/// that arrives is the real one. While the grab is active the keyboard is the
/// grabbing client's, so the release comes here whichever modifier the user
/// let go of first.
///
/// ## Errors
///
/// Xlib reports `BadAccess` to the process's error handler, after the
/// request, and the default handler exits the process. A handler of this
/// class's own is installed around the grabs, with `XSync` to collect their
/// errors, and the one before it is put back. An error on another display
/// while it is installed, SDL's, is handed on to the handler that was there.
public final class XlibHotkeys extends HotkeyWorker {

    static final String SONAME = "libX11.so.6";

    // X.h's modifier masks.
    static final int SHIFT_MASK = 1;
    static final int LOCK_MASK = 1 << 1;
    static final int CONTROL_MASK = 1 << 2;
    static final int MOD1_MASK = 1 << 3;
    static final int MOD2_MASK = 1 << 4;
    static final int MOD4_MASK = 1 << 6;

    /// The modifiers a grab is matched on; the lock modifiers are not.
    static final int MATCHED = SHIFT_MASK | CONTROL_MASK | MOD1_MASK | MOD4_MASK;

    static final int KEY_PRESS = 2;
    static final int KEY_RELEASE = 3;
    static final int BAD_ACCESS = 10;
    private static final int GRAB_MODE_ASYNC = 1;
    private static final int XK_NUM_LOCK = 0xff7f;

    // XKeyEvent as an LP64 compiler lays it out, inside a 192-byte XEvent.
    static final long EVENT_BYTES = 192;
    static final long EVENT_STATE = 80;
    static final long EVENT_KEYCODE = 84;

    // XErrorEvent: the display at 8, the error code at 32.
    static final long ERROR_DISPLAY = 8;
    static final long ERROR_CODE = 32;

    private static final FunctionDescriptor P_P = Downcalls.describe(FunctionDescriptor.of(ADDRESS, ADDRESS));
    private static final FunctionDescriptor I_P = Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final FunctionDescriptor J_P = Downcalls.describe(FunctionDescriptor.of(JAVA_LONG, ADDRESS));
    private static final FunctionDescriptor I_PI =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
    private static final FunctionDescriptor I_PP =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    private static final FunctionDescriptor B_PJ =
            Downcalls.describe(FunctionDescriptor.of(JAVA_BYTE, ADDRESS, JAVA_LONG));
    private static final FunctionDescriptor I_PIP =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));

    /// `XGrabKey(Display*, int keycode, unsigned modifiers, Window, Bool
    /// owner_events, int pointer_mode, int keyboard_mode)`.
    private static final FunctionDescriptor GRAB_KEY = Downcalls.describe(
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT));

    /// `XUngrabKey(Display*, int keycode, unsigned modifiers, Window)`.
    private static final FunctionDescriptor UNGRAB_KEY =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_LONG));

    /// `int (*XErrorHandler)(Display*, XErrorEvent*)`, which is also how the
    /// handler before this one is called.
    private static final FunctionDescriptor ERROR_HANDLER =
            Upcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

    private static final Logger LOG = Logs.of(XlibHotkeys.class);

    /// Every open backend's errors, by its display's address, which is how the
    /// one handler finds them.
    private static final Map<Long, XlibHotkeys> DISPLAYS = new ConcurrentHashMap<>();

    /// The handler that was installed before this class's, while it is.
    private static volatile MemorySegment previousHandler = MemorySegment.NULL;

    /// One grabbed hotkey.
    record Grab(String id, int keycode, int modifiers) {}

    private final String displayName;
    private final List<Grab> grabs = new ArrayList<>();
    private final List<Integer> errors = new ArrayList<>();
    private @Nullable Xlib xlib;
    private MemorySegment display = MemorySegment.NULL;
    private long root;
    private int numLock = MOD2_MASK;
    private @Nullable PollWakeup wakeup;
    private @Nullable MemorySegment event;
    private @Nullable Arena arena;

    private XlibHotkeys(String displayName) {
        super("goldberry-global-shortcuts-x11");
        this.displayName = displayName;
    }

    /// Grabs on the display `displayName`, or empty where there is no libX11
    /// or the display does not open.
    static Optional<XlibHotkeys> open(String displayName) {
        if (Xlib.get() == null) {
            return Optional.empty();
        }
        var backend = new XlibHotkeys(displayName);
        return backend.start() ? Optional.of(backend) : Optional.empty();
    }

    @Override
    public String name() {
        return "X11 key grabs";
    }

    @Override
    boolean openOnThread() {
        var lib = Xlib.get();
        var pipe = PollWakeup.open().orElse(null);
        if (lib == null || pipe == null) {
            if (pipe != null) {
                pipe.close();
            }
            return false;
        }
        var scratch = Arena.ofConfined();
        var opened = (MemorySegment) lib.call(lib.openDisplay, scratch.allocateFrom(displayName));
        if (opened.address() == 0) {
            scratch.close();
            pipe.close();
            return false;
        }
        xlib = lib;
        display = opened;
        arena = scratch;
        wakeup = pipe;
        event = scratch.allocate(EVENT_BYTES, 8);
        root = (long) lib.call(lib.defaultRootWindow, opened);
        var supported = scratch.allocate(JAVA_INT);
        lib.call(lib.detectableAutoRepeat, opened, 1, supported);
        if (supported.get(JAVA_INT, 0) == 0) {
            LOG.debug("this X server repeats a held key as releases; a held shortcut may stutter");
        }
        numLock = numLockMask(lib, opened);
        DISPLAYS.put(opened.address(), this);
        return true;
    }

    /// Which modifier bit the keyboard map gives Num Lock.
    @SuppressWarnings("restricted")
    private static int numLockMask(Xlib lib, MemorySegment display) {
        var keycode = Byte.toUnsignedInt((byte) lib.call(lib.keysymToKeycode, display, (long) XK_NUM_LOCK));
        var map = (MemorySegment) lib.call(lib.getModifierMapping, display);
        if (keycode == 0 || map.address() == 0) {
            return MOD2_MASK;
        }
        try {
            var keymap = map.reinterpret(16);
            var perModifier = keymap.get(JAVA_INT, 0);
            var codes = keymap.get(ADDRESS, 8).reinterpret(8L * perModifier);
            return numLockMask(codes, perModifier, keycode);
        } finally {
            lib.call(lib.freeModifierMap, map);
        }
    }

    /// The modifier whose keys in `codes` (eight modifiers of `perModifier`
    /// keycodes each, as `XModifierKeymap` holds them) include `keycode`, or
    /// `Mod2`.
    static int numLockMask(MemorySegment codes, int perModifier, int keycode) {
        for (var modifier = 0; modifier < 8; modifier++) {
            for (var index = 0; index < perModifier; index++) {
                if (Byte.toUnsignedInt(codes.get(JAVA_BYTE, (long) modifier * perModifier + index)) == keycode) {
                    return 1 << modifier;
                }
            }
        }
        return MOD2_MASK;
    }

    /// The X modifier mask for a [Hotkey]'s modifiers.
    static int xModifiers(int modifiers) {
        var mask = 0;
        if ((modifiers & Hotkey.SHIFT) != 0) {
            mask |= SHIFT_MASK;
        }
        if ((modifiers & Hotkey.CTRL) != 0) {
            mask |= CONTROL_MASK;
        }
        if ((modifiers & Hotkey.ALT) != 0) {
            mask |= MOD1_MASK;
        }
        if ((modifiers & Hotkey.META) != 0) {
            mask |= MOD4_MASK;
        }
        return mask;
    }

    /// The four states a hotkey is grabbed in: as asked, and with each lock.
    static int[] lockVariants(int modifiers, int numLock) {
        return new int[] {modifiers, modifiers | LOCK_MASK, modifiers | numLock, modifiers | LOCK_MASK | numLock};
    }

    @Override
    void awaitEvents() {
        var lib = xlib;
        var pipe = wakeup;
        if (lib == null || pipe == null) {
            return;
        }
        readEvents(lib);
        pipe.await((int) lib.call(lib.connectionNumber, display), -1);
        readEvents(lib);
    }

    private void readEvents(Xlib lib) {
        var buffer = event;
        if (buffer == null) {
            return;
        }
        while ((int) lib.call(lib.pending, display) > 0) {
            lib.call(lib.nextEvent, display, buffer);
            var type = buffer.get(JAVA_INT, 0);
            if (type == KEY_PRESS || type == KEY_RELEASE) {
                key(type == KEY_PRESS, buffer.get(JAVA_INT, EVENT_KEYCODE), buffer.get(JAVA_INT, EVENT_STATE));
            }
        }
    }

    /// A key event the grab reported: a press is matched on the key and the
    /// modifiers, a release on the key alone.
    void key(boolean down, int keycode, int state) {
        var current = state();
        if (current == null) {
            return;
        }
        for (var grab : grabs) {
            if (grab.keycode() != keycode) {
                continue;
            }
            if (down && grab.modifiers() == (state & MATCHED)) {
                current.pressed(grab.id());
            } else if (!down && current.isHeld(grab.id())) {
                current.released(grab.id());
            }
        }
    }

    @Override
    void wake() {
        var pipe = wakeup;
        if (pipe != null) {
            pipe.wake();
        }
    }

    @Override
    void bindOnThread(List<Hotkey> hotkeys, HotkeyState state, CompletableFuture<List<HotkeyOutcome>> outcome) {
        var lib = xlib;
        if (lib == null) {
            outcome.complete(HotkeyBackend.refuseAll(hotkeys, HotkeyOutcome.Refusal.UNSUPPORTED, "no display"));
            return;
        }
        ungrabAll(lib);
        var outcomes = new ArrayList<HotkeyOutcome>(hotkeys.size());
        for (var hotkey : hotkeys) {
            outcomes.add(grab(lib, hotkey));
        }
        outcome.complete(List.copyOf(outcomes));
    }

    private HotkeyOutcome grab(Xlib lib, Hotkey hotkey) {
        var keycode = Byte.toUnsignedInt((byte)
                lib.call(lib.keysymToKeycode, display, (long) hotkey.key().keysym()));
        if (keycode == 0) {
            return new HotkeyOutcome.Refused(
                    hotkey.id(),
                    HotkeyOutcome.Refusal.UNMAPPABLE,
                    "this keyboard has no key for " + hotkey.key().keysymName());
        }
        var modifiers = xModifiers(hotkey.modifiers());
        var variants = lockVariants(modifiers, numLock);
        var failed = collectingErrors(lib, () -> {
            for (var variant : variants) {
                lib.call(lib.grabKey, display, keycode, variant, root, 0, GRAB_MODE_ASYNC, GRAB_MODE_ASYNC);
            }
        });
        if (failed.contains(BAD_ACCESS)) {
            collectingErrors(lib, () -> {
                for (var variant : variants) {
                    lib.call(lib.ungrabKey, display, keycode, variant, root);
                }
            });
            return new HotkeyOutcome.Refused(
                    hotkey.id(), HotkeyOutcome.Refusal.TAKEN, "another X client grabbed " + hotkey.xdgTrigger());
        }
        grabs.add(new Grab(hotkey.id(), keycode, modifiers));
        return new HotkeyOutcome.Bound(hotkey.id(), hotkey.xdgTrigger());
    }

    private void ungrabAll(Xlib lib) {
        if (grabs.isEmpty()) {
            return;
        }
        collectingErrors(lib, () -> {
            for (var grab : grabs) {
                for (var variant : lockVariants(grab.modifiers(), numLock)) {
                    lib.call(lib.ungrabKey, display, grab.keycode(), variant, root);
                }
            }
        });
        grabs.clear();
    }

    /// Runs `requests` with this class's error handler installed, waits for
    /// the server to have answered them all, and answers the error codes it
    /// reported.
    private List<Integer> collectingErrors(Xlib lib, Runnable requests) {
        synchronized (XlibHotkeys.class) {
            errors.clear();
            var previous = (MemorySegment) lib.call(lib.setErrorHandler, lib.handler());
            previousHandler = previous;
            try {
                requests.run();
                lib.call(lib.sync, display, 0);
            } finally {
                lib.call(lib.setErrorHandler, previous);
                previousHandler = MemorySegment.NULL;
            }
            return List.copyOf(errors);
        }
    }

    /// The error handler. An error on a display of this class's goes to its
    /// backend; any other goes to the handler that was installed before. An
    /// upcall must never throw, so nothing escapes it.
    @SuppressWarnings("restricted")
    static int onError(MemorySegment display, MemorySegment error) {
        try {
            var report = error.reinterpret(EVENT_BYTES);
            var owner = DISPLAYS.get(display.address());
            if (owner != null) {
                owner.errors.add(Byte.toUnsignedInt(report.get(JAVA_BYTE, ERROR_CODE)));
                return 0;
            }
            var previous = previousHandler;
            if (previous.address() != 0) {
                return (int) Xlib.FORWARD.invokeExact(previous, display, error);
            }
        } catch (Throwable t) {
            // Nothing may unwind into Xlib.
        }
        return 0;
    }

    @Override
    void closeOnThread() {
        var lib = xlib;
        if (lib != null && display.address() != 0) {
            ungrabAll(lib);
            DISPLAYS.remove(display.address());
            lib.call(lib.closeDisplay, display);
        }
        xlib = null;
        display = MemorySegment.NULL;
        var pipe = wakeup;
        wakeup = null;
        if (pipe != null) {
            pipe.close();
        }
        var scratch = arena;
        arena = null;
        event = null;
        if (scratch != null) {
            scratch.close();
        }
    }

    /// libX11's functions, bound once, or null without it.
    static final class Xlib {

        /// Calls the handler that was installed before this class's.
        static final MethodHandle FORWARD = Downcalls.link(ERROR_HANDLER);

        private static final @Nullable Xlib INSTANCE = find();

        final MethodHandle openDisplay;
        final MethodHandle closeDisplay;
        final MethodHandle defaultRootWindow;
        final MethodHandle connectionNumber;
        final MethodHandle keysymToKeycode;
        final MethodHandle grabKey;
        final MethodHandle ungrabKey;
        final MethodHandle sync;
        final MethodHandle pending;
        final MethodHandle nextEvent;
        final MethodHandle setErrorHandler;
        final MethodHandle detectableAutoRepeat;
        final MethodHandle getModifierMapping;
        final MethodHandle freeModifierMap;
        private @Nullable MemorySegment handler;

        private Xlib(SymbolLookup lookup) {
            openDisplay = bind(lookup, "XOpenDisplay", P_P);
            closeDisplay = bind(lookup, "XCloseDisplay", I_P);
            defaultRootWindow = bind(lookup, "XDefaultRootWindow", J_P);
            connectionNumber = bind(lookup, "XConnectionNumber", I_P);
            keysymToKeycode = bind(lookup, "XKeysymToKeycode", B_PJ);
            grabKey = bind(lookup, "XGrabKey", GRAB_KEY);
            ungrabKey = bind(lookup, "XUngrabKey", UNGRAB_KEY);
            sync = bind(lookup, "XSync", I_PI);
            pending = bind(lookup, "XPending", I_P);
            nextEvent = bind(lookup, "XNextEvent", I_PP);
            setErrorHandler = bind(lookup, "XSetErrorHandler", P_P);
            detectableAutoRepeat = bind(lookup, "XkbSetDetectableAutoRepeat", I_PIP);
            getModifierMapping = bind(lookup, "XGetModifierMapping", P_P);
            freeModifierMap = bind(lookup, "XFreeModifiermap", I_P);
        }

        static @Nullable Xlib get() {
            return INSTANCE;
        }

        /// The error handler's stub, made the first time a grab needs it.
        @SuppressWarnings("restricted")
        synchronized MemorySegment handler() {
            if (handler == null) {
                try {
                    var target = MethodHandles.lookup()
                            .findStatic(
                                    XlibHotkeys.class,
                                    "onError",
                                    MethodType.methodType(int.class, MemorySegment.class, MemorySegment.class));
                    handler = Linker.nativeLinker().upcallStub(target, ERROR_HANDLER, Arena.global());
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("the X error handler is missing", e);
                }
            }
            return handler;
        }

        private static MethodHandle bind(SymbolLookup lookup, String name, FunctionDescriptor shape) {
            var address =
                    lookup.find(name).orElseThrow(() -> new UnsatisfiedLinkError(SONAME + " does not export " + name));
            return Downcalls.link(shape).bindTo(address);
        }

        // Restricted for XlibVisuals' reason: opening a system library is what
        // this module exists to do.
        @SuppressWarnings("restricted")
        private static @Nullable Xlib find() {
            try {
                return new Xlib(SymbolLookup.libraryLookup(SONAME, Arena.global()));
            } catch (IllegalArgumentException | UnsatisfiedLinkError e) {
                LOG.debug("no {} ({}); no global shortcuts on X11", SONAME, e.getMessage());
                return null;
            }
        }

        /// Calls a bound function.
        Object call(MethodHandle handle, Object... arguments) {
            try {
                var result = handle.invokeWithArguments(arguments);
                return result == null ? Boolean.TRUE : result;
            } catch (Throwable e) {
                throw new IllegalStateException("an Xlib call failed", e);
            }
        }
    }
}
