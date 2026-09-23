package io.github.digitalsmile.goldberry.text.font.sfnt;

import static io.github.digitalsmile.goldberry.text.font.sfnt.SyntheticFont.colourFace;
import static io.github.digitalsmile.goldberry.text.font.sfnt.SyntheticFont.colrV1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.text.font.sfnt.SyntheticFont.Clip;
import io.github.digitalsmile.goldberry.text.font.sfnt.SyntheticFont.Line;
import io.github.digitalsmile.goldberry.text.font.sfnt.SyntheticFont.Stop;

/// The `COLR` version 1 reader, against tables assembled here — [ADR-0456].
///
/// Every paint format the shipped face uses is read back field by field, and
/// the formats it does not use are read too: the reader is for any COLRv1 face,
/// and a format read wrongly draws a plausible wrong picture rather than an
/// error.
class ColorPaintsTest {

    private static final int RED = 0xFFFF0000;
    private static final int GREEN = 0xFF00FF00;
    private static final int BLUE = 0xFF0000FF;

    private static final double EPSILON = 1e-4;

    /// One base glyph, 5, painted `root`, with `layers` in the layer list.
    private static ColorPaints one(SyntheticFont.Paint root, SyntheticFont.Paint... layers) {
        return ColorPaints.read(colourFace(colrV1(Map.of(5, root), List.of(layers), List.of()), RED, GREEN, BLUE));
    }

    private static ColorPaint root(SyntheticFont.Paint paint) {
        var graph = one(paint).paint(5);
        assertNotNull(graph, "the graph was read");
        return graph;
    }

    @Nested
    @DisplayName("what is not a version 1 face")
    class NotVersionOne {

        @Test
        @DisplayName("no COLR, no CPAL, or bytes that are not a font: none, and no exception")
        void nothingToRead() {
            assertSame(ColorPaints.NONE, ColorPaints.read(SyntheticFont.of(Map.of())));
            assertSame(
                    ColorPaints.NONE,
                    ColorPaints.read(SyntheticFont.of(Map.of(
                            "COLR", colrV1(Map.of(5, new SyntheticFont.Solid(0, 1, false)), List.of(), List.of())))),
                    "a graph with no palette has nothing to fill with");
            assertSame(ColorPaints.NONE, ColorPaints.read(new byte[] {1, 2, 3}));
            assertTrue(ColorPaints.NONE.isEmpty());
            assertNull(ColorPaints.NONE.paint(5));
        }

        @Test
        @DisplayName("a version 0 table is ColorLayers' business, not this reader's")
        void versionZeroIsNotAGraph() {
            var colr = colrV1(Map.of(5, new SyntheticFont.Solid(0, 1, false)), List.of(), List.of());
            colr[1] = 0;
            assertSame(ColorPaints.NONE, ColorPaints.read(colourFace(colr, RED)));
        }

        @Test
        @DisplayName("base glyphs out of order are refused, because they are binary-searched")
        void unorderedBasesAreRefused() {
            var bases = new LinkedHashMap<Integer, SyntheticFont.Paint>();
            bases.put(9, new SyntheticFont.Solid(0, 1, false));
            bases.put(5, new SyntheticFont.Solid(1, 1, false));
            assertSame(ColorPaints.NONE, ColorPaints.read(colourFace(colrV1(bases, List.of(), List.of()), RED)));
        }
    }

    @Nested
    @DisplayName("the index")
    class Index {

        @Test
        @DisplayName("says which glyphs have a graph without reading any of them")
        void membership() {
            var bases = new LinkedHashMap<Integer, SyntheticFont.Paint>();
            bases.put(3, new SyntheticFont.Solid(0, 1, false));
            bases.put(8, new SyntheticFont.Solid(1, 1, false));
            var paints = ColorPaints.read(colourFace(colrV1(bases, List.of(), List.of()), RED, GREEN));

            assertEquals(2, paints.size());
            assertEquals(2, paints.paletteSize());
            assertTrue(paints.has(3));
            assertTrue(paints.has(8));
            assertFalse(paints.has(4));
            assertNull(paints.paint(4), "an ordinary glyph has no graph");
        }

        @Test
        @DisplayName("finds a glyph's clip box by the range it falls in, and none outside every range")
        void clipBoxes() {
            var colr = colrV1(
                    Map.of(5, new SyntheticFont.Solid(0, 1, false)),
                    List.of(),
                    List.of(new Clip(2, 4, 0, -100, 1000, 900), new Clip(7, 7, 10, 20, 30, 40)));
            var paints = ColorPaints.read(colourFace(colr, RED));

            assertEquals(new ColorPaints.ClipBox(0, -100, 1000, 900), paints.clipBox(2));
            assertEquals(new ColorPaints.ClipBox(0, -100, 1000, 900), paints.clipBox(4));
            assertEquals(new ColorPaints.ClipBox(10, 20, 30, 40), paints.clipBox(7));
            assertNull(paints.clipBox(1), "before every range");
            assertNull(paints.clipBox(5), "between two");
            assertNull(paints.clipBox(8), "after every range");
        }

        @Test
        @DisplayName("the same graph is handed back on the second ask, not parsed again")
        void graphsAreKept() {
            var paints = one(new SyntheticFont.Solid(0, 1, false));
            assertSame(paints.paint(5), paints.paint(5));
        }
    }

    @Nested
    @DisplayName("the paints")
    class Paints {

        @Test
        @DisplayName("a solid is its palette colour and its alpha; a variable one reads as its default")
        void solid() {
            var solid = assertInstanceOf(ColorPaint.Solid.class, root(new SyntheticFont.Solid(1, 0.5, false)));
            assertEquals(GREEN, solid.colour().argb());
            assertEquals(0.5, solid.colour().alpha(), EPSILON);
            assertFalse(solid.colour().followsText());

            var variable = assertInstanceOf(ColorPaint.Solid.class, root(new SyntheticFont.Solid(2, 1, true)));
            assertEquals(BLUE, variable.colour().argb());
        }

        @Test
        @DisplayName("palette entry 0xFFFF, and one past the palette, follow the text")
        void foreground() {
            var text = assertInstanceOf(ColorPaint.Solid.class, root(new SyntheticFont.Solid(0xFFFF, 1, false)));
            assertTrue(text.colour().followsText());
            assertEquals(0xFF123456, text.colour().resolve(0xFF123456));

            var past = assertInstanceOf(ColorPaint.Solid.class, root(new SyntheticFont.Solid(40, 1, false)));
            assertTrue(past.colour().followsText(), "not somebody else's colour");
        }

        @Test
        @DisplayName("a linear gradient keeps its three points, and its stops come back sorted")
        void linear() {
            var line = new Line(0, new Stop(1, 2, 1), new Stop(0, 0, 1), new Stop(0.5, 1, 0.25));
            var linear = assertInstanceOf(
                    ColorPaint.LinearGradient.class, root(new SyntheticFont.Linear(line, 10, 20, 110, 20, 10, 120)));

            assertEquals(10, linear.x0());
            assertEquals(20, linear.y0());
            assertEquals(110, linear.x1());
            assertEquals(120, linear.y2());
            var stops = linear.line().stops();
            assertEquals(3, stops.size());
            assertEquals(0, stops.get(0).offset(), EPSILON);
            assertEquals(RED, stops.get(0).colour().argb());
            assertEquals(0.5, stops.get(1).offset(), EPSILON);
            assertEquals(0.25, stops.get(1).colour().alpha(), EPSILON);
            assertEquals(BLUE, stops.get(2).colour().argb());
            assertEquals(ColorPaint.Extend.PAD, linear.line().extend());
        }

        @Test
        @DisplayName("a radial gradient keeps both circles, in the font's order")
        void radial() {
            var radial = assertInstanceOf(
                    ColorPaint.RadialGradient.class,
                    root(new SyntheticFont.Radial(new Line(2, new Stop(0, 0, 1)), 1, 2, 3, 4, 5, 600)));
            assertEquals(1, radial.x0());
            assertEquals(2, radial.y0());
            assertEquals(3, radial.r0());
            assertEquals(4, radial.x1());
            assertEquals(5, radial.y1());
            assertEquals(600, radial.r1());
            assertEquals(ColorPaint.Extend.REFLECT, radial.line().extend());
        }

        @Test
        @DisplayName("a sweep's angles are half-turns in the file and degrees here")
        void sweep() {
            var sweep = assertInstanceOf(
                    ColorPaint.SweepGradient.class,
                    root(new SyntheticFont.Sweep(new Line(1, new Stop(0, 0, 1)), 50, 60, 0.5, 1.5)));
            assertEquals(90, sweep.startAngle(), EPSILON);
            assertEquals(270, sweep.endAngle(), EPSILON);
            assertEquals(ColorPaint.Extend.REPEAT, sweep.line().extend());
        }

        @Test
        @DisplayName("a glyph clips its child, and a reference names another graph without following it")
        void glyphAndReference() {
            var glyph = assertInstanceOf(
                    ColorPaint.Glyph.class, root(new SyntheticFont.Glyph(42, new SyntheticFont.ColrGlyph(5))));
            assertEquals(42, glyph.glyphId());
            // A reference to itself, which inlining would loop on.
            assertEquals(new ColorPaint.ColrGlyph(5), glyph.paint());
        }

        @Test
        @DisplayName("layers come out of the layer list, in order, and one layer shared is one record")
        void layers() {
            var paints = one(
                    new SyntheticFont.Layers(2, 1),
                    new SyntheticFont.Solid(0, 1, false),
                    new SyntheticFont.Solid(1, 1, false),
                    new SyntheticFont.Solid(2, 1, false));
            var layers = assertInstanceOf(ColorPaint.Layers.class, paints.paint(5));

            assertEquals(2, layers.layers().size(), "two, starting at the second");
            assertEquals(
                    GREEN, ((ColorPaint.Solid) layers.layers().get(0)).colour().argb());
            assertEquals(
                    BLUE, ((ColorPaint.Solid) layers.layers().get(1)).colour().argb());
        }

        @Test
        @DisplayName("a composite keeps its source, its mode and its backdrop")
        void composite() {
            var composite = assertInstanceOf(
                    ColorPaint.Composite.class,
                    root(new SyntheticFont.Composite(
                            new SyntheticFont.Solid(0, 1, false), 20, new SyntheticFont.Solid(1, 1, false))));
            assertEquals(CompositeMode.SOFT_LIGHT, composite.mode());
            assertEquals(RED, ((ColorPaint.Solid) composite.source()).colour().argb());
            assertEquals(
                    GREEN, ((ColorPaint.Solid) composite.backdrop()).colour().argb());
        }
    }

    @Nested
    @DisplayName("the transforms, every one of which becomes one matrix")
    class Transforms {

        private static ColorPaint.Transform transform(SyntheticFont.Paint paint) {
            return assertInstanceOf(ColorPaint.Transform.class, root(paint));
        }

        private static final SyntheticFont.Paint LEAF = new SyntheticFont.Solid(0, 1, false);

        @Test
        @DisplayName("an affine is read as the table's Affine2x3, in its own field order")
        void affine() {
            var t = transform(new SyntheticFont.Affine(2, 0.5, -0.25, 3, 10, -20, LEAF));
            assertEquals(2, t.xx(), EPSILON);
            assertEquals(0.5, t.yx(), EPSILON);
            assertEquals(-0.25, t.xy(), EPSILON);
            assertEquals(3, t.yy(), EPSILON);
            assertEquals(10, t.dx(), EPSILON);
            assertEquals(-20, t.dy(), EPSILON);
            assertInstanceOf(ColorPaint.Solid.class, t.paint());
        }

        @Test
        @DisplayName("a translate is an identity with an offset")
        void translate() {
            var t = transform(new SyntheticFont.Translate(-7, 9, LEAF));
            assertEquals(1, t.xx(), EPSILON);
            assertEquals(1, t.yy(), EPSILON);
            assertEquals(-7, t.dx(), EPSILON);
            assertEquals(9, t.dy(), EPSILON);
        }

        @Test
        @DisplayName("a scale about a centre leaves the centre where it was")
        void scaleAroundCentre() {
            var t = transform(new SyntheticFont.ScaleAround(0.5, 1.5, 100, 200, LEAF));
            // (100, 200) maps to itself.
            assertEquals(100, t.xx() * 100 + t.xy() * 200 + t.dx(), EPSILON);
            assertEquals(200, t.yx() * 100 + t.yy() * 200 + t.dy(), EPSILON);
            // And (0, 0) moves half way to the centre in x.
            assertEquals(50, t.dx(), EPSILON);
        }

        @Test
        @DisplayName("a rotation is counter-clockwise with y up: a quarter turn takes +x to +y")
        void rotate() {
            var t = transform(new SyntheticFont.Rotate(0.5, LEAF));
            assertEquals(0, t.xx(), EPSILON);
            assertEquals(1, t.yx(), EPSILON, "x axis goes to +y");
            assertEquals(-1, t.xy(), EPSILON);
            assertEquals(0, t.yy(), EPSILON);
        }

        @Test
        @DisplayName("a positive x skew leans the top left, which is the font format's sign and not CSS's")
        void skew() {
            // 45 degrees: a point one unit up moves one unit left.
            var t = transform(new SyntheticFont.Skew(0.25, 0, LEAF));
            assertEquals(-1, t.xy(), EPSILON);
            assertEquals(0, t.yx(), EPSILON);
        }

        @Test
        @DisplayName("a matrix that collapses the plane says it cannot be undone")
        void singular() {
            assertFalse(
                    transform(new SyntheticFont.Affine(1, 2, 2, 4, 0, 0, LEAF)).isInvertible());
            assertTrue(transform(new SyntheticFont.Translate(3, 4, LEAF)).isInvertible());
        }
    }

    @Nested
    @DisplayName("a malformed graph")
    class Malformed {

        @Test
        @DisplayName("an unknown paint format is no graph at all, not half of one")
        void unknownFormat() {
            var paints = one(new SyntheticFont.Glyph(3, new SyntheticFont.Raw((byte) 99, (byte) 0, (byte) 0)));
            assertTrue(paints.has(5), "the index still lists it");
            assertNull(paints.paint(5), "but it is drawn as its own outline");
        }

        @Test
        @DisplayName("an undefined composite mode is refused")
        void unknownMode() {
            var paints = one(new SyntheticFont.Composite(
                    new SyntheticFont.Solid(0, 1, false), 99, new SyntheticFont.Solid(0, 1, false)));
            assertNull(paints.paint(5));
        }

        @Test
        @DisplayName("layers that run past the layer list are refused")
        void layersPastTheEnd() {
            assertNull(one(new SyntheticFont.Layers(3, 0), new SyntheticFont.Solid(0, 1, false))
                    .paint(5));
        }

        @Test
        @DisplayName("a layer list that contains itself stops at the depth limit instead of the stack")
        void cycle() {
            // Layer 0 is "layers 0..0", which is itself.
            var paints = one(new SyntheticFont.Layers(1, 0), new SyntheticFont.Layers(1, 0));
            assertNull(paints.paint(5));
        }

        @Test
        @DisplayName("and a malformed glyph leaves its well-formed neighbours alone")
        void neighboursSurvive() {
            var bases = new LinkedHashMap<Integer, SyntheticFont.Paint>();
            bases.put(3, new SyntheticFont.Raw((byte) 0));
            bases.put(4, new SyntheticFont.Solid(0, 1, false));
            var paints = ColorPaints.read(colourFace(colrV1(bases, List.of(), List.of()), RED));

            assertNull(paints.paint(3));
            assertNotNull(paints.paint(4));
        }
    }

    @Nested
    @DisplayName("the values")
    class Values {

        @Test
        @DisplayName("a colour's alpha multiplies whichever colour it turns out to be")
        void colourResolves() {
            assertEquals(0x80FF0000, new ColorPaint.Colour(RED, false, 128 / 255.0).resolve(0xFF000000));
            assertEquals(0xFF0000FF, new ColorPaint.Colour(RED, true, 1).resolve(BLUE), "the text's own colour");
            assertEquals(0x00FF0000, new ColorPaint.Colour(RED, false, -1).resolve(0), "never below transparent");
        }

        @Test
        @DisplayName("a three-point linear gradient's two-point equivalent is perpendicular to p0→p2")
        void linearNormal() {
            var line = new ColorPaint.ColorLine(
                    ColorPaint.Extend.PAD, List.of(new ColorPaint.ColorStop(0, new ColorPaint.Colour(RED, false, 1))));
            // p0→p2 is vertical, so the ramp runs horizontally and p1's height
            // is dropped.
            var skewed = new ColorPaint.LinearGradient(line, 0, 0, 100, 40, 0, 50);
            var end = skewed.normal();
            assertEquals(100, end[0], EPSILON);
            assertEquals(0, end[1], EPSILON);

            // p0→p2 of no length says nothing, and p1 is kept.
            var bare = new ColorPaint.LinearGradient(line, 0, 0, 30, 40, 0, 0);
            assertEquals(30, bare.normal()[0], EPSILON);
            assertEquals(40, bare.normal()[1], EPSILON);
        }

        @Test
        @DisplayName("a colour line has a stop, and a composite mode is looked up by the byte the font writes")
        void lineAndMode() {
            assertThrows(
                    IllegalArgumentException.class, () -> new ColorPaint.ColorLine(ColorPaint.Extend.PAD, List.of()));
            assertEquals(CompositeMode.CLEAR, CompositeMode.of(0));
            assertEquals(CompositeMode.SRC_IN, CompositeMode.of(5));
            assertEquals(CompositeMode.HSL_LUMINOSITY, CompositeMode.of(27));
            assertNull(CompositeMode.of(28));
            assertNull(CompositeMode.of(-1));
        }
    }
}
