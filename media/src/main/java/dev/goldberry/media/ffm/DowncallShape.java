package dev.goldberry.media.ffm;

import java.lang.foreign.FunctionDescriptor;
import java.util.Objects;
import java.util.OptionalInt;

/// A downcall as it is linked: its descriptor, and where its `...` begins when
/// the C function is variadic.
///
/// A native image registers the two together. A variadic call linked with
/// `Linker.Option.firstVariadicArg` is a different stub from a plain call of
/// the same descriptor, and an image that has only the plain one refuses the
/// variadic one at the first link.
///
/// @param descriptor    the descriptor, variadic arguments included
/// @param firstVariadic the index of the first variadic argument, which may be
///                      the argument count when a call passes none; empty for a
///                      function that is not variadic
public record DowncallShape(FunctionDescriptor descriptor, OptionalInt firstVariadic) {

    /// Checks the variadic index falls within the arguments.
    public DowncallShape {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(firstVariadic, "firstVariadic");
        if (firstVariadic.isPresent()
                && (firstVariadic.getAsInt() < 0
                        || firstVariadic.getAsInt()
                                > descriptor.argumentLayouts().size())) {
            throw new IllegalArgumentException("first variadic argument " + firstVariadic.getAsInt() + " of "
                    + descriptor.argumentLayouts().size());
        }
    }

    /// A function that is not variadic.
    public static DowncallShape of(FunctionDescriptor descriptor) {
        return new DowncallShape(descriptor, OptionalInt.empty());
    }

    /// A variadic function, whose arguments from `firstVariadic` on are the `...`.
    public static DowncallShape variadic(FunctionDescriptor descriptor, int firstVariadic) {
        return new DowncallShape(descriptor, OptionalInt.of(firstVariadic));
    }
}
