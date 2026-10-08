package dev.goldberry.css.image;

import static dev.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.TestElement;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.css.image.BorderImage.Extent;
import dev.goldberry.css.image.BorderImage.Repeat;
import dev.goldberry.css.image.BorderImage.Slice;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.layout.Length;

/// `border-image` and its five longhands: what each reads, what each refuses,
/// and that a state's rule swaps the picture through the cascade.
class BorderImageParserTest {

    private static final CssLength.Context CONTEXT = CssLength.Context.DEFAULT;

    private static final CssImage.Url PANEL = new CssImage.Url("classpath:/ui/panel.png");

    private static List<Token> tokens(String property, String value) {
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, "button { " + property + ": " + value + " }");
        return sheet.rules().getFirst().declarations().getFirst().value();
    }

    private static Extent px(double value) {
        return new Extent(Extent.Kind.LENGTH, value);
    }

    private static Extent number(double value) {
        return new Extent(Extent.Kind.NUMBER, value);
    }

    private static Slice slice(float top, float right, float bottom, float left, boolean fill) {
        return new Slice(Length.points(top), Length.points(right), Length.points(bottom), Length.points(left), fill);
    }

    @Nested
    @DisplayName("the longhands")
    class Longhands {

        @Test
        @DisplayName("border-image-source: a url, or none")
        void source() {
            var image = BorderImageParser.source(
                    tokens("border-image-source", "url(\"classpath:/ui/panel.png\")"), BorderImage.NONE);

            assertNotNull(image);
            assertEquals(PANEL, image.source());
            assertTrue(image.isDrawn());
            var none = BorderImageParser.source(tokens("border-image-source", "none"), image);
            assertNotNull(none);
            assertFalse(none.isDrawn());
        }

        @Test
        @DisplayName("border-image-slice: one to four numbers or percentages, and fill either side")
        void slices() {
            assertEquals(slice(48, 48, 48, 48, false), sliceOf("48"));
            assertEquals(slice(10, 20, 10, 20, true), sliceOf("10 20 fill"));
            assertEquals(slice(1, 2, 3, 2, true), sliceOf("fill 1 2 3"));
            assertEquals(slice(1, 2, 3, 4, false), sliceOf("1 2 3 4"));
            assertEquals(
                    new Slice(Length.percent(25), Length.points(8), Length.percent(25), Length.points(8), false),
                    sliceOf("25% 8"));
        }

        private static Slice sliceOf(String value) {
            var image = BorderImageParser.slice(tokens("border-image-slice", value), BorderImage.NONE);
            assertNotNull(image, value);
            return image.slice();
        }

        @ParameterizedTest
        @ValueSource(strings = {"48px", "-1", "1 2 3 4 5", "1 fill 2", "fill fill 3", "none"})
        @DisplayName("a slice that is not numbers is refused")
        void sliceRefused(String value) {
            assertNull(BorderImageParser.slice(tokens("border-image-slice", value), BorderImage.NONE), value);
        }

        @Test
        @DisplayName("border-image-width: numbers, lengths, percentages and auto")
        void widths() {
            var image = BorderImageParser.widths(
                    tokens("border-image-width", "48px 2 10% auto"), CONTEXT, BorderImage.NONE);

            assertNotNull(image);
            assertEquals(List.of(px(48), number(2), new Extent(Extent.Kind.PERCENT, 10), Extent.AUTO), image.widths());
            var one = BorderImageParser.widths(tokens("border-image-width", "1em"), CONTEXT, BorderImage.NONE);
            assertNotNull(one);
            assertEquals(List.of(px(16), px(16), px(16), px(16)), one.widths());
        }

        @Test
        @DisplayName("border-image-outset: numbers and lengths")
        void outsets() {
            var image = BorderImageParser.outsets(tokens("border-image-outset", "4px 1"), CONTEXT, BorderImage.NONE);

            assertNotNull(image);
            assertEquals(List.of(px(4), number(1), px(4), number(1)), image.outsets());
            assertNull(BorderImageParser.outsets(tokens("border-image-outset", "10%"), CONTEXT, BorderImage.NONE));
            assertNull(BorderImageParser.outsets(tokens("border-image-outset", "-2px"), CONTEXT, BorderImage.NONE));
        }

        @Test
        @DisplayName("border-image-repeat: one keyword for both axes, or two")
        void repeat() {
            var one = BorderImageParser.repeat(tokens("border-image-repeat", "round"), BorderImage.NONE);
            var two = BorderImageParser.repeat(tokens("border-image-repeat", "repeat stretch"), BorderImage.NONE);

            assertNotNull(one);
            assertNotNull(two);
            assertEquals(Repeat.ROUND, one.across());
            assertEquals(Repeat.ROUND, one.down());
            assertEquals(Repeat.REPEAT, two.across());
            assertEquals(Repeat.STRETCH, two.down());
            assertNull(BorderImageParser.repeat(tokens("border-image-repeat", "space"), BorderImage.NONE));
            assertNull(BorderImageParser.repeat(tokens("border-image-repeat", "round round round"), BorderImage.NONE));
        }
    }

    @Nested
    @DisplayName("the shorthand")
    class Shorthand {

        private static BorderImage read(String value) {
            var image = BorderImageParser.shorthand(tokens("border-image", value), CONTEXT);
            assertNotNull(image, value);
            return image;
        }

        @Test
        @DisplayName("the repro: source, slice with fill, a width and a repeat")
        void repro() {
            var image = read("url(\"classpath:/ui/panel.png\") 48 fill / 48px stretch");

            assertEquals(PANEL, image.source());
            assertEquals(slice(48, 48, 48, 48, true), image.slice());
            assertEquals(List.of(px(48), px(48), px(48), px(48)), image.widths());
            assertEquals(BorderImage.NONE.outsets(), image.outsets());
            assertEquals(Repeat.STRETCH, image.across());
        }

        @Test
        @DisplayName("any order, an outset after two slashes, and no width between them")
        void orderAndOutset() {
            var image = read("round repeat 10 20 / 1 / 4px url(\"classpath:/ui/panel.png\")");

            assertEquals(PANEL, image.source());
            assertEquals(slice(10, 20, 10, 20, false), image.slice());
            assertEquals(List.of(number(1), number(1), number(1), number(1)), image.widths());
            assertEquals(List.of(px(4), px(4), px(4), px(4)), image.outsets());
            assertEquals(Repeat.ROUND, image.across());
            assertEquals(Repeat.REPEAT, image.down());

            var skipped = read("url(\"classpath:/ui/panel.png\") 30 / / 2");
            assertEquals(BorderImage.NONE.widths(), skipped.widths());
            assertEquals(List.of(number(2), number(2), number(2), number(2)), skipped.outsets());
        }

        @Test
        @DisplayName("what it does not name goes back to its initial value")
        void resets() {
            var image = read("url(\"classpath:/ui/panel.png\")");

            assertEquals(BorderImage.NONE.source(PANEL), image);
            assertFalse(read("none").isDrawn());
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "url(\"a.png\") url(\"b.png\")",
                    "48 / ",
                    "48 / / ",
                    "48 / 10% / 10%",
                    "stretch 48 stretch",
                    "linear-gradient(red, blue) 10",
                    "url(\"a.png\") 10 space"
                })
        @DisplayName("what is not a border image is refused whole")
        void refused(String value) {
            assertNull(BorderImageParser.shorthand(tokens("border-image", value), CONTEXT), value);
        }
    }

    @Nested
    @DisplayName("the cascade")
    class Cascade {

        private static ComputedStyle style(String css, TestElement button) {
            var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, css);
            var root = element("window");
            root.with(button);
            return ComputedStyle.of(new StyleResolver(List.of(sheet)).resolve(root.descend(1)), CONTEXT);
        }

        @Test
        @DisplayName(":hover swaps the picture and keeps the slice the plain rule gave")
        void hoverSwapsSource() {
            var css = """
                    button { border-image: url("classpath:/ui/button.png") 12 fill / 12px }
                    button:hover { border-image-source: url("classpath:/ui/button-hover.png") }
                    """;

            var plain = style(css, element("button")).decoration().borderImage();
            var hovered = style(css, element("button:hover")).decoration().borderImage();

            assertEquals(new CssImage.Url("classpath:/ui/button.png"), plain.source());
            assertEquals(new CssImage.Url("classpath:/ui/button-hover.png"), hovered.source());
            assertEquals(plain.slice(), hovered.slice());
            assertEquals(plain.widths(), hovered.widths());
        }

        @Test
        @DisplayName("every longhand is a property the engine has")
        void known() {
            for (var property : List.of(
                    "border-image",
                    "border-image-source",
                    "border-image-slice",
                    "border-image-width",
                    "border-image-outset",
                    "border-image-repeat",
                    "background-size",
                    "background-repeat")) {
                assertTrue(ComputedStyle.isProperty(property), property);
            }
        }
    }
}
