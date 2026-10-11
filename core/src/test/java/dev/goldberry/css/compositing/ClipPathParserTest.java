package dev.goldberry.css.compositing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Corners;
import dev.goldberry.css.parse.CssParser;
import dev.goldberry.css.parse.ParseMode;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.layout.Length;

/// `clip-path`, `mix-blend-mode` and `isolation`, read.
class ClipPathParserTest {

    private static List<Token> value(String css) {
        return CssParser.parseSheet("a { clip-path: " + css + " }", ParseMode.STRICT, "t")
                .rules()
                .getFirst()
                .declarations()
                .getFirst()
                .value();
    }

    private static ClipPath parse(String css) {
        return ClipPathParser.parse(value(css), CssLength.Context.DEFAULT).orElseThrow();
    }

    private static Compositing compositing(String css) {
        var map = new LinkedHashMap<String, List<Token>>();
        for (var declaration : CssParser.parseSheet("a { " + css + " }", ParseMode.LENIENT, "t")
                .rules()
                .getFirst()
                .declarations()) {
            map.put(declaration.property(), declaration.value());
        }
        return ComputedStyle.of(Map.copyOf(map), CssLength.Context.DEFAULT)
                .presence()
                .compositing();
    }

    private static Length px(float value) {
        return Length.points(value);
    }

    @Test
    @DisplayName("none, a box, a shape, and a shape in a box in either order")
    void forms() {
        assertEquals(ClipPath.NONE, parse("none"));
        assertEquals(new ClipPath.Boxed(ReferenceBox.PADDING_BOX), parse("padding-box"));
        var circle = new ClipPath.Circle(new ClipPath.Radius.Of(Length.percent(50)), ClipPath.Position.CENTER);
        assertEquals(new ClipPath.Shaped(circle, ReferenceBox.BORDER_BOX), parse("circle(50%)"));
        assertEquals(new ClipPath.Shaped(circle, ReferenceBox.CONTENT_BOX), parse("circle(50%) content-box"));
        assertEquals(new ClipPath.Shaped(circle, ReferenceBox.CONTENT_BOX), parse("content-box circle(50%)"));
    }

    @Test
    @DisplayName("inset takes one to four sides and rounded corners")
    void inset() {
        assertEquals(
                new ClipPath.Shaped(
                        new ClipPath.Inset(px(1), px(2), px(1), px(2), Corners.all(4)), ReferenceBox.BORDER_BOX),
                parse("inset(1px 2px round 4px)"));
        var three = (ClipPath.Shaped) parse("inset(1px 2px 3px)");
        assertEquals(new ClipPath.Inset(px(1), px(2), px(3), px(2), Corners.SQUARE), three.shape());
    }

    @Test
    @DisplayName("circle and ellipse take radii, side keywords and a position")
    void radii() {
        var circle = (ClipPath.Circle) ((ClipPath.Shaped) parse("circle(farthest-side at left top)")).shape();
        assertEquals(new ClipPath.Radius.ToSide(true), circle.radius());
        assertEquals(new ClipPath.Position(Length.percent(0), Length.percent(0)), circle.at());
        var plain = (ClipPath.Circle) ((ClipPath.Shaped) parse("circle()")).shape();
        assertEquals(ClipPath.Radius.CLOSEST_SIDE, plain.radius());
        var ellipse = (ClipPath.Ellipse) ((ClipPath.Shaped) parse("ellipse(10px 20% at 30px bottom)")).shape();
        assertEquals(new ClipPath.Radius.Of(px(10)), ellipse.rx());
        assertEquals(new ClipPath.Position(px(30), Length.percent(100)), ellipse.at());
    }

    @Test
    @DisplayName("polygon and path take a fill rule first")
    void rules() {
        var polygon = (ClipPath.Polygon) ((ClipPath.Shaped) parse("polygon(evenodd, 0 0, 100% 0, 50% 100%)")).shape();
        assertTrue(polygon.evenOdd());
        assertEquals(3, polygon.points().size());
        var path = (ClipPath.PathData) ((ClipPath.Shaped) parse("path('M0 0 L10 0 L5 5 Z')")).shape();
        assertEquals("M0 0 L10 0 L5 5 Z", path.data());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "url(#mask)",
                "circle(-5px)",
                "polygon(0 0, 1px 1px)",
                "inset()",
                "inset(1px round)",
                "ellipse(10px)",
                "circle(10px) circle(5px)",
                "border-box padding-box",
                "path(zigzag, 'M0 0')",
                "square(4px)",
            })
    @DisplayName("refuses what is not a clip it can draw")
    void refused(String css) {
        assertTrue(ClipPathParser.parse(value(css), CssLength.Context.DEFAULT).isEmpty(), css);
    }

    @Test
    @DisplayName("the three properties land on the box's compositing, and none inherits")
    void applied() {
        var all = compositing("clip-path: circle(); mix-blend-mode: screen; isolation: isolate");
        assertTrue(all.clip() instanceof ClipPath.Shaped);
        assertEquals(BlendMode.SCREEN, all.blend());
        assertTrue(all.isolate());
        assertTrue(all.needsLayer());
        assertEquals(Compositing.NONE, compositing("color: red"));
    }

    @Test
    @DisplayName("the non-separable blend modes are refused, the rest read")
    void blendModes() {
        assertEquals(BlendMode.NORMAL, compositing("mix-blend-mode: hue").blend());
        assertEquals(
                BlendMode.PLUS_LIGHTER,
                compositing("mix-blend-mode: plus-lighter").blend());
        assertEquals(
                BlendMode.COLOR_DODGE,
                compositing("mix-blend-mode: color-dodge").blend());
    }
}
