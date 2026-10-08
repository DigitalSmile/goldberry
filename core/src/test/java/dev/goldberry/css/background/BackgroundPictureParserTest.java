package dev.goldberry.css.background;

import static dev.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.css.image.CssImage;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.layout.Length;

/// `background-image: url()`, `background-size` and `background-repeat`: what
/// each reads, what each refuses, and what reaches a computed style.
class BackgroundPictureParserTest {

    private static final CssLength.Context CONTEXT = CssLength.Context.DEFAULT;

    private static final CssImage.Url LEATHER = new CssImage.Url("classpath:/ui/leather.png");

    private static List<Token> tokens(String property, String value) {
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, "button { " + property + ": " + value + " }");
        return sheet.rules().getFirst().declarations().getFirst().value();
    }

    private static ComputedStyle compute(String declarations) {
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, "button { " + declarations + " }");
        var root = element("window");
        root.with(element("button"));
        return ComputedStyle.of(new StyleResolver(List.of(sheet)).resolve(root.descend(1)), CONTEXT);
    }

    @Nested
    @DisplayName("background-image")
    class Images {

        @Test
        @DisplayName("a url() is a layer beside the gradients, in the order written")
        void urlAmongGradients() {
            var layers = BackgroundParser.images(
                    tokens("background-image", "url(\"classpath:/ui/leather.png\"), linear-gradient(red, blue)"),
                    CONTEXT);

            assertNotNull(layers);
            assertEquals(LEATHER, layers.getFirst());
            assertInstanceOf(GradientLayer.Linear.class, layers.get(1));
        }

        @Test
        @DisplayName("a region of a sheet is an address like any other")
        void region() {
            var layers = BackgroundParser.images(
                    tokens("background-image", "url('classpath:/ui/kit.png#xywh=0,0,64,64')"), CONTEXT);

            assertNotNull(layers);
            var url = assertInstanceOf(CssImage.Url.class, layers.getFirst());
            assertEquals(64, Objects.requireNonNull(url.address().region()).width());
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "url(ui/leather.png)",
                    "url(\"\")",
                    "url(\"a.png\" \"b.png\")",
                    "url(\"a.png#xywh=1,2,3\")",
                    "url(\"a.png\") url(\"b.png\")"
                })
        @DisplayName("an unquoted url, an empty one or a malformed region is refused")
        void refused(String value) {
            assertNull(BackgroundParser.images(tokens("background-image", value), CONTEXT), value);
        }

        @Test
        @DisplayName("it reaches the computed background")
        void cascade() {
            var fill = compute("background-image: url(\"classpath:/ui/leather.png\"); background-color: #102030")
                    .fill();

            assertEquals(List.of(LEATHER), fill.layers());
            assertEquals(0xFF102030, fill.colour());
            assertTrue(fill.hasPictures());
        }
    }

    @Nested
    @DisplayName("background-size")
    class Sizes {

        @Test
        @DisplayName("cover, contain, one length, two, percentages and auto")
        void reads() {
            assertEquals(
                    List.of(BackgroundSize.COVER), BackgroundParser.sizes(tokens("background-size", "cover"), CONTEXT));
            assertEquals(
                    List.of(BackgroundSize.CONTAIN),
                    BackgroundParser.sizes(tokens("background-size", "contain"), CONTEXT));
            assertEquals(
                    List.of(new BackgroundSize(BackgroundSize.Kind.LENGTHS, Length.points(64), Length.AUTO)),
                    BackgroundParser.sizes(tokens("background-size", "64px"), CONTEXT));
            assertEquals(
                    List.of(new BackgroundSize(BackgroundSize.Kind.LENGTHS, Length.percent(50), Length.points(32))),
                    BackgroundParser.sizes(tokens("background-size", "50% 2em"), CONTEXT));
            assertEquals(
                    List.of(BackgroundSize.AUTO, BackgroundSize.COVER),
                    BackgroundParser.sizes(tokens("background-size", "auto, cover"), CONTEXT));
        }

        @ParameterizedTest
        @ValueSource(strings = {"cover contain", "-4px", "1px 2px 3px", "big", "cover 10px"})
        @DisplayName("what is not a size is refused")
        void refused(String value) {
            assertNull(BackgroundParser.sizes(tokens("background-size", value), CONTEXT), value);
        }

        @Test
        @DisplayName("cover fills the box, contain fits in it, one length keeps the shape")
        void resolves() {
            assertSize(new double[] {200, 100}, BackgroundSize.COVER.resolve(200, 50, 40, 20));
            assertSize(new double[] {100, 50}, BackgroundSize.CONTAIN.resolve(200, 50, 40, 20));
            assertSize(new double[] {40, 20}, BackgroundSize.AUTO.resolve(200, 50, 40, 20));
            assertSize(
                    new double[] {80, 40},
                    new BackgroundSize(BackgroundSize.Kind.LENGTHS, Length.points(80), Length.AUTO)
                            .resolve(200, 50, 40, 20));
            assertSize(
                    new double[] {100, 25},
                    new BackgroundSize(BackgroundSize.Kind.LENGTHS, Length.percent(50), Length.percent(50))
                            .resolve(200, 50, 40, 20));
        }

        @Test
        @DisplayName("a size applied before the image it sizes is kept")
        void beforeTheImage() {
            var fill = compute("background-size: cover; background-image: url(\"classpath:/ui/leather.png\")")
                    .fill();

            assertEquals(List.of(BackgroundSize.COVER), fill.sizes());
            assertEquals(BackgroundSize.COVER, fill.size(3), "a short list repeats over the layers");
        }

        private static void assertSize(double[] expected, double[] actual) {
            assertArrayEquals(expected, actual, 1e-9);
        }
    }

    @Nested
    @DisplayName("background-repeat")
    class Repeats {

        @Test
        @DisplayName("the four keywords, the two-value form and a list")
        void reads() {
            assertEquals(
                    List.of(BackgroundRepeat.NO_REPEAT),
                    BackgroundParser.repeats(tokens("background-repeat", "no-repeat")));
            assertEquals(
                    List.of(BackgroundRepeat.REPEAT_X),
                    BackgroundParser.repeats(tokens("background-repeat", "repeat-x")));
            assertEquals(
                    List.of(BackgroundRepeat.REPEAT_Y),
                    BackgroundParser.repeats(tokens("background-repeat", "repeat-y")));
            assertEquals(
                    List.of(BackgroundRepeat.REPEAT_X),
                    BackgroundParser.repeats(tokens("background-repeat", "repeat no-repeat")));
            assertEquals(
                    List.of(BackgroundRepeat.REPEAT, BackgroundRepeat.NO_REPEAT),
                    BackgroundParser.repeats(tokens("background-repeat", "repeat, no-repeat")));
        }

        @ParameterizedTest
        @ValueSource(strings = {"space", "round", "repeat-x repeat", "repeat repeat repeat", "tile"})
        @DisplayName("what is not read is refused")
        void refused(String value) {
            assertNull(BackgroundParser.repeats(tokens("background-repeat", value)), value);
        }

        @Test
        @DisplayName("the keyword is CSS's spelling")
        void spelling() {
            assertEquals("repeat-x", BackgroundRepeat.REPEAT_X.toString());
            assertEquals(BackgroundRepeat.NO_REPEAT, BackgroundRepeat.named(BackgroundRepeat.NO_REPEAT.toString()));
        }
    }

    @Nested
    @DisplayName("the shorthand")
    class Shorthand {

        @Test
        @DisplayName("a url layer with its repeat, over a colour")
        void urlAndRepeat() {
            var fill = BackgroundParser.shorthand(
                    tokens("background", "url(\"classpath:/ui/leather.png\") no-repeat, #3b2a1e"), CONTEXT);

            assertNotNull(fill);
            assertEquals(List.of(LEATHER), fill.layers());
            assertEquals(List.of(BackgroundRepeat.NO_REPEAT), fill.repeats());
            assertEquals(0xFF3B2A1E, fill.colour());
        }

        @Test
        @DisplayName("it resets the size and the repeat a longhand set before it")
        void resets() {
            var fill = compute("background-size: cover; background-repeat: no-repeat;"
                            + " background: url(\"classpath:/ui/leather.png\")")
                    .fill();

            assertEquals(Background.AUTO_SIZE, fill.sizes());
            assertEquals(Background.REPEAT, fill.repeats());
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "url(\"a.png\") url(\"b.png\")",
                    "repeat url(\"a.png\") repeat repeat",
                    "url(\"a.png\") space"
                })
        @DisplayName("what is not a layer is refused whole")
        void refused(String value) {
            assertNull(BackgroundParser.shorthand(tokens("background", value), CONTEXT), value);
        }
    }

    @Test
    @DisplayName("opacity reaches a picture as its alpha")
    void fade() {
        var fill = Background.of(0, List.of(LEATHER), BackgroundPosition.ZERO).fade(0.5);

        assertEquals(0.5, ((CssImage.Url) fill.layers().getFirst()).alpha(), 1e-9);
    }
}
