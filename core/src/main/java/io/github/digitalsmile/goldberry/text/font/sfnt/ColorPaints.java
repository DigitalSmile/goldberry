package io.github.digitalsmile.goldberry.text.font.sfnt;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.text.font.sfnt.ColorPaint.ColorLine;
import io.github.digitalsmile.goldberry.text.font.sfnt.ColorPaint.ColorStop;
import io.github.digitalsmile.goldberry.text.font.sfnt.ColorPaint.Colour;
import io.github.digitalsmile.goldberry.text.font.sfnt.ColorPaint.Extend;

/// A face's `COLR` version 1 paint graphs — its colour glyphs, as Noto Color
/// Emoji draws them ([ADR-0456]).
///
/// ## What is read up front, and what on demand
///
/// The **index** is read when the face is: the sorted list of base glyphs that
/// have a graph, the layer list's offsets, the clip boxes and the palette. That
/// is a few flat arrays, four thousand entries long for Noto.
///
/// A glyph's **graph** is read the first time it is asked for and kept. Noto's
/// graphs are some 150,000 nodes between them and a window draws a handful, so
/// parsing all of them to draw a reaction bar would be time and memory spent on
/// emoji nobody sent. Layers are shared between glyphs — the
/// same eyes appear in a dozen faces — so they are cached by layer index as well,
/// and two glyphs that share one hold one record.
///
/// ## What a malformed graph does
///
/// Answers "no colour glyph here", as [ColorLayers] does for a malformed version
/// 0 table: [#paint] returns null, and the pen draws the base glyph's own
/// outline. Every read is bounds-checked by the buffer, recursion is limited to
/// [#MAX_DEPTH], and an unknown paint format is refused rather than skipped — a
/// graph with a hole in it is a different picture from the one the font meant.
///
/// ## Thread safety
///
/// Safe to share. The caches are concurrent, and the records they hold are
/// immutable; two threads that race to parse one glyph both produce the same
/// value and one of them is kept.
public final class ColorPaints {

    private static final int COLR = TableDirectory.tag('C', 'O', 'L', 'R');
    private static final int CPAL = TableDirectory.tag('C', 'P', 'A', 'L');

    /// How deep a graph may nest before it is taken to be a cycle.
    ///
    /// Noto's deepest graph is five levels. Sixty-four is room for any font a
    /// person drew, and small enough that a table that points at itself fails in
    /// microseconds rather than in a stack overflow.
    public static final int MAX_DEPTH = 64;

    /// A face with no version 1 colour glyphs, which is nearly every face.
    public static final ColorPaints NONE = new ColorPaints();

    private final @Nullable ByteBuffer colr;
    private final int[] palette;

    /// The base glyphs that have a graph, ascending.
    private final int[] baseGlyphs;

    /// Where each one's root paint is, as an offset into [#colr].
    private final int[] rootOffsets;

    /// Where the layer list's paints are, as offsets into [#colr].
    private final int[] layerOffsets;

    /// Clip ranges, ascending and disjoint: `[clipFirst[i], clipLast[i]]` shares
    /// the box at `clipBoxes[i]`.
    private final int[] clipFirst;
    private final int[] clipLast;
    private final ClipBox[] clipBoxes;

    private final AtomicReferenceArray<@Nullable ColorPaint> layers;
    private final ConcurrentHashMap<Integer, Optional<ColorPaint>> graphs = new ConcurrentHashMap<>();

    private ColorPaints() {
        this(null, new int[0], new int[0], new int[0], new int[0], new int[0], new int[0], new ClipBox[0]);
    }

    private ColorPaints(
            @Nullable ByteBuffer colr,
            int[] palette,
            int[] baseGlyphs,
            int[] rootOffsets,
            int[] layerOffsets,
            int[] clipFirst,
            int[] clipLast,
            ClipBox[] clipBoxes) {
        this.colr = colr;
        this.palette = palette;
        this.baseGlyphs = baseGlyphs;
        this.rootOffsets = rootOffsets;
        this.layerOffsets = layerOffsets;
        this.clipFirst = clipFirst;
        this.clipLast = clipLast;
        this.clipBoxes = clipBoxes;
        this.layers = new AtomicReferenceArray<>(layerOffsets.length);
    }

    /// The rectangle a colour glyph is drawn inside, in design units, y up.
    ///
    /// A font writes one per glyph so that a renderer knows how large an
    /// offscreen surface a composite needs without walking the graph first —
    /// which is exactly what the painter uses it for.
    public record ClipBox(double xMin, double yMin, double xMax, double yMax) {

        /// Whether the box encloses no area at all.
        public boolean isEmpty() {
            return !(xMax > xMin) || !(yMax > yMin);
        }
    }

    /// The version 1 colour glyphs in `font`, or [#NONE] when it has none.
    ///
    /// [#NONE] rather than an exception for a face this cannot read — no `COLR`,
    /// no `CPAL`, a version 0 table, an index that points outside the table —
    /// for [ColorLayers#read]'s reason: the caller is asking whether there is
    /// colour here, and "no" is an answer it can draw with.
    ///
    /// @param font the face's bytes
    public static ColorPaints read(byte[] font) {
        Objects.requireNonNull(font, "font");
        var colr = TableDirectory.table(font, COLR);
        var cpal = TableDirectory.table(font, CPAL);
        if (colr == null || cpal == null) {
            return NONE;
        }
        try {
            var palette = Palette.first(cpal);
            return palette == null ? NONE : index(colr, palette);
        } catch (RuntimeException e) {
            return NONE;
        }
    }

    private static ColorPaints index(ByteBuffer colr, int[] palette) {
        if (Short.toUnsignedInt(colr.getShort(0)) != 1) {
            // Version 0 has no graph; a version after 1 may have moved the
            // offsets this reads.
            return NONE;
        }
        var baseListOffset = colr.getInt(14);
        var layerListOffset = colr.getInt(18);
        var clipListOffset = colr.getInt(22);
        if (baseListOffset <= 0) {
            return NONE;
        }

        var bases = colr.getInt(baseListOffset);
        if (bases <= 0) {
            return NONE;
        }
        var baseGlyphs = new int[bases];
        var rootOffsets = new int[bases];
        for (var i = 0; i < bases; i++) {
            var at = baseListOffset + 4 + i * 6;
            baseGlyphs[i] = Short.toUnsignedInt(colr.getShort(at));
            rootOffsets[i] = baseListOffset + colr.getInt(at + 2);
            if (i > 0 && baseGlyphs[i] <= baseGlyphs[i - 1]) {
                // Required ascending, and searched as if it were — see the same
                // check in ColorLayers.
                return NONE;
            }
        }

        var layerOffsets = new int[0];
        if (layerListOffset > 0) {
            var count = colr.getInt(layerListOffset);
            if (count < 0 || count > (colr.limit() - layerListOffset) / 4) {
                return NONE;
            }
            layerOffsets = new int[count];
            for (var i = 0; i < count; i++) {
                layerOffsets[i] = layerListOffset + colr.getInt(layerListOffset + 4 + i * 4);
            }
        }

        var clipFirst = new int[0];
        var clipLast = new int[0];
        var clipBoxes = new ClipBox[0];
        if (clipListOffset > 0) {
            var count = colr.getInt(clipListOffset + 1);
            if (count < 0 || count > (colr.limit() - clipListOffset) / 7) {
                return NONE;
            }
            clipFirst = new int[count];
            clipLast = new int[count];
            clipBoxes = new ClipBox[count];
            for (var i = 0; i < count; i++) {
                var at = clipListOffset + 5 + i * 7;
                clipFirst[i] = Short.toUnsignedInt(colr.getShort(at));
                clipLast[i] = Short.toUnsignedInt(colr.getShort(at + 2));
                var box = clipListOffset + uint24(colr, at + 4);
                // Format 1 and 2 share these four fields; 2 appends a variation
                // index, which the default instance does not read.
                clipBoxes[i] = new ClipBox(
                        colr.getShort(box + 1), colr.getShort(box + 3), colr.getShort(box + 5), colr.getShort(box + 7));
            }
        }

        return new ColorPaints(colr, palette, baseGlyphs, rootOffsets, layerOffsets, clipFirst, clipLast, clipBoxes);
    }

    /// Whether this face has any version 1 colour glyphs.
    public boolean isEmpty() {
        return baseGlyphs.length == 0;
    }

    /// How many base glyphs have a paint graph.
    public int size() {
        return baseGlyphs.length;
    }

    /// How many colours the face's first palette holds.
    public int paletteSize() {
        return palette.length;
    }

    /// Whether `glyphId` has a paint graph — without reading it.
    public boolean has(int glyphId) {
        return Arrays.binarySearch(baseGlyphs, glyphId) >= 0;
    }

    /// The paint graph for `glyphId`, or null when it has none or it cannot be
    /// read.
    ///
    /// Read on the first call and kept; every later call for the same glyph is a
    /// map lookup.
    public @Nullable ColorPaint paint(int glyphId) {
        var at = Arrays.binarySearch(baseGlyphs, glyphId);
        if (at < 0) {
            return null;
        }
        return graphs.computeIfAbsent(glyphId, _ -> {
                    try {
                        return Optional.of(parse(rootOffsets[at], 0));
                    } catch (RuntimeException e) {
                        // Out of bounds, too deep, or a format this does not know: the
                        // glyph is drawn as its own outline, and not as half a picture.
                        return Optional.empty();
                    }
                })
                .orElse(null);
    }

    /// The box `glyphId` is drawn inside, or null when the font does not say.
    public @Nullable ClipBox clipBox(int glyphId) {
        // The ranges are ascending and disjoint, so the candidate is the last one
        // that starts at or before the glyph.
        var at = Arrays.binarySearch(clipFirst, glyphId);
        if (at < 0) {
            at = -at - 2;
        }
        return at >= 0 && glyphId <= clipLast[at] ? clipBoxes[at] : null;
    }

    // ------------------------------------------------------------------------
    // The graph, one node at a time. Offsets inside a paint are relative to the
    // start of that paint; `at` is always an offset into the whole table.
    // ------------------------------------------------------------------------

    private ColorPaint parse(int at, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalStateException("a paint graph nested deeper than " + MAX_DEPTH + " levels");
        }
        var table = requireTable();
        var format = Byte.toUnsignedInt(table.get(at));
        return switch (format) {
            case 1 -> layers(Byte.toUnsignedInt(table.get(at + 1)), table.getInt(at + 2), depth);
            case 2, 3 -> new ColorPaint.Solid(colour(table.getShort(at + 1), f2dot14(table, at + 3)));
            case 4, 5 ->
                new ColorPaint.LinearGradient(
                        colorLine(at + uint24(table, at + 1), format == 5),
                        table.getShort(at + 4),
                        table.getShort(at + 6),
                        table.getShort(at + 8),
                        table.getShort(at + 10),
                        table.getShort(at + 12),
                        table.getShort(at + 14));
            case 6, 7 ->
                new ColorPaint.RadialGradient(
                        colorLine(at + uint24(table, at + 1), format == 7),
                        table.getShort(at + 4),
                        table.getShort(at + 6),
                        Short.toUnsignedInt(table.getShort(at + 8)),
                        table.getShort(at + 10),
                        table.getShort(at + 12),
                        Short.toUnsignedInt(table.getShort(at + 14)));
            case 8, 9 ->
                new ColorPaint.SweepGradient(
                        colorLine(at + uint24(table, at + 1), format == 9),
                        table.getShort(at + 4),
                        table.getShort(at + 6),
                        // Half-turns in the file: 1.0 is 180 degrees.
                        f2dot14(table, at + 8) * 180,
                        f2dot14(table, at + 10) * 180);
            case 10 -> new ColorPaint.Glyph(Short.toUnsignedInt(table.getShort(at + 4)), child(at, table, depth));
            case 11 -> new ColorPaint.ColrGlyph(Short.toUnsignedInt(table.getShort(at + 1)));
            case 12, 13 -> {
                var affine = at + uint24(table, at + 4);
                yield new ColorPaint.Transform(
                        fixed(table, affine),
                        fixed(table, affine + 4),
                        fixed(table, affine + 8),
                        fixed(table, affine + 12),
                        fixed(table, affine + 16),
                        fixed(table, affine + 20),
                        child(at, table, depth));
            }
            case 14, 15 ->
                new ColorPaint.Transform(
                        1, 0, 0, 1, table.getShort(at + 4), table.getShort(at + 6), child(at, table, depth));
            case 16, 17 -> scale(f2dot14(table, at + 4), f2dot14(table, at + 6), 0, 0, child(at, table, depth));
            case 18, 19 ->
                scale(
                        f2dot14(table, at + 4),
                        f2dot14(table, at + 6),
                        table.getShort(at + 8),
                        table.getShort(at + 10),
                        child(at, table, depth));
            case 20, 21 -> {
                var s = f2dot14(table, at + 4);
                yield scale(s, s, 0, 0, child(at, table, depth));
            }
            case 22, 23 -> {
                var s = f2dot14(table, at + 4);
                yield scale(s, s, table.getShort(at + 6), table.getShort(at + 8), child(at, table, depth));
            }
            case 24, 25 -> rotate(f2dot14(table, at + 4) * 180, 0, 0, child(at, table, depth));
            case 26, 27 ->
                rotate(
                        f2dot14(table, at + 4) * 180,
                        table.getShort(at + 6),
                        table.getShort(at + 8),
                        child(at, table, depth));
            case 28, 29 ->
                skew(f2dot14(table, at + 4) * 180, f2dot14(table, at + 6) * 180, 0, 0, child(at, table, depth));
            case 30, 31 ->
                skew(
                        f2dot14(table, at + 4) * 180,
                        f2dot14(table, at + 6) * 180,
                        table.getShort(at + 8),
                        table.getShort(at + 10),
                        child(at, table, depth));
            case 32 -> {
                var mode = CompositeMode.of(Byte.toUnsignedInt(table.get(at + 4)));
                if (mode == null) {
                    throw new IllegalStateException("composite mode " + table.get(at + 4) + " is not defined");
                }
                yield new ColorPaint.Composite(
                        parse(at + uint24(table, at + 1), depth + 1),
                        mode,
                        parse(at + uint24(table, at + 5), depth + 1));
            }
            default -> throw new IllegalStateException("paint format " + format + " is not defined");
        };
    }

    /// The paint an `Offset24` at `at + 1` names — where every single-child node
    /// keeps its child.
    private ColorPaint child(int at, ByteBuffer table, int depth) {
        return parse(at + uint24(table, at + 1), depth + 1);
    }

    /// `count` consecutive entries of the layer list, from `first`.
    private ColorPaint layers(int count, int first, int depth) {
        if (first < 0 || first + count > layerOffsets.length) {
            throw new IllegalStateException(
                    "layers " + first + "+" + count + " run past a list of " + layerOffsets.length);
        }
        var list = new ArrayList<ColorPaint>(count);
        for (var i = first; i < first + count; i++) {
            var cached = layers.get(i);
            if (cached == null) {
                cached = parse(layerOffsets[i], depth + 1);
                layers.compareAndSet(i, null, cached);
            }
            list.add(cached);
        }
        return new ColorPaint.Layers(list);
    }

    private ColorLine colorLine(int at, boolean variable) {
        var table = requireTable();
        var extend =
                switch (Byte.toUnsignedInt(table.get(at))) {
                    case 1 -> Extend.REPEAT;
                    case 2 -> Extend.REFLECT;
                    // 0, and anything the specification has not defined yet, which it
                    // says to treat as PAD.
                    default -> Extend.PAD;
                };
        var count = Short.toUnsignedInt(table.getShort(at + 1));
        if (count == 0) {
            throw new IllegalStateException("a colour line with no stops");
        }
        var size = variable ? 10 : 6;
        var stops = new ArrayList<ColorStop>(count);
        for (var i = 0; i < count; i++) {
            var stop = at + 3 + i * size;
            stops.add(new ColorStop(f2dot14(table, stop), colour(table.getShort(stop + 2), f2dot14(table, stop + 4))));
        }
        // The specification does not require the stops sorted; renderers sort
        // them. Stably, so two at one offset keep their order and a hard edge
        // does not turn around.
        stops.sort(Comparator.comparingDouble(ColorStop::offset));
        return new ColorLine(extend, stops);
    }

    private Colour colour(short index, double alpha) {
        var entry = Short.toUnsignedInt(index);
        if (entry == Palette.FOREGROUND || entry >= palette.length) {
            // Out of range follows the text too, as in ColorLayers: the
            // alternative is somebody else's colour.
            return new Colour(0, true, alpha);
        }
        return new Colour(palette[entry], false, alpha);
    }

    private static ColorPaint scale(double sx, double sy, double cx, double cy, ColorPaint child) {
        return around(sx, 0, 0, sy, cx, cy, child);
    }

    /// Counter-clockwise, y up.
    private static ColorPaint rotate(double degrees, double cx, double cy, ColorPaint child) {
        var radians = Math.toRadians(degrees);
        var cos = Math.cos(radians);
        var sin = Math.sin(radians);
        return around(cos, sin, -sin, cos, cx, cy, child);
    }

    /// A positive x skew leans the top of the glyph to the **left** — the
    /// specification's sign, which is opposite to the one a CSS `skewX` uses.
    private static ColorPaint skew(double xDegrees, double yDegrees, double cx, double cy, ColorPaint child) {
        return around(1, Math.tan(Math.toRadians(yDegrees)), -Math.tan(Math.toRadians(xDegrees)), 1, cx, cy, child);
    }

    /// `[xx yx xy yy]` applied about `(cx, cy)` rather than the origin: moved
    /// there, transformed, moved back.
    private static ColorPaint around(
            double xx, double yx, double xy, double yy, double cx, double cy, ColorPaint child) {
        var dx = cx - (xx * cx + xy * cy);
        var dy = cy - (yx * cx + yy * cy);
        return new ColorPaint.Transform(xx, yx, xy, yy, dx, dy, child);
    }

    private ByteBuffer requireTable() {
        var table = colr;
        if (table == null) {
            throw new IllegalStateException("no COLR table");
        }
        return table;
    }

    private static int uint24(ByteBuffer table, int at) {
        return (Byte.toUnsignedInt(table.get(at)) << 16)
                | (Byte.toUnsignedInt(table.get(at + 1)) << 8)
                | Byte.toUnsignedInt(table.get(at + 2));
    }

    /// `F2DOT14`: a signed 2.14 fixed-point number.
    private static double f2dot14(ByteBuffer table, int at) {
        return table.getShort(at) / 16384.0;
    }

    /// `Fixed`: a signed 16.16 fixed-point number.
    private static double fixed(ByteBuffer table, int at) {
        return table.getInt(at) / 65536.0;
    }

    @Override
    public String toString() {
        return "ColorPaints[" + baseGlyphs.length + " glyphs, " + layerOffsets.length + " layers, " + palette.length
                + " colours]";
    }
}
