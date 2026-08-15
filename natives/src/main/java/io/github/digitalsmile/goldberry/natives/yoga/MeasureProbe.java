package io.github.digitalsmile.goldberry.natives.yoga;

import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

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

    private static final Linker LINKER = Linker.nativeLinker();

    /// ```c
    /// void goldberry_probe_measure(YGMeasureFunc measure,
    ///                              float width, int width_mode,
    ///                              float height, int height_mode,
    ///                              float *out_width, float *out_height);
    /// ```
    private static final FunctionDescriptor DESCRIPTOR = FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_FLOAT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_FLOAT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS);

    private static final class Holder {
        private static final MethodHandle PROBE = downcall(NativeLibrary.get().lookup());
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

            try {
                Holder.PROBE.invokeExact(
                        callback.pointer(),
                        width,
                        widthMode.nativeValue(),
                        height,
                        heightMode.nativeValue(),
                        outWidth,
                        outHeight);
            } catch (Throwable t) {
                throw new IllegalStateException("goldberry_probe_measure() failed", t);
            }

            measuredWidth = out.getAtIndex(ValueLayout.JAVA_FLOAT, 0);
            measuredHeight = out.getAtIndex(ValueLayout.JAVA_FLOAT, 1);
        }

        // Before the values are trusted: if the Java side threw, what C received
        // was the zero this reports on failure, not a measurement.
        callback.throwIfFailed();

        return new MeasuredSize(measuredWidth, measuredHeight);
    }

    // Restricted: see GoldberryShim.downcall -- same obligation, same reason.
    @SuppressWarnings("restricted")
    private static MethodHandle downcall(SymbolLookup lookup) {
        var address = lookup.find("goldberry_probe_measure").orElseThrow(() -> new UnsatisfiedLinkError(
                "libgoldberry does not export goldberry_probe_measure"
                        + " — is it listed in natives/src/main/cmake/exports/goldberry.symbols?"));
        return LINKER.downcallHandle(address, DESCRIPTOR);
    }
}
