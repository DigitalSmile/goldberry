package io.github.digitalsmile.goldberry.text.font.sfnt;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/// Fonts assembled byte by byte, for the table readers' tests.
///
/// `:core` ships no colour face — it lives in `goldberry-emoji` — so what is
/// checked here is the **format**, including the malformed shapes a real face
/// never produces and a reader must not crash on. Each builder writes exactly
/// the fields its reader reads and nothing more; that is enough for
/// [TableDirectory] and for the readers, and deliberately not enough for a
/// rasterizer.
public final class SyntheticFont {

    private SyntheticFont() {}

    /// A font of `tables`, keyed by tag, in the order given, each starting on a
    /// four-byte boundary as the format asks.
    public static byte[] of(Map<String, byte[]> tables) {
        var directory = ByteBuffer.allocate(12 + tables.size() * 16).order(ByteOrder.BIG_ENDIAN);
        directory.putInt(0x00010000);
        directory.putShort((short) tables.size());
        directory.putShort((short) 0);
        directory.putShort((short) 0);
        directory.putShort((short) 0);

        var at = 12 + tables.size() * 16;
        for (var table : tables.entrySet()) {
            var tag = table.getKey();
            directory.putInt(TableDirectory.tag(tag.charAt(0), tag.charAt(1), tag.charAt(2), tag.charAt(3)));
            directory.putInt(0);
            directory.putInt(at);
            directory.putInt(table.getValue().length);
            at += padded(table.getValue().length);
        }

        var out = new ByteArrayOutputStream();
        out.writeBytes(directory.array());
        for (var table : tables.values()) {
            out.writeBytes(table);
            out.writeBytes(new byte[padded(table.length) - table.length]);
        }
        return out.toByteArray();
    }

    private static int padded(int length) {
        return (length + 3) & ~3;
    }

    /// `font` with `extra` tables added — or replacing ones of the same tag —
    /// and the directory sorted by tag, as parsers that binary-search it want.
    ///
    /// What lets a test give a real face colour glyphs: the rasterizer and the
    /// shaper read the real outlines and metrics, and the colour tables name
    /// glyphs that exist.
    public static byte[] splice(byte[] font, Map<String, byte[]> extra) {
        var in = ByteBuffer.wrap(font).order(ByteOrder.BIG_ENDIAN);
        var tables = new TreeMap<String, byte[]>();
        var count = Short.toUnsignedInt(in.getShort(4));
        for (var i = 0; i < count; i++) {
            var record = 12 + i * 16;
            var tag = in.getInt(record);
            var name = new String(new char[] {
                (char) (tag >>> 24), (char) ((tag >>> 16) & 0xFF), (char) ((tag >>> 8) & 0xFF), (char) (tag & 0xFF)
            });
            var offset = in.getInt(record + 8);
            var length = in.getInt(record + 12);
            tables.put(name, Arrays.copyOfRange(font, offset, offset + length));
        }
        tables.putAll(extra);
        return of(tables);
    }

    /// One palette of `colours`, each `0xAARRGGBB`, written in the file's BGRA.
    public static byte[] cpal(int... colours) {
        var out = ByteBuffer.allocate(14 + colours.length * 4).order(ByteOrder.BIG_ENDIAN);
        out.putShort((short) 0);
        out.putShort((short) colours.length);
        out.putShort((short) 1);
        out.putShort((short) colours.length);
        out.putInt(14);
        out.putShort((short) 0);
        for (var argb : colours) {
            out.put((byte) argb);
            out.put((byte) (argb >>> 8));
            out.put((byte) (argb >>> 16));
            out.put((byte) (argb >>> 24));
        }
        return out.array();
    }

    // ------------------------------------------------------------------------
    // COLR version 1
    // ------------------------------------------------------------------------

    /// A paint, as a test writes one. Serialized parent first, children after
    /// it, because every child offset in the format is unsigned and relative to
    /// the parent.
    public sealed interface Paint {}

    public record Layers(int count, int first) implements Paint {}

    public record Solid(int palette, double alpha, boolean variable) implements Paint {}

    public record Stop(double offset, int palette, double alpha) {}

    public record Line(int extend, Stop... stops) {}

    public record Linear(Line line, int x0, int y0, int x1, int y1, int x2, int y2) implements Paint {}

    public record Radial(Line line, int x0, int y0, int r0, int x1, int y1, int r1) implements Paint {}

    public record Sweep(Line line, int cx, int cy, double startHalfTurns, double endHalfTurns) implements Paint {}

    public record Glyph(int glyphId, Paint paint) implements Paint {}

    public record ColrGlyph(int glyphId) implements Paint {}

    public record Affine(double xx, double yx, double xy, double yy, double dx, double dy, Paint paint)
            implements Paint {}

    public record Translate(int dx, int dy, Paint paint) implements Paint {}

    public record ScaleAround(double sx, double sy, int cx, int cy, Paint paint) implements Paint {}

    public record Rotate(double halfTurns, Paint paint) implements Paint {}

    public record Skew(double xHalfTurns, double yHalfTurns, Paint paint) implements Paint {}

    public record Composite(Paint source, int mode, Paint backdrop) implements Paint {}

    /// Bytes written as they are, for a format this reader must refuse.
    public record Raw(byte... bytes) implements Paint {}

    /// A clip box over glyphs `[first, last]`.
    public record Clip(int first, int last, int xMin, int yMin, int xMax, int yMax) {}

    /// A `COLR` version 1 table.
    ///
    /// @param bases  base glyph → root paint, written in the map's order (so a
    ///               test can write them out of order on purpose)
    /// @param layers the layer list
    /// @param clips  the clip list, or empty for none
    public static byte[] colrV1(Map<Integer, Paint> bases, List<Paint> layers, List<Clip> clips) {
        var out = new Writer();
        // Header: version, then the version 0 fields all zero, then five
        // version 1 offsets patched below.
        out.u16(1);
        out.u16(0);
        out.u32(0);
        out.u32(0);
        out.u16(0);
        var baseListField = out.reserve(4);
        var layerListField = out.reserve(4);
        var clipListField = out.reserve(4);
        out.u32(0);
        out.u32(0);

        var baseList = out.position();
        out.patch32(baseListField, baseList);
        out.u32(bases.size());
        var records = new ArrayList<int[]>();
        for (var base : bases.entrySet()) {
            out.u16(base.getKey());
            records.add(new int[] {out.reserve(4)});
        }
        var index = 0;
        for (var base : bases.values()) {
            var at = out.write(base);
            out.patch32(records.get(index++)[0], at - baseList);
        }

        if (!layers.isEmpty()) {
            var layerList = out.position();
            out.patch32(layerListField, layerList);
            out.u32(layers.size());
            var fields = new int[layers.size()];
            for (var i = 0; i < layers.size(); i++) {
                fields[i] = out.reserve(4);
            }
            for (var i = 0; i < layers.size(); i++) {
                out.patch32(fields[i], out.write(layers.get(i)) - layerList);
            }
        }

        if (!clips.isEmpty()) {
            var clipList = out.position();
            out.patch32(clipListField, clipList);
            out.u8(1);
            out.u32(clips.size());
            var fields = new int[clips.size()];
            for (var i = 0; i < clips.size(); i++) {
                out.u16(clips.get(i).first());
                out.u16(clips.get(i).last());
                fields[i] = out.reserve(3);
            }
            for (var i = 0; i < clips.size(); i++) {
                var box = out.position();
                out.patch24(fields[i], box - clipList);
                var clip = clips.get(i);
                out.u8(1);
                out.u16(clip.xMin());
                out.u16(clip.yMin());
                out.u16(clip.xMax());
                out.u16(clip.yMax());
            }
        }
        return out.bytes();
    }

    /// A face with `colr` and a palette of `colours` — everything [ColorPaints]
    /// reads.
    public static byte[] colourFace(byte[] colr, int... colours) {
        var tables = new LinkedHashMap<String, byte[]>();
        tables.put("COLR", colr);
        tables.put("CPAL", cpal(colours));
        return of(tables);
    }

    /// A growable big-endian buffer that knows how to lay out a paint graph.
    private static final class Writer {

        private byte[] bytes = new byte[256];
        private int size;

        int position() {
            return size;
        }

        byte[] bytes() {
            return Arrays.copyOf(bytes, size);
        }

        void u8(int value) {
            ensure(1);
            bytes[size++] = (byte) value;
        }

        void u16(int value) {
            u8(value >>> 8);
            u8(value);
        }

        void u24(int value) {
            u8(value >>> 16);
            u16(value);
        }

        void u32(int value) {
            u16(value >>> 16);
            u16(value);
        }

        void f2dot14(double value) {
            u16((int) Math.round(value * 16384));
        }

        void fixed(double value) {
            u32((int) Math.round(value * 65536));
        }

        int reserve(int count) {
            var at = size;
            for (var i = 0; i < count; i++) {
                u8(0);
            }
            return at;
        }

        void patch24(int at, int value) {
            bytes[at] = (byte) (value >>> 16);
            bytes[at + 1] = (byte) (value >>> 8);
            bytes[at + 2] = (byte) value;
        }

        void patch32(int at, int value) {
            bytes[at] = (byte) (value >>> 24);
            patch24(at + 1, value);
        }

        /// Writes `paint` and everything under it; answers where it starts.
        int write(Paint paint) {
            var at = size;
            switch (paint) {
                case Layers layers -> {
                    u8(1);
                    u8(layers.count());
                    u32(layers.first());
                }
                case Solid solid -> {
                    u8(solid.variable() ? 3 : 2);
                    u16(solid.palette());
                    f2dot14(solid.alpha());
                    if (solid.variable()) {
                        u32(0);
                    }
                }
                case Linear linear -> {
                    u8(4);
                    var line = reserve(3);
                    for (var v :
                            new int[] {linear.x0(), linear.y0(), linear.x1(), linear.y1(), linear.x2(), linear.y2()}) {
                        u16(v);
                    }
                    patch24(line, line(linear.line()) - at);
                }
                case Radial radial -> {
                    u8(6);
                    var line = reserve(3);
                    for (var v :
                            new int[] {radial.x0(), radial.y0(), radial.r0(), radial.x1(), radial.y1(), radial.r1()}) {
                        u16(v);
                    }
                    patch24(line, line(radial.line()) - at);
                }
                case Sweep sweep -> {
                    u8(8);
                    var line = reserve(3);
                    u16(sweep.cx());
                    u16(sweep.cy());
                    f2dot14(sweep.startHalfTurns());
                    f2dot14(sweep.endHalfTurns());
                    patch24(line, line(sweep.line()) - at);
                }
                case Glyph glyph -> {
                    u8(10);
                    var child = reserve(3);
                    u16(glyph.glyphId());
                    patch24(child, write(glyph.paint()) - at);
                }
                case ColrGlyph reference -> {
                    u8(11);
                    u16(reference.glyphId());
                }
                case Affine affine -> {
                    u8(12);
                    var child = reserve(3);
                    var matrix = reserve(3);
                    patch24(matrix, size - at);
                    for (var v :
                            new double[] {affine.xx(), affine.yx(), affine.xy(), affine.yy(), affine.dx(), affine.dy()
                            }) {
                        fixed(v);
                    }
                    patch24(child, write(affine.paint()) - at);
                }
                case Translate translate -> {
                    u8(14);
                    var child = reserve(3);
                    u16(translate.dx());
                    u16(translate.dy());
                    patch24(child, write(translate.paint()) - at);
                }
                case ScaleAround scale -> {
                    u8(18);
                    var child = reserve(3);
                    f2dot14(scale.sx());
                    f2dot14(scale.sy());
                    u16(scale.cx());
                    u16(scale.cy());
                    patch24(child, write(scale.paint()) - at);
                }
                case Rotate rotate -> {
                    u8(24);
                    var child = reserve(3);
                    f2dot14(rotate.halfTurns());
                    patch24(child, write(rotate.paint()) - at);
                }
                case Skew skew -> {
                    u8(28);
                    var child = reserve(3);
                    f2dot14(skew.xHalfTurns());
                    f2dot14(skew.yHalfTurns());
                    patch24(child, write(skew.paint()) - at);
                }
                case Composite composite -> {
                    u8(32);
                    var source = reserve(3);
                    u8(composite.mode());
                    var backdrop = reserve(3);
                    patch24(source, write(composite.source()) - at);
                    patch24(backdrop, write(composite.backdrop()) - at);
                }
                case Raw raw -> {
                    for (var b : raw.bytes()) {
                        u8(b);
                    }
                }
            }
            return at;
        }

        private int line(Line line) {
            var at = size;
            u8(line.extend());
            u16(line.stops().length);
            for (var stop : line.stops()) {
                f2dot14(stop.offset());
                u16(stop.palette());
                f2dot14(stop.alpha());
            }
            return at;
        }

        private void ensure(int more) {
            if (size + more > bytes.length) {
                bytes = Arrays.copyOf(bytes, Math.max(bytes.length * 2, size + more));
            }
        }
    }

    // ------------------------------------------------------------------------
    // glyf and loca
    // ------------------------------------------------------------------------

    /// A face whose glyphs are `glyphs`, each already encoded as `glyf` data —
    /// an empty array for a glyph with no outline.
    public static byte[] outlineFace(int unitsPerEm, byte[]... glyphs) {
        var glyf = new ByteArrayOutputStream();
        var loca = ByteBuffer.allocate((glyphs.length + 1) * 4).order(ByteOrder.BIG_ENDIAN);
        for (var glyph : glyphs) {
            loca.putInt(glyf.size());
            glyf.writeBytes(glyph);
            // Glyph data is conventionally 4-byte aligned; nothing here needs it.
        }
        loca.putInt(glyf.size());

        var head = ByteBuffer.allocate(54).order(ByteOrder.BIG_ENDIAN);
        head.putShort(18, (short) unitsPerEm);
        head.putShort(50, (short) 1);
        var maxp = ByteBuffer.allocate(6).order(ByteOrder.BIG_ENDIAN);
        maxp.putInt(0, 0x00005000);
        maxp.putShort(4, (short) glyphs.length);

        var tables = new LinkedHashMap<String, byte[]>();
        tables.put("head", head.array());
        tables.put("maxp", maxp.array());
        tables.put("loca", loca.array());
        tables.put("glyf", glyf.toByteArray());
        return of(tables);
    }

    /// One simple glyph: `contours` of `{x, y, onCurve}` points, with every
    /// coordinate written as a full 16-bit delta so the test controls nothing
    /// but the points.
    public static byte[] simpleGlyph(int[][]... contours) {
        var out = new Writer();
        out.u16(contours.length);
        out.u16(0);
        out.u16(0);
        out.u16(0);
        out.u16(0);
        var end = -1;
        for (var contour : contours) {
            end += contour.length;
            out.u16(end);
        }
        out.u16(0);
        for (var contour : contours) {
            for (var point : contour) {
                out.u8(point[2] != 0 ? 0x01 : 0x00);
            }
        }
        var previous = 0;
        for (var contour : contours) {
            for (var point : contour) {
                out.u16(point[0] - previous);
                previous = point[0];
            }
        }
        previous = 0;
        for (var contour : contours) {
            for (var point : contour) {
                out.u16(point[1] - previous);
                previous = point[1];
            }
        }
        return out.bytes();
    }

    /// A composite glyph: each component `{glyphId, dx, dy}`, and a uniform
    /// scale applied to all of them when `scale` is not 1.
    public static byte[] compositeGlyph(double scale, int[]... components) {
        var out = new Writer();
        out.u16(0xFFFF);
        out.u16(0);
        out.u16(0);
        out.u16(0);
        out.u16(0);
        for (var i = 0; i < components.length; i++) {
            var flags = 0x0001 | 0x0002 | (scale != 1 ? 0x0008 : 0) | (i < components.length - 1 ? 0x0020 : 0);
            out.u16(flags);
            out.u16(components[i][0]);
            out.u16(components[i][1]);
            out.u16(components[i][2]);
            if (scale != 1) {
                out.f2dot14(scale);
            }
        }
        return out.bytes();
    }

    /// Every call an [OutlineSink] received, as text — the easiest thing to
    /// compare exactly.
    static final class RecordingSink implements OutlineSink {

        final List<String> calls = new ArrayList<>();

        @Override
        public void moveTo(double x, double y) {
            calls.add("M " + n(x) + " " + n(y));
        }

        @Override
        public void lineTo(double x, double y) {
            calls.add("L " + n(x) + " " + n(y));
        }

        @Override
        public void quadTo(double cx, double cy, double x, double y) {
            calls.add("Q " + n(cx) + " " + n(cy) + " " + n(x) + " " + n(y));
        }

        @Override
        public void close() {
            calls.add("Z");
        }

        private static String n(double value) {
            return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
        }
    }
}
