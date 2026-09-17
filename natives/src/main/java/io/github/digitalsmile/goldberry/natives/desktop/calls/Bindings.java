package io.github.digitalsmile.goldberry.natives.desktop.calls;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/// The crossing, for the libraries that are **not** `libgoldberry`.
///
/// [io.github.digitalsmile.goldberry.natives.Downcalls] is the toolkit's own
/// binding machinery, and it is right about `libgoldberry` in ways that are
/// wrong here: its "symbol is missing" failure names the export list, and every
/// descriptor it links is recorded for the native image's metadata. Neither
/// applies to a system library that is `dlopen`ed by name and may not be there
/// at all ([ADR-0383]).
///
/// So this is the same three lines with the opposite failure policy: **a missing
/// symbol is a missing feature**, and a crossing that throws is caught by the
/// caller and answered as "the desktop does not say".
final class Bindings {

    private static final Linker LINKER = Linker.nativeLinker();

    private Bindings() {}

    /// A handle for `descriptor`.
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
