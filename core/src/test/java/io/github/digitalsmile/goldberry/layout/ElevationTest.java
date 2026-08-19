package io.github.digitalsmile.goldberry.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.TestFrames;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `Box.elevated` — one bit meaning "draw me last"
/// ([ADR-0123](../../../../../../book/src/adr/0123-a-pinned-box-paints-after-its-siblings.md)).
class ElevationTest {

    @BeforeAll
    static void requireLibrary() {
        RendererRequirement.enforce();
    }

    /// The order boxes reach the painter, by the tag each was given.
    private static List<String> paintOrder(Box root) {
        var target = TestFrames.of(200, 200, 1.0f, 0);
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

    private static Box row(String tag) {
        return Box.of().owner(tag).size(StyleLength.points(50), StyleLength.points(20));
    }

    @Test
    @DisplayName("an ordinary tree paints in document order")
    void documentOrder() {
        assertEquals(List.of("a", "b", "c"), paintOrder(
                Box.of().children(row("a"), row("b"), row("c"))));
    }

    @Test
    @DisplayName("an elevated box paints after its siblings, wherever it sits")
    void elevatedPaintsLast() {
        // The `affix` case exactly: a pinned header in the middle of a list, with
        // the rows below it drawn afterwards and straight over the top of it
        // until this.
        assertEquals(List.of("a", "c", "b"), paintOrder(
                Box.of().children(row("a"), row("b").elevated(true), row("c"))));
    }

    @Test
    @DisplayName("elevated siblings keep document order among themselves")
    void amongThemselves() {
        // Deliberately not a z-order: there is no ordering *between* elevated
        // boxes to define, so they stay in the order they were written.
        assertEquals(List.of("c", "a", "b"), paintOrder(
                Box.of().children(
                        row("a").elevated(true), row("b").elevated(true), row("c"))));
    }

    @Test
    @DisplayName("elevation moves the paint and not the layout")
    void layoutUnmoved() {
        var target = TestFrames.of(200, 200, 1.0f, 0);
        try (var tree = RenderTree.create()) {
            tree.update(target.frame(), Box.of()
                    .direction(io.github.digitalsmile.goldberry.natives.yoga.FlexDirection.COLUMN)
                    .children(row("a"), row("b").elevated(true), row("c")));
            var tops = new java.util.HashMap<String, Float>();
            tree.forEachPlacedBox(placed -> {
                if (placed.box().owner() instanceof String tag) {
                    tops.put(tag, placed.layout().top());
                }
            });
            // b is still the second row. A header that relayed out its list when
            // it lifted would be worse than one painted under it.
            assertEquals(0f, tops.get("a"), 0.01);
            assertEquals(20f, tops.get("b"), 0.01);
            assertEquals(40f, tops.get("c"), 0.01);
        } finally {
            target.end();
        }
    }

    @Test
    @DisplayName("the hit test sees the same order the painter does")
    void hitTestAgrees() {
        var target = TestFrames.of(200, 200, 1.0f, 0);
        try (var tree = RenderTree.create()) {
            // Two boxes on top of each other: `b` is elevated, so it is painted
            // last and must therefore be *hit* first. `HitTest.at` scans
            // backwards, so this is the same fact stated twice on purpose.
            tree.update(target.frame(), Box.of().children(
                    row("a").position(io.github.digitalsmile.goldberry.natives.yoga.PositionType.ABSOLUTE)
                            .inset(io.github.digitalsmile.goldberry.natives.yoga.Insets.all(
                                    StyleLength.points(0))),
                    row("b").elevated(true)
                            .position(io.github.digitalsmile.goldberry.natives.yoga.PositionType.ABSOLUTE)
                            .inset(io.github.digitalsmile.goldberry.natives.yoga.Insets.all(
                                    StyleLength.points(0)))));
            var regions = io.github.digitalsmile.goldberry.input.HitTest.capture(tree);
            var hit = io.github.digitalsmile.goldberry.input.HitTest.at(regions, 10, 10);
            assertTrue(hit.isPresent(), "nothing was hit");
            assertEquals("b", hit.get(),
                    "the box drawn on top was not the one clicked; a header you can"
                            + " see and point through is worse than one you cannot see");
        } finally {
            target.end();
        }
    }
}
