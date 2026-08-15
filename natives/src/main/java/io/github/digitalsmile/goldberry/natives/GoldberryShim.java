package io.github.digitalsmile.goldberry.natives;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/// Bindings for libgoldberry's own three exported functions.
///
/// This is the first hand-written binding (ADR-0010) and the template for every
/// other: look the symbol up once, describe its signature exactly, keep the
/// [MethodHandle] in a final field, and never let a [MemorySegment] out of the
/// `natives` module untyped.
public final class GoldberryShim {

    /// The ABI this Java code was written against. `goldberry_shim.c` must agree.
    public static final int SUPPORTED_ABI_VERSION = 1;

    private static final Linker LINKER = Linker.nativeLinker();

    private static final class Holder {
        private static final GoldberryShim INSTANCE = create();
    }

    private final MethodHandle abiVersion;
    private final MethodHandle layoutTable;
    private final MethodHandle layoutCount;

    private GoldberryShim(SymbolLookup lookup) {
        this.abiVersion = downcall(lookup, "goldberry_abi_version",
                FunctionDescriptor.of(ValueLayout.JAVA_INT));
        this.layoutTable = downcall(lookup, "goldberry_layout_table",
                FunctionDescriptor.of(ValueLayout.ADDRESS));
        this.layoutCount = downcall(lookup, "goldberry_layout_count",
                FunctionDescriptor.of(ValueLayout.JAVA_INT));
    }

    /// The shim bindings, loading and ABI-checking the library on first call.
    public static GoldberryShim get() {
        return Holder.INSTANCE;
    }

    /// The ABI version reported by the loaded library.
    public int abiVersion() {
        try {
            return (int) abiVersion.invokeExact();
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
            return (MemorySegment) layoutTable.invokeExact();
        } catch (Throwable t) {
            throw new IllegalStateException("goldberry_layout_table() failed", t);
        }
    }

    /// Number of entries in the layout table.
    public int layoutCount() {
        try {
            return (int) layoutCount.invokeExact();
        } catch (Throwable t) {
            throw new IllegalStateException("goldberry_layout_count() failed", t);
        }
    }

    private static GoldberryShim create() {
        var shim = new GoldberryShim(NativeLibrary.get().lookup());
        var version = shim.abiVersion();
        if (version != SUPPORTED_ABI_VERSION) {
            throw new UnsatisfiedLinkError(
                    "libgoldberry reports ABI version " + version + ", but this build of "
                            + "goldberry-natives was written against " + SUPPORTED_ABI_VERSION
                            + ". The Java and native artifacts are mismatched.");
        }
        return shim;
    }

    // Restricted: binding a native function is the point of this class. The
    // descriptor must match the C signature exactly — that is the hand-written
    // obligation ADR-0010 accepts in exchange for a narrow, readable surface.
    @SuppressWarnings("restricted")
    private static MethodHandle downcall(SymbolLookup lookup, String symbol, FunctionDescriptor descriptor) {
        var address = lookup.find(symbol).orElseThrow(() -> new UnsatisfiedLinkError(
                "libgoldberry does not export " + symbol
                        + " — is it listed in natives/src/main/cmake/exports/goldberry.symbols?"));
        return LINKER.downcallHandle(address, descriptor);
    }
}
