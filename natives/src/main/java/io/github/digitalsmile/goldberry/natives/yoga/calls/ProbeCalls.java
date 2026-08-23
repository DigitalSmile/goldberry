package io.github.digitalsmile.goldberry.natives.yoga.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import io.github.digitalsmile.goldberry.natives.Downcalls;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/// The one shim function that exists to be *called from a test*.
///
/// `goldberry_probe_measure` invokes a `YGMeasureFunc` from C, which is how
/// the struct-by-value upcall is proved without a layout pass
/// ([ADR-0017](../../../../../../../../book/src/adr/0017-proving-the-struct-by-value-upcall.md)).
///
/// See [io.github.digitalsmile.goldberry.natives.calls] for why the holder
/// lives in a package of its own.
public record ProbeCalls(
        ProbeMeasure probeMeasure) {

    /// Binds every function above.
    public static ProbeCalls bind(SymbolLookup lookup) {
        return new ProbeCalls(
                new ProbeMeasure(lookup));
    }

    /// `void goldberry_probe_measure(void*, float, int, float, int, void*, void*)`
    public static final class ProbeMeasure {

        private static final MethodHandle FD_goldberry_probe_measure =
                Downcalls.link(FunctionDescriptor.ofVoid(
                        ADDRESS, JAVA_FLOAT, JAVA_INT, JAVA_FLOAT, JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        ProbeMeasure(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_probe_measure");
        }

        public void call(
            MemorySegment a1, float a2, int a3, float a4, int a5, MemorySegment a6,
            MemorySegment a7) {
            try {
                FD_goldberry_probe_measure.invokeExact(address, a1, a2, a3, a4, a5, a6, a7);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_probe_measure", t);
            }
        }
    }
}
