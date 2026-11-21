package dev.goldberry.mic.view;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.kdl.KdlNode;
import dev.goldberry.mic.analysis.SpectrumAnalyzer;
import dev.goldberry.mic.source.SampleFormat;
import dev.goldberry.mic.source.SampleSource;
import dev.goldberry.mic.view.draw.SpectrumPlot;
import dev.goldberry.mic.view.draw.SpectrumShape;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;

/// `spectrum-view`: what frequencies a source is made of, now.
///
/// ```java
/// new SpectrumView(recording, 48, SpectrumShape.LINE, Attributes.NONE)
/// ```
///
/// ```kdl
/// spectrum-view source="mic" bands=48 shape="line"
/// ```
///
/// The spectrum of the last 40 ms or so ([#transformSize]) through a Hann
/// window, cut into `bands` bands spaced evenly in log frequency from
/// [#LOW_HERTZ] to [#HIGH_HERTZ] (or just under half the sample rate, where
/// that is lower), each as loud as its loudest bin. Drawn by [SpectrumPlot],
/// 90 dB tall.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
///
/// @param source     what to analyse
/// @param bands      how many bands, one at least
/// @param shape      bars or a line
/// @param attributes id, classes and key
@Markup("spectrum-view")
public record SpectrumView(SampleSource source, int bands, SpectrumShape shape, Attributes attributes)
        implements Widget.Stateful, Attributed<SpectrumView> {

    /// How many bands unless told.
    public static final int BANDS = 32;

    /// The lowest frequency drawn: below it a voice has nothing to show.
    public static final double LOW_HERTZ = 50;

    /// The highest frequency drawn, where the sample rate allows.
    public static final double HIGH_HERTZ = 16_000;

    public SpectrumView {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(attributes, "attributes");
        if (bands < 1) {
            throw new IllegalArgumentException(bands + " bands");
        }
    }

    /// [#BANDS] bars of `source`.
    public SpectrumView(SampleSource source) {
        this(source, BANDS, SpectrumShape.BARS, Attributes.NONE);
    }

    @Override
    public State<?> createState() {
        return new SpectrumState();
    }

    @Override
    public SpectrumView withAttributes(Attributes attributes) {
        return new SpectrumView(source, bands, shape, attributes);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    /// `spectrum-view source="name" bands=48 shape="line"`.
    ///
    /// @throws IllegalArgumentException when the node has children, `bands` is
    ///         not a whole number above zero, or `shape` is neither `bars` nor
    ///         `line`
    @SuppressWarnings("unused")
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        if (!children.isEmpty()) {
            throw new IllegalArgumentException("spectrum-view takes no children");
        }
        var bands = node.numberProperty("bands", BANDS);
        if (bands != Math.rint(bands) || bands < 1 || bands > 1024) {
            throw new IllegalArgumentException("bands is a whole number from 1 to 1024, not " + bands);
        }
        return new SpectrumView(
                Sources.named(node, wiring),
                (int) bands,
                SpectrumShape.named(node.stringProperty("shape")),
                Attributes.of(node));
    }

    /// The transform's size for samples in `format`: the power of two that
    /// holds at least 40 ms, 2048 points at 48 kHz.
    public static int transformSize(SampleFormat format) {
        var frames = Math.max(2, format.sampleRate() / 25);
        return Integer.highestOneBit(frames - 1) << 1;
    }

    /// The highest frequency drawn for samples in `format`.
    static double highHertz(SampleFormat format) {
        return Math.min(HIGH_HERTZ, format.sampleRate() * 0.95 / 2);
    }

    /// Feeds an analyzer, and draws its bands.
    static final class SpectrumState extends Listening<SpectrumView, SpectrumAnalyzer> {

        @Override
        SampleSource source(SpectrumView widget) {
            return widget.source();
        }

        @Override
        SpectrumAnalyzer analysis(SpectrumView widget) {
            var format = widget.source().format();
            return new SpectrumAnalyzer(format, transformSize(format));
        }

        @Override
        public Widget build(BuildContext context) {
            var view = widget();
            var format = view.source().format();
            var bands = analysis().spectrum().bands(view.bands(), LOW_HERTZ, highHertz(format));
            return new SpectrumPlot(SpectrumPlot.levels(bands), view.shape(), view.attributes());
        }
    }
}
