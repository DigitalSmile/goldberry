package dev.goldberry.gpu.render.mapped;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.goldberry.natives.sdl.SdlException;
import dev.goldberry.natives.sdl.gpu.SdlGpuDevice;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTransferUsage;
import dev.goldberry.paint.Frame;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.model.DisplayScale;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.render.model.PixelFormat;

/// The check at device creation: a translucent fill over a mebibyte, painted
/// in heap memory and in a persistently mapped upload buffer in turn, judged by
/// an [InPlaceGate]. Painting in place is worth it only where the device's
/// upload memory reads like heap memory; SDL does not say which memory it
/// picked, so this is how it is learned.
///
/// [Mode] lets a run decide instead: `goldberry.gpu.inPlace=on` or `off`.
public final class InPlaceProbe {

    /// What a run asked for.
    public enum Mode {
        /// Measure, and let the gate decide.
        AUTO,
        /// On, unmeasured.
        ON,
        /// Off, unmeasured.
        OFF;

        /// The mode `value` names, `auto` when it is null, blank or unknown:
        /// a typo should not stop a window opening.
        public static Mode parse(@Nullable String value) {
            if (value == null) {
                return AUTO;
            }
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "on", "true" -> ON;
                case "off", "false" -> OFF;
                default -> AUTO;
            };
        }
    }

    /// The decision, and the measurement it came from when there was one.
    ///
    /// @param on       whether rasters are painted in place on this device
    /// @param mode     what the run asked for
    /// @param measured the gate's verdict, empty when the mode decided
    public record Decision(boolean on, Mode mode, Optional<InPlaceGate.Verdict> measured) {

        /// Checks nothing is missing.
        public Decision {
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(measured, "measured");
        }

        /// For the log: "on (1.02× heap)", "off (asked)", "off (the check
        /// failed)".
        public String describe() {
            var why = measured.map(InPlaceGate.Verdict::describe)
                    .orElse(mode == Mode.AUTO ? "the check failed" : "asked");
            return (on ? "on" : "off") + " (" + why + ")";
        }
    }

    private static final PhysicalSize SIZE = new PhysicalSize(512, 512);
    private static final DisplayScale SCALE = new DisplayScale(1.0f);

    private InPlaceProbe() {}

    /// Decides for `device` as `mode` says, measuring under `gate` when it is
    /// [Mode#AUTO]. A measurement SDL refuses the memory for is off: the
    /// staged path needs none of it.
    public static Decision decide(SdlGpuDevice device, Mode mode, InPlaceGate gate) {
        return switch (mode) {
            case ON -> new Decision(true, mode, Optional.empty());
            case OFF -> new Decision(false, mode, Optional.empty());
            case AUTO -> {
                try {
                    var verdict = measure(device, gate);
                    yield new Decision(verdict.on(), mode, Optional.of(verdict));
                } catch (SdlException e) {
                    yield new Decision(false, mode, Optional.empty());
                }
            }
        };
    }

    /// Times the fill `gate.pairs()` times each way, heap and mapped in turn so
    /// a clock that drifts lands on both, after one pair that warms up and is
    /// discarded.
    static InPlaceGate.Verdict measure(SdlGpuDevice device, InPlaceGate gate) {
        var heap = PixelBuffer.allocate(SIZE, PixelFormat.BGRA32_PREMULTIPLIED);
        var stride = heap.stride();
        try (var buffer = device.createTransferBuffer(SdlGpuTransferUsage.UPLOAD, stride * SIZE.height())) {
            var mapped = new PixelBuffer(SIZE, PixelFormat.BGRA32_PREMULTIPLIED, stride, buffer.mapPersistently());
            var onHeap = new long[gate.pairs()];
            var inMapped = new long[gate.pairs()];
            for (var i = -1; i < gate.pairs(); i++) {
                var a = fill(heap);
                var b = fill(mapped);
                if (i >= 0) {
                    onHeap[i] = a;
                    inMapped[i] = b;
                }
            }
            return gate.decide(onHeap, inMapped);
        }
    }

    /// One translucent fill over `buffer`, synchronous, in nanoseconds: it reads
    /// every pixel it writes.
    private static long fill(PixelBuffer buffer) {
        var started = System.nanoTime();
        var frame = Frame.over(buffer, SCALE, 0);
        try {
            frame.fillRect(0, 0, SIZE.width(), SIZE.height(), 0x80FFFFFF);
        } finally {
            frame.end();
        }
        return System.nanoTime() - started;
    }
}
