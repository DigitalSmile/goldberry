package dev.goldberry.css.background;

import static dev.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.layout.Length;

/// The `background` properties: what each reads, and what each refuses.
class BackgroundParserTest {

    private static final CssLength.Context CONTEXT = CssLength.Context.DEFAULT;

    private static final int RED = 0xFFFF0000;

    private static final int BLUE = 0xFF0000FF;

    /// The tokens of a `background` value, through the real tokenizer.
    private static List<Token> tokens(String value) {
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, "button { background: " + value + " }");
        return sheet.rules().getFirst().declarations().getFirst().value();
    }

    /// One gradient function, as `background-image` would read it.
    private static @Nullable GradientLayer gradient(String value) {
        var layers = BackgroundParser.images(tokens(value), CONTEXT);
        return layers == null || layers.size() != 1 || !(layers.getFirst() instanceof GradientLayer layer)
                ? null
                : layer;
    }

    private static ComputedStyle compute(String declarations) {
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, "button { " + declarations + " }");
        var root = element("window");
        root.with(element("button"));
        return ComputedStyle.of(new StyleResolver(List.of(sheet)).resolve(root.descend(1)), CONTEXT);
    }

    @Nested
    @DisplayName("linear-gradient")
    class Linear {

        @Test
        @DisplayName("with no direction it runs to the bottom, and its stops are placed by their neighbours")
        void plain() {
            var layer = assertInstanceOf(GradientLayer.Linear.class, gradient("linear-gradient(red, blue)"));

            assertEquals(new GradientLayer.Direction.Angle(Math.PI), layer.direction());
            assertEquals(
                    List.of(new GradientLayer.ColorStop(RED, null), new GradientLayer.ColorStop(BLUE, null)),
                    layer.stops());
            assertFalse(layer.repeating());
        }

        @ParameterizedTest
        @CsvSource({"to top, 0", "to right, 90", "to bottom, 180", "to left, 270", "90deg, 90", "0.25turn, 90", "0, 0"})
        @DisplayName("a side or an angle is an angle, zero up and clockwise")
        void angles(String direction, double degrees) {
            var layer = assertInstanceOf(
                    GradientLayer.Linear.class, gradient("linear-gradient(" + direction + ", red, blue)"));

            var angle = assertInstanceOf(GradientLayer.Direction.Angle.class, layer.direction());
            assertEquals(Math.toRadians(degrees), angle.radians(), 1e-9);
        }

        @Test
        @DisplayName("a corner is a corner, whichever word comes first")
        void corners() {
            var layer = assertInstanceOf(
                    GradientLayer.Linear.class, gradient("linear-gradient(to bottom right, red, blue)"));
            var other = assertInstanceOf(
                    GradientLayer.Linear.class, gradient("linear-gradient(to right bottom, red, blue)"));

            assertEquals(new GradientLayer.Direction.Corner(true, true), layer.direction());
            assertEquals(layer, other);
        }

        @Test
        @DisplayName("a stop with two positions is two stops of one colour")
        void twoPositions() {
            var layer = assertInstanceOf(
                    GradientLayer.Linear.class,
                    gradient("repeating-linear-gradient(90deg, red 0 6px, transparent 6px 12px)"));

            assertTrue(layer.repeating());
            assertEquals(4, layer.stops().size());
            assertEquals(Length.points(6), layer.stops().get(1).position());
            assertEquals(0, layer.stops().get(2).argb() >>> 24);
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "linear-gradient(red)",
                    "linear-gradient(to middle, red, blue)",
                    "linear-gradient(to left right, red, blue)",
                    "linear-gradient(red, 30%, blue)",
                    "linear-gradient(45px, red, blue)",
                    "linear-gradient(red auto, blue)",
                    "conic-gradient(red, blue)"
                })
        @DisplayName("what is not a linear gradient here is refused whole")
        void refused(String value) {
            assertNull(gradient(value), value);
        }
    }

    @Nested
    @DisplayName("radial-gradient")
    class Radial {

        @Test
        @DisplayName("with nothing said it is an ellipse to the farthest corner, centred")
        void plain() {
            var layer = assertInstanceOf(GradientLayer.Radial.class, gradient("radial-gradient(red, blue)"));

            assertFalse(layer.circle());
            assertEquals(GradientLayer.Extent.FARTHEST_CORNER, layer.extent());
            assertEquals(Length.percent(50), layer.centreX());
            assertEquals(Length.percent(50), layer.centreY());
        }

        @Test
        @DisplayName("a shape, an extent and a centre, in CSS's order")
        void configured() {
            var layer = assertInstanceOf(
                    GradientLayer.Radial.class,
                    gradient("radial-gradient(circle closest-side at top left, red, blue 40%)"));

            assertTrue(layer.circle());
            assertEquals(GradientLayer.Extent.CLOSEST_SIDE, layer.extent());
            assertEquals(Length.percent(0), layer.centreX());
            assertEquals(Length.percent(0), layer.centreY());
            assertEquals(Length.percent(40), layer.stops().getLast().position());
        }

        @Test
        @DisplayName("one length is a circle and two are an ellipse")
        void explicitSizes() {
            var circle = assertInstanceOf(GradientLayer.Radial.class, gradient("radial-gradient(20px, red, blue)"));
            var ellipse = assertInstanceOf(
                    GradientLayer.Radial.class, gradient("radial-gradient(20px 50% at 10px 30%, red, blue)"));

            assertTrue(circle.circle());
            assertEquals(Length.points(20), circle.radiusX());
            assertFalse(ellipse.circle());
            assertEquals(Length.percent(50), ellipse.radiusY());
            assertEquals(Length.points(10), ellipse.centreX());
            assertEquals(Length.percent(30), ellipse.centreY());
        }

        @ParameterizedTest
        @CsvSource({
            "at 10px, 10px, 50%",
            "at 10px top, 10px, 0%",
            "at left 30%, 0%, 30%",
            "at center 30%, 50%, 30%",
            "at 30% center, 30%, 50%"
        })
        @DisplayName("a centre's length is horizontal first, vertical once a keyword or a length has taken x")
        void centreLengths(String centre, String x, String y) {
            var layer = assertInstanceOf(
                    GradientLayer.Radial.class, gradient("radial-gradient(" + centre + ", red, blue)"));

            assertEquals(length(x), layer.centreX(), centre);
            assertEquals(length(y), layer.centreY(), centre);
        }

        private static Length length(String written) {
            return written.endsWith("%")
                    ? Length.percent(Float.parseFloat(written.substring(0, written.length() - 1)))
                    : Length.points(Float.parseFloat(written.substring(0, written.length() - 2)));
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "radial-gradient(circle 20%, red, blue)",
                    "radial-gradient(circle ellipse, red, blue)",
                    "radial-gradient(ellipse 20px, red, blue)",
                    "radial-gradient(at nowhere, red, blue)",
                    "radial-gradient(at top 30%, red, blue)",
                    "radial-gradient(at left right, red, blue)",
                    "radial-gradient(at top bottom, red, blue)",
                    "radial-gradient(at 10px left, red, blue)",
                    "radial-gradient(at 10px 20px 30px, red, blue)",
                    "radial-gradient(closest-side 20px, red, blue)"
                })
        @DisplayName("what CSS refuses is refused")
        void refused(String value) {
            assertNull(gradient(value), value);
        }
    }

    @Nested
    @DisplayName("through the cascade")
    class Cascade {

        @Test
        @DisplayName("the shorthand takes layers, top first, and a colour in the last")
        void shorthand() {
            var style = compute("background: linear-gradient(red, blue), radial-gradient(blue, red) #00ff00");

            assertEquals(0xFF00FF00, style.background());
            assertEquals(2, style.fill().layers().size());
            assertInstanceOf(GradientLayer.Linear.class, style.fill().layers().getFirst());
        }

        @Test
        @DisplayName("a colour on its own is a plain colour, as it always was")
        void colourOnly() {
            var style = compute("background: #ff0000");

            assertEquals(Background.of(RED), style.fill());
        }

        @Test
        @DisplayName("`none` is no fill at all")
        void none() {
            assertEquals(
                    Background.none(),
                    compute("background: #ff0000; background: none").fill());
        }

        @Test
        @DisplayName("`background-color` keeps the layers and `background` resets them")
        void longhandsAndShorthand() {
            var kept = compute("background: linear-gradient(red, blue); background-color: #0000ff");
            var reset = compute("background-image: linear-gradient(red, blue); background: #0000ff");

            assertEquals(BLUE, kept.background());
            assertEquals(1, kept.fill().layers().size());
            assertEquals(Background.of(BLUE), reset.fill());
        }

        @Test
        @DisplayName("`background-image` replaces the layers and keeps the colour")
        void image() {
            var style = compute("background-color: #ff0000; background-image: radial-gradient(red, blue)");

            assertEquals(RED, style.background());
            assertEquals(1, style.fill().layers().size());
            assertEquals(List.of(), compute("background-image: none").fill().layers());
        }

        @Test
        @DisplayName("a colour before the last layer is refused, and the declaration with it")
        void colourNotLast() {
            var value = "linear-gradient(red, blue) #00ff00, linear-gradient(red, blue)";

            assertNull(BackgroundParser.shorthand(tokens(value), CONTEXT));
            assertEquals(Background.none(), compute("background: " + value).fill(), "nothing is half-applied");
        }
    }

    @Nested
    @DisplayName("background-position")
    class Position {

        @ParameterizedTest
        @CsvSource({
            "'10px 4px', 10, 4",
            "'10px', 10, 0",
            "'top', 0, 0",
            "'50% 20px', 0, 20",
            "'right bottom', 0, 0",
            "'1em 0', 16, 0"
        })
        @DisplayName("only a length moves a layer the size of its box")
        void lengths(String value, double x, double y) {
            var position = BackgroundParser.position(positionTokens(value), CONTEXT);

            assertNotNull(position);
            assertEquals(new BackgroundPosition(x, y), position);
        }

        @ParameterizedTest
        @ValueSource(strings = {"1px 2px 3px", "left left", "nonsense", "top bottom"})
        @DisplayName("what is not a position is refused")
        void refused(String value) {
            assertNull(BackgroundParser.position(positionTokens(value), CONTEXT), value);
        }

        @Test
        @DisplayName("it reaches the computed background, and the shorthand puts it back to zero")
        void cascade() {
            assertEquals(
                    new BackgroundPosition(12, 0),
                    compute("background-position: 12px 0").fill().position());
            assertEquals(
                    BackgroundPosition.ZERO,
                    compute("background-position: 12px 0; background: #ff0000")
                            .fill()
                            .position());
        }

        private static List<Token> positionTokens(String value) {
            var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, "button { background-position: " + value + " }");
            return sheet.rules().getFirst().declarations().getFirst().value();
        }
    }
}
