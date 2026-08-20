package io.github.digitalsmile.goldberry.natives;

import io.github.digitalsmile.goldberry.natives.log.Logs;
import io.github.digitalsmile.goldberry.natives.log.Startup;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import org.slf4j.Logger;

/// BindingRegistry for libgoldberry's own three exported functions.
///
/// This is the first hand-written binding (ADR-0010) and the template for every
/// other: look the symbol up once, keep its address in a final field, call it
/// through the [Downcalls] constant that names its signature exactly, and never
/// let a [MemorySegment] out of the `natives` module untyped.
public final class GoldberryShim {

    /// The ABI this Java code was written against. `goldberry_shim.c` must agree.
    ///
    /// 2 added `goldberry_probe_measure` (ADR-0017); 3 added SDL3's windowing and
    /// event surface, and the constant rows in the layout table (ADR-0020); 4
    /// added `SDL_GetCurrentVideoDriver` and `SDL_SetHint` (ADR-0026); 5 added
    /// Yoga's node API and its enumerators (ADR-0029); 6 added Blend2D's image
    /// and context surface (ADR-0031); 7 added HarfBuzz shaping (ADR-0032).
    public static final int SUPPORTED_ABI_VERSION = 8;

    private static final Logger LOG = Logs.of(GoldberryShim.class);

    private static final class Holder {
        private static final GoldberryShim INSTANCE = create();
    }

    /// `int goldberry_abi_version(void)`
    private final MemorySegment abiVersion;

    /// `const goldberry_layout_entry* goldberry_layout_table(void)`
    private final MemorySegment layoutTable;

    /// `int goldberry_layout_count(void)`
    private final MemorySegment layoutCount;

    private GoldberryShim(SymbolLookup lookup) {
        this.abiVersion = Downcalls.symbol(lookup, "goldberry_abi_version");
        this.layoutTable = Downcalls.symbol(lookup, "goldberry_layout_table");
        this.layoutCount = Downcalls.symbol(lookup, "goldberry_layout_count");
    }

    /// The shim bindings, loading and ABI-checking the library on first call.
    public static GoldberryShim get() {
        return Holder.INSTANCE;
    }

    /// The ABI version reported by the loaded library.
    public int abiVersion() {
        try {
            return (int) Downcalls.INT__VOID.invokeExact(abiVersion);
        } catch (Throwable t) {
            throw new IllegalStateException("goldberry_abi_version() failed", t);
        }
    }

    /// Pointer to the first entry of the layout table.
    ///
    /// The returned segment is zero-length; callers resize it against
    /// [#layoutCount()] rather than trusting the pointer's own bounds.
    public MemorySegment layoutTable() {
        try {
            return (MemorySegment) Downcalls.PTR__VOID.invokeExact(layoutTable);
        } catch (Throwable t) {
            throw new IllegalStateException("goldberry_layout_table() failed", t);
        }
    }

    /// Number of entries in the layout table.
    public int layoutCount() {
        try {
            return (int) Downcalls.INT__VOID.invokeExact(layoutCount);
        } catch (Throwable t) {
            throw new IllegalStateException("goldberry_layout_count() failed", t);
        }
    }

    private static GoldberryShim create() {
        var shim = new GoldberryShim(NativeLibrary.get().lookup());
        var version = shim.abiVersion();
        LOG.debug("libgoldberry reports ABI version {}, {} layout entries",
                version, version == SUPPORTED_ABI_VERSION ? shim.layoutCount() : -1);
        Startup.mark("libgoldberry ABI " + version + " verified");
        if (version != SUPPORTED_ABI_VERSION) {
            throw new UnsatisfiedLinkError(
                    "libgoldberry reports ABI version " + version + ", but this build of "
                            + "goldberry-natives was written against " + SUPPORTED_ABI_VERSION
                            + ". The Java and native artifacts are mismatched.");
        }
        return shim;
    }
}
