package dev.goldberry.media.ffi;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// The linker FFmpeg's holders share, and the ways a symbol is found and a
/// crossing fails.
///
/// This is `:natives`' `Downcalls` for this module, and the holders under
/// `…media.ffi.calls` follow its idiom exactly. A holder keeps its
/// unbound handle as a `private static final FD_<symbol>` and its address as a
/// field, and has a `call` in ordinary Java types. The handle has to be a
/// constant: in a native image, a handle that is not a compile-time constant is
/// an interpreted lambda form, and that costs a factor of 450 per call.
/// The holders sit in a package of their own so that
/// `--initialize-at-build-time` can name it.
public final class FfmpegDowncalls {

    private static final Linker LINKER = Linker.nativeLinker();

    /// Every descriptor linked so far, for the native-image metadata this module
    /// ships, as `Downcalls` records its own.
    private static final List<FunctionDescriptor> LINKED = Collections.synchronizedList(new ArrayList<>());

    private FfmpegDowncalls() {}

    /// The unbound handle for `descriptor`.
    ///
    /// Restricted: linking a foreign signature is what this class is for. The
    /// obligation that the descriptor matches the C prototype is the holder's.
    /// Each holder writes the prototype in its doc, next to its descriptor.
    @SuppressWarnings("restricted")
    public static MethodHandle link(FunctionDescriptor descriptor) {
        synchronized (LINKED) {
            if (!LINKED.contains(descriptor)) {
                LINKED.add(descriptor);
            }
        }
        return LINKER.downcallHandle(descriptor);
    }

    /// The address of `symbol` in `library`.
    ///
    /// @throws UnsatisfiedLinkError when the library does not export it: an FFmpeg
    ///                              built without a component these bindings need
    public static MemorySegment symbol(SymbolLookup lookup, FfmpegLibrary library, String symbol) {
        return lookup.find(symbol)
                .orElseThrow(() -> new UnsatisfiedLinkError("lib" + library.stem() + " does not export " + symbol
                        + " — was it built by media/src/main/cmake with the pinned configure line?"));
    }

    /// The distinct descriptors linked so far.
    public static List<FunctionDescriptor> linked() {
        synchronized (LINKED) {
            return List.copyOf(LINKED);
        }
    }

    /// What a holder raises when the crossing itself fails, rather than the call
    /// returning an error code.
    public static IllegalStateException failure(String name, Throwable cause) {
        return new IllegalStateException(name + "() failed", cause);
    }
}
