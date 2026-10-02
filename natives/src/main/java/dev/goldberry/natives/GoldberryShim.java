package dev.goldberry.natives;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;

import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.log.Startup;
import dev.goldberry.natives.calls.ShimCalls;

/// The binding for `libgoldberry`'s own exported functions: the ABI version, the
/// layout table and the GTK backend hint.
///
/// `GoldberryShim.get()` loads the library on first call and checks that the
/// ABI version it reports is [#SUPPORTED_ABI_VERSION]; a mismatch is an
/// `UnsatisfiedLinkError` at start-up rather than a struct read wrongly later.
///
/// This is the template every other binding follows: declare what is bound as a
/// [ShimCalls], call each function by name, and never let a [MemorySegment] out
/// of the `natives` module untyped. The holder keeps the address and the
/// signature together, so this class has neither.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public final class GoldberryShim {

    /// The ABI this Java code was written against. `goldberry_shim.c` must agree.
    ///
    /// The number goes up whenever the export list or the layout table changes
    /// shape: a new library function, a new struct or constant row, a new upcall.
    /// A Java build and a library that disagree about it are mismatched
    /// artifacts, and [#get()] refuses the pair.
    public static final int SUPPORTED_ABI_VERSION = 17;

    private static final Logger LOG = Logs.of(GoldberryShim.class);

    private static final class Holder {
        private static final GoldberryShim INSTANCE = create();
    }

    private final ShimCalls calls;

    private GoldberryShim(SymbolLookup lookup) {
        this.calls = ShimCalls.bind(lookup);
    }

    /// The shim bindings, loading and ABI-checking the library on first call.
    public static GoldberryShim get() {
        return Holder.INSTANCE;
    }

    /// The ABI version reported by the loaded library.
    public int abiVersion() {
        return calls.abiVersion().call();
    }

    /// Pointer to the first entry of the layout table.
    ///
    /// The returned segment is zero-length; callers resize it against
    /// [#layoutCount()] rather than trusting the pointer's own bounds.
    public MemorySegment layoutTable() {
        return calls.layoutTable().call();
    }

    /// Number of entries in the layout table.
    public int layoutCount() {
        return calls.layoutCount().call();
    }

    private static GoldberryShim create() {
        var shim = new GoldberryShim(NativeLibrary.get().lookup());
        var version = shim.abiVersion();
        LOG.debug(
                "libgoldberry reports ABI version {}, {} layout entries",
                version,
                version == SUPPORTED_ABI_VERSION ? shim.layoutCount() : -1);
        Startup.mark("libgoldberry ABI " + version + " verified");
        if (version != SUPPORTED_ABI_VERSION) {
            throw new UnsatisfiedLinkError("libgoldberry reports ABI version " + version + ", but this build of "
                    + "goldberry-natives was written against " + SUPPORTED_ABI_VERSION
                    + ". The Java and native artifacts are mismatched.");
        }
        return shim;
    }

    /// Asks GTK to use the same window system SDL chose.
    ///
    /// Called once by the backend, **before anything initialises GTK** — which on
    /// Linux means before the first `tray-icon`, since SDL's tray is
    /// libayatana-appindicator and that calls `gtk_init`.
    ///
    /// Without it, an application on an XWayland desktop has an X11 window and
    /// Wayland GTK surfaces, and `web-view` cannot reparent one into the other.
    /// A no-op off Linux, and it never overwrites a `GDK_BACKEND` somebody set
    /// deliberately.
    ///
    /// @param backend the GDK backend name — `"x11"` or `"wayland"`
    public void preferGtkBackend(String backend) {
        java.util.Objects.requireNonNull(backend, "backend");
        try (var arena = java.lang.foreign.Arena.ofConfined()) {
            calls.preferGtkBackend().call(arena.allocateFrom(backend));
        }
    }
}
