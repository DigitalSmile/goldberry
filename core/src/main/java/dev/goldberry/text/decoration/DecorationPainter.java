package dev.goldberry.text.decoration;

import java.util.Objects;
import java.util.Set;

import dev.goldberry.paint.Frame;
import dev.goldberry.paint.Path;
import dev.goldberry.paint.stroke.Cap;
import dev.goldberry.paint.stroke.Stroke;
import dev.goldberry.text.flow.DecorationPaint;
import dev.goldberry.text.flow.DecorationStyle;
import dev.goldberry.text.flow.TextDecoration;
import dev.goldberry.text.font.Font;

/// Draws a paragraph's decoration lines along one piece of one line.
///
/// A piece is a stretch drawn in one colour and one face. A line of text may be
/// several, so every pattern here is anchored to the frame's own x rather than to
/// the piece: dots, dashes and the wave fall where they would have fallen had
/// the line been one piece, and a word in a span of its own does not restart
/// the wave under it. Each dot and dash belongs to the piece its start falls in,
/// so two pieces never draw one twice.
///
/// Where a line goes and how thick it is come from the face
/// ([Font#decorations()]) unless the paint says otherwise. An underline with a
/// thickness of its own grows down from its position, and a line-through stays
/// centred on the face's.
///
/// Read more: [Text flow](https://goldberry.dev/docs/guide/styling.html#text-flow).
public final class DecorationPainter {

    private DecorationPainter() {}

    /// How many thicknesses a dash is long.
    static final double DASH = 3;

    /// How many thicknesses one dash and the gap after it take.
    static final double DASH_PITCH = 5;

    /// How many thicknesses one dot and the gap after it take.
    static final double DOT_PITCH = 2;

    /// Below this thickness, in logical units, a dot is a square: a disc that
    /// small is a smudge of coverage rather than a dot.
    static final double ROUND_DOTS_FROM = 1.5;

    /// A wave's period, in amplitudes.
    static final double WAVE_PERIOD = 6;

    /// How many straight segments a wave's period is drawn with.
    static final int WAVE_STEPS = 12;

    /// Draws `lines` along `[x, x + width)` on the line whose baseline is at
    /// `baseline`.
    ///
    /// @param textArgb the colour the piece's text is drawn in, which a line
    ///                 takes unless its paint has a colour of its own
    /// @param rules    the face's metrics, already substituted for by
    ///                 [Font.Decorations#orElse]
    public static void paint(
            Frame frame,
            double x,
            double baseline,
            double width,
            int textArgb,
            Set<TextDecoration> lines,
            DecorationPaint paint,
            Font.Decorations rules) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(paint, "paint");
        Objects.requireNonNull(rules, "rules");
        if (lines.isEmpty() || !(width > 0)) {
            return;
        }
        var argb = paint.argbOver(textArgb);
        if (lines.contains(TextDecoration.UNDERLINE)) {
            var thickness = Double.isNaN(paint.thickness()) ? rules.underlineThickness() : paint.thickness();
            var offset = Double.isNaN(paint.underlineOffset()) ? rules.underlinePosition() : paint.underlineOffset();
            var top = baseline + offset;
            draw(frame, x, bandTop(paint.style(), top, thickness, false), width, thickness, paint.style(), argb);
        }
        if (lines.contains(TextDecoration.LINE_THROUGH)) {
            var faceThickness = rules.strikethroughThickness();
            var thickness = Double.isNaN(paint.thickness()) ? faceThickness : paint.thickness();
            var centre = baseline + rules.strikethroughPosition() + faceThickness / 2;
            var top = centre - thickness / 2;
            draw(frame, x, bandTop(paint.style(), top, thickness, true), width, thickness, paint.style(), argb);
        }
    }

    /// How tall the ink of one line in `style` is, `thickness` thick.
    static double band(DecorationStyle style, double thickness) {
        return switch (style) {
            case SOLID, DOTTED, DASHED -> thickness;
            case DOUBLE -> 3 * thickness;
            case WAVY -> 2 * amplitude(thickness) + thickness;
        };
    }

    /// The top of the ink for a line whose straight form would start at `top`.
    /// An underline's ink grows down from there. A line-through's stays centred
    /// on the straight line, so a double or a wave through the text sits where
    /// a solid one would.
    static double bandTop(DecorationStyle style, double top, double thickness, boolean centred) {
        return centred ? top + thickness / 2 - band(style, thickness) / 2 : top;
    }

    /// How far a wave swings either side of its centre: at least a logical unit,
    /// so a hairline wave is still a wave.
    static double amplitude(double thickness) {
        return Math.max(1, thickness);
    }

    private static void draw(
            Frame frame, double x, double top, double width, double thickness, DecorationStyle style, int argb) {
        if (!(thickness > 0)) {
            return;
        }
        switch (style) {
            case SOLID -> rect(frame, x, top, width, thickness, argb);
            case DOUBLE -> {
                rect(frame, x, top, width, thickness, argb);
                rect(frame, x, top + 2 * thickness, width, thickness, argb);
            }
            case DOTTED -> dots(frame, x, top, width, thickness, argb);
            case DASHED -> dashes(frame, x, top, width, thickness, argb);
            case WAVY -> wave(frame, x, top, width, thickness, argb);
        }
    }

    private static void rect(Frame frame, double x, double top, double width, double thickness, int argb) {
        frame.fillRect((float) x, (float) top, (float) width, (float) thickness, argb);
    }

    /// Dots whose starts are a pitch apart from the frame's x = 0, each drawn by
    /// the piece its start falls in.
    private static void dots(Frame frame, double x, double top, double width, double thickness, int argb) {
        var pitch = DOT_PITCH * thickness;
        var round = thickness >= ROUND_DOTS_FROM;
        var radius = thickness / 2;
        var path = round ? Path.builder() : null;
        for (var start = firstStart(x, pitch); start < x + width; start += pitch) {
            if (path != null) {
                path.append(Path.circle(start + radius, top + radius, radius));
            } else {
                rect(frame, start, top, thickness, thickness, argb);
            }
        }
        if (path != null && !path.isEmpty()) {
            frame.fillPath(path.build(), argb);
        }
    }

    /// Dashes whose starts are a pitch apart from the frame's x = 0. A dash is
    /// cut at the piece's end, and the piece after it draws none of it, since
    /// its start was not there.
    private static void dashes(Frame frame, double x, double top, double width, double thickness, int argb) {
        var pitch = DASH_PITCH * thickness;
        var length = DASH * thickness;
        var end = x + width;
        // A dash that started in the piece before and runs into this one is
        // finished here, so a span boundary does not cut a dash short.
        var before = firstStart(x, pitch) - pitch;
        if (before + length > x) {
            rect(frame, x, top, Math.min(before + length, end) - x, thickness, argb);
        }
        for (var start = firstStart(x, pitch); start < end; start += pitch) {
            rect(frame, start, top, Math.min(length, end - start), thickness, argb);
        }
    }

    /// A sine wave sampled at the frame's own x, so its phase is the same in
    /// every piece of a line. Butt caps, so where two pieces meet the wave does
    /// not thicken.
    private static void wave(Frame frame, double x, double top, double width, double thickness, int argb) {
        var amplitude = amplitude(thickness);
        var period = WAVE_PERIOD * amplitude;
        var centre = top + amplitude + thickness / 2;
        var step = period / WAVE_STEPS;
        var end = x + width;
        var path = Path.builder().moveTo(x, waveY(x, centre, amplitude, period));
        for (var at = firstStart(x, step) + step; at < end; at += step) {
            path.lineTo(at, waveY(at, centre, amplitude, period));
        }
        path.lineTo(end, waveY(end, centre, amplitude, period));
        frame.strokePath(path.build(), Stroke.round(thickness).cap(Cap.BUTT), argb);
    }

    private static double waveY(double x, double centre, double amplitude, double period) {
        return centre - amplitude * Math.sin(2 * Math.PI * x / period);
    }

    /// The first multiple of `pitch` at or after `x`.
    static double firstStart(double x, double pitch) {
        return Math.ceil(x / pitch) * pitch;
    }
}
