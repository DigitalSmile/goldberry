package dev.goldberry.mic.view;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.kdl.KdlNode;
import dev.goldberry.mic.analysis.LevelTracker;
import dev.goldberry.mic.source.SampleSource;
import dev.goldberry.mic.view.draw.LevelBar;
import dev.goldberry.mic.view.draw.MeterOrientation;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;

/// `level-meter`: how loud a source is, as a bar.
///
/// ```java
/// new LevelMeter(recording)
/// ```
///
/// ```kdl
/// level-meter source="mic" orientation="vertical"
/// ```
///
/// The RMS of the last block is the solid bar, its peak a fainter one past it,
/// and the loudest peak of the last moment a mark that holds for 1.5 s and then
/// falls ([LevelTracker]). The scale is 60 dB long; the loud end turns to the
/// hot colour at −6 dBFS and the clip colour at −1. Drawn by [LevelBar].
///
/// The widget does not own the source, and listens to it only while it is
/// mounted: a meter shown again starts from silence.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
///
/// @param source      what to measure
/// @param orientation which way the bar fills
/// @param attributes  id, classes and key
@Markup("level-meter")
public record LevelMeter(SampleSource source, MeterOrientation orientation, Attributes attributes)
        implements Widget.Stateful, Attributed<LevelMeter> {

    public LevelMeter {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(orientation, "orientation");
        Objects.requireNonNull(attributes, "attributes");
    }

    /// `source`'s levels, filling from left to right.
    public LevelMeter(SampleSource source) {
        this(source, MeterOrientation.HORIZONTAL, Attributes.NONE);
    }

    @Override
    public State<?> createState() {
        return new LevelMeterState();
    }

    @Override
    public LevelMeter withAttributes(Attributes attributes) {
        return new LevelMeter(source, orientation, attributes);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    /// `level-meter source="name" orientation="vertical"`: the source is a
    /// named object the application registered with the document.
    ///
    /// @throws IllegalArgumentException when the node has children, or
    ///         `orientation` is neither `horizontal` nor `vertical`
    @SuppressWarnings("unused")
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        if (!children.isEmpty()) {
            throw new IllegalArgumentException("level-meter takes no children");
        }
        return new LevelMeter(
                Sources.named(node, wiring),
                MeterOrientation.named(node.stringProperty("orientation")),
                Attributes.of(node));
    }

    /// Feeds a tracker, and draws its reading.
    static final class LevelMeterState extends Listening<LevelMeter, LevelTracker> {

        @Override
        SampleSource source(LevelMeter widget) {
            return widget.source();
        }

        @Override
        LevelTracker analysis(LevelMeter widget) {
            return new LevelTracker();
        }

        @Override
        public Widget build(BuildContext context) {
            var meter = widget();
            return new LevelBar(analysis().reading(), meter.orientation(), meter.attributes());
        }
    }
}
