package io.github.digitalsmile.goldberry.natives;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/// The linker every binding shares, and the two ways a symbol is looked up.
///
/// What used to be here was a table of fifty-six `MethodHandle` constants named
/// for their C signature, which a hundred and thirty-four bindings called through
/// by passing an address they kept separately. That worked and read badly: a call
/// site named a shape rather than a function, the function's name travelled
/// beside it as a string for the failure message, and every binding class grew a
/// set of `call`/`invoke`/`callBoolean` helpers to keep the `try`/`catch` in one
/// place.
///
/// A function is now a **holder**
/// ([ADR-0173](../../../../../../book/src/adr/0173-a-bound-function-is-a-holder-and-its-handle-is-a-constant.md)):
/// its handle, its address, and a `call` with ordinary Java argument types.
///
/// ```java
/// // before
/// this.contextEnd = Downcalls.symbol(lookup, "bl_context_end");
/// check("bl_context_end", (int) Downcalls.INT__PTR.invokeExact(contextEnd, context));
///
/// // after
/// check("bl_context_end", calls.contextEnd().call(context));
/// ```
///
/// What survives here is what all of them share: one [Linker], one way to fail
/// when a symbol is missing, and one way to fail when the crossing does. The
/// rest of this note is what a holder is and why it is shaped that way, because
/// this is the class every one of them calls.
///
/// ## What a holder is
///
/// It pairs the one thing that is known while the image is being built — the
/// signature — with the one thing that cannot be: the address, which exists only
/// once `libgoldberry` has been `dlopen`ed. The signature is a
/// `private static final MethodHandle FD_<symbol>`; the address is a
/// `private final MemorySegment`; and `call` is an ordinary Java method with
/// ordinary Java argument types, so the `invokeExact`, the cast of its result and
/// the `try`/`catch` that names the function in the failure all live in one place
/// instead of at each of the two hundred and eighty call sites.
///
/// Each holder carries its **C prototype** in its doc, in C's words rather than
/// Java's: `_Bool` is one byte and not the four an `int` would take, `int64_t`
/// is what `JAVA_LONG` carries, and `void*` is any pointer. That line and the
/// `call` under it are two statements of one signature — one in layouts, one in
/// Java types — and `HolderShapeTest` is what checks they agree.
///
/// ## One record per subject, not per library
///
/// A library's functions are grouped by what they act on rather than by which
/// `.so` they came from: `ImageCalls`, `ContextCalls`, `PathCalls` and
/// `FontCalls` rather than one `Blend2DCalls` of forty-six; `ConfigCalls`,
/// `NodeCalls`, `StyleCalls` and `LayoutCalls` rather than one `YogaCalls`.
/// Each is the surface of one object, so the binding class that holds it is the
/// surface of one object too — `Blend2dContext` holds `ContextCalls`, and
/// `BlendContext` is the wrapper over both.
///
/// ## Why the holders are in packages of their own
///
/// Because of the flag. ADR-0161 measured that a downcall handle is 450× slower
/// in a native image unless it is a **compile-time constant**, which it only is
/// if its class was initialised while the image was being built. `:natives`
/// therefore ships `--initialize-at-build-time`, and what that flag can name is
/// the constraint:
///
/// | flag                                | holder                          | ns/call |
/// |-------------------------------------|---------------------------------|---------|
/// | `--initialize-at-build-time=Outer`  | `static final` on `Outer`       | 10.55   |
/// | `--initialize-at-build-time=Outer`  | `static final` on `Outer$Nested`| 4537.82 |
/// | `--initialize-at-build-time=Outer,Outer$Nested` | same nested class    | 11.25   |
/// | `--initialize-at-build-time=<package>` | nested, anywhere in it       | 8.07    |
/// | any                                 | instance field of a record      | 4539.53 |
///
/// Measured on GraalVM CE 25.2.4 by the probe in ADR-0173, twenty million calls
/// to `goldberry_abi_version`. **Naming the enclosing class is not enough** — the
/// second row is the trap, and it is silent: the image builds, runs and paints
/// correctly, at a fortieth of the speed.
///
/// Naming a hundred and thirty-four nested classes in a build flag is not a
/// maintainable list, and naming the *binding* packages instead would build-time
/// initialise `Sdl` and `Blend2D`, whose holder idiom `dlopen`s the library — in
/// the builder, which is the wrong process. So the holders get `…calls` packages
/// that contain nothing else, and the flag names those. It is safe by
/// construction: a holder added tomorrow is covered by the package that already
/// exists.
///
/// The last row is why the handle is not simply a field of the pair, which is the
/// design this one replaced before it was measured.
public final class Downcalls {

    private static final Linker LINKER = Linker.nativeLinker();

    private Downcalls() {
    }

    /// The address of `symbol`, for a holder to keep.
    ///
    /// The failure is the one every binding used to raise for itself, in the
    /// same words: a symbol missing from `libgoldberry` is an export list that is
    /// wrong, and the message names the file to fix.
    ///
    /// @throws UnsatisfiedLinkError if the library does not export it
    public static MemorySegment symbol(SymbolLookup lookup, String symbol) {
        return lookup.find(symbol).orElseThrow(() -> new UnsatisfiedLinkError(
                "libgoldberry does not export " + symbol
                        + " — is it listed in natives/src/main/cmake/exports/goldberry.symbols?"));
    }

    /// The address of `symbol`, or null when this library does not export it.
    ///
    /// For the handful of calls the toolkit can do without — see
    /// [io.github.digitalsmile.goldberry.natives.sdl.SdlVideo]'s display-mode
    /// pair, which feed the frame pacer and have a defined answer for "the
    /// platform will not say".
    public static MemorySegment optionalSymbol(SymbolLookup lookup, String symbol) {
        return lookup.find(symbol).orElse(null);
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
    /// the Java types, and ADR-0010 accepted that obligation.
    @SuppressWarnings("restricted")
    public static MethodHandle link(FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(descriptor);
    }

    /// What a binding raises when the crossing itself fails.
    ///
    /// Not a bad result code -- that is the caller's to check -- but the call not
    /// happening: a signature that does not match the stub, or a handle that could
    /// not be linked. Every binding class used to spell this for itself, in these
    /// words.
    public static IllegalStateException failure(String name, Throwable cause) {
        return new IllegalStateException(name + "() failed", cause);
    }
}
