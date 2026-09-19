package io.github.digitalsmile.goldberry.text.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

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

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledAssets;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.assets.BundledFont.Style;
import io.github.digitalsmile.goldberry.assets.BundledFont.Weight;
import io.github.digitalsmile.goldberry.assets.Face;
import io.github.digitalsmile.goldberry.css.Typography;

/// `docs/gaps.md` G39: faces an application ships, reachable from `font-family`
/// ([ADR-0349]).
///
/// No new font file is committed for this. The "shipped" face is JetBrains
/// Mono's bytes under another family name, which is enough to tell it from Inter
/// by its advance widths, and it proves the book opened *these* bytes rather than
/// a bundled face of the same shape.
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

    private static FontSource source(String family, Weight weight, Style style) {
        return new FontSource(family, weight, style, () -> {
            throw new AssertionError("matching must not read a face");
        });
    }

    private static FontSource shipped(String family, Weight weight, Style style) {
        return FontSource.of(family, weight, style, BundledAssets.font(BundledFont.CODE));
    }
}
