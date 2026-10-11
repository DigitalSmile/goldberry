package dev.goldberry.paint.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import dev.goldberry.layout.Align;
import dev.goldberry.layout.Justify;
import dev.goldberry.layout.Length;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.TestFrames;

/// `filter`, drawn: each function changes the picture as CSS says, and the
/// ones that reach past the box are drawn there.
class FilterPaintTest {

    private static final int BLUE = 0xFF0000FF;
    private static final int WHITE = 0xFFFFFFFF;

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

    /// A page of `page` holding one 40×40 box styled `css`, centred.
    private int[] paint(int page, String css) {
        var box = Box.of().style(style("width: 40px; height: 40px; flex-shrink: 0; " + css));
        var root = Box.filled(page)
                .size(Length.points(100), Length.points(100))
                .alignItems(Align.CENTER)
                .justifyContent(Justify.CENTER)
                .children(box);
        try (var tree = RenderTree.create()) {
            tree.update(target.frame(), root);
            tree.paint(target.frame());
        }
        return new int[] {target.pixel(50, 50), target.pixel(29, 50), target.pixel(75, 75)};
    }

    private static int channel(int argb, int shift) {
        return (argb >>> shift) & 0xFF;
    }

    @Test
    @DisplayName("grayscale turns red to its luminance")
    void grayscale() {
        var middle = paint(WHITE, "background: red; filter: grayscale(1)")[0];
        assertEquals(channel(middle, 16), channel(middle, 8), 1);
        assertEquals(channel(middle, 16), channel(middle, 0), 1);
        assertEquals(54, channel(middle, 16), 3, "0.2126 of full red");
    }

    @Test
    @DisplayName("invert turns red to cyan, and brightness halves it")
    void invertAndBrightness() {
        var inverted = paint(WHITE, "background: red; filter: invert(1)")[0];
        assertEquals(0xFF00FFFF, inverted, Integer.toHexString(inverted));
        var dimmed = paint(WHITE, "background: red; filter: brightness(50%)")[0];
        assertEquals(0x80, channel(dimmed, 16), 2);
    }

    @Test
    @DisplayName("opacity inside the list lets the page through")
    void opacity() {
        var middle = paint(BLUE, "background: red; filter: opacity(0.5)")[0];
        assertEquals(0x80, channel(middle, 16), 3);
        assertEquals(0x80, channel(middle, 0), 3);
    }

    @Test
    @DisplayName("a blur reaches past the box, and is drawn there")
    void blur() {
        var pixels = paint(WHITE, "background: red; filter: blur(4px)");
        assertNotEquals(WHITE, pixels[1], "a pixel just left of the box has some of its red");
        assertTrue(channel(pixels[0], 8) < 0x10, "the middle stays red");
    }

    @Test
    @DisplayName("a drop shadow is cast under and past the box, in its colour")
    void dropShadow() {
        var pixels = paint(WHITE, "background: red; filter: drop-shadow(8px 8px 0 #00ff00)");
        assertEquals(0xFF00FF00, pixels[2], "below and right of the box, where only the shadow is");
        assertEquals(0xFFFF0000, pixels[0], "the box is over its shadow");
    }

    @Test
    @DisplayName("the clip is cut after the filter, so it cuts the shadow too")
    void clipAfterFilter() {
        var pixels = paint(WHITE, "background: red; filter: drop-shadow(8px 8px 0 #00ff00); clip-path: inset(0)");
        assertEquals(WHITE, pixels[2], "the shadow outside the box is clipped away");
    }
}
