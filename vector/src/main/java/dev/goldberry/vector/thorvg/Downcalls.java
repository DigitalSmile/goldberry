package dev.goldberry.vector.thorvg;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/// The linker the vector bindings share, and the ways a symbol is found and a
/// crossing fails.
///
/// This is `:media`'s `Downcalls` for this module, and the holders under
/// `…thorvg.calls` follow its idiom exactly: a holder keeps its unbound handle
/// as a `private static final FD_<symbol>`, because in a native image a handle
/// that is not a compile-time constant is an interpreted lambda form.
public final class Downcalls {

    private static final Linker LINKER = Linker.nativeLinker();

    private Downcalls() {}

    /// The unbound handle for `descriptor`.
    ///
    /// Restricted: linking a foreign signature is what this class is for. The
    /// obligation that the descriptor matches the C prototype is the holder's,
    /// and each holder writes the prototype in its doc.
    @SuppressWarnings("restricted")
    public static MethodHandle link(FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(descriptor);
    }

    /// The address of `symbol` in the library.
    ///
    /// @throws UnsatisfiedLinkError when the library does not export it
    public static MemorySegment symbol(SymbolLookup lookup, String symbol) {
        return lookup.find(symbol)
                .orElseThrow(() -> new UnsatisfiedLinkError(VectorPlatform.LIBRARY_STEM + " does not export " + symbol
                        + " — was it built by vector/src/main/cmake with exports/goldberry-vector.symbols?"));
    }

    /// What a holder raises when the crossing itself fails, rather than the call
    /// returning a result code.
    public static IllegalStateException failure(String name, Throwable cause) {
        return new IllegalStateException(name + "() failed", cause);
    }
}
