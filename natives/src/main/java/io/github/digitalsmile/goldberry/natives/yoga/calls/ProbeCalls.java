package io.github.digitalsmile.goldberry.natives.yoga.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import io.github.digitalsmile.goldberry.natives.Downcalls;

/// The measure probe, which calls a Yoga measure function from C.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record ProbeCalls(ProbeMeasure probeMeasure) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static ProbeCalls bind(SymbolLookup lookup) {
        return new ProbeCalls(new ProbeMeasure(lookup));
    }

    /// Invokes a Yoga measure callback from C and writes back what it returned.
    ///
    /// The one shim function that exists to be called from a *test*: it is how
    /// the struct-by-value upcall is proved without running a layout pass
    /// (ADR-0017). `YGSize` is returned by value across the boundary, which is
    /// the part that has to be proved on every target.
    ///
    /// `void goldberry_probe_measure(void*, float, int, float, int, void*, void*)`
    ///
    /// @param measureFunc a `YGMeasureFunc` upcall stub to invoke
    /// @param width the width constraint to pass it
    /// @param widthMode a `YGMeasureMode`
    /// @param height the height constraint to pass it
    /// @param heightMode a `YGMeasureMode`
    /// @param outWidth a caller-allocated `float*` for the measured width
    /// @param outHeight a caller-allocated `float*` for the measured height
    public static final class ProbeMeasure {

        private static final MethodHandle FD_goldberry_probe_measure = Downcalls.link(
                FunctionDescriptor.ofVoid(ADDRESS, JAVA_FLOAT, JAVA_INT, JAVA_FLOAT, JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        ProbeMeasure(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_probe_measure");
        }

        public void call(
                MemorySegment measureFunc,
                float width,
                int widthMode,
                float height,
                int heightMode,
                MemorySegment outWidth,
                MemorySegment outHeight) {
            try {
                FD_goldberry_probe_measure.invokeExact(
                        address, measureFunc, width, widthMode, height, heightMode, outWidth, outHeight);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_probe_measure", t);
            }
        }
    }
}
