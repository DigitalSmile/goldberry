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
/// A function is now a **holder**: its handle, its address, and a `call` with
/// ordinary Java argument types. They live in `…calls` packages beside each
/// library — see [io.github.digitalsmile.goldberry.natives.calls], which is also
/// where the reason they are packages rather than nested classes is written down,
/// with the measurements
/// ([ADR-0173](../../../../../../book/src/adr/0173-a-bound-function-is-a-holder-and-its-handle-is-a-constant.md)).
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
/// when a symbol is missing, and one way to fail when the crossing does.
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
    /// Public because the holders are in packages of their own -- see
    /// [io.github.digitalsmile.goldberry.natives.calls] -- and this is the one
    /// linker they all share.
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
