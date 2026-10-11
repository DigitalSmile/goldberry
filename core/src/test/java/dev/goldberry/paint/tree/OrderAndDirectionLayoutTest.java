package dev.goldberry.paint.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.IdentityHashMap;
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
import dev.goldberry.layout.FlexDirection;
import dev.goldberry.layout.Length;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.TestFrames;
import dev.goldberry.render.model.LogicalRect;

/// `order` and `direction` reach Yoga: a row runs from the right in a
/// right-to-left box, and children are laid out in `order`.
class OrderAndDirectionLayoutTest {

    private TestFrames.Target target;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        target = TestFrames.of(300, 100, 1.0f, 0);
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

    private static Box child(String css) {
        return Box.of().style(style("width: 50px; height: 20px; " + css));
    }

    private static Box row(String css, Box... children) {
        return Box.of()
                .style(style(css))
                .direction(FlexDirection.ROW)
                .alignItems(Align.FLEX_START)
                .size(Length.points(300), Length.points(100))
                .children(children);
    }

    /// Each box's rectangle, by the box itself, after one layout: by identity,
    /// since two children with the same style are equal boxes.
    private Map<Box, LogicalRect> laidOut(Box root) {
        try (var tree = RenderTree.create()) {
            tree.update(target.frame(), root);
            var out = new IdentityHashMap<Box, LogicalRect>();
            tree.forEachPlacedBox(placed -> out.put(placed.box(), placed.layout()));
            return out;
        }
    }

    @Test
    @DisplayName("a right-to-left row starts at the right")
    void rtlRow() {
        var a = child("");
        var b = child("");
        var placed = laidOut(row("direction: rtl", a, b));
        assertEquals(250, placed.get(a).left(), 0.5);
        assertEquals(200, placed.get(b).left(), 0.5);
    }

    @Test
    @DisplayName("a left-to-right row starts at the left, as before")
    void ltrRow() {
        var a = child("");
        var b = child("");
        var placed = laidOut(row("", a, b));
        assertEquals(0, placed.get(a).left(), 0.5);
        assertEquals(50, placed.get(b).left(), 0.5);
    }

    @Test
    @DisplayName("children are laid out in order, equal orders in document order")
    void order() {
        var a = child("order: 2");
        var b = child("");
        var c = child("order: -1");
        var d = child("");
        var placed = laidOut(row("", a, b, c, d));
        assertEquals(0, placed.get(c).left(), 0.5);
        assertEquals(50, placed.get(b).left(), 0.5);
        assertEquals(100, placed.get(d).left(), 0.5);
        assertEquals(150, placed.get(a).left(), 0.5);
    }

    @Test
    @DisplayName("a list in one order is handed back as it is")
    void unchanged() {
        var children = List.of(child("order: 1"), child("order: 1"));
        assertSame(children, RenderObject.inOrder(children));
    }
}
