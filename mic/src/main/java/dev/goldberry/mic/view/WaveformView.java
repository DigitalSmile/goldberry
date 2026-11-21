package dev.goldberry.mic.view;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.kdl.KdlNode;
import dev.goldberry.mic.analysis.EnvelopeTracker;
import dev.goldberry.mic.source.SampleSource;
import dev.goldberry.mic.view.draw.WaveformPlot;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;

/// `waveform-view`: the outline of the last few seconds a source said,
/// scrolling in from the right.
///
/// ```java
/// new WaveformView(recording, Duration.ofSeconds(5))
/// ```
///
/// ```kdl
/// waveform-view source="mic" seconds=5
/// ```
///
/// The window is cut into [#BUCKETS] buckets, each drawn from its lowest
/// sample to its highest at full scale; what has not been heard yet is a flat
/// line. A recorded clip drawn whole, with the part played in another colour,
/// is a [WaveformPlot] of the clip's own
/// [dev.goldberry.mic.analysis.Envelope].
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
///
/// @param source     what to draw
/// @param window     how much of the past the view spans
/// @param attributes id, classes and key
@Markup("waveform-view")
public record WaveformView(SampleSource source, Duration window, Attributes attributes)
        implements Widget.Stateful, Attributed<WaveformView> {

    /// How many buckets the window is cut into.
    public static final int BUCKETS = 256;

    /// The window a view spans unless told.
    public static final Duration WINDOW = Duration.ofSeconds(3);

    public WaveformView {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(attributes, "attributes");
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("a window of " + window);
        }
    }

    /// The last [#WINDOW] of `source`.
    public WaveformView(SampleSource source) {
        this(source, WINDOW, Attributes.NONE);
    }

    /// The last `window` of `source`.
    public WaveformView(SampleSource source, Duration window) {
        this(source, window, Attributes.NONE);
    }

    @Override
    public State<?> createState() {
        return new WaveformState();
    }

    @Override
    public WaveformView withAttributes(Attributes attributes) {
        return new WaveformView(source, window, attributes);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    /// `waveform-view source="name" seconds=5`.
    ///
    /// @throws IllegalArgumentException when the node has children, or
    ///         `seconds` is not above zero
    @SuppressWarnings("unused")
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        if (!children.isEmpty()) {
            throw new IllegalArgumentException("waveform-view takes no children");
        }
        var seconds = node.numberProperty("seconds", WINDOW.toMillis() / 1000.0);
        if (!(seconds > 0) || !Double.isFinite(seconds)) {
            throw new IllegalArgumentException("seconds is above zero, not " + seconds);
        }
        return new WaveformView(
                Sources.named(node, wiring), Duration.ofNanos(Math.round(seconds * 1e9)), Attributes.of(node));
    }

    /// The frames in one bucket of `window` at `source`'s rate: one at least.
    static int framesPerBucket(SampleSource source, Duration window) {
        var frames = source.format().framesIn(window.toNanos());
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, frames / BUCKETS));
    }

    /// Feeds an envelope tracker, and draws its outline.
    static final class WaveformState extends Listening<WaveformView, EnvelopeTracker> {

        @Override
        SampleSource source(WaveformView widget) {
            return widget.source();
        }

        @Override
        EnvelopeTracker analysis(WaveformView widget) {
            return new EnvelopeTracker(BUCKETS, framesPerBucket(widget.source(), widget.window()));
        }

        @Override
        boolean restarts(WaveformView previous, WaveformView current) {
            return super.restarts(previous, current) || !previous.window().equals(current.window());
        }

        @Override
        public Widget build(BuildContext context) {
            var view = widget();
            return new WaveformPlot(analysis().envelope(), 1, false, view.attributes());
        }
    }
}
