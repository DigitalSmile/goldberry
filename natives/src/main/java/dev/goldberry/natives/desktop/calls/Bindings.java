package dev.goldberry.natives.desktop.calls;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import dev.goldberry.natives.Downcalls;

/// The crossing, for the libraries that are **not** `libgoldberry`.
///
/// [dev.goldberry.natives.Downcalls] is the toolkit's own
/// binding machinery, and its failure policy is right about `libgoldberry` and
/// wrong here: its "symbol is missing" failure names the export list, which has
/// nothing to say about a system library that is `dlopen`ed by name and may not
/// be there at all.
///
/// So this is the same three lines with the opposite failure policy: **a missing
/// symbol is a missing feature**, and a crossing that throws is caught by the
/// caller and answered as "the desktop does not say".
///
/// What it keeps from `Downcalls` is the metadata: an image still has to be told
/// every shape it may cross, and whether the library is present on the machine
/// that *builds* it has nothing to do with whether it is present on the machine
/// that *runs* it. The shapes are declared through [#describe] on a constant,
/// so they are recorded wherever the image is built, and linked here.
final class Bindings {

    private static final Linker LINKER = Linker.nativeLinker();

    private Bindings() {}

    /// Declares `descriptor` as a shape this package may cross, for the native
    /// image's metadata, and hands it straight back.
    ///
    /// Called from a `static final` on the holder rather than from the binding
    /// that uses it: the binding runs only where the library is, and the whole
    /// point is to record the shape where it cannot be missed.
    static FunctionDescriptor describe(FunctionDescriptor descriptor) {
        return Downcalls.describe(descriptor);
    }

    /// A handle for `descriptor`, which is expected to be one [#describe] has
    /// already been given.
    @SuppressWarnings("restricted")
    static MethodHandle link(FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(descriptor);
    }

    /// The address of `symbol`.
    ///
    /// @throws UnsatisfiedLinkError when this library does not export it, which
    ///         is how a binding set gives up on a library it does not recognise
    static MemorySegment symbol(SymbolLookup lookup, String symbol) {
        return lookup.find(symbol)
                .orElseThrow(() -> new UnsatisfiedLinkError("this library does not export " + symbol));
    }

    /// Calls a function that returns a pointer.
    static MemorySegment address(MethodHandle handle, MemorySegment function, Object... arguments) {
        try {
            return (MemorySegment) handle.invokeWithArguments(prepend(function, arguments));
        } catch (Throwable e) {
            throw new IllegalStateException("a desktop call failed", e);
        }
    }

    /// Calls a function that returns an `int`.
    static int integer(MethodHandle handle, MemorySegment function, Object... arguments) {
        try {
            return (int) handle.invokeWithArguments(prepend(function, arguments));
        } catch (Throwable e) {
            throw new IllegalStateException("a desktop call failed", e);
        }
    }

    /// Calls a function that returns nothing.
    static void nothing(MethodHandle handle, MemorySegment function, Object... arguments) {
        try {
            handle.invokeWithArguments(prepend(function, arguments));
        } catch (Throwable e) {
            throw new IllegalStateException("a desktop call failed", e);
        }
    }

    private static Object[] prepend(MemorySegment function, Object... arguments) {
        var all = new Object[arguments.length + 1];
        all[0] = function;
        System.arraycopy(arguments, 0, all, 1, arguments.length);
        return all;
    }
}
