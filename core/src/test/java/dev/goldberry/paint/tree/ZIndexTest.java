package dev.goldberry.paint.tree;

import static dev.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.input.hit.HitTest;
import dev.goldberry.layout.Insets;
import dev.goldberry.layout.Length;
import dev.goldberry.layout.Position;
import dev.goldberry.layout.Presence;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.TestFrames;

/// `z-index`: siblings drawn and hit by it, document order among equals.
class ZIndexTest {

    @BeforeAll
    static void requireLibrary() {
        RendererRequirement.enforce();
    }

    /// A 40x40 square, absolutely placed over its siblings, tagged and coloured.
    private static Box square(String tag, int z, int argb) {
        return Box.of()
                .owner(tag)
                .background(argb)
                .position(Position.ABSOLUTE)
                .inset(Insets.all(Length.points(0)))
                .size(Length.points(40), Length.points(40))
                .presence(Presence.NORMAL.zIndex(z));
    }

    private static List<String> order(Box root) {
        var target = TestFrames.of(60, 60, 1.0f, 0);
        var order = new ArrayList<String>();
        try (var tree = RenderTree.create()) {
            tree.update(target.frame(), root);
            tree.forEachPlacedBox(placed -> {
                if (placed.box().owner() instanceof String tag) {
                    order.add(tag);
                }
            });
        } finally {
            target.end();
        }
        return order;
    }

    @Test
    @DisplayName("siblings are drawn lowest first, and in document order among equals")
    void order() {
        var root = Box.of()
                .children(
                        square("a", 2, 0xFFFF0000),
                        square("b", -1, 0xFF00FF00),
                        square("c", 0, 0xFF0000FF),
                        square("d", 2, 0xFF000000),
                        square("e", 0, 0xFFFFFFFF));
        assertEquals(List.of("b", "c", "e", "a", "d"), order(root));
    }

    @Test
    @DisplayName("the highest is the one painted on top and the one the pointer finds")
    void paintedAndHit() {
        var target = TestFrames.of(60, 60, 1.0f, 0);
        try (var tree = RenderTree.create()) {
            tree.update(
                    target.frame(), Box.of().children(square("top", 5, 0xFFFF0000), square("under", 0, 0xFF0000FF)));
            tree.paint(target.frame());
            assertEquals("top", HitTest.at(HitTest.capture(tree), 10, 10).orElseThrow());
        } finally {
            target.end();
        }
        assertEquals(0xFFFF0000, target.pixel(10, 10), "the higher square is drawn over the later one");
    }

    @Test
    @DisplayName("a change of z-index, or of visibility, repaints a retained tree")
    void repaints() {
        var target = TestFrames.of(60, 60, 1.0f, 0);
        try (var tree = RenderTree.create()) {
            tree.update(target.frame(), Box.of().children(square("a", 0, 0xFFFF0000), square("b", 0, 0xFF0000FF)));
            tree.paint(target.frame());
            tree.update(target.frame(), Box.of().children(square("a", 1, 0xFFFF0000), square("b", 0, 0xFF0000FF)));
            tree.paint(target.frame());
            tree.update(
                    target.frame(),
                    Box.of()
                            .children(
                                    square("a", 1, 0xFFFF0000)
                                            .presence(Presence.NORMAL.zIndex(1).visibility(Presence.Visibility.HIDDEN)),
                                    square("b", 0, 0xFF0000FF)));
            tree.paint(target.frame());
        } finally {
            target.end();
        }
        assertEquals(0xFF0000FF, target.pixel(10, 10), "the raised square was hidden, and the other shows");
    }

    @Test
    @DisplayName("the cascade reads a whole number or auto, and elevated keeps a higher one")
    void cascade() {
        var resolver = new StyleResolver(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, """
                a { z-index: -3 }
                b { z-index: auto }
                c { z-index: 2.5 }
                """)));
        var context = new CssLength.Context(16, 16);
        assertEquals(
                -3,
                ComputedStyle.of(resolver.resolve(element("a")), context)
                        .presence()
                        .zIndex());
        assertEquals(
                0,
                ComputedStyle.of(resolver.resolve(element("b")), context)
                        .presence()
                        .zIndex());
        assertEquals(
                0,
                ComputedStyle.of(resolver.resolve(element("c")), context)
                        .presence()
                        .zIndex(),
                "a fraction is dropped");
        var raised = Box.of().presence(Presence.NORMAL.zIndex(7));
        assertSame(raised, raised.elevated(true), "a pinned box under a higher rule keeps the rule's");
        assertEquals(1, Box.of().elevated(true).presence().zIndex());
    }
}
