package io.github.digitalsmile.goldberry.text.font.sfnt;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// A face's TrueType outlines — its `glyf` table, read through `loca`.
///
/// ## Why the toolkit reads outlines at all
///
/// It did not need to while every glyph went straight to the rasterizer, which
/// reads `glyf` itself. A `COLR` version 1 glyph changes that: its outlines are
/// **clips** — a gradient is filled *inside* a glyph's shape — and the
/// rasterizer's glyph call fills with a colour and nothing else. So the painter
/// needs the shape as a path it can fill with anything, and this is where the
/// shape comes from ([ADR-0456]).
///
/// Java for [ColorLayers]'s reason: the format is a page of flags and deltas,
/// and binding the rasterizer's outline call would put a native path and its
/// lifetime across FFM for arithmetic.
///
/// ## What is read
///
/// Simple glyphs — contours of on- and off-curve points, with the implied
/// on-curve midpoints between two off-curve ones — and composite glyphs, whose
/// components are placed by offset and by a scale or a 2×2 matrix. A component
/// placed by **matching points** rather than by offset is placed at its origin:
/// that form is for hinting-era fonts and no colour face uses it.
///
/// CFF outlines (`OTTO` fonts) are not read; such a face has no `glyf` table and
/// [#read] answers [#NONE].
public final class GlyphOutlines {

    private static final int HEAD = TableDirectory.tag('h', 'e', 'a', 'd');
    private static final int MAXP = TableDirectory.tag('m', 'a', 'x', 'p');
    private static final int LOCA = TableDirectory.tag('l', 'o', 'c', 'a');
    private static final int GLYF = TableDirectory.tag('g', 'l', 'y', 'f');

    /// How deep composite glyphs may nest. Real fonts nest two or three levels;
    /// a component that names itself would otherwise recurse until the stack
    /// ran out.
    private static final int MAX_DEPTH = 16;

    // Simple-glyph flags.
    private static final int ON_CURVE = 0x01;
    private static final int X_SHORT = 0x02;
    private static final int Y_SHORT = 0x04;
    private static final int REPEAT = 0x08;
    private static final int X_SAME_OR_POSITIVE = 0x10;
    private static final int Y_SAME_OR_POSITIVE = 0x20;

    // Composite-glyph flags.
    private static final int ARGS_ARE_WORDS = 0x0001;
    private static final int ARGS_ARE_XY = 0x0002;
    private static final int HAS_SCALE = 0x0008;
    private static final int MORE_COMPONENTS = 0x0020;
    private static final int HAS_XY_SCALE = 0x0040;
    private static final int HAS_TWO_BY_TWO = 0x0080;

    /// A face with no TrueType outlines this can read.
    public static final GlyphOutlines NONE =
            new GlyphOutlines(ByteBuffer.allocate(0), ByteBuffer.allocate(0), false, 0, 1000);

    private final ByteBuffer glyf;
    private final ByteBuffer loca;
    private final boolean longOffsets;
    private final int glyphCount;
    private final int unitsPerEm;

    private GlyphOutlines(ByteBuffer glyf, ByteBuffer loca, boolean longOffsets, int glyphCount, int unitsPerEm) {
        this.glyf = glyf;
        this.loca = loca;
        this.longOffsets = longOffsets;
        this.glyphCount = glyphCount;
        this.unitsPerEm = unitsPerEm;
    }

    /// The outlines in `font`, or [#NONE] when it has no `glyf` table or the
    /// tables that index it are unreadable.
    public static GlyphOutlines read(byte[] font) {
        Objects.requireNonNull(font, "font");
        var head = TableDirectory.table(font, HEAD);
        var maxp = TableDirectory.table(font, MAXP);
        var loca = TableDirectory.table(font, LOCA);
        var glyf = TableDirectory.table(font, GLYF);
        if (head == null || maxp == null || loca == null || glyf == null) {
            return NONE;
        }
        try {
            var unitsPerEm = Short.toUnsignedInt(head.getShort(18));
            var longOffsets = head.getShort(50) != 0;
            var glyphCount = Short.toUnsignedInt(maxp.getShort(4));
            var needed = (glyphCount + 1) * (longOffsets ? 4 : 2);
            if (unitsPerEm == 0 || loca.limit() < needed) {
                return NONE;
            }
            return new GlyphOutlines(glyf, loca, longOffsets, glyphCount, unitsPerEm);
        } catch (RuntimeException e) {
            return NONE;
        }
    }

    /// Design units to the em — what turns a size in pixels into the scale a
    /// glyph's coordinates are multiplied by.
    public int unitsPerEm() {
        return unitsPerEm;
    }

    /// How many glyphs the face has, or zero for [#NONE].
    public int glyphCount() {
        return glyphCount;
    }

    /// Sends the outline of `glyphId` to `sink`, and says whether it could.
    ///
    /// **All or nothing.** The glyph is read completely — every component of a
    /// composite — before the first call reaches the sink, so a glyph whose data
    /// runs off the end of the table sends nothing and answers false, rather
    /// than half a shape.
    ///
    /// A glyph with no contours at all — a space — sends nothing and answers
    /// true: that is its outline.
    ///
    /// @return false when the glyph is out of range or its data is unreadable
    public boolean outline(int glyphId, OutlineSink sink) {
        Objects.requireNonNull(sink, "sink");
        if (glyphId < 0 || glyphId >= glyphCount) {
            return false;
        }
        var parts = new ArrayList<Contours>();
        try {
            collect(glyphId, 1, 0, 0, 1, 0, 0, parts, 0);
        } catch (RuntimeException e) {
            return false;
        }
        for (var part : parts) {
            part.emit(sink);
        }
        return true;
    }

    /// Adds the contours of `glyphId`, mapped through `[a b c d e f]`, to
    /// `parts`.
    ///
    /// ```
    ///   x' = a·x + c·y + e
    ///   y' = b·x + d·y + f
    /// ```
    private void collect(
            int glyphId, double a, double b, double c, double d, double e, double f, List<Contours> parts, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalStateException("composite glyphs nested deeper than " + MAX_DEPTH);
        }
        var start = offset(glyphId);
        var end = offset(glyphId + 1);
        if (end <= start) {
            // No data: an empty glyph, which is legal and is nothing.
            return;
        }
        var contours = glyf.getShort(start);
        if (contours >= 0) {
            parts.add(simple(start, contours, a, b, c, d, e, f));
            return;
        }

        var at = start + 10;
        int flags;
        do {
            flags = Short.toUnsignedInt(glyf.getShort(at));
            var component = Short.toUnsignedInt(glyf.getShort(at + 2));
            at += 4;
            double dx;
            double dy;
            if ((flags & ARGS_ARE_WORDS) != 0) {
                dx = glyf.getShort(at);
                dy = glyf.getShort(at + 2);
                at += 4;
            } else {
                dx = glyf.get(at);
                dy = glyf.get(at + 1);
                at += 2;
            }
            if ((flags & ARGS_ARE_XY) == 0) {
                // Point matching: see the class note.
                dx = 0;
                dy = 0;
            }
            double xx = 1;
            double yx = 0;
            double xy = 0;
            double yy = 1;
            if ((flags & HAS_SCALE) != 0) {
                xx = yy = f2dot14(at);
                at += 2;
            } else if ((flags & HAS_XY_SCALE) != 0) {
                xx = f2dot14(at);
                yy = f2dot14(at + 2);
                at += 4;
            } else if ((flags & HAS_TWO_BY_TWO) != 0) {
                xx = f2dot14(at);
                yx = f2dot14(at + 2);
                xy = f2dot14(at + 4);
                yy = f2dot14(at + 6);
                at += 8;
            }
            // The component's own matrix first, then the one it was placed in.
            collect(
                    component,
                    a * xx + c * yx,
                    b * xx + d * yx,
                    a * xy + c * yy,
                    b * xy + d * yy,
                    a * dx + c * dy + e,
                    b * dx + d * dy + f,
                    parts,
                    depth + 1);
        } while ((flags & MORE_COMPONENTS) != 0);
    }

    private Contours simple(int start, int contours, double a, double b, double c, double d, double e, double f) {
        var endPoints = new int[contours];
        var at = start + 10;
        for (var i = 0; i < contours; i++) {
            endPoints[i] = Short.toUnsignedInt(glyf.getShort(at));
            at += 2;
            if (i > 0 && endPoints[i] < endPoints[i - 1]) {
                throw new IllegalStateException("contour end points go backwards");
            }
        }
        var points = contours == 0 ? 0 : endPoints[contours - 1] + 1;
        var instructions = Short.toUnsignedInt(glyf.getShort(at));
        at += 2 + instructions;

        var flags = new byte[points];
        for (var i = 0; i < points; ) {
            var flag = glyf.get(at++);
            flags[i++] = flag;
            if ((flag & REPEAT) != 0) {
                var repeats = Byte.toUnsignedInt(glyf.get(at++));
                for (var r = 0; r < repeats && i < points; r++) {
                    flags[i++] = flag;
                }
            }
        }

        var xs = new double[points];
        var value = 0;
        for (var i = 0; i < points; i++) {
            var flag = flags[i];
            if ((flag & X_SHORT) != 0) {
                var delta = Byte.toUnsignedInt(glyf.get(at++));
                value += (flag & X_SAME_OR_POSITIVE) != 0 ? delta : -delta;
            } else if ((flag & X_SAME_OR_POSITIVE) == 0) {
                value += glyf.getShort(at);
                at += 2;
            }
            xs[i] = value;
        }
        var ys = new double[points];
        value = 0;
        for (var i = 0; i < points; i++) {
            var flag = flags[i];
            if ((flag & Y_SHORT) != 0) {
                var delta = Byte.toUnsignedInt(glyf.get(at++));
                value += (flag & Y_SAME_OR_POSITIVE) != 0 ? delta : -delta;
            } else if ((flag & Y_SAME_OR_POSITIVE) == 0) {
                value += glyf.getShort(at);
                at += 2;
            }
            ys[i] = value;
        }

        var onCurve = new boolean[points];
        for (var i = 0; i < points; i++) {
            onCurve[i] = (flags[i] & ON_CURVE) != 0;
            var x = xs[i];
            var y = ys[i];
            xs[i] = a * x + c * y + e;
            ys[i] = b * x + d * y + f;
        }
        return new Contours(endPoints, onCurve, xs, ys);
    }

    private int offset(int glyphId) {
        if (longOffsets) {
            return loca.getInt(glyphId * 4);
        }
        // Short offsets are stored halved.
        return Short.toUnsignedInt(loca.getShort(glyphId * 2)) * 2;
    }

    private double f2dot14(int at) {
        return glyf.getShort(at) / 16384.0;
    }

    /// One simple glyph's points, already placed, waiting to be sent.
    ///
    /// A class and not a record: its components are arrays, and a record's
    /// `equals` over arrays compares identities, which is a trap even for a type
    /// nothing compares.
    private static final class Contours {

        private final int[] endPoints;
        private final boolean[] onCurve;
        private final double[] xs;
        private final double[] ys;

        Contours(int[] endPoints, boolean[] onCurve, double[] xs, double[] ys) {
            this.endPoints = endPoints;
            this.onCurve = onCurve;
            this.xs = xs;
            this.ys = ys;
        }

        /// The contours as moves, lines and quadratics.
        ///
        /// TrueType leaves an on-curve point **implied** halfway between two
        /// consecutive off-curve ones, so a run of controls is a chain of
        /// quadratics through their midpoints. A contour may also start on an
        /// off-curve point, in which case it starts at the last point if that is
        /// on the curve, and at the midpoint of the first and last otherwise.
        void emit(OutlineSink sink) {
            var first = 0;
            for (var end : endPoints) {
                if (end >= first) {
                    contour(sink, first, end);
                }
                first = end + 1;
            }
        }

        private void contour(OutlineSink sink, int first, int last) {
            double startX;
            double startY;
            int from;
            int to;
            if (onCurve[first]) {
                startX = xs[first];
                startY = ys[first];
                from = first + 1;
                to = last;
            } else if (onCurve[last]) {
                startX = xs[last];
                startY = ys[last];
                from = first;
                to = last - 1;
            } else {
                startX = (xs[first] + xs[last]) / 2;
                startY = (ys[first] + ys[last]) / 2;
                from = first;
                to = last;
            }
            sink.moveTo(startX, startY);

            var pending = false;
            double controlX = 0;
            double controlY = 0;
            for (var i = from; i <= to; i++) {
                var x = xs[i];
                var y = ys[i];
                if (onCurve[i]) {
                    if (pending) {
                        sink.quadTo(controlX, controlY, x, y);
                        pending = false;
                    } else {
                        sink.lineTo(x, y);
                    }
                } else {
                    if (pending) {
                        sink.quadTo(controlX, controlY, (controlX + x) / 2, (controlY + y) / 2);
                    }
                    controlX = x;
                    controlY = y;
                    pending = true;
                }
            }
            if (pending) {
                sink.quadTo(controlX, controlY, startX, startY);
            }
            sink.close();
        }
    }

    @Override
    public String toString() {
        return "GlyphOutlines[" + glyphCount + " glyphs, " + unitsPerEm + " units/em]";
    }
}
