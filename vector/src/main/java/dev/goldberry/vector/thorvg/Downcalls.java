package dev.goldberry.vector.thorvg;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// The linker the vector bindings share, and the ways a symbol is found and a
/// crossing fails.
///
/// This is `:media`'s `Downcalls` for this module, and the holders under
/// `…thorvg.calls` follow its idiom exactly: a holder keeps its unbound handle
/// as a `private static final FD_<symbol>`, because in a native image a handle
/// that is not a compile-time constant is an interpreted lambda form.
///
/// Every descriptor linked is recorded, for the native-image metadata this
/// module ships: [VectorDescriptors] initialises every holder and reads them
/// back, so a foreign call is registered because it exists, not because a
/// traced run reached it.
public final class Downcalls {

    private static final Linker LINKER = Linker.nativeLinker();

    /// Every descriptor linked so far, in order and without repeats.
    private static final List<FunctionDescriptor> LINKED = Collections.synchronizedList(new ArrayList<>());

    private Downcalls() {}

    /// The unbound handle for `descriptor`.
    ///
    /// Restricted: linking a foreign signature is what this class is for. The
    /// obligation that the descriptor matches the C prototype is the holder's,
    /// and each holder writes the prototype in its doc.
    @SuppressWarnings("restricted")
    public static MethodHandle link(FunctionDescriptor descriptor) {
        synchronized (LINKED) {
            if (!LINKED.contains(descriptor)) {
                LINKED.add(descriptor);
            }
        }
        return LINKER.downcallHandle(descriptor);
    }

    /// The distinct descriptors linked so far: complete once every holder has
    /// been initialised, which [VectorDescriptors#downcalls] does first.
    public static List<FunctionDescriptor> linked() {
        synchronized (LINKED) {
            return List.copyOf(LINKED);
        }
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
