package io.github.digitalsmile.goldberry.natives.blend2d;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;

import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import io.github.digitalsmile.goldberry.natives.blend2d.calls.GradientCalls;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendExtendMode;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendGradientType;
import io.github.digitalsmile.goldberry.natives.blend2d.error.BlendException;

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
    /// One method per shape, for [Blend2dContext#contextTransform]'s reason:
    /// the operand crosses as `const void*` and the type is what says how much
    /// of it to read, so a method that took any [BlendGradientType] would let a
    /// radial gradient read six doubles out of a four-double allocation and
    /// report success. Each shape has its own method and its own layout row.
    ///
    /// No stops: `NULL`, because [#gradientAddStop] is how stops arrive.
    ///
    /// @param transform a `BLMatrix2D` placing the gradient in user space, or
    ///        `NULL` for none
    void gradientInitLinear(
            MemorySegment gradient, MemorySegment values, BlendExtendMode extendMode, MemorySegment transform) {
        init(gradient, BlendGradientType.LINEAR, values, extendMode, transform);
    }

    /// Constructs a **radial** gradient over the six doubles in `values` — a
    /// `BLRadialGradientValues`, and nothing smaller (ADR-0456).
    void gradientInitRadial(
            MemorySegment gradient, MemorySegment values, BlendExtendMode extendMode, MemorySegment transform) {
        init(gradient, BlendGradientType.RADIAL, values, extendMode, transform);
    }

    /// Constructs a **conic** gradient over the four doubles in `values` — a
    /// `BLConicGradientValues`, whose four doubles mean something else entirely
    /// from a linear gradient's (ADR-0456).
    void gradientInitConic(
            MemorySegment gradient, MemorySegment values, BlendExtendMode extendMode, MemorySegment transform) {
        init(gradient, BlendGradientType.CONIC, values, extendMode, transform);
    }

    private void init(
            MemorySegment gradient,
            BlendGradientType type,
            MemorySegment values,
            BlendExtendMode extendMode,
            MemorySegment transform) {
        check(
                "bl_gradient_init_as",
                calls.gradientInitAs()
                        .call(
                                gradient,
                                type.nativeValue(),
                                values,
                                extendMode.nativeValue(),
                                MemorySegment.NULL,
                                0,
                                transform));
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
        check("bl_gradient_add_stop_rgba32", calls.gradientAddStopRgba32().call(gradient, offset, argb));
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
