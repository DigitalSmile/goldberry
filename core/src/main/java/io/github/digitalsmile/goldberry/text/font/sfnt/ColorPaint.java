package io.github.digitalsmile.goldberry.text.font.sfnt;

import java.util.List;
import java.util.Objects;

/// One node of a `COLR` version 1 **paint graph** — what a colour glyph is in
/// that format ([ADR-0456]).
///
/// ## What changed from version 0
///
/// A version 0 colour glyph is a list of outlines, each filled with one flat
/// colour ([ColorLayers]). Version 1 keeps the outlines and replaces the list
/// with a **tree**: a node either *paints* — a colour, a gradient — or *shapes*
/// what is painted beneath it — a glyph outline that clips it, a transform that
/// moves it, a composite that blends two subtrees. Noto Color Emoji is drawn
/// this way: a face is a radial gradient clipped to a circle, a flag is its
/// stripes composited under a soft-light wave.
///
/// ## Why a sealed interface of records
///
/// Because the renderer is a `switch` over it, and a `switch` over a sealed type
/// is **exhaustive**: a node added here stops the painter compiling until it is
/// drawn, rather than falling through a default branch that draws nothing and
/// reports nothing. The records are values in the font's own design units, y
/// up, exactly as the table writes them; turning them into pixels is `paint`'s
/// business and nothing here knows a pixel exists.
///
/// ## What is folded at read time
///
/// - **Eleven transform formats become one.** Translate, scale, rotate and skew,
///   each with and without a centre, are all an affine matrix; [Transform] is
///   that matrix, so the painter has one case and not twelve.
/// - **Variable formats are read as their defaults.** Every `PaintVar…` is its
///   static twin plus a variation index, and a face drawn at its default
///   instance — the only instance this toolkit asks for — uses the static
///   values unchanged.
/// - **Palette indices become colours** ([Colour]), keeping the one index that
///   means "whatever colour the text is" as a flag rather than a number.
public sealed interface ColorPaint {

    /// Several paints drawn one over the next — `PaintColrLayers`.
    ///
    /// @param layers bottom first
    record Layers(List<ColorPaint> layers) implements ColorPaint {

        public Layers {
            layers = List.copyOf(layers);
        }
    }

    /// One colour, everywhere it is allowed to reach — `PaintSolid`.
    record Solid(Colour colour) implements ColorPaint {

        public Solid {
            Objects.requireNonNull(colour, "colour");
        }
    }

    /// A ramp along a line — `PaintLinearGradient`.
    ///
    /// **Three points, not two.** The ramp runs from `p0` towards `p1`, but its
    /// lines of equal colour are parallel to `p0 → p2` rather than
    /// perpendicular to `p0 → p1`. That is what lets a font skew a gradient
    /// without a transform; [#normal()] is the two-point gradient a rasterizer
    /// wants.
    record LinearGradient(ColorLine line, double x0, double y0, double x1, double y1, double x2, double y2)
            implements ColorPaint {

        public LinearGradient {
            Objects.requireNonNull(line, "line");
        }

        /// The end point of the equivalent two-point gradient: `p1` moved along
        /// the direction `p0 → p2` until the line `p0 → end` is perpendicular to
        /// it.
        ///
        /// `p0` and this are what a two-point gradient is given. When `p0 → p2`
        /// has no length the font has said nothing about the direction and `p1`
        /// is returned unchanged, which is the specification's own fallback.
        ///
        /// @return `{x, y}`
        public double[] normal() {
            var dx = x2 - x0;
            var dy = y2 - y0;
            // The perpendicular to p0→p2.
            var px = dy;
            var py = -dx;
            var squared = px * px + py * py;
            if (squared == 0) {
                return new double[] {x1, y1};
            }
            // Project p0→p1 onto that perpendicular.
            var along = ((x1 - x0) * px + (y1 - y0) * py) / squared;
            return new double[] {x0 + along * px, y0 + along * py};
        }
    }

    /// A ramp between two circles — `PaintRadialGradient`.
    ///
    /// The first stop sits on circle 0 and the last on circle 1; the circles
    /// between are interpolated, centre and radius both. That is the
    /// *two-point conical* gradient, which covers a plain radial (concentric,
    /// one radius zero) and a spotlight (offset) with the same arithmetic.
    record RadialGradient(ColorLine line, double x0, double y0, double r0, double x1, double y1, double r1)
            implements ColorPaint {

        public RadialGradient {
            Objects.requireNonNull(line, "line");
        }
    }

    /// A ramp around a centre — `PaintSweepGradient`.
    ///
    /// @param startAngle where the first stop sits, in degrees counter-clockwise
    ///        from the positive x axis, y up
    /// @param endAngle   where the last one does
    record SweepGradient(ColorLine line, double centerX, double centerY, double startAngle, double endAngle)
            implements ColorPaint {

        public SweepGradient {
            Objects.requireNonNull(line, "line");
        }
    }

    /// `paint`, clipped to one glyph's outline — `PaintGlyph`.
    ///
    /// The outline is an ordinary glyph in the same face, as in version 0. What
    /// is new is that the fill inside it may be anything — usually a gradient,
    /// sometimes under a transform of its own.
    record Glyph(int glyphId, ColorPaint paint) implements ColorPaint {

        public Glyph {
            Objects.requireNonNull(paint, "paint");
        }
    }

    /// Another colour glyph's whole paint graph, reused — `PaintColrGlyph`.
    ///
    /// Left as a reference rather than inlined, because a graph may reach
    /// itself through one: inlining at read time would loop, and a painter that
    /// follows it with a depth limit draws what a well-formed font meant and
    /// stops on one that is not.
    record ColrGlyph(int glyphId) implements ColorPaint {}

    /// `paint`, drawn through an affine matrix — every transform format folded
    /// into one.
    ///
    /// ```
    ///   x' = xx·x + xy·y + dx
    ///   y' = yx·x + yy·y + dy
    /// ```
    ///
    /// The field order is the table's own `Affine2x3`. The matrix maps the
    /// child's coordinates into the parent's.
    record Transform(double xx, double yx, double xy, double yy, double dx, double dy, ColorPaint paint)
            implements ColorPaint {

        public Transform {
            Objects.requireNonNull(paint, "paint");
        }

        /// Whether the matrix can be undone. A singular one collapses its child
        /// onto a line, which draws nothing — and a gradient placed through it
        /// has no inverse to be evaluated with.
        public boolean isInvertible() {
            var determinant = xx * yy - xy * yx;
            return determinant != 0 && Double.isFinite(determinant);
        }
    }

    /// `source` blended onto `backdrop` with `mode` — `PaintComposite`.
    ///
    /// The one node that cannot be drawn straight onto the surface: both sides
    /// have to exist as pictures before one is blended onto the other, so the
    /// painter renders them offscreen. Noto uses it for its waving flags.
    record Composite(ColorPaint source, CompositeMode mode, ColorPaint backdrop) implements ColorPaint {

        public Composite {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(backdrop, "backdrop");
        }
    }

    /// A colour out of the palette, or the text's own, with an alpha applied.
    ///
    /// @param argb        the palette colour as `0xAARRGGBB`, not premultiplied;
    ///                    ignored when `followsText`
    /// @param followsText whether this is palette index `0xFFFF` — the colour
    ///                    of the text being drawn
    /// @param alpha       multiplied into whichever colour it turns out to be,
    ///                    from 0 to 1
    record Colour(int argb, boolean followsText, double alpha) {

        /// The colour to draw with, when the text around it is `textArgb`.
        public int resolve(int textArgb) {
            var base = followsText ? textArgb : argb;
            if (alpha >= 1) {
                return base;
            }
            var faded = (int) Math.round(((base >>> 24) & 0xFF) * Math.max(0, alpha));
            return (faded << 24) | (base & 0x00FFFFFF);
        }
    }

    /// One colour at one position along a gradient.
    ///
    /// @param offset where along the ramp; a font may write values outside
    ///        `[0, 1]`, which place the stop beyond the geometry's own ends
    record ColorStop(double offset, Colour colour) {

        public ColorStop {
            Objects.requireNonNull(colour, "colour");
        }
    }

    /// A gradient's stops and what happens beyond them — `ColorLine`.
    ///
    /// @param stops in ascending offset order, never empty
    record ColorLine(Extend extend, List<ColorStop> stops) {

        public ColorLine {
            Objects.requireNonNull(extend, "extend");
            stops = List.copyOf(stops);
            if (stops.isEmpty()) {
                throw new IllegalArgumentException("a colour line has at least one stop");
            }
        }
    }

    /// What a gradient does beyond its first and last stops.
    enum Extend {

        /// The end colours hold.
        PAD,

        /// The ramp starts again.
        REPEAT,

        /// The ramp runs back, then forwards again.
        REFLECT
    }
}
