package dev.goldberry.media.ffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// The linker the media bindings share, and the ways a symbol is found and a
/// crossing fails.
///
/// This is `:natives`' `Downcalls` for this module, and the holders under
/// `…media.ffm.calls` follow its idiom exactly. A holder keeps its unbound
/// handle as a `private static final FD_<symbol>` and its address as a field,
/// and has a `call` in ordinary Java types. The handle has to be a constant: in
/// a native image, a handle that is not a compile-time constant is an
/// interpreted lambda form, and that costs a factor of 450 per call. The holders
/// sit in a package of their own so that `--initialize-at-build-time` can name
/// it.
public final class Downcalls {

    private static final Linker LINKER = Linker.nativeLinker();

    /// Every shape linked so far, for the native-image metadata this module
    /// ships, as `Downcalls` records its own.
    private static final List<DowncallShape> LINKED = Collections.synchronizedList(new ArrayList<>());

    private Downcalls() {}

    /// The unbound handle for `descriptor`.
    ///
    /// Restricted: linking a foreign signature is what this class is for. The
    /// obligation that the descriptor matches the C prototype is the holder's.
    /// Each holder writes the prototype in its doc, next to its descriptor.
    @SuppressWarnings("restricted")
    public static MethodHandle link(FunctionDescriptor descriptor) {
        record(DowncallShape.of(descriptor));
        return LINKER.downcallHandle(descriptor);
    }

    /// The unbound handle for a variadic `descriptor` whose arguments from
    /// `firstVariadic` on are the `...`: `opus_decoder_ctl(st, request, ...)`.
    @SuppressWarnings("restricted")
    public static MethodHandle linkVariadic(FunctionDescriptor descriptor, int firstVariadic) {
        record(DowncallShape.variadic(descriptor, firstVariadic));
        return LINKER.downcallHandle(descriptor, Linker.Option.firstVariadicArg(firstVariadic));
    }

    /// `handle` taking a Java `long` at each of `positions`, where its descriptor
    /// named a C `long` ([NativeTypes#C_LONG]). On Linux and macOS that is the
    /// handle as it is; on Windows, where a C `long` is an `int`, the `long` is
    /// narrowed on the way in. A holder for a function with a `long` parameter
    /// then has one Java signature on every target.
    ///
    /// @param positions the handle's parameter indices, counting the address as 0
    public static MethodHandle cLongsAsLongs(MethodHandle handle, int... positions) {
        if (NativeTypes.C_LONG.carrier() == long.class) {
            return handle;
        }
        var type = handle.type();
        for (var position : positions) {
            type = type.changeParameterType(position, long.class);
        }
        return MethodHandles.explicitCastArguments(handle, type);
    }

    private static void record(DowncallShape shape) {
        synchronized (LINKED) {
            if (!LINKED.contains(shape)) {
                LINKED.add(shape);
            }
        }
    }

    /// The address of `symbol` in the library.
    ///
    /// @throws UnsatisfiedLinkError when the library does not export it: a
    ///                              `libgoldberry-media` whose export list lacks it
    public static MemorySegment symbol(SymbolLookup lookup, String symbol) {
        return lookup.find(symbol)
                .orElseThrow(() -> new UnsatisfiedLinkError(MediaPlatform.LIBRARY_STEM + " does not export " + symbol
                        + " — was it built by media/src/main/cmake with exports/goldberry-media.symbols?"));
    }

    /// The distinct shapes linked so far: a variadic link and a plain one of the
    /// same descriptor are two.
    public static List<DowncallShape> linked() {
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
