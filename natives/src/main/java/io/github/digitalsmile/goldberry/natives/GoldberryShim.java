package io.github.digitalsmile.goldberry.natives;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;

import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.log.Logs;
import io.github.digitalsmile.goldberry.log.Startup;
import io.github.digitalsmile.goldberry.natives.calls.ShimCalls;

/// BindingRegistry for libgoldberry's own three exported functions.
///
/// This is the first hand-written binding (ADR-0010) and the template for every
/// other: declare what is bound as a [ShimCalls], call each function by name, and
/// never let a [MemorySegment] out of the `natives` module untyped. The holder
/// keeps the address and the signature together, so this class has neither.
public final class GoldberryShim {

    /// The ABI this Java code was written against. `goldberry_shim.c` must agree.
    ///
    /// 2 added `goldberry_probe_measure` (ADR-0017); 3 added SDL3's windowing and
    /// event surface, and the constant rows in the layout table (ADR-0020); 4
    /// added `SDL_GetCurrentVideoDriver` and `SDL_SetHint` (ADR-0026); 5 added
    /// Yoga's node API and its enumerators (ADR-0029); 6 added Blend2D's image
    /// and context surface (ADR-0031); 7 added HarfBuzz shaping (ADR-0032); 9
    /// added Markdown's encoded event stream and md4c's entity table (ADR-0294);
    /// 10 added `goldberry_platform_capabilities`, which is what a build that
    /// cannot ask the desktop anything says about itself (ADR-0325,
    /// `docs/gaps.md` G32); 11 added libwebp's three decoder entry points and the
    /// `SDL_DropEvent` layout, which are the two halves of `docs/gaps.md` G35
    /// (ADR-0329, ADR-0330); 12 put libwebp's three animation structs, its demux
    /// ABI version and SDL's `SDL_INIT_*` bits on the layout table, which were
    /// hand-counted in Java and checked by nothing.
    public static final int SUPPORTED_ABI_VERSION = 12;

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
}
