package io.github.digitalsmile.goldberry.natives.blend2d;

import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import io.github.digitalsmile.goldberry.natives.blend2d.calls.GradientCalls;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendExtendMode;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendGradientType;
import io.github.digitalsmile.goldberry.natives.blend2d.error.BlendException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;

/// Blend2D's gradient calls, behind [BlendGradient] (ADR-0207).
///
/// Package-private for [Blend2dPath]'s reason: the wrapper owns the handle and
/// knows whether the gradient has been released.
final class Blend2dGradient {

    private static final class Holder {
        private static final Blend2dGradient INSTANCE =
                new Blend2dGradient(NativeLibrary.get().lookup());
    }

    private final GradientCalls calls;

    private Blend2dGradient(SymbolLookup lookup) {
        this.calls = GradientCalls.bind(lookup);
    }

    static Blend2dGradient get() {
        return Holder.INSTANCE;
    }

    /// Constructs a **linear** gradient over the four doubles in `values`.
    ///
    /// Restricted to one type for [Blend2dContext#contextTransform]'s reason:
    /// the operand crosses as `const void*` and the type is what says how much
    /// of it to read, so a method that took any [BlendGradientType] would let a
    /// radial gradient read six doubles out of a four-double allocation and
    /// report success. A second shape here is a second method, with its own
    /// layout row beside it.
    ///
    /// No stops and no transform: `NULL` for both, because
    /// [#gradientAddStop] is how stops arrive and nothing has wanted a gradient
    /// with a matrix of its own.
    void gradientInitLinear(
            MemorySegment gradient, MemorySegment values, BlendExtendMode extendMode) {
        check("bl_gradient_init_as", calls.gradientInitAs().call(
                gradient, BlendGradientType.LINEAR.nativeValue(), values,
                extendMode.nativeValue(), MemorySegment.NULL, 0, MemorySegment.NULL));
    }

    void gradientDestroy(MemorySegment gradient) {
        check("bl_gradient_destroy", calls.gradientDestroy().call(gradient));
    }

    /// Adds a stop at `offset`, with a straight-alpha `0xAARRGGBB`.
    ///
    /// Not premultiplied, like every other colour crossing this boundary:
    /// Blend2D premultiplies stops itself when it builds the ramp, and a caller
    /// that did it first would get a fade that darkens toward the transparent
    /// end rather than one that thins out.
    void gradientAddStop(MemorySegment gradient, double offset, int argb) {
        check("bl_gradient_add_stop_rgba32",
                calls.gradientAddStopRgba32().call(gradient, offset, argb));
    }

    /// A `BLResult` that is not `BL_SUCCESS` is the call reporting a problem, not
    /// the crossing failing -- so it is raised as a [BlendException] naming the
    /// operation, and not as the [IllegalStateException] a holder raises.
    private static void check(String operation, int result) {
        if (result != 0) {
            throw new BlendException(operation, result);
        }
    }
}
