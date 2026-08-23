package io.github.digitalsmile.goldberry.natives.yoga;

import io.github.digitalsmile.goldberry.natives.yoga.calls.ProbeCalls;
import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import io.github.digitalsmile.goldberry.natives.yoga.measure.MeasureMode;
import io.github.digitalsmile.goldberry.natives.yoga.measure.MeasuredSize;

/// Calls a [MeasureCallback] from C and reports what arrived.
///
/// The only way to prove a struct-by-value upcall return is correct is to have
/// code compiled by the target's own C compiler receive the struct and say what
/// it got. `goldberry_probe_measure` is that code; this is its binding.
///
/// It exists for the check in ADR-0017, not for the layout engine — Yoga will
/// call the stub itself once the node API is bound. It stays in main sources
/// rather than test sources for the same reason
/// [io.github.digitalsmile.goldberry.natives.layout.LayoutProbe] does: it is a
/// binding to a symbol the library exports, and the export list and the bindings
/// are checked against each other.
public final class MeasureProbe {

    /// ```c
    /// void goldberry_probe_measure(YGMeasureFunc measure,
    ///                              float width, int width_mode,
    ///                              float height, int height_mode,
    ///                              float *out_width, float *out_height);
    /// ```
    ///
    /// — which is [ProbeCalls.ProbeMeasure], the holder the call below names.
    private static final class Holder {
        private static final ProbeCalls CALLS = ProbeCalls.bind(NativeLibrary.get().lookup());
    }

    private MeasureProbe() {
    }

    /// Invokes `callback` through C under the given constraints and returns the
    /// `YGSize` that survived the crossing.
    ///
    /// @throws IllegalStateException if the callback failed, wrapping its cause
    public static MeasuredSize measure(
            MeasureCallback callback,
            float width,
            MeasureMode widthMode,
            float height,
            MeasureMode heightMode) {

        float measuredWidth;
        float measuredHeight;
        try (var arena = Arena.ofConfined()) {
            var out = arena.allocate(ValueLayout.JAVA_FLOAT, 2);
            // Sentinels: if C writes nothing -- a null guard tripping, say -- the
            // assertion fails on these rather than on a plausible zero.
            out.setAtIndex(ValueLayout.JAVA_FLOAT, 0, Float.MIN_VALUE);
            out.setAtIndex(ValueLayout.JAVA_FLOAT, 1, Float.MIN_VALUE);

            var outWidth = out.asSlice(0, ValueLayout.JAVA_FLOAT.byteSize());
            var outHeight = out.asSlice(ValueLayout.JAVA_FLOAT.byteSize(), ValueLayout.JAVA_FLOAT.byteSize());

            Holder.CALLS.probeMeasure().call(
                    callback.pointer(),
                    width,
                    widthMode.nativeValue(),
                    height,
                    heightMode.nativeValue(),
                    outWidth,
                    outHeight);

            measuredWidth = out.getAtIndex(ValueLayout.JAVA_FLOAT, 0);
            measuredHeight = out.getAtIndex(ValueLayout.JAVA_FLOAT, 1);
        }

        // Before the values are trusted: if the Java side threw, what C received
        // was the zero this reports on failure, not a measurement.
        callback.throwIfFailed();

        return new MeasuredSize(measuredWidth, measuredHeight);
    }
}
