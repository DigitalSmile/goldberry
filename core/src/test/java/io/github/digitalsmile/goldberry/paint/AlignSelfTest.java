package io.github.digitalsmile.goldberry.paint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.layout.Align;
import io.github.digitalsmile.goldberry.layout.FlexDirection;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;

/// `align-self` — where **one child** sits in its parent's cross axis
/// ([ADR-0244]).
///
/// §8 has listed `align-items/self/content` from the beginning and only the
/// first was built, which a tab strip's `+` found: a child shorter than its row
/// sat at the top of it and there was no per-child way to say otherwise.
///
/// Asserted against **Yoga's own output** rather than against the record. The
/// property is one line in `RenderObject` and the whole of the risk is whether
/// that line runs — a test that read `box.alignSelf()` back would pass on a box
/// nothing ever laid out.
class AlignSelfTest {

    private TestFrames.Target target;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        target = TestFrames.of(200, 200, 1.0f);
    }

    @AfterEach
    void tearDown() {
        if (target != null) {
            target.end();
        }
    }

    /// The laid-out boxes, parent first.
    private List<LogicalRect> layouts(Box root) {
        var out = new ArrayList<LogicalRect>();
        BoxPainter.forEachBox(target.frame(), root, (box, layout) -> out.add(layout));
        return out;
    }

    /// A 200×100 row that packs its children at the **top**, holding two 40×20
    /// children — the second of which is given `self`.
    private Box row(Align self) {
        return Box.filled(0xFF000000)
                .size(Length.points(200), Length.points(100))
                .direction(FlexDirection.ROW)
                .alignItems(Align.FLEX_START)
                .children(child(), child().alignSelf(self));
    }

    private static Box child() {
        return Box.filled(0xFF00FF00).size(Length.points(40), Length.points(20));
    }

    @Test
    @DisplayName("a child says where it sits, and its sibling is unmoved")
    void oneChildOverridesTheRow() {
        var placed = layouts(row(Align.FLEX_END));

        // [0] is the row, [1] the child that said nothing, [2] the one that did.
        assertEquals(0, placed.get(1).top(), 1e-6, "the sibling should still be where align-items put it");
        assertEquals(80, placed.get(2).top(), 1e-6, "the child asking for flex-end should be at the bottom");
    }

    @Test
    @DisplayName("centre is the middle of the cross axis, not the top")
    void centre() {
        var placed = layouts(row(Align.CENTER));

        assertEquals(40, placed.get(2).top(), 1e-6);
    }

    /// `stretch` is the one that changes a *size* rather than a position, which
    /// is what makes it worth its own case: a child with a height of its own
    /// keeps it, so the row is built without one here.
    @Test
    @DisplayName("stretch fills the cross axis")
    void stretch() {
        var row = Box.filled(0xFF000000)
                .size(Length.points(200), Length.points(100))
                .direction(FlexDirection.ROW)
                .alignItems(Align.FLEX_START)
                .children(
                        Box.filled(0xFF00FF00).size(Length.points(40), Length.points(20)),
                        Box.filled(0xFF0000FF)
                                .size(Length.points(40), Length.UNDEFINED)
                                .alignSelf(Align.STRETCH));

        var placed = layouts(row);

        assertEquals(20, placed.get(1).height(), 1e-6, "the sibling keeps its own height");
        assertEquals(100, placed.get(2).height(), 1e-6, "the stretching child should fill the row");
    }

    /// [Align#AUTO] is the default and means "defer to my container" — the only
    /// value that reads differently on a child than it would on a parent. A box
    /// that never mentions `align-self` must lay out exactly as one that says
    /// `auto`, or the default is not a default.
    @Test
    @DisplayName("auto is the default, and is the same as saying nothing")
    void autoIsTheDefault() {
        assertEquals(Align.AUTO, Box.of().alignSelf(), "a box that says nothing should defer to its container");
        assertEquals(layouts(row(Align.AUTO)), layouts(rowWithNothingSaid()));
    }

    /// And the check that keeps the one above honest: the *reason* `auto` and
    /// `flex-end` agree must not be that nothing is wired up at all.
    @Test
    @DisplayName("and a value that is not auto really does move the child")
    void notEverythingIsTheSame() {
        assertNotEquals(layouts(row(Align.AUTO)), layouts(row(Align.FLEX_END)));
    }

    private Box rowWithNothingSaid() {
        return Box.filled(0xFF000000)
                .size(Length.points(200), Length.points(100))
                .direction(FlexDirection.ROW)
                .alignItems(Align.FLEX_START)
                .children(child(), child());
    }
}
