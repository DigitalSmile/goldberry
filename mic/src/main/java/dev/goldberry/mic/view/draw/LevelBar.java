package dev.goldberry.mic.view.draw;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.value.CssColor;
import dev.goldberry.mic.analysis.LevelTracker;
import dev.goldberry.mic.analysis.Levels;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.Frame;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// One reading of a level meter, drawn: the RMS as a solid bar, the peak as a
/// fainter one past it, and the held peak as a mark.
///
/// ```java
/// new LevelBar(tracker.reading())
/// ```
///
/// What a `level-meter` builds, and the same node to a stylesheet. The scale
/// is [#SCALE], 60 dB long. The part of a bar louder than [#HOT_DECIBELS] is
/// drawn in `--gb-mic-hot` and past [#CLIP_DECIBELS] in `--gb-mic-clip`; the
/// rest is `--gb-mic-level`, and the hold mark is `--gb-mic-hold` until the
/// held peak clips. The track is the box's own background.
///
/// A vertical bar also carries the class `vertical`, which is what `mic.css`
/// sizes it by.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
///
/// @param reading     the levels to draw
/// @param orientation which way the bar fills
/// @param attributes  id, classes and key
public record LevelBar(LevelTracker.Reading reading, MeterOrientation orientation, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Attributed<LevelBar> {

    /// The meter's scale: −60 dBFS at the start, full scale at the end.
    public static final DecibelScale SCALE = new DecibelScale(-60, 0);

    /// Where a bar turns to the hot colour.
    public static final double HOT_DECIBELS = -6;

    /// Where a bar turns to the clip colour.
    public static final double CLIP_DECIBELS = -1;

    /// How faint the peak bar is beside the RMS bar.
    static final double PEAK_ALPHA = 0.4;

    /// How thick the hold mark is, in logical pixels.
    static final float HOLD = 2;

    /// The colours with no stylesheet: Nord's, as the dark theme sets them.
    static final Colours FALLBACK = new Colours(0xFFA3BE8C, 0xFFEBCB8B, 0xFFBF616A, 0xFFECEFF4);

    public LevelBar {
        Objects.requireNonNull(reading, "reading");
        Objects.requireNonNull(orientation, "orientation");
        Objects.requireNonNull(attributes, "attributes");
    }

    /// `reading`, filling from left to right.
    public LevelBar(LevelTracker.Reading reading) {
        this(reading, MeterOrientation.HORIZONTAL, Attributes.NONE);
    }

    /// What a bar is drawn in.
    ///
    /// @param level the bar below [#HOT_DECIBELS]
    /// @param hot   the bar from there to [#CLIP_DECIBELS]
    /// @param clip  the bar past that, and a hold mark that clipped
    /// @param hold  the hold mark
    record Colours(int level, int hot, int clip, int hold) {

        /// The colours the cascade gives this node.
        static Colours of(Context context) {
            return new Colours(
                    context.color("--gb-mic-level", FALLBACK.level),
                    context.color("--gb-mic-hot", FALLBACK.hot),
                    context.color("--gb-mic-clip", FALLBACK.clip),
                    context.color("--gb-mic-hold", FALLBACK.hold));
        }
    }

    @Override
    public String cssType() {
        return "level-meter";
    }

    @Override
    public @Nullable String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        if (orientation == MeterOrientation.HORIZONTAL) {
            return attributes.classes();
        }
        var classes = new LinkedHashSet<>(attributes.classes());
        classes.add("vertical");
        return Set.copyOf(classes);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    @Override
    public LevelBar withAttributes(Attributes value) {
        return new LevelBar(reading, orientation, value);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var colours = Colours.of(context);
        return Box.of().style(style).painting((frame, size) -> paint(frame, size, colours));
    }

    /// Draws the reading into `size`, from the box's own origin.
    void paint(Frame frame, LogicalSize size, Colours colours) {
        if (size.width() <= 0 || size.height() <= 0) {
            return;
        }
        var levels = reading.levels();
        bar(frame, size, SCALE.fraction(levels.peakDecibels()), colours, PEAK_ALPHA);
        bar(frame, size, SCALE.fraction(levels.rmsDecibels()), colours, 1);

        var held = reading.heldPeakDecibels();
        if (held > SCALE.floorDecibels() && held > Levels.FLOOR_DECIBELS) {
            var along = length(size);
            var at = (float) Math.clamp(SCALE.fraction(held) * along - HOLD / 2, 0, Math.max(0, along - HOLD));
            span(frame, size, at, at + HOLD, held >= CLIP_DECIBELS ? colours.clip : colours.hold);
        }
    }

    /// A bar from the start to `fraction`, each zone in its own colour.
    private void bar(Frame frame, LogicalSize size, double fraction, Colours colours, double alpha) {
        if (fraction <= 0) {
            return;
        }
        var along = length(size);
        var hot = SCALE.fraction(HOT_DECIBELS);
        var clip = SCALE.fraction(CLIP_DECIBELS);
        zone(frame, size, 0, Math.min(fraction, hot), along, CssColor.fade(colours.level, alpha));
        zone(frame, size, hot, Math.min(fraction, clip), along, CssColor.fade(colours.hot, alpha));
        zone(frame, size, clip, fraction, along, CssColor.fade(colours.clip, alpha));
    }

    private void zone(Frame frame, LogicalSize size, double from, double to, double along, int argb) {
        if (to > from) {
            span(frame, size, (float) (from * along), (float) (to * along), argb);
        }
    }

    /// Fills the bar's length from `from` to `to`, logical pixels from its start.
    private void span(Frame frame, LogicalSize size, float from, float to, int argb) {
        var width = size.width();
        var height = size.height();
        switch (orientation) {
            case HORIZONTAL -> frame.fillRect(from, 0, to - from, height, argb);
            case VERTICAL -> frame.fillRect(0, height - to, width, to - from, argb);
        }
    }

    /// The length a bar fills along.
    private double length(LogicalSize size) {
        return switch (orientation) {
            case HORIZONTAL -> size.width();
            case VERTICAL -> size.height();
        };
    }
}
