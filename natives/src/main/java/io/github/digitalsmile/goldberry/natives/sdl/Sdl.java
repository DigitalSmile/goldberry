package io.github.digitalsmile.goldberry.natives.sdl;

import io.github.digitalsmile.goldberry.natives.Downcalls;
import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.util.Collection;
import java.util.Set;

/// BindingRegistry for SDL3's lifecycle, error and version calls.
///
/// The first slice of the backend (ADR-0003): enough to prove SDL3 is reachable
/// through `libgoldberry`'s export list and to report which SDL is statically
/// linked, without needing a display. Windowing follows.
///
/// Two SDL conventions are translated at this boundary rather than passed on:
///
/// **`bool` plus `SDL_GetError()` becomes [SdlException].** SDL returns `false`
/// and leaves a message that the next failing call on the same thread will
/// overwrite. Checking every return value by hand is how that message gets lost.
///
/// **`SDL_InitFlags` becomes a [Set] of [SdlSubsystem].** A bit mask is
/// error-prone in a language with enums and sets.
///
/// SDL is process-global: initializing twice is not an error but quitting once
/// undoes it for everyone. Ownership of that lifecycle belongs to the backend,
/// not to callers.
public final class Sdl {

    /// The SDL these bindings were written against, and the floor the pinned ref
    /// in `gradle/libs.versions.toml` must meet. Before 3.2.0 the lifecycle calls
    /// returned `int` rather than `bool`, so on an older SDL the descriptors here
    /// are the wrong width — which reads as a working binding that returns
    /// nonsense, not as a link error.
    public static final SdlVersion MINIMUM_VERSION = new SdlVersion(3, 2, 0);

    private static final class Holder {
        private static final Sdl INSTANCE = new Sdl(NativeLibrary.get().lookup());
    }

    private final MemorySegment init;
    private final MemorySegment initSubSystem;
    private final MemorySegment quitSubSystem;
    private final MemorySegment wasInit;
    private final MemorySegment quit;
    private final MemorySegment getError;
    private final MemorySegment clearError;
    private final MemorySegment getVersion;
    private final MemorySegment getRevision;
    private final MemorySegment getCurrentVideoDriver;
    private final MemorySegment setHint;
    private final MemorySegment getModState;

    private Sdl(SymbolLookup lookup) {
        // `bool` is C's _Bool -- one byte, not the four an int would take.
        this.init = Downcalls.symbol(lookup, "SDL_Init");
        this.initSubSystem = Downcalls.symbol(lookup, "SDL_InitSubSystem");
        this.quitSubSystem = Downcalls.symbol(lookup, "SDL_QuitSubSystem");
        this.wasInit = Downcalls.symbol(lookup, "SDL_WasInit");
        this.quit = Downcalls.symbol(lookup, "SDL_Quit");
        this.getError = Downcalls.symbol(lookup, "SDL_GetError");
        this.clearError = Downcalls.symbol(lookup, "SDL_ClearError");
        this.getVersion = Downcalls.symbol(lookup, "SDL_GetVersion");
        this.getRevision = Downcalls.symbol(lookup, "SDL_GetRevision");
        this.getCurrentVideoDriver = Downcalls.symbol(lookup, "SDL_GetCurrentVideoDriver");
        this.setHint = Downcalls.symbol(lookup, "SDL_SetHint");
        // `SDL_Keymod` is a Uint16, not an int -- the layout table's "Uint16"
        // scalar row is what says so, and binding it as JAVA_INT would read two
        // bytes of whatever follows it in the return register.
        this.getModState = Downcalls.symbol(lookup, "SDL_GetModState");
    }

    /// The SDL bindings, loading `libgoldberry` on first call.
    public static Sdl get() {
        return Holder.INSTANCE;
    }

    /// The version of SDL linked into this library.
    ///
    /// Static linking makes this a build fact rather than a runtime one, which is
    /// the point: there is no system SDL to disagree with.
    public SdlVersion version() {
        return SdlVersion.decode(callInt(getVersion, "SDL_GetVersion"));
    }

    /// SDL's source revision string. Empty when SDL was built from a tarball
    /// rather than a checkout.
    public String revision() {
        return readString(callPointer(getRevision, "SDL_GetRevision"));
    }

    /// The hint naming the video driver to use — `SDL_VIDEO_DRIVER`.
    ///
    /// Must be set before video is initialized.
    public static final String VIDEO_DRIVER_HINT = "SDL_VIDEO_DRIVER";

    /// The hint deciding whether a renderer's `present` waits for vertical blank
    /// — `SDL_RENDER_VSYNC`.
    ///
    /// Goldberry creates no renderer, so this looks like it should not matter.
    /// It does: on a platform whose SDL video driver implements no window
    /// surface — Wayland is one — `SDL_GetWindowSurface` falls back to a hidden
    /// `SDL_Renderer`, and every `SDL_UpdateWindowSurfaceRects` ends in that
    /// renderer's `SDL_RenderPresent` (ADR-0046). This is the only channel that
    /// reaches it.
    ///
    /// Must be set before the surface is first acquired, which is when SDL
    /// builds the renderer.
    public static final String RENDER_VSYNC_HINT = "SDL_RENDER_VSYNC";

    /// Sets an SDL hint.
    ///
    /// Hints are SDL's configuration channel; most must be set before the
    /// subsystem they affect is initialized.
    ///
    /// @return whether SDL accepted it
    public boolean setHint(String name, String value) {
        try (var arena = Arena.ofConfined()) {
            return invokeHint(setHint, arena.allocateFrom(name), arena.allocateFrom(value));
        }
    }

    private static boolean invokeHint(
            MemorySegment function, MemorySegment name, MemorySegment value) {
        try {
            return (boolean) Downcalls.BOOL__PTR_PTR.invokeExact(function, name, value);
        } catch (Throwable t) {
            throw new IllegalStateException("SDL_SetHint() failed", t);
        }
    }

    /// The video driver SDL chose — `wayland`, `x11`, `windows`, `cocoa`.
    ///
    /// Worth logging at startup, and worth checking before believing anything
    /// about windowing behaviour: a Wayland session running an application
    /// through XWayland behaves like X11, including its resize characteristics,
    /// and nothing else in the process gives that away.
    ///
    /// Empty until video is initialized.
    public String videoDriver() {
        return readString(callPointer(getCurrentVideoDriver, "SDL_GetCurrentVideoDriver"));
    }

    /// The modifier keys held **right now**, as SDL's `SDL_Keymod` bitmask.
    ///
    /// Polled rather than carried on the event, because SDL's mouse events do not
    /// carry one -- `SDL_MouseMotionEvent` has no `mod` field where
    /// `SDL_KeyboardEvent` does. Read at the moment an event is translated, which
    /// is inside the same pump that produced it.
    ///
    /// The alternative was latching the modifiers from the last key event, which
    /// needs no new symbol and is wrong in a way that lasts: a window that loses
    /// focus while Shift is held never sees the key release, so the flag stays
    /// down until the next time Shift is pressed and let go
    /// ([ADR-0089](../../../../../../../book/src/adr/0089-a-knobs-gesture-is-a-rate.md)).
    ///
    /// Widened to an int here, because the mask is unsigned and Java's short is
    /// not -- SDL's `SDL_KMOD_*` bits stop at 0x4000, but sign extension would
    /// still be a bug waiting for the day one is added above it.
    public int modifierState() {
        return callShort(getModState, "SDL_GetModState") & 0xFFFF;
    }

    /// Initializes SDL.
    ///
    /// @throws SdlException if SDL refuses
    public void initialize(Collection<SdlSubsystem> subsystems) {
        if (!callBoolean(init, "SDL_Init", SdlSubsystem.mask(subsystems))) {
            throw new SdlException("SDL_Init", lastError());
        }
    }

    /// Initializes further subsystems on top of an existing [#initialize].
    ///
    /// @throws SdlException if SDL refuses
    public void initializeSubsystems(Collection<SdlSubsystem> subsystems) {
        if (!callBoolean(initSubSystem, "SDL_InitSubSystem", SdlSubsystem.mask(subsystems))) {
            throw new SdlException("SDL_InitSubSystem", lastError());
        }
    }

    /// Shuts specific subsystems down. Cannot fail, by SDL's design.
    public void quitSubsystems(Collection<SdlSubsystem> subsystems) {
        callVoid(quitSubSystem, "SDL_QuitSubSystem", SdlSubsystem.mask(subsystems));
    }

    /// Which subsystems are currently initialized.
    ///
    /// Usually a superset of what was requested, because SDL initializes implied
    /// subsystems too — video brings events with it.
    public Set<SdlSubsystem> wasInit() {
        return SdlSubsystem.decode(callInt(wasInit, "SDL_WasInit", 0));
    }

    /// Shuts SDL down entirely. Process-global: this undoes every initialization,
    /// not only the caller's.
    public void quit() {
        callVoid(quit, "SDL_Quit");
    }

    /// The current thread's SDL error, empty when there is none.
    ///
    /// Rarely useful directly — a failing call raises [SdlException] with this
    /// message already attached.
    public String lastError() {
        return readString(callPointer(getError, "SDL_GetError"));
    }

    /// Clears the current thread's SDL error.
    public void clearError() {
        callBoolean(clearError, "SDL_ClearError");
    }

    private static boolean callBoolean(MemorySegment function, String name, int flags) {
        try {
            return (boolean) Downcalls.BOOL__INT.invokeExact(function, flags);
        } catch (Throwable t) {
            throw new IllegalStateException(name + "() failed", t);
        }
    }

    private static boolean callBoolean(MemorySegment function, String name) {
        try {
            return (boolean) Downcalls.BOOL__VOID.invokeExact(function);
        } catch (Throwable t) {
            throw new IllegalStateException(name + "() failed", t);
        }
    }

    private static short callShort(MemorySegment function, String name) {
        try {
            return (short) Downcalls.SHORT__VOID.invokeExact(function);
        } catch (Throwable t) {
            throw new IllegalStateException(name + "() failed", t);
        }
    }

    private static int callInt(MemorySegment function, String name) {
        try {
            return (int) Downcalls.INT__VOID.invokeExact(function);
        } catch (Throwable t) {
            throw new IllegalStateException(name + "() failed", t);
        }
    }

    private static int callInt(MemorySegment function, String name, int flags) {
        try {
            return (int) Downcalls.INT__INT.invokeExact(function, flags);
        } catch (Throwable t) {
            throw new IllegalStateException(name + "() failed", t);
        }
    }

    private static MemorySegment callPointer(MemorySegment function, String name) {
        try {
            return (MemorySegment) Downcalls.PTR__VOID.invokeExact(function);
        } catch (Throwable t) {
            throw new IllegalStateException(name + "() failed", t);
        }
    }

    private static void callVoid(MemorySegment function, String name) {
        try {
            Downcalls.VOID__VOID.invokeExact(function);
        } catch (Throwable t) {
            throw new IllegalStateException(name + "() failed", t);
        }
    }

    private static void callVoid(MemorySegment function, String name, int flags) {
        try {
            Downcalls.VOID__INT.invokeExact(function, flags);
        } catch (Throwable t) {
            throw new IllegalStateException(name + "() failed", t);
        }
    }

    /// SDL's strings are NUL-terminated and owned by SDL. The returned pointer is
    /// zero-length, so it has to be resized before it can be read.
    // Restricted: same reason as LayoutProbe.readString -- a bounded window is
    // what stops a corrupt pointer from becoming an unbounded read. SDL's error
    // and revision strings are short by construction.
    @SuppressWarnings("restricted")
    private static String readString(MemorySegment pointer) {
        if (MemorySegment.NULL.equals(pointer)) {
            return "";
        }
        return pointer.reinterpret(MAX_STRING_LENGTH).getString(0);
    }

    /// Bound on strings read back from SDL.
    private static final long MAX_STRING_LENGTH = 4096;

}
