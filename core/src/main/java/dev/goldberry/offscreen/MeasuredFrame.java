package dev.goldberry.offscreen;

import java.util.List;
import java.util.Objects;

import dev.goldberry.render.DamageRect;
import dev.goldberry.stats.FrameRecord;

/// One frame of a [MeasuringStrip]: what each stage cost and what was painted.
///
/// ```java
/// var frame = meter.frame();
/// if (frame.isQuiet()) {
///     // nothing changed, and nothing was rasterized
/// }
/// long rasterNanos = frame.timings().rasterNanos();
/// ```
///
/// @param timings the frame's stages, in the shape a window's frame listener
///                hears them. The present readings are zero: a strip has no
///                present
/// @param damage  the regions painted again, in physical pixels; the whole
///                buffer for the first frame, empty when nothing changed
public record MeasuredFrame(FrameRecord timings, List<DamageRect> damage) {

    public MeasuredFrame {
        Objects.requireNonNull(timings, "timings");
        damage = List.copyOf(damage);
    }

    /// How many pixels were painted again: the damaged rectangles' areas,
    /// summed. The rectangles never overlap, because the render tree merges
    /// any two that do.
    public long damagedPixels() {
        var area = 0L;
        for (var rect : damage) {
            area += (long) rect.width() * rect.height();
        }
        return area;
    }

    /// Whether nothing changed, so nothing was rasterized.
    public boolean isQuiet() {
        return damage.isEmpty();
    }
}
