package dev.goldberry.mic.view.draw;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.DoubleStream;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.value.CssColor;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.Frame;
import dev.goldberry.paint.Path;
import dev.goldberry.paint.stroke.Stroke;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// A spectrum's bands drawn as bars or as a line, low frequencies on the left.
///
/// ```java
/// new SpectrumPlot(SpectrumPlot.levels(analyzer.spectrum().bands(32, 50, 16_000)))
/// ```
///
/// What a `spectrum-view` builds, and the same node to a stylesheet, drawn in
/// `--gb-mic-spectrum`. The height is [#SCALE]: 90 dB from the floor to full
/// scale, which a full-scale sine reaches in its band.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
///
/// @param bands      each band's level in dBFS, lowest frequency first
/// @param shape      bars or a line
/// @param attributes id, classes and key
public record SpectrumPlot(List<Double> bands, SpectrumShape shape, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Attributed<SpectrumPlot> {

    /// The plot's height: −90 dBFS at the bottom, full scale at the top.
    public static final DecibelScale SCALE = new DecibelScale(-90, 0);

    /// The colour with no stylesheet: Nord's accent.
    static final int FALLBACK = 0xFF88C0D0;

    /// The widest gap between two bars, in logical pixels.
    static final double GAP = 2;

    /// How thick the line is, in logical pixels: a sparkline's.
    static final double STROKE = 1.5;

    /// How faint the area under the line is.
    static final double FILL_ALPHA = 0.18;

    public SpectrumPlot {
        bands = List.copyOf(Objects.requireNonNull(bands, "bands"));
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(attributes, "attributes");
    }

    /// `bands` as bars.
    public SpectrumPlot(List<Double> bands) {
        this(bands, SpectrumShape.BARS, Attributes.NONE);
    }

    /// `decibels` as the list a plot takes: what
    /// [dev.goldberry.mic.analysis.Spectrum#bands] hands out.
    public static List<Double> levels(double[] decibels) {
        return DoubleStream.of(decibels).boxed().toList();
    }

    @Override
    public String cssType() {
        return "spectrum-view";
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
    public SpectrumPlot withAttributes(Attributes value) {
        return new SpectrumPlot(bands, shape, value);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var argb = context.color("--gb-mic-spectrum", FALLBACK);
        return Box.of().style(style).painting((frame, size) -> paint(frame, size, argb));
    }

    /// Draws the bands into `size`, from the box's own origin.
    void paint(Frame frame, LogicalSize size, int argb) {
        if (bands.size() == 0 || size.width() <= 0 || size.height() <= 0) {
            return;
        }
        switch (shape) {
            case BARS -> bars(frame, size, argb);
            case LINE -> line(frame, size, argb);
        }
    }

    private void bars(Frame frame, LogicalSize size, int argb) {
        var slot = size.width() / bands.size();
        // A quarter of the slot, up to two pixels; none at all once the bars
        // are thinner than two, where a gap would leave nothing to see.
        var gap = slot < 2 ? 0 : Math.min(GAP, slot / 4);
        for (var band = 0; band < bands.size(); band++) {
            var height = SCALE.fraction(bands.get(band)) * size.height();
            if (height > 0) {
                frame.fillRect(
                        (float) (band * slot + gap / 2),
                        (float) (size.height() - height),
                        (float) (slot - gap),
                        (float) height,
                        argb);
            }
        }
    }

    private void line(Frame frame, LogicalSize size, int argb) {
        var slot = size.width() / bands.size();
        var top = STROKE / 2;
        var bottom = size.height();
        var span = Math.max(0, bottom - top);
        var line = Path.builder();
        var under = Path.builder().moveTo(0, bottom);
        for (var band = 0; band < bands.size(); band++) {
            var x = bands.size() == 1 ? size.width() / 2 : band * slot + slot / 2;
            var y = bottom - SCALE.fraction(bands.get(band)) * span;
            if (band == 0) {
                line.moveTo(x, y);
                under.lineTo(0, y);
            } else {
                line.lineTo(x, y);
            }
            under.lineTo(x, y);
            if (band == bands.size() - 1) {
                under.lineTo(size.width(), y);
            }
        }
        under.lineTo(size.width(), bottom).close();
        frame.fillPath(under.build(), CssColor.fade(argb, FILL_ALPHA));
        frame.strokePath(line.build(), Stroke.round(STROKE), argb);
    }
}
