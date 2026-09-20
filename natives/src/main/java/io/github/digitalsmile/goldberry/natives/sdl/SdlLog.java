package io.github.digitalsmile.goldberry.natives.sdl;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.log.Logs;
import io.github.digitalsmile.goldberry.log.bridge.NativeLogBridge;
import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import io.github.digitalsmile.goldberry.natives.Upcalls;
import io.github.digitalsmile.goldberry.natives.sdl.calls.SdlLogCalls;
import io.github.digitalsmile.goldberry.natives.sdl.log.SdlLogCategory;
import io.github.digitalsmile.goldberry.natives.sdl.log.SdlLogPriority;

/// Sends SDL's own log messages to SLF4J instead of to stderr.
///
/// The companion of
/// [io.github.digitalsmile.goldberry.natives.glib.GlibLog]
/// and the simpler half of ADR-0443: SDL has one hook, it is documented as
/// replaceable, and replacing it cannot abort anything.
///
/// Messages land on `native.sdl.<category>` — `native.sdl.video`,
/// `native.sdl.render` — so an application can raise one subsystem without the
/// rest:
///
/// ```xml
/// <logger name="native.sdl.video" level="debug"/>
/// ```
///
/// ## What it does not do
///
/// It does not change **what SDL emits**. SDL keeps its own per-category
/// priority thresholds, and by default they are high — so an application that
/// turns `native.sdl.video` up to `trace` and sees nothing is not looking at a
/// broken bridge. `SDL_SetLogPriorities` is what would lower them, and it is
/// deliberately not bound: SDL's defaults are SDL's opinion about what is worth
/// saying, and a toolkit that overrode them would be choosing verbosity on every
/// application's behalf.
///
/// What this changes is the **destination**. When SDL does say something, it
/// says it into the application's log, with a level and a name, instead of onto
/// a console nobody is capturing.
///
/// ## Installed before `SDL_Init`
///
/// Which is the point: "no video driver could be initialised" is written during
/// initialisation, and a bridge installed afterwards would miss the one message
/// worth having most.
///
/// **Must not throw**, like every upcall — see [#output].
public final class SdlLog {

    /// The `source` segment of every logger name this bridge writes to.
    public static final String SOURCE = "sdl";

    /// The longest native string this bridge will read. See
    /// [io.github.digitalsmile.goldberry.natives.glib.GlibLog]'s note.
    private static final long MAX_STRING_LENGTH = 64 * 1024;

    private static final Logger LOG = Logs.of(SdlLog.class);

    private static final Linker LINKER = Linker.nativeLinker();

    /// ```c
    /// typedef void (SDLCALL *SDL_LogOutputFunction)(void *userdata, int category,
    ///                                               SDL_LogPriority priority, const char *message);
    /// ```
    private static final FunctionDescriptor DESCRIPTOR = Upcalls.describe(FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static boolean installed;

    private SdlLog() {}

    /// Routes SDL's log messages to SLF4J, once.
    ///
    /// Loads `libgoldberry` if it is not loaded, which is not a cost: the only
    /// caller is the backend, one line before it initialises SDL through the
    /// same library.
    ///
    /// @return true if this call installed the bridge
    public static synchronized boolean install() {
        if (installed) {
            return false;
        }
        installed = true;
        if (!NativeLogBridge.isEnabled()) {
            LOG.debug("{}=false, so SDL keeps its own log output", NativeLogBridge.ENABLED_PROPERTY);
            return false;
        }
        try {
            return install(NativeLibrary.get().lookup());
        } catch (RuntimeException | LinkageError e) {
            // No library, or one without the symbol. Either way SDL logs where
            // it always did, and the backend is about to raise a much better
            // error than this one if the library is really missing.
            LOG.debug("could not install the SDL log bridge ({}); SDL keeps its own output", e.toString());
            return false;
        }
    }

    /// The half that takes a lookup, so a test can install against a library it
    /// chose rather than against the process's.
    static boolean install(SymbolLookup lookup) {
        var calls = SdlLogCalls.bind(lookup);
        if (!calls.setLogOutputFunction().isPresent()) {
            LOG.debug("this libgoldberry does not export SDL_SetLogOutputFunction; SDL keeps its own output");
            return false;
        }
        calls.setLogOutputFunction().call(stub(), MemorySegment.NULL);
        LOG.debug("SDL's log messages now arrive on {}.{}.*", NativeLogBridge.ROOT, SOURCE);
        return true;
    }

    /// Whether the bridge has been installed in this process.
    public static synchronized boolean isInstalled() {
        return installed;
    }

    /// SDL's `SDL_LogOutputFunction`. **Called from C; must not throw.**
    // Restricted: `reinterpret` gives a pointer handed over by C the size it
    // always had. Suppressed per call site, like every other crossing here.
    @SuppressWarnings({"unused", "restricted"})
    private static void output(MemorySegment userData, int category, int priority, MemorySegment message) {
        try {
            var text = MemorySegment.NULL.equals(message)
                    ? null
                    : message.reinterpret(MAX_STRING_LENGTH).getString(0);
            report(category, priority, text);
        } catch (Throwable t) {
            // Nowhere to report it, and a throw would cross back into C.
        }
    }

    /// One SDL message, on the logger it belongs to.
    ///
    /// Package-private and taking only Java values, so the translation —
    /// category to logger name, priority to SLF4J level — is testable without
    /// SDL, a display or a native library.
    static void report(int category, int priority, String message) {
        NativeLogBridge.log(
                SOURCE,
                SdlLogCategory.nameOf(category),
                SdlLogPriority.of(priority).level(),
                message);
    }

    /// The upcall stub over [#output], in `Arena.global()`: SDL keeps the
    /// pointer for the life of the process, so a scoped arena would be a
    /// dangling function pointer the moment it closed.
    @SuppressWarnings("restricted")
    private static MemorySegment stub() {
        try {
            var target = MethodHandles.lookup()
                    .findStatic(
                            SdlLog.class,
                            "output",
                            MethodType.methodType(
                                    void.class, MemorySegment.class, int.class, int.class, MemorySegment.class));
            return LINKER.upcallStub(target, DESCRIPTOR, Arena.global());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("no output method to bind SDL's log callback to", e);
        }
    }
}
