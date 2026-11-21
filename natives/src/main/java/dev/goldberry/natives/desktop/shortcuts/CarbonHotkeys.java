package dev.goldberry.natives.desktop.shortcuts;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.natives.Downcalls;
import dev.goldberry.natives.Upcalls;

/// Global shortcuts on macOS: Carbon's `RegisterEventHotKey`, which every Mac
/// application with a global shortcut still uses, and the one way to one that
/// asks for no Accessibility permission.
///
/// A registered hotkey is delivered as a Carbon event to the application's
/// event target, `kEventHotKeyPressed` when it goes down and
/// `kEventHotKeyReleased` when it comes up, which is the release push-to-talk
/// needs. The events are dispatched by the main thread's event loop, the one
/// SDL pumps, so registering, the handler and unregistering all happen on the
/// main thread, and [#bindsOnMainThread()] says so.
///
/// `RegisterEventHotKey` answers `eventHotKeyExistsErr` when another
/// application registered the same combination, which is the refusal a
/// shortcut gets here. The key codes are virtual key codes, the US layout's
/// positions, as every Carbon hotkey's are.
///
/// UNVERIFIED: written against `HIToolbox/CarbonEvents.h` and not yet run on
/// macOS. The modifier arithmetic is checked by tests that run anywhere.
public final class CarbonHotkeys implements HotkeyBackend {

    static final String FRAMEWORK = "/System/Library/Frameworks/Carbon.framework/Carbon";

    // Events.h's modifier bits.
    static final int CMD_KEY = 0x0100;
    static final int SHIFT_KEY = 0x0200;
    static final int OPTION_KEY = 0x0800;
    static final int CONTROL_KEY = 0x1000;

    // CarbonEvents.h, four-character codes as their big-endian integers.
    static final int EVENT_CLASS_KEYBOARD = fourCharCode("keyb");
    static final int EVENT_HOT_KEY_PRESSED = 5;
    static final int EVENT_HOT_KEY_RELEASED = 6;
    static final int PARAM_DIRECT_OBJECT = fourCharCode("----");
    static final int TYPE_EVENT_HOT_KEY_ID = fourCharCode("hkid");
    static final int SIGNATURE = fourCharCode("gbry");
    static final int HOT_KEY_EXISTS = -9878;
    private static final int EVENT_NOT_HANDLED = -9874;

    /// `EventHotKeyID`, passed by value: a signature and an id.
    static final StructLayout HOT_KEY_ID =
            MemoryLayout.structLayout(JAVA_INT.withName("signature"), JAVA_INT.withName("id"));

    private static final FunctionDescriptor P_V = Downcalls.describe(FunctionDescriptor.of(ADDRESS));
    private static final FunctionDescriptor I_P = Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS));

    /// `InstallEventHandler(EventTargetRef, EventHandlerUPP, ItemCount, const
    /// EventTypeSpec *, void *, EventHandlerRef *)`.
    private static final FunctionDescriptor INSTALL_HANDLER =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS, ADDRESS, ADDRESS));

    /// `RegisterEventHotKey(UInt32, UInt32, EventHotKeyID, EventTargetRef,
    /// OptionBits, EventHotKeyRef *)`.
    private static final FunctionDescriptor REGISTER_HOT_KEY = Downcalls.describe(
            FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT, HOT_KEY_ID, ADDRESS, JAVA_INT, ADDRESS));

    /// `GetEventParameter(EventRef, EventParamName, EventParamType,
    /// EventParamType *, ByteCount, ByteCount *, void *)`.
    private static final FunctionDescriptor GET_PARAMETER = Downcalls.describe(
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS, ADDRESS));

    /// `OSStatus (*EventHandlerProcPtr)(EventHandlerCallRef, EventRef, void *)`.
    private static final FunctionDescriptor HANDLER =
            Upcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

    private static final Logger LOG = Logs.of(CarbonHotkeys.class);

    /// The one backend whose handler is installed, which the static handler
    /// reports to.
    private static volatile @Nullable CarbonHotkeys installed;

    /// One registered hotkey.
    record Registered(String id, int hotkeyId, MemorySegment reference) {}

    private final Carbon carbon;
    private final List<Registered> registered = new ArrayList<>();
    private @Nullable MemorySegment handlerRef;
    private @Nullable HotkeyState state;
    private boolean closed;

    private CarbonHotkeys(Carbon carbon) {
        this.carbon = carbon;
    }

    /// Hotkeys through Carbon, or empty off macOS.
    static Optional<CarbonHotkeys> open() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
            return Optional.empty();
        }
        return Optional.ofNullable(Carbon.find()).map(CarbonHotkeys::new);
    }

    /// The big-endian integer of a four-character code.
    static int fourCharCode(String code) {
        if (code.length() != 4) {
            throw new IllegalArgumentException("a four-character code has four characters: " + code);
        }
        return (code.charAt(0) << 24) | (code.charAt(1) << 16) | (code.charAt(2) << 8) | code.charAt(3);
    }

    /// Carbon's modifiers for a [Hotkey]'s. The fourth modifier is Command.
    static int carbonModifiers(int modifiers) {
        var mask = 0;
        if ((modifiers & Hotkey.META) != 0) {
            mask |= CMD_KEY;
        }
        if ((modifiers & Hotkey.SHIFT) != 0) {
            mask |= SHIFT_KEY;
        }
        if ((modifiers & Hotkey.ALT) != 0) {
            mask |= OPTION_KEY;
        }
        if ((modifiers & Hotkey.CTRL) != 0) {
            mask |= CONTROL_KEY;
        }
        return mask;
    }

    @Override
    public String name() {
        return "Carbon hotkeys";
    }

    @Override
    public boolean bindsOnMainThread() {
        return true;
    }

    @Override
    public synchronized CompletableFuture<List<HotkeyOutcome>> bind(List<Hotkey> hotkeys, HotkeyListener listener) {
        var copy = List.copyOf(hotkeys);
        if (closed) {
            return CompletableFuture.completedFuture(
                    HotkeyBackend.refuseAll(copy, HotkeyOutcome.Refusal.UNSUPPORTED, "closed"));
        }
        unregisterAll();
        state = new HotkeyState(listener);
        if (copy.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        try {
            installHandler();
        } catch (RuntimeException e) {
            LOG.debug("Carbon would not take an event handler", e);
            return CompletableFuture.completedFuture(
                    HotkeyBackend.refuseAll(copy, HotkeyOutcome.Refusal.UNSUPPORTED, e.toString()));
        }
        var outcomes = new ArrayList<HotkeyOutcome>(copy.size());
        var next = 1;
        try (var arena = Arena.ofConfined()) {
            for (var hotkey : copy) {
                var hotkeyId = next++;
                var code = hotkey.key().macKeyCode();
                if (code < 0) {
                    outcomes.add(new HotkeyOutcome.Refused(
                            hotkey.id(), HotkeyOutcome.Refusal.UNMAPPABLE, "a Mac keyboard has no " + hotkey.key()));
                    continue;
                }
                var id = arena.allocate(HOT_KEY_ID);
                id.set(JAVA_INT, 0, SIGNATURE);
                id.set(JAVA_INT, 4, hotkeyId);
                var out = arena.allocate(ADDRESS);
                var status = (int) carbon.call(
                        carbon.registerHotKey,
                        code,
                        carbonModifiers(hotkey.modifiers()),
                        id,
                        carbon.call(carbon.applicationTarget),
                        0,
                        out);
                if (status == 0) {
                    registered.add(new Registered(hotkey.id(), hotkeyId, out.get(ADDRESS, 0)));
                    outcomes.add(new HotkeyOutcome.Bound(hotkey.id(), hotkey.xdgTrigger()));
                } else if (status == HOT_KEY_EXISTS) {
                    outcomes.add(new HotkeyOutcome.Refused(
                            hotkey.id(), HotkeyOutcome.Refusal.TAKEN, "another application registered it"));
                } else {
                    outcomes.add(new HotkeyOutcome.Refused(
                            hotkey.id(), HotkeyOutcome.Refusal.UNSUPPORTED, "RegisterEventHotKey: " + status));
                }
            }
        }
        return CompletableFuture.completedFuture(List.copyOf(outcomes));
    }

    private void installHandler() {
        if (handlerRef != null) {
            return;
        }
        try (var arena = Arena.ofConfined()) {
            var types = arena.allocate(JAVA_INT, 4);
            types.setAtIndex(JAVA_INT, 0, EVENT_CLASS_KEYBOARD);
            types.setAtIndex(JAVA_INT, 1, EVENT_HOT_KEY_PRESSED);
            types.setAtIndex(JAVA_INT, 2, EVENT_CLASS_KEYBOARD);
            types.setAtIndex(JAVA_INT, 3, EVENT_HOT_KEY_RELEASED);
            var out = arena.allocate(ADDRESS);
            var status = (int) carbon.call(
                    carbon.installHandler,
                    carbon.call(carbon.applicationTarget),
                    carbon.handler(),
                    2L,
                    types,
                    MemorySegment.NULL,
                    out);
            if (status != 0) {
                throw new IllegalStateException("InstallEventHandler: " + status);
            }
            handlerRef = out.get(ADDRESS, 0);
            installed = this;
        }
    }

    /// The event handler: which of this backend's hotkeys, and down or up. An
    /// upcall must never throw, so nothing escapes it.
    static int onEvent(MemorySegment callRef, MemorySegment event, MemorySegment userData) {
        try {
            var backend = installed;
            if (backend == null) {
                return EVENT_NOT_HANDLED;
            }
            return backend.handle(event) ? 0 : EVENT_NOT_HANDLED;
        } catch (Throwable t) {
            return EVENT_NOT_HANDLED;
        }
    }

    private synchronized boolean handle(MemorySegment event) {
        var current = state;
        if (current == null) {
            return false;
        }
        try (var arena = Arena.ofConfined()) {
            var id = arena.allocate(HOT_KEY_ID);
            var status = (int) carbon.call(
                    carbon.getParameter,
                    event,
                    PARAM_DIRECT_OBJECT,
                    TYPE_EVENT_HOT_KEY_ID,
                    MemorySegment.NULL,
                    HOT_KEY_ID.byteSize(),
                    MemorySegment.NULL,
                    id);
            if (status != 0 || id.get(JAVA_INT, 0) != SIGNATURE) {
                return false;
            }
            var hotkeyId = id.get(JAVA_INT, 4);
            var kind = (int) carbon.call(carbon.eventKind, event);
            for (var hotkey : registered) {
                if (hotkey.hotkeyId() == hotkeyId) {
                    if (kind == EVENT_HOT_KEY_PRESSED) {
                        current.pressed(hotkey.id());
                    } else if (kind == EVENT_HOT_KEY_RELEASED) {
                        current.released(hotkey.id());
                    }
                    return true;
                }
            }
            return false;
        }
    }

    private void unregisterAll() {
        var current = state;
        if (current != null) {
            current.releaseAll();
        }
        for (var hotkey : registered) {
            carbon.call(carbon.unregisterHotKey, hotkey.reference());
        }
        registered.clear();
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        unregisterAll();
        var reference = handlerRef;
        handlerRef = null;
        if (reference != null) {
            carbon.call(carbon.removeHandler, reference);
        }
        if (installed == this) {
            installed = null;
        }
    }

    /// The framework's functions, bound once, or null without it.
    private static final class Carbon {

        final MethodHandle applicationTarget;
        final MethodHandle installHandler;
        final MethodHandle removeHandler;
        final MethodHandle registerHotKey;
        final MethodHandle unregisterHotKey;
        final MethodHandle eventKind;
        final MethodHandle getParameter;
        private @Nullable MemorySegment stub;

        private Carbon(SymbolLookup lookup) {
            applicationTarget = bind(lookup, "GetApplicationEventTarget", P_V);
            installHandler = bind(lookup, "InstallEventHandler", INSTALL_HANDLER);
            removeHandler = bind(lookup, "RemoveEventHandler", I_P);
            registerHotKey = bind(lookup, "RegisterEventHotKey", REGISTER_HOT_KEY);
            unregisterHotKey = bind(lookup, "UnregisterEventHotKey", I_P);
            eventKind = bind(lookup, "GetEventKind", I_P);
            getParameter = bind(lookup, "GetEventParameter", GET_PARAMETER);
        }

        @SuppressWarnings("restricted")
        static @Nullable Carbon find() {
            try {
                return new Carbon(SymbolLookup.libraryLookup(FRAMEWORK, Arena.global()));
            } catch (IllegalArgumentException | UnsatisfiedLinkError e) {
                LOG.debug("no Carbon ({}); no global shortcuts", e.getMessage());
                return null;
            }
        }

        @SuppressWarnings("restricted")
        synchronized MemorySegment handler() {
            if (stub == null) {
                try {
                    var target = MethodHandles.lookup()
                            .findStatic(
                                    CarbonHotkeys.class,
                                    "onEvent",
                                    MethodType.methodType(
                                            int.class, MemorySegment.class, MemorySegment.class, MemorySegment.class));
                    stub = Linker.nativeLinker().upcallStub(target, HANDLER, Arena.global());
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("the Carbon event handler is missing", e);
                }
            }
            return stub;
        }

        private static MethodHandle bind(SymbolLookup lookup, String name, FunctionDescriptor shape) {
            var address = lookup.find(name).orElseThrow(() -> new UnsatisfiedLinkError("Carbon has no " + name));
            return Downcalls.link(shape).bindTo(address);
        }

        Object call(MethodHandle handle, Object... arguments) {
            try {
                var result = handle.invokeWithArguments(arguments);
                return result == null ? Boolean.TRUE : result;
            } catch (Throwable e) {
                throw new IllegalStateException("a Carbon call failed", e);
            }
        }
    }
}
