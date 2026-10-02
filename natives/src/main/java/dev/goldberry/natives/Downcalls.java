package dev.goldberry.natives;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// The linker every binding shares, and the two ways a symbol is looked up.
///
/// A bound C function is a **holder**: a small final class that pairs the one
/// thing known while a native image is built — the signature, a
/// `private static final MethodHandle FD_<symbol>` made by [#link] — with the one
/// thing that cannot be known until `libgoldberry` is loaded: the address, a
/// `private final MemorySegment` from [#symbol]. Its `call` takes ordinary Java
/// argument types, so the `invokeExact`, the cast of the result and the
/// `try`/`catch` that names the function in [#failure] live in one place.
///
/// ```java
/// check("bl_context_end", calls.contextEnd().call(context));
/// ```
///
/// Each holder carries its C prototype in its doc, in C's words: `_Bool` is one
/// byte, `int64_t` is what `JAVA_LONG` carries, `void*` is any pointer. That line
/// and the `call` under it are two statements of one signature, and
/// `HolderShapeTest` checks they agree.
///
/// Holders are grouped by what they act on, not by library: `ImageCalls`,
/// `ContextCalls`, `PathCalls` and `FontCalls` rather than one `Blend2DCalls`.
/// Each record is the surface of one object, and the binding class that holds it
/// is the Java surface of the same object.
///
/// The holders live in `…calls` packages that contain nothing else because of a
/// native-image flag. A downcall handle is about 450 times slower in an image
/// unless it is a compile-time constant, which it is only when its class was
/// initialised while the image was built. `:natives` therefore ships
/// `--initialize-at-build-time` naming the `…calls` packages. Naming the binding
/// packages instead would initialise `Sdl` and `Blend2D` in the builder, whose
/// singletons `dlopen` the library in the wrong process; naming each nested class
/// is a list nobody could maintain. A package covers a holder added tomorrow by
/// construction.
///
/// | flag                                            | holder                           | ns/call |
/// |-------------------------------------------------|----------------------------------|---------|
/// | `--initialize-at-build-time=Outer`              | `static final` on `Outer`        | 10.55   |
/// | `--initialize-at-build-time=Outer`              | `static final` on `Outer$Nested` | 4537.82 |
/// | `--initialize-at-build-time=Outer,Outer$Nested` | same nested class                | 11.25   |
/// | `--initialize-at-build-time=<package>`          | nested, anywhere in it           | 8.07    |
/// | any                                             | instance field of a record       | 4539.53 |
///
/// Measured on GraalVM CE 25.2.4, twenty million calls to `goldberry_abi_version`.
/// The second row is the trap, and it is silent: the image builds, runs and
/// paints correctly, at a fortieth of the speed. The last row is why the handle
/// is a `static final` and not a field beside the address.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary),
/// [What the flags are for](https://goldberry.dev/docs/native.html#what-the-flags-are-for).
public final class Downcalls {

    private static final Linker LINKER = Linker.nativeLinker();

    /// Every descriptor [#link] has been handed, in the order it was handed
    /// them and without repeats. This is the downcall half of what a native
    /// image has to be told before it is built, and the reason it is kept here
    /// rather than traced from a run: a run records the screens it reached, and
    /// this records the holders that exist.
    private static final List<FunctionDescriptor> LINKED = Collections.synchronizedList(new ArrayList<>());

    private Downcalls() {}

    /// The address of `symbol`, for a holder to keep.
    ///
    /// A symbol missing from `libgoldberry` is an export list that is wrong, and
    /// the message names the file to fix.
    ///
    /// @throws UnsatisfiedLinkError if the library does not export it
    public static MemorySegment symbol(SymbolLookup lookup, String symbol) {
        return lookup.find(symbol)
                .orElseThrow(() -> new UnsatisfiedLinkError("libgoldberry does not export " + symbol
                        + " — is it listed in natives/src/main/cmake/exports/goldberry.symbols?"));
    }

    /// The address of `symbol`, or null when this library does not export it.
    ///
    /// For the handful of calls the toolkit can do without — see
    /// [dev.goldberry.natives.sdl.SdlVideo]'s display-mode
    /// pair, which feed the frame pacer and have a defined answer for "the
    /// platform will not say".
    public static MemorySegment optionalSymbol(SymbolLookup lookup, String symbol) {
        return lookup.find(symbol).orElse(null);
    }

    /// Records `descriptor` as a shape some downcall will have, and returns it,
    /// so a `static final` can be declared through this call.
    ///
    /// The twin of [Upcalls#describe], and it exists for the same reason.
    /// [#link] records what it links, which is enough for a holder
    /// against `libgoldberry`: the library is always there, so the holder always
    /// initialises and the shape is always recorded. It is *not* enough for a
    /// system library that may be absent — `libdbus`, `libobjc`, `user32` — where
    /// the descriptor is built inside the binding that only runs on a machine
    /// that has the library. The build machine may not be that machine, and a
    /// shape that was never described is a `MissingForeignRegistrationError` on
    /// one that is.
    ///
    /// So: describe the shape where it can always be reached — a constant on the
    /// holder — and link it where the library is.
    public static FunctionDescriptor describe(FunctionDescriptor descriptor) {
        synchronized (LINKED) {
            if (!LINKED.contains(descriptor)) {
                LINKED.add(descriptor);
            }
        }
        return descriptor;
    }

    /// The unbound handle for `descriptor`, which is what a holder's `FD_…`
    /// constant is.
    ///
    /// Public because the holders are in `…calls` packages of their own, and this
    /// is the one linker they all share.
    ///
    /// Restricted: linking a foreign signature is this class's entire purpose. No
    /// address is named here and none is checked -- the obligation that the
    /// signature matches the C prototype sits on the holder, whose `call` states
    /// the Java types; that obligation is the price of hand-written bindings, and
    /// the layout probe is what keeps it honest.
    @SuppressWarnings("restricted")
    public static MethodHandle link(FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(describe(descriptor));
    }

    /// The distinct descriptors linked so far.
    ///
    /// Complete only once every holder class has been initialised, which is
    /// what `ForeignSurface` does before asking; on an ordinary JVM a holder is
    /// initialised when its `…Calls` record first binds, so this list grows as
    /// the program runs.
    public static List<FunctionDescriptor> linked() {
        synchronized (LINKED) {
            return List.copyOf(LINKED);
        }
    }

    /// What a binding raises when the crossing itself fails.
    ///
    /// Not a bad result code -- that is the caller's to check -- but the call not
    /// happening: a signature that does not match the stub, or a handle that could
    /// not be linked.
    public static IllegalStateException failure(String name, Throwable cause) {
        return new IllegalStateException(name + "() failed", cause);
    }
}
