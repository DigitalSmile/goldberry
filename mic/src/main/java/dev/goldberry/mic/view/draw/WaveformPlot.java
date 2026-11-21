package dev.goldberry.mic.view.draw;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.mic.analysis.Envelope;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.Frame;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// An [Envelope] drawn as a waveform: for each column, a stroke from the
/// lowest sample in it to the highest, about the middle of the box.
///
/// ```java
/// // A voice message, two fifths played.
/// new WaveformPlot(Envelope.of(clip, 1, 64), 0.4, true, Attributes.NONE)
/// ```
///
/// What a `waveform-view` builds, and the same node to a stylesheet. The part
/// already played is drawn in `--gb-mic-wave` and the rest in
/// `--gb-mic-wave-ahead`; a live outline is all played. A column is never
/// thinner than a pixel, so silence still shows as a line. An envelope with
/// a third as many buckets as the box has pixels, or fewer, is drawn a bar
/// a bucket with gaps between, as a voice message's usually is.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
///
/// @param envelope   the outline to draw, oldest bucket on the left
/// @param played     how much of it, from the left, is played: 0 to 1
/// @param normalized whether the loudest bucket fills the height, rather than
///                   full scale
/// @param attributes id, classes and key
public record WaveformPlot(Envelope envelope, double played, boolean normalized, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Attributed<WaveformPlot> {

    /// The colours with no stylesheet: Nord's accent and muted text.
    static final Colours FALLBACK = new Colours(0xFF88C0D0, 0xFF4C566A);

    /// Below this a normalized outline is drawn at full scale instead: silence
    /// blown up to the whole height would be noise floor drawn as speech.
    static final float QUIET = 1e-3f;

    /// Columns a bucket needs before the buckets are drawn as bars with gaps
    /// rather than as a continuous outline.
    static final int BAR_SPACING = 3;

    /// The widest gap between two bars, in logical pixels.
    static final float GAP = 2;

    public WaveformPlot {
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(attributes, "attributes");
        if (!(played >= 0 && played <= 1)) {
            throw new IllegalArgumentException("played is 0 to 1, not " + played);
        }
    }

    /// `envelope`, all of it played, at full scale: a live outline.
    public WaveformPlot(Envelope envelope) {
        this(envelope, 1, false, Attributes.NONE);
    }

    /// What a waveform is drawn in.
    ///
    /// @param wave  the part played
    /// @param ahead the part not played yet
    record Colours(int wave, int ahead) {

        static Colours of(Context context) {
            return new Colours(
                    context.color("--gb-mic-wave", FALLBACK.wave),
                    context.color("--gb-mic-wave-ahead", FALLBACK.ahead));
        }
    }

    @Override
    public String cssType() {
        return "waveform-view";
    }

    @Override
    public @Nullable String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    @Override
    public WaveformPlot withAttributes(Attributes value) {
        return new WaveformPlot(envelope, played, normalized, value);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var colours = Colours.of(context);
        return Box.of().style(style).painting((frame, size) -> paint(frame, size, colours));
    }

    /// Draws the outline into `size`, from the box's own origin.
    void paint(Frame frame, LogicalSize size, Colours colours) {
        var buckets = envelope.buckets();
        if (buckets == 0 || size.width() < 1 || size.height() <= 0) {
            return;
        }
        var peak = envelope.peak();
        var gain = normalized && peak > QUIET ? 1 / peak : 1f;
        var middle = size.height() / 2;
        var columns = (int) Math.floor(size.width());
        if (buckets * BAR_SPACING <= columns) {
            // Few buckets, a short clip's: a bar each, with a gap between.
            var slot = size.width() / buckets;
            var gap = Math.min(GAP, slot / 3);
            for (var bucket = 0; bucket < buckets; bucket++) {
                var argb = bucket + 0.5 < played * buckets ? colours.wave : colours.ahead;
                stroke(
                        frame,
                        bucket * slot + gap / 2,
                        slot - gap,
                        middle,
                        gain,
                        envelope.lowest(bucket),
                        envelope.highest(bucket),
                        argb);
            }
            return;
        }
        var step = size.width() / columns;
        var playedTo = played * columns;
        for (var column = 0; column < columns; column++) {
            // Each column takes every bucket under it, or the one bucket it
            // falls in when there are a little fewer buckets than columns.
            var first = (int) ((long) column * buckets / columns);
            var last = Math.max(first + 1, (int) ((long) (column + 1) * buckets / columns));
            var low = Float.POSITIVE_INFINITY;
            var high = Float.NEGATIVE_INFINITY;
            for (var bucket = first; bucket < last; bucket++) {
                low = Math.min(low, envelope.lowest(bucket));
                high = Math.max(high, envelope.highest(bucket));
            }
            var argb = column + 0.5 < playedTo ? colours.wave : colours.ahead;
            stroke(frame, column * step, step, middle, gain, low, high, argb);
        }
    }

    /// One upright stroke at `x`, `width` wide, from `low` to `high` about
    /// `middle`, never thinner than a pixel.
    private static void stroke(
            Frame frame, float x, float width, float middle, float gain, float low, float high, int argb) {
        var top = middle - Math.clamp(high * gain, -1f, 1f) * middle;
        var bottom = middle - Math.clamp(low * gain, -1f, 1f) * middle;
        if (bottom - top < 1) {
            var centre = (top + bottom) / 2;
            top = centre - 0.5f;
            bottom = centre + 0.5f;
        }
        frame.fillRect(x, top, width, bottom - top, argb);
    }
}
