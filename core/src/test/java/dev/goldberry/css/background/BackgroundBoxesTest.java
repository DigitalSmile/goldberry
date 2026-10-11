package dev.goldberry.css.background;

import static dev.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.layout.Length;

/// `background-origin`, `background-clip`, `space` and `round`, and the
/// position, size and boxes inside the `background` shorthand.
class BackgroundBoxesTest {

    private static final CssLength.Context CONTEXT = CssLength.Context.DEFAULT;

    private static List<Token> tokens(String value) {
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, "button { background: " + value + " }");
        return sheet.rules().getFirst().declarations().getFirst().value();
    }

    private static ComputedStyle compute(String declarations) {
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, "button { " + declarations + " }");
        var root = element("window");
        root.with(element("button"));
        return ComputedStyle.of(new StyleResolver(List.of(sheet)).resolve(root.descend(1)), CONTEXT);
    }

    @Nested
    @DisplayName("the shorthand")
    class Shorthand {

        @Test
        @DisplayName("reads a position, and a size after a slash")
        void positionAndSize() {
            var fill = BackgroundParser.shorthand(tokens("url(\"a.png\") 3px 4px / 10px 20px no-repeat"), CONTEXT);

            assertNotNull(fill);
            assertEquals(List.of(new BackgroundPosition(3, 4)), fill.positions());
            assertEquals(
                    List.of(new BackgroundSize(BackgroundSize.Kind.LENGTHS, Length.points(10), Length.points(20))),
                    fill.sizes());
            assertEquals(List.of(BackgroundRepeat.NO_REPEAT), fill.repeats());
        }

        @Test
        @DisplayName("reads a slash with no spaces round it, and keywords on either side")
        void tight() {
            var fill = BackgroundParser.shorthand(tokens("url(\"a.png\") center/cover"), CONTEXT);

            assertNotNull(fill);
            assertEquals(List.of(BackgroundPosition.CENTRE), fill.positions());
            assertEquals(List.of(BackgroundSize.COVER), fill.sizes());
        }

        @Test
        @DisplayName("one box is the origin and the clip; two are the origin, then the clip")
        void boxes() {
            var one = BackgroundParser.shorthand(tokens("url(\"a.png\") content-box"), CONTEXT);
            var two = BackgroundParser.shorthand(tokens("url(\"a.png\") border-box padding-box"), CONTEXT);

            assertNotNull(one);
            assertNotNull(two);
            assertEquals(List.of(BackgroundBox.CONTENT_BOX), one.origins());
            assertEquals(List.of(BackgroundBox.CONTENT_BOX), one.clips());
            assertEquals(List.of(BackgroundBox.BORDER_BOX), two.origins());
            assertEquals(List.of(BackgroundBox.PADDING_BOX), two.clips());
        }

        @Test
        @DisplayName("each layer has its own position, and a colour alone keeps its clip")
        void perLayer() {
            var fill = BackgroundParser.shorthand(tokens("url(\"a.png\") right, url(\"b.png\") left top"), CONTEXT);
            var colour = BackgroundParser.shorthand(tokens("#ff0000 padding-box"), CONTEXT);

            assertNotNull(fill);
            assertEquals(List.of(new BackgroundPosition(0, 0, 100, 50), BackgroundPosition.ZERO), fill.positions());
            assertNotNull(colour);
            assertEquals(BackgroundBox.PADDING_BOX, colour.colourClip());
        }

        @Test
        @DisplayName("resets what it does not name")
        void resets() {
            var fill = compute("background-origin: content-box; background-clip: content-box;"
                            + " background-position: right; background: url(\"a.png\")")
                    .fill();

            assertEquals(Background.ZERO, fill.positions());
            assertEquals(Background.PADDING_BOX, fill.origins());
            assertEquals(Background.BORDER_BOX, fill.clips());
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "url(\"a.png\") / 10px",
                    "url(\"a.png\") 3px / nonsense",
                    "url(\"a.png\") top 10px",
                    "url(\"a.png\") local"
                })
        @DisplayName("refuses what is not a layer")
        void refused(String value) {
            assertNull(BackgroundParser.shorthand(tokens(value), CONTEXT), value);
        }
    }

    @Nested
    @DisplayName("origin and clip")
    class Boxes {

        @Test
        @DisplayName("are comma lists of the three boxes, matched to the layers")
        void lists() {
            var fill = compute("background-image: url(\"a.png\"), url(\"b.png\"), url(\"c.png\");"
                            + " background-origin: border-box, content-box; background-clip: padding-box")
                    .fill();

            assertEquals(BackgroundBox.BORDER_BOX, fill.origin(0));
            assertEquals(BackgroundBox.CONTENT_BOX, fill.origin(1));
            assertEquals(BackgroundBox.BORDER_BOX, fill.origin(2), "a short list repeats");
            assertEquals(BackgroundBox.PADDING_BOX, fill.clip(2));
            assertEquals(BackgroundBox.PADDING_BOX, fill.colourClip(), "the colour takes the last layer's");
        }

        @Test
        @DisplayName("start inside the border and under it")
        void initial() {
            var fill = Background.none();
            assertEquals(BackgroundBox.PADDING_BOX, fill.origin(0));
            assertEquals(BackgroundBox.BORDER_BOX, fill.clip(0));
            assertEquals("content-box", BackgroundBox.CONTENT_BOX.toString());
        }

        @ParameterizedTest
        @ValueSource(strings = {"text", "margin-box", "border-box padding-box"})
        @DisplayName("refuse anything but the three")
        void refused(String value) {
            assertNull(BackgroundParser.boxes(
                    List.copyOf(Stylesheet.parse(CascadeLayer.APPLICATION, "a { background-clip: " + value + " }")
                            .rules()
                            .getFirst()
                            .declarations()
                            .getFirst()
                            .value())));
        }
    }

    @Nested
    @DisplayName("space and round")
    class Tiling {

        @Test
        @DisplayName("are read alone and per axis, and written back short")
        void read() {
            var fill = compute("background-repeat: space, round space").fill();

            assertEquals(
                    List.of(
                            new BackgroundRepeat(BackgroundRepeat.Tiling.SPACE, BackgroundRepeat.Tiling.SPACE),
                            new BackgroundRepeat(BackgroundRepeat.Tiling.ROUND, BackgroundRepeat.Tiling.SPACE)),
                    fill.repeats());
            assertEquals("space", fill.repeat(0).toString());
            assertEquals("round space", fill.repeat(1).toString());
            assertEquals("repeat-y", BackgroundRepeat.REPEAT_Y.toString());
        }
    }
}
