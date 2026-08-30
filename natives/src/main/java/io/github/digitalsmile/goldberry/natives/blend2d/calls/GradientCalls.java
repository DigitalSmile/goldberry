package io.github.digitalsmile.goldberry.natives.blend2d.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import io.github.digitalsmile.goldberry.natives.Downcalls;

/// Blend2D’s `BLGradient` — a fill style that is not a colour (ADR-0207).
///
/// Three functions, which is the whole of it: build one with its geometry, add
/// each stop, release it. The stops are added rather than handed over as an
/// array because `bl_gradient_init_as` takes a `const BLGradientStop*` and a
/// count, and that array would be the one struct in this module whose *contents*
/// Blend2D reads field by field — a `BLRgba64` per stop, four `uint16_t` in an
/// order the header does not promise. `add_stop_rgba32` takes the same
/// `0xAARRGGBB` every other call on the context does and converts it itself.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record GradientCalls(
        GradientInitAs gradientInitAs, GradientDestroy gradientDestroy, GradientAddStopRgba32 gradientAddStopRgba32) {

    /// Binds every function above, failing if the library exports none of them.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static GradientCalls bind(SymbolLookup lookup) {
        return new GradientCalls(
                new GradientInitAs(lookup), new GradientDestroy(lookup), new GradientAddStopRgba32(lookup));
    }

    /// Constructs a gradient of `type` with the geometry in `values`.
    ///
    /// **`values` is a `const void*` whose shape the `type` implies**, which is
    /// the unchecked cast in this file: four doubles for a linear gradient, six
    /// for a radial. Handing over the wrong one reads whatever is next in the
    /// arena and returns `BL_SUCCESS`, which is why
    /// [io.github.digitalsmile.goldberry.natives.blend2d.BlendGradient] builds
    /// only the linear form and the layout registry carries a row for its
    /// values.
    ///
    /// `int bl_gradient_init_as(void*, int, const void*, int, const void*,`
    /// `size_t, const void*)`
    ///
    /// @param gradient an uninitialised `BLGradientCore` to take over
    /// @param type a `BLGradientType`
    /// @param values the geometry that type expects, as doubles
    /// @param extendMode a `BLExtendMode` — what happens outside the two ends
    /// @param stops a `const BLGradientStop*`, or NULL to add them afterwards
    /// @param stopCount how many `stops` holds
    /// @param transform a `const BLMatrix2D*` applied to the gradient, or NULL
    public static final class GradientInitAs {

        private static final MethodHandle FD_bl_gradient_init_as = Downcalls.link(
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS));

        private final MemorySegment address;

        GradientInitAs(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_gradient_init_as");
        }

        public int call(
                MemorySegment gradient,
                int type,
                MemorySegment values,
                int extendMode,
                MemorySegment stops,
                long stopCount,
                MemorySegment transform) {
            try {
                return (int) FD_bl_gradient_init_as.invokeExact(
                        address, gradient, type, values, extendMode, stops, stopCount, transform);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_gradient_init_as", t);
            }
        }
    }

    /// Releases the gradient.
    ///
    /// `int bl_gradient_destroy(void*)`
    ///
    /// @param gradient the gradient to release
    public static final class GradientDestroy {

        private static final MethodHandle FD_bl_gradient_destroy =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        GradientDestroy(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_gradient_destroy");
        }

        public int call(MemorySegment gradient) {
            try {
                return (int) FD_bl_gradient_destroy.invokeExact(address, gradient);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_gradient_destroy", t);
            }
        }
    }

    /// Adds a stop at `offset` along the gradient.
    ///
    /// `int bl_gradient_add_stop_rgba32(void*, double, unsigned int)`
    ///
    /// @param gradient the gradient to add to
    /// @param offset 0 at the start point, 1 at the end
    /// @param argb a colour as `0xAARRGGBB`, straight alpha — the same packing
    ///        every other drawing call takes
    public static final class GradientAddStopRgba32 {

        private static final MethodHandle FD_bl_gradient_add_stop_rgba32 =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_DOUBLE, JAVA_INT));

        private final MemorySegment address;

        GradientAddStopRgba32(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_gradient_add_stop_rgba32");
        }

        public int call(MemorySegment gradient, double offset, int argb) {
            try {
                return (int) FD_bl_gradient_add_stop_rgba32.invokeExact(address, gradient, offset, argb);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_gradient_add_stop_rgba32", t);
            }
        }
    }
}
