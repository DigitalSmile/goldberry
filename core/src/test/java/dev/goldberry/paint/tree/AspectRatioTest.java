package dev.goldberry.paint.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
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
import dev.goldberry.layout.Length;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.TestFrames;
import dev.goldberry.render.model.LogicalRect;

/// `aspect-ratio` reaches Yoga, which sizes the axis a box does not fix.
class AspectRatioTest {

    private TestFrames.Target target;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        target = TestFrames.of(400, 400, 1.0f, 0);
    }

    @AfterEach
    void tearDown() {
        if (target != null) {
            target.end();
        }
    }

    private static ComputedStyle style(String css) {
        var declarations = CssParser.parse("x { " + css + " }").getFirst().declarations();
        var map = new LinkedHashMap<String, List<Token>>();
        for (var declaration : declarations) {
            map.put(declaration.property(), declaration.value());
        }
        return ComputedStyle.of(Map.copyOf(map), CssLength.Context.DEFAULT);
    }

    private List<LogicalRect> laidOut(Box root) {
        try (var tree = RenderTree.create()) {
            tree.update(target.frame(), root);
            var out = new ArrayList<LogicalRect>();
            tree.forEachPlacedBox(placed -> out.add(placed.layout()));
            return out;
        }
    }

    @Test
    @DisplayName("a width and a ratio give the height")
    void heightFromWidth() {
        var child = Box.of().style(style("width: 160px; aspect-ratio: 16 / 9"));
        var root = Box.of()
                .size(Length.points(400), Length.points(400))
                .alignItems(Align.FLEX_START)
                .children(child);
        var child1 = laidOut(root).get(1);
        assertEquals(160, child1.width(), 0.5);
        assertEquals(90, child1.height(), 0.5);
    }

    @Test
    @DisplayName("auto, the default, leaves the height to the content")
    void auto() {
        var child = Box.of().style(style("width: 160px; aspect-ratio: auto"));
        var root = Box.of()
                .size(Length.points(400), Length.points(400))
                .alignItems(Align.FLEX_START)
                .children(child);
        assertEquals(0, laidOut(root).get(1).height(), 0.5);
    }

    @Test
    @DisplayName("is read as a number, a fraction, or auto, and refuses zero")
    void parsing() {
        assertEquals(1.5, style("aspect-ratio: 1.5").presence().aspectRatio());
        assertEquals(16.0 / 9, style("aspect-ratio: 16/9").presence().aspectRatio(), 1e-12);
        assertEquals(2.0, style("aspect-ratio: auto 2 / 1").presence().aspectRatio());
        assertTrue(style("aspect-ratio: auto").presence().hasAutoRatio());
        assertTrue(style("aspect-ratio: 0").presence().hasAutoRatio(), "dropped, so still auto");
        assertTrue(style("aspect-ratio: 1 / 0").presence().hasAutoRatio());
    }
}
