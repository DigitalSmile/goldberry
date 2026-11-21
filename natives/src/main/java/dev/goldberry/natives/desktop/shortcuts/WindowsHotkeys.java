package dev.goldberry.natives.desktop.shortcuts;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.jspecify.annotations.Nullable;

import dev.goldberry.natives.Downcalls;

/// Global shortcuts on Windows: `RegisterHotKey`, on a thread of this
/// backend's own whose message queue the hotkeys post to.
///
/// `RegisterHotKey` with no window posts `WM_HOTKEY` to the thread that
/// registered it, and fails when another application registered the same
/// combination, which is the refusal a shortcut gets here. `MOD_NOREPEAT`
/// keeps a held key from posting it again.
///
/// ## The release
///
/// Windows reports a hotkey going down and nothing else. While one is down, a
/// 20 ms thread timer asks `GetAsyncKeyState` whether its key still is, and
/// the first time it is not, the hotkey is released; the timer stops when
/// nothing is held. A low-level keyboard hook would hear the release itself,
/// but it hears every key the user types in every application, and Windows
/// removes one silently that does not answer within its time limit, which a
/// garbage-collection pause in the hook's upcall can exceed. The timer costs
/// up to 20 ms on the release, and only while a shortcut is held.
///
/// No window and no upcall: the thread's queue is the whole of it. Another
/// thread wakes it with `PostThreadMessageW`.
///
/// UNVERIFIED: written against the Windows SDK's documentation and not yet run
/// on Windows. The modifier and message arithmetic is checked by tests that run
/// anywhere.
final class WindowsHotkeys extends HotkeyWorker {

    // WinUser.h.
    static final int MOD_ALT = 0x0001;
    static final int MOD_CONTROL = 0x0002;
    static final int MOD_SHIFT = 0x0004;
    static final int MOD_WIN = 0x0008;
    static final int MOD_NOREPEAT = 0x4000;
    static final int WM_TIMER = 0x0113;
    static final int WM_HOTKEY = 0x0312;
    static final int WM_APP = 0x8000;
    private static final int WM_USER = 0x0400;
    private static final int PM_NOREMOVE = 0;

    /// How often a held hotkey's key is looked at.
    static final int RELEASE_POLL_MILLIS = 20;

    /// `MSG`, 64-bit: the message at 8, `wParam` at 16, 48 bytes in all.
    static final long MSG_BYTES = 48;
    static final long MSG_MESSAGE = 8;
    static final long MSG_WPARAM = 16;

    private static final FunctionDescriptor REGISTER_HOT_KEY =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));
    private static final FunctionDescriptor UNREGISTER_HOT_KEY =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
    private static final FunctionDescriptor GET_MESSAGE =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT));
    private static final FunctionDescriptor PEEK_MESSAGE =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));
    private static final FunctionDescriptor POST_THREAD_MESSAGE =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG, JAVA_LONG));
    private static final FunctionDescriptor SET_TIMER =
            Downcalls.describe(FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_LONG, JAVA_INT, ADDRESS));
    private static final FunctionDescriptor KILL_TIMER =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG));
    private static final FunctionDescriptor ASYNC_KEY_STATE =
            Downcalls.describe(FunctionDescriptor.of(JAVA_SHORT, JAVA_INT));
    private static final FunctionDescriptor CURRENT_THREAD_ID = Downcalls.describe(FunctionDescriptor.of(JAVA_INT));

    /// One registered hotkey: its id among this thread's and its key.
    record Registered(String id, int hotkeyId, int vk) {}

    private final List<Registered> registered = new ArrayList<>();
    private @Nullable User32 user;
    private @Nullable Arena arena;
    private @Nullable MemorySegment message;
    private volatile int threadId;
    private long timer;

    private WindowsHotkeys() {
        super("goldberry-global-shortcuts-windows");
    }

    /// Hotkeys on a thread of their own, or empty off Windows.
    static Optional<WindowsHotkeys> open() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return Optional.empty();
        }
        var backend = new WindowsHotkeys();
        return backend.start() ? Optional.of(backend) : Optional.empty();
    }

    @Override
    public String name() {
        return "Windows hotkeys";
    }

    /// `RegisterHotKey`'s modifiers for a [Hotkey]'s.
    static int windowsModifiers(int modifiers) {
        var mask = MOD_NOREPEAT;
        if ((modifiers & Hotkey.ALT) != 0) {
            mask |= MOD_ALT;
        }
        if ((modifiers & Hotkey.CTRL) != 0) {
            mask |= MOD_CONTROL;
        }
        if ((modifiers & Hotkey.SHIFT) != 0) {
            mask |= MOD_SHIFT;
        }
        if ((modifiers & Hotkey.META) != 0) {
            mask |= MOD_WIN;
        }
        return mask;
    }

    /// Whether `GetAsyncKeyState`'s answer says the key is down: the high bit.
    static boolean isDown(short state) {
        return (state & 0x8000) != 0;
    }

    @Override
    boolean openOnThread() {
        var lib = User32.find();
        if (lib == null) {
            return false;
        }
        user = lib;
        var scratch = Arena.ofConfined();
        arena = scratch;
        var msg = scratch.allocate(MSG_BYTES, 8);
        message = msg;
        // A thread has no queue until it asks for a message, and a message
        // posted to a thread without one is lost.
        lib.call(lib.peekMessage, msg, MemorySegment.NULL, WM_USER, WM_USER, PM_NOREMOVE);
        threadId = (int) lib.call(lib.currentThreadId);
        return true;
    }

    @Override
    void awaitEvents() {
        var lib = user;
        var msg = message;
        if (lib == null || msg == null) {
            return;
        }
        if ((int) lib.call(lib.getMessage, msg, MemorySegment.NULL, 0, 0) <= 0) {
            return;
        }
        var kind = msg.get(JAVA_INT, MSG_MESSAGE);
        if (kind == WM_HOTKEY) {
            pressed((int) msg.get(JAVA_LONG, MSG_WPARAM));
        } else if (kind == WM_TIMER) {
            pollReleases();
        }
    }

    private void pressed(int hotkeyId) {
        var current = state();
        if (current == null) {
            return;
        }
        for (var hotkey : registered) {
            if (hotkey.hotkeyId() == hotkeyId) {
                current.pressed(hotkey.id());
                startTimer();
            }
        }
    }

    private void pollReleases() {
        var lib = user;
        var current = state();
        if (lib == null || current == null) {
            stopTimer();
            return;
        }
        for (var hotkey : registered) {
            if (current.isHeld(hotkey.id()) && !isDown((short) lib.call(lib.asyncKeyState, hotkey.vk()))) {
                current.released(hotkey.id());
            }
        }
        if (current.held().isEmpty()) {
            stopTimer();
        }
    }

    private void startTimer() {
        var lib = user;
        if (lib != null && timer == 0) {
            timer = (long) lib.call(lib.setTimer, MemorySegment.NULL, 0L, RELEASE_POLL_MILLIS, MemorySegment.NULL);
        }
    }

    private void stopTimer() {
        var lib = user;
        if (lib != null && timer != 0) {
            lib.call(lib.killTimer, MemorySegment.NULL, timer);
            timer = 0;
        }
    }

    @Override
    void wake() {
        // The thread id first: it is volatile and written after the
        // functions, so reading it makes them visible.
        var thread = threadId;
        var lib = user;
        if (lib != null && thread != 0) {
            lib.call(lib.postThreadMessage, thread, WM_APP, 0L, 0L);
        }
    }

    @Override
    void bindOnThread(List<Hotkey> hotkeys, HotkeyState state, CompletableFuture<List<HotkeyOutcome>> outcome) {
        var lib = user;
        if (lib == null) {
            outcome.complete(HotkeyBackend.refuseAll(hotkeys, HotkeyOutcome.Refusal.UNSUPPORTED, "no user32"));
            return;
        }
        unregisterAll(lib);
        var outcomes = new ArrayList<HotkeyOutcome>(hotkeys.size());
        var next = 1;
        for (var hotkey : hotkeys) {
            var hotkeyId = next++;
            var vk = hotkey.key().windowsVk();
            var ok = (int) lib.call(
                    lib.registerHotKey, MemorySegment.NULL, hotkeyId, windowsModifiers(hotkey.modifiers()), vk);
            if (ok == 0) {
                outcomes.add(new HotkeyOutcome.Refused(
                        hotkey.id(), HotkeyOutcome.Refusal.TAKEN, "another application registered it"));
            } else {
                registered.add(new Registered(hotkey.id(), hotkeyId, vk));
                outcomes.add(new HotkeyOutcome.Bound(hotkey.id(), hotkey.xdgTrigger()));
            }
        }
        outcome.complete(List.copyOf(outcomes));
    }

    private void unregisterAll(User32 lib) {
        stopTimer();
        for (var hotkey : registered) {
            lib.call(lib.unregisterHotKey, MemorySegment.NULL, hotkey.hotkeyId());
        }
        registered.clear();
    }

    @Override
    void closeOnThread() {
        var lib = user;
        if (lib != null) {
            unregisterAll(lib);
        }
        threadId = 0;
        user = null;
        message = null;
        var scratch = arena;
        arena = null;
        if (scratch != null) {
            scratch.close();
        }
    }

    /// `user32`'s and `kernel32`'s functions, bound on the thread that uses
    /// them.
    private record User32(
            MethodHandle registerHotKey,
            MethodHandle unregisterHotKey,
            MethodHandle getMessage,
            MethodHandle peekMessage,
            MethodHandle postThreadMessage,
            MethodHandle setTimer,
            MethodHandle killTimer,
            MethodHandle asyncKeyState,
            MethodHandle currentThreadId) {

        @SuppressWarnings("restricted")
        static @Nullable User32 find() {
            try {
                var user = SymbolLookup.libraryLookup("user32.dll", Arena.global());
                var kernel = SymbolLookup.libraryLookup("kernel32.dll", Arena.global());
                return new User32(
                        bind(user, "RegisterHotKey", REGISTER_HOT_KEY),
                        bind(user, "UnregisterHotKey", UNREGISTER_HOT_KEY),
                        bind(user, "GetMessageW", GET_MESSAGE),
                        bind(user, "PeekMessageW", PEEK_MESSAGE),
                        bind(user, "PostThreadMessageW", POST_THREAD_MESSAGE),
                        bind(user, "SetTimer", SET_TIMER),
                        bind(user, "KillTimer", KILL_TIMER),
                        bind(user, "GetAsyncKeyState", ASYNC_KEY_STATE),
                        bind(kernel, "GetCurrentThreadId", CURRENT_THREAD_ID));
            } catch (IllegalArgumentException | UnsatisfiedLinkError e) {
                return null;
            }
        }

        private static MethodHandle bind(SymbolLookup lookup, String name, FunctionDescriptor shape) {
            var address = lookup.find(name).orElseThrow(() -> new UnsatisfiedLinkError("no " + name));
            return Downcalls.link(shape).bindTo(address);
        }

        Object call(MethodHandle handle, Object... arguments) {
            try {
                var result = handle.invokeWithArguments(arguments);
                return result == null ? Boolean.TRUE : result;
            } catch (Throwable e) {
                throw new IllegalStateException("a user32 call failed", e);
            }
        }
    }
}
