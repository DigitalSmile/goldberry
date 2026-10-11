package dev.goldberry.paint.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.parse.CssParser;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.input.hit.HitTest;
import dev.goldberry.layout.Align;
import dev.goldberry.layout.Length;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.TestFrames;

/// `clip-path`, `mix-blend-mode` and `isolation`, drawn: a clipped box keeps
/// only its shape, children and all, and a blended one combines with what is
/// under it.
class CompositingPaintTest {

    private static final int BLUE = 0xFF0000FF;
    private static final int RED = 0xFFFF0000;

    private TestFrames.Target target;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        target = TestFrames.of(100, 100, 1.0f);
    }

    @AfterEach
    void tearDown() {
        if (target != null) {
            target.end();
        }
    }

    private static ComputedStyle style(String css) {
        var map = new LinkedHashMap<String, List<Token>>();
        for (var declaration : CssParser.parse("x { " + css + " }").getFirst().declarations()) {
            map.put(declaration.property(), declaration.value());
        }
        return ComputedStyle.of(Map.copyOf(map), CssLength.Context.DEFAULT);
    }

    /// A blue page holding one 80×80 box styled `css` at its top-left.
    private static Box scene(String css, Box... children) {
        var box = Box.of()
                .style(style("width: 80px; height: 80px; flex-shrink: 0; " + css))
                .children(children);
        return Box.filled(BLUE)
                .size(Length.points(100), Length.points(100))
                .alignItems(Align.FLEX_START)
                .children(box);
    }

    private void paint(Box root) {
        try (var tree = RenderTree.create()) {
            tree.update(target.frame(), root);
            tree.paint(target.frame());
        }
    }

    @Test
    @DisplayName("a circle keeps its middle and drops its corners")
    void circle() {
        paint(scene("background: red; clip-path: circle(50%)"));
        assertEquals(RED, target.pixel(40, 40));
        assertEquals(BLUE, target.pixel(3, 3), "the corner the circle leaves out");
        assertEquals(BLUE, target.pixel(76, 76));
        assertEquals(RED, target.pixel(40, 2), "the top of the circle");
    }

    @Test
    @DisplayName("children are cut with their parent")
    void children() {
        var child = Box.filled(RED).size(Length.points(80), Length.points(80)).shrink(0);
        paint(scene("clip-path: polygon(0 0, 100% 0, 0 100%)", child));
        assertEquals(RED, target.pixel(10, 10), "inside the triangle");
        assertEquals(BLUE, target.pixel(70, 70), "outside it, though the child covers it");
    }

    @Test
    @DisplayName("an inset with rounded corners keeps the rectangle inside it")
    void inset() {
        paint(scene("background: red; clip-path: inset(10px 20px round 4px)"));
        assertEquals(BLUE, target.pixel(5, 40), "left of the inset");
        assertEquals(RED, target.pixel(40, 40));
        assertEquals(BLUE, target.pixel(40, 75), "below it");
        assertEquals(BLUE, target.pixel(20, 10), "its rounded corner");
    }

    @Test
    @DisplayName("an even-odd path keeps what its rule says is inside")
    void evenOdd() {
        paint(scene("background: red; clip-path: path(evenodd, 'M0 0 H80 V80 H0 Z M20 20 H60 V60 H20 Z')"));
        assertEquals(RED, target.pixel(10, 10), "the frame");
        assertEquals(BLUE, target.pixel(40, 40), "the hole");
    }

    @Test
    @DisplayName("multiply combines the box with what is under it")
    void multiply() {
        paint(scene("background: #808080; mix-blend-mode: multiply"));
        var pixel = target.pixel(40, 40);
        assertEquals(0, (pixel >>> 16) & 0xFF, "red times nothing is nothing");
        assertTrue(Math.abs((pixel & 0xFF) - 0x80) <= 2, () -> Integer.toHexString(pixel));
    }

    @Test
    @DisplayName("isolation makes a group a child blends inside, not with the page")
    void isolation() {
        var child = Box.of()
                .style(style("width: 80px; height: 80px; flex-shrink: 0; background: #808080;"
                        + " mix-blend-mode: multiply"));
        paint(scene("isolation: isolate", child));
        var pixel = target.pixel(40, 40);
        assertTrue(Math.abs(((pixel >>> 16) & 0xFF) - 0x80) <= 2, () -> Integer.toHexString(pixel));
        assertTrue(Math.abs((pixel & 0xFF) - 0x80) <= 2, () -> Integer.toHexString(pixel));
    }

    @Test
    @DisplayName("the pointer finds a clipped box inside its shape only")
    void hitTest() {
        var root = scene("background: red; clip-path: circle(50%)");
        try (var tree = RenderTree.create()) {
            tree.update(target.frame(), root);
            var regions = HitTest.capture(tree);
            var box = regions.get(1);
            assertTrue(box.contains(40, 40));
            assertTrue(!box.contains(3, 3), "a corner the circle leaves out");
        }
    }
}
