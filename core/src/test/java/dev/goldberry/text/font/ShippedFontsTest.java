package dev.goldberry.text.font;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.ByteArrayInputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.RendererRequirement;
import dev.goldberry.assets.BundledAssets;
import dev.goldberry.assets.BundledFont;
import dev.goldberry.assets.BundledFont.Style;
import dev.goldberry.assets.BundledFont.Weight;
import dev.goldberry.assets.Face;
import dev.goldberry.css.Typography;

/// Faces an application ships, reachable from `font-family` and found after the
/// bundled ones.
///
/// No new font file is committed for this. The "shipped" face is JetBrains
/// Mono's bytes under another family name, which is enough to tell it from Inter
/// by its advance widths, and it proves the book opened *these* bytes rather than
/// a bundled face of the same shape.
///
/// Read more: [Shipping a face](https://goldberry.dev/docs/guide/text.html#shipping-a-face).
class ShippedFontsTest {

    @Nested
    @DisplayName("matching a corner of the matrix")
    class Matching {

        private static final List<FontSource> FORUM = List.of(
                source("Forum", Weight.REGULAR, Style.UPRIGHT),
                source("Forum", Weight.SEMI_BOLD, Style.UPRIGHT),
                source("Forum", Weight.REGULAR, Style.ITALIC));

        /// One row per rung of the fallback ladder: what is asked for, the faces
        /// there are to answer with, and which of them wins — `null` where the
        /// family is nobody's. The first column says which rung it is, so a
        /// failure names the rule rather than an index.
        static Stream<Arguments> ladder() {
            var upright = List.of(FORUM.get(0), FORUM.get(1));
            return Stream.of(
                    arguments("the exact corner wins", FORUM, "forum", Weight.SEMI_BOLD, Style.UPRIGHT, FORUM.get(1)),
                    arguments(
                            "style before weight: a semi-bold italic that is not there is the regular italic",
                            FORUM,
                            "Forum",
                            Weight.SEMI_BOLD,
                            Style.ITALIC,
                            FORUM.get(2)),
                    arguments(
                            "a family with no italic keeps the weight it was asked for, upright",
                            upright,
                            "Forum",
                            Weight.SEMI_BOLD,
                            Style.ITALIC,
                            upright.get(1)),
                    arguments(
                            "a family nobody ships is no match at all",
                            FORUM,
                            "Golos Text",
                            Weight.REGULAR,
                            Style.UPRIGHT,
                            null));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("ladder")
        @DisplayName("the match takes the exact corner, then the style, then the weight, and stops at the family")
        void match(
                String what, List<FontSource> sources, String family, Weight weight, Style style, FontSource expected) {
            assertSame(expected, Face.match(sources, family, weight, style), what);
        }

        @Test
        @DisplayName("the bundled faces answer by the same rule they always did")
        void bundledUnchanged() {
            assertSame(BundledFont.UI_ITALIC, BundledFont.of("Inter", Weight.REGULAR, Style.ITALIC));
            assertSame(BundledFont.UI_STRONG_ITALIC, BundledFont.of("Inter", Weight.SEMI_BOLD, Style.ITALIC));
            assertSame(BundledFont.CODE, BundledFont.of("JetBrains Mono", Weight.SEMI_BOLD, Style.ITALIC));
            assertNull(BundledFont.of("Forum", Weight.REGULAR, Style.UPRIGHT));
        }
    }

    @Nested
    @DisplayName("describing a source")
    class Describing {

        @Test
        @DisplayName("a family a stylesheet cannot write is refused")
        void blankFamily() {
            assertThrows(IllegalArgumentException.class, () -> source(" ", Weight.REGULAR, Style.UPRIGHT));
        }

        @Test
        @DisplayName("two sources for one corner are refused, because one could never be drawn")
        void duplicateCorner() {
            var twice = List.of(
                    source("Forum", Weight.REGULAR, Style.UPRIGHT), source("FORUM", Weight.REGULAR, Style.UPRIGHT));

            assertThrows(IllegalArgumentException.class, () -> Fonts.bundled(twice));
        }

        @Test
        @DisplayName("bytes handed over are copied, so changing the array later changes nothing")
        void copied() {
            var bytes = new byte[] {1, 2, 3};
            var source = FontSource.of("Forum", Weight.REGULAR, Style.UPRIGHT, bytes);
            bytes[0] = 9;

            assertEquals(1, source.bytes().get()[0]);
        }

        @Test
        @DisplayName("a resource that is not there fails when it is read, not when it is named")
        void missingResource() {
            var source =
                    FontSource.resource("Forum", Weight.REGULAR, Style.UPRIGHT, ShippedFontsTest.class, "nope.ttf");

            assertThrows(UncheckedIOException.class, () -> source.bytes().get());
        }
    }

    @Nested
    @DisplayName("in a book")
    class InABook {

        @BeforeEach
        void requireRenderer() {
            RendererRequirement.enforce();
        }

        @Test
        @DisplayName("`font-family: Forum` draws the shipped bytes, not Inter")
        void reachesTheCascadesFamily() {
            var reads = new AtomicInteger();
            var forum = new FontSource("Forum", Weight.REGULAR, Style.UPRIGHT, () -> {
                reads.incrementAndGet();
                return BundledAssets.font(BundledFont.CODE);
            });
            try (var fonts = Fonts.bundled(List.of(forum))) {
                assertEquals(0, reads.get(), "nothing is read until something is drawn in it");

                var shipped = fonts.of(Typography.INITIAL.family("Forum"));
                var inter = fonts.of(Typography.INITIAL);

                assertEquals("Forum", shipped.face().name());
                assertNotSame(inter, shipped);
                assertTrue(
                        Math.abs(advance(shipped, "iiii") - advance(shipped, "WWWW")) < 1e-6,
                        "the shipped bytes are a monospace face, which Inter is not");
                assertSame(shipped, fonts.of(Typography.INITIAL.family("forum")), "one font per face and size");
                assertSame(shipped, fonts.of(forum, 13));
                assertEquals(1, reads.get(), "a face is read once, however often it is asked for");
            }
        }

        @Test
        @DisplayName("a missing corner falls back within the shipped family")
        void fallsBackWithinTheFamily() {
            var forum = shipped("Forum", Weight.REGULAR, Style.UPRIGHT);
            try (var fonts = Fonts.bundled(List.of(forum))) {
                var bold = fonts.of(Typography.INITIAL
                        .family("Forum")
                        .weight(Weight.SEMI_BOLD)
                        .style(Style.ITALIC));

                assertEquals("Forum", bold.face().name());
            }
        }

        @Test
        @DisplayName("`font-weight: 500` draws the 500 an application shipped, and 700 the nearest heavier")
        void numericWeightReachesItsFace() {
            var medium = FontSource.of("Forum", 500, Style.UPRIGHT, BundledAssets.font(BundledFont.CODE));
            var heavy = FontSource.of("Forum", 800, Style.UPRIGHT, BundledAssets.font(BundledFont.CODE));
            try (var fonts = Fonts.bundled(List.of(medium, heavy))) {
                var forum = Typography.INITIAL.family("Forum");

                assertSame(fonts.of(medium, 13), fonts.of(forum.weight(500)));
                assertSame(fonts.of(heavy, 13), fonts.of(forum.weight(700)));
                assertSame(fonts.of(medium, 13), fonts.of(forum), "400 tries heavier up to 500 first");
            }
        }

        @Test
        @DisplayName("the bundled families are searched first, so a file called Inter is not drawn")
        void cannotShadowInter() {
            var impostor = shipped("Inter", Weight.REGULAR, Style.UPRIGHT);
            try (var fonts = Fonts.bundled(List.of(impostor))) {
                assertSame(fonts.of(BundledFont.UI, 13), fonts.of(Typography.INITIAL));
            }
        }

        @Test
        @DisplayName("a face that cannot be opened is drawn in the UI face, and is not asked again")
        void unreadableFallsBack() {
            var reads = new AtomicInteger();
            var broken = new FontSource("Forum", Weight.REGULAR, Style.UPRIGHT, () -> {
                reads.incrementAndGet();
                return new byte[] {0, 1, 2, 3};
            });
            try (var fonts = Fonts.bundled(List.of(broken))) {
                var style = Typography.INITIAL.family("Forum");

                assertSame(fonts.of(BundledFont.UI, 13), fonts.of(style));
                assertSame(fonts.of(BundledFont.UI, 13), fonts.of(style));
                assertEquals(1, reads.get(), "a face that failed once is not re-read every frame");
            }
        }

        @Test
        @DisplayName("a source the book was not opened with is refused by name")
        void foreignSource() {
            try (var fonts = Fonts.bundled()) {
                assertThrows(
                        IllegalArgumentException.class,
                        () -> fonts.of(shipped("Forum", Weight.REGULAR, Style.UPRIGHT), 13));
            }
        }

        private static double advance(Font font, String text) {
            return font.widthOf(text);
        }
    }

    /// A weight is a number, and a family registered at several of them gives
    /// each one to the stylesheet that asks.
    @Nested
    @DisplayName("numeric weights")
    class NumericWeights {

        private static final List<FontSource> GROTESK = List.of(
                source("Grotesk", 500, Style.UPRIGHT),
                source("Grotesk", 600, Style.UPRIGHT),
                source("Grotesk", 700, Style.UPRIGHT),
                source("Grotesk", 800, Style.UPRIGHT));

        @ParameterizedTest(name = "{0}")
        @ValueSource(ints = {500, 600, 700, 800})
        @DisplayName("a family shipped at 500, 600, 700 and 800 answers each with its own face")
        void eachWeightIsItsOwnFace(int weight) {
            var face = Face.match(GROTESK, "Grotesk", weight, Style.UPRIGHT);

            assertEquals(weight, face == null ? -1 : face.weight());
        }

        /// CSS's nearest-weight order, one row per band: the weight asked for,
        /// the weights the family has, and the one that answers.
        static Stream<Arguments> nearest() {
            return Stream.of(
                    arguments("500 over 400 and 600 is the 400, as a browser draws it", 500, List.of(400, 600), 400),
                    arguments("550 over 400 and 600 is the 600", 550, List.of(400, 600), 600),
                    arguments("700 over 400 and 600 is the 600", 700, List.of(400, 600), 600),
                    arguments("400 tries heavier up to 500 first", 400, List.of(300, 500, 800), 500),
                    arguments("420 with nothing up to 500 goes lighter before heavier", 420, List.of(300, 600), 300),
                    arguments("above 500 goes heavier first", 600, List.of(500, 900), 900),
                    arguments("above 500 with nothing heavier takes the nearest lighter", 900, List.of(300, 800), 800),
                    arguments("below 400 goes lighter first", 350, List.of(300, 400), 300),
                    arguments("below 400 with nothing lighter takes the nearest heavier", 250, List.of(400, 300), 300));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("nearest")
        @DisplayName("a weight the family does not have is the nearest one by CSS's rule")
        void nearestByCss(String what, int asked, List<Integer> available, int expected) {
            var faces = available.stream()
                    .map(weight -> source("Grotesk", weight, Style.UPRIGHT))
                    .toList();

            var face = Face.match(faces, "Grotesk", asked, Style.UPRIGHT);

            assertEquals(expected, face == null ? -1 : face.weight(), what);
        }

        @Test
        @DisplayName("style still comes before weight")
        void styleFirst() {
            var faces = List.of(source("Grotesk", 700, Style.UPRIGHT), source("Grotesk", 300, Style.ITALIC));

            assertSame(faces.get(1), Face.match(faces, "Grotesk", 700, Style.ITALIC));
            assertSame(faces.get(0), Face.match(faces, "Grotesk", 300, Style.UPRIGHT));
        }

        @Test
        @DisplayName("a family that ships only a semi-bold italic is still that family")
        void anyFaceOfTheFamily() {
            // CSS draws a family in whatever it has rather than in another family.
            var only = List.of(source("Grotesk", 600, Style.ITALIC));

            assertSame(only.getFirst(), Face.match(only, "Grotesk", 400, Style.UPRIGHT));
        }

        @Test
        @DisplayName("a weight CSS cannot write is refused, on a source and in a match")
        void outOfRange() {
            assertThrows(IllegalArgumentException.class, () -> source("Grotesk", 0, Style.UPRIGHT));
            assertThrows(IllegalArgumentException.class, () -> source("Grotesk", 1001, Style.UPRIGHT));
            assertThrows(IllegalArgumentException.class, () -> Face.match(GROTESK, "Grotesk", 0, Style.UPRIGHT));
            assertThrows(IllegalArgumentException.class, () -> Typography.INITIAL.weight(1001));
        }

        @Test
        @DisplayName("the named weights are the numbers 400 and 600")
        void namedWeights() {
            assertEquals(400, source("Grotesk", Weight.REGULAR, Style.UPRIGHT).weight());
            assertEquals(600, Typography.INITIAL.weight(Weight.SEMI_BOLD).weight());
            assertSame(BundledFont.UI_STRONG, BundledFont.of("Inter", 700, Style.UPRIGHT));
            assertSame(BundledFont.UI, BundledFont.of("Inter", 500, Style.UPRIGHT));
        }
    }

    /// The book looks for every file when it opens, so a missing one is a line
    /// at start rather than a fallback found on a screen later.
    @Nested
    @DisplayName("when the book opens")
    class Probing {

        @Test
        @DisplayName("a resource that is not there is unreadable at once, and nothing is parsed to find out")
        void missingResource() {
            var missing = FontSource.resource("Forum", 400, Style.ITALIC, ShippedFontsTest.class, "nope.ttf");
            var reads = new AtomicInteger();
            var present = new FontSource("Forum", 400, Style.UPRIGHT, () -> {
                reads.incrementAndGet();
                return new byte[0];
            });

            try (var fonts = Fonts.bundled(List.of(present, missing))) {
                assertEquals(List.of(missing), fonts.unreadable());
                assertEquals(0, reads.get(), "bytes from anywhere else are taken on trust until drawn");
            }
        }

        @Test
        @DisplayName("the reason names the file and where it was looked for")
        void reasonNamesTheFile() {
            var missing = FontSource.resource("Forum", 400, Style.ITALIC, ShippedFontsTest.class, "nope.ttf");

            var problem = missing.problem().orElseThrow();

            assertTrue(problem.contains("dev/goldberry/text/font/nope.ttf"), problem);
            assertTrue(problem.contains(ShippedFontsTest.class.getName()), problem);
        }

        @Test
        @DisplayName("a stream is opened and closed unread, and read for real only when drawn")
        void streamIsOpenedNotRead() {
            var opened = new AtomicInteger();
            var closed = new AtomicInteger();
            var source = FontSource.stream("Forum", 400, Style.UPRIGHT, () -> {
                opened.incrementAndGet();
                return new ByteArrayInputStream(new byte[] {1, 2, 3}) {
                    @Override
                    public void close() {
                        closed.incrementAndGet();
                    }
                };
            });

            try (var fonts = Fonts.bundled(List.of(source))) {
                assertEquals(List.of(), fonts.unreadable());
                assertEquals(1, opened.get());
                assertEquals(1, closed.get(), "the probe closes what it opened");
            }
            assertArrayEquals(new byte[] {1, 2, 3}, source.bytes().get());
            assertEquals(2, closed.get());
        }

        @Test
        @DisplayName("a stream supplier that answers null, or throws, is a face that is not there")
        void nullStream() {
            var none = FontSource.stream("Forum", 400, Style.UPRIGHT, () -> null);
            var thrown = FontSource.stream("Forum", 700, Style.UPRIGHT, () -> {
                throw new IllegalStateException("no disk");
            });

            try (var fonts = Fonts.bundled(List.of(none, thrown))) {
                assertEquals(List.of(none, thrown), fonts.unreadable());
            }
            assertThrows(UncheckedIOException.class, () -> none.bytes().get());
        }
    }

    private static FontSource source(String family, int weight, Style style) {
        return new FontSource(family, weight, style, () -> {
            throw new AssertionError("matching must not read a face");
        });
    }

    private static FontSource source(String family, Weight weight, Style style) {
        return new FontSource(family, weight, style, () -> {
            throw new AssertionError("matching must not read a face");
        });
    }

    private static FontSource shipped(String family, Weight weight, Style style) {
        return FontSource.of(family, weight, style, BundledAssets.font(BundledFont.CODE));
    }
}
