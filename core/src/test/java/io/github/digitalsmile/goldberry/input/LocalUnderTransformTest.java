package io.github.digitalsmile.goldberry.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.css.value.Affine;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Where the pointer is inside a widget that has been **moved** since it was laid
/// out.
///
/// A `scroll` does not lay its content out somewhere else — it puts a
/// `translate` on it, for §1.7's reason that movement stays off layout
/// properties. So every widget inside a scrolled viewport is laid out where it
/// always was and painted a long way from there, and a hit-test region stores
/// the layout rectangle plus the **inverse** of the matrix it was painted with
/// ([ADR-0054](../../../../../../book/src/adr/0054-hit-testing-runs-against-the-painted-frame.md),
/// [ADR-0068](../../../../../../book/src/adr/0068-the-transform-stack-is-java-side.md)).
///
/// `Region.contains` uses that inverse. Everything that answers *where inside*
/// has to use it too, or a control keeps receiving events and starts reading a
/// position from a coordinate system nobody is in.
class LocalUnderTransformTest {

    private final List<PointerEvent.Local> seen = new ArrayList<>();

    private class Control implements Widget.Leaf, Styled, Handles {

        @Override
        public String cssType() {
            return "control";
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public void onPointer(PointerEvent event) {
            if (event.kind() == PointerEvent.Kind.PRESSED) {
                seen.add(event.local());
            }
        }
    }

    /// A 100×40 control laid out at `(0, 500)` and painted `offset` pixels
    /// higher — a card a long way down a scrolled panel.
    private PointerRouter scrolled(double offset) {
        var router = new PointerRouter();
        var tree = new ElementTree(new Control());
        var moved = Affine.translate(0, -offset);
        var regions = new ArrayList<HitTest.Region>();
        regions.add(new HitTest.Region(tree.root(),
                io.github.digitalsmile.goldberry.render.Cursor.DEFAULT,
                0, 500, 100, 40, moved.invert()));
        router.updateRegions(List.copyOf(regions));
        return router;
    }

    @Test
    @DisplayName("the region is found where it was painted, not where it was laid out")
    void hitTestingAlreadyKnew() {
        // The half that has always worked, asserted here so the other half's
        // failure is unambiguous: the control is laid out at y 500 and painted at
        // y 200, and a press at 210 reaches it.
        scrolled(300).pointerPressed(50, 210, PointerEvent.Button.PRIMARY, 1);

        assertEquals(1, seen.size(), "the press reached the control");
    }

    @Test
    @DisplayName("and the local position is measured in the box's own coordinates")
    void theLocalPointIsInTheBox() {
        scrolled(300).pointerPressed(50, 210, PointerEvent.Button.PRIMARY, 1);

        var local = seen.getFirst();
        // Ten pixels down from the control's own top edge. Subtracting the
        // *layout* origin from the *window* point instead gives 210 - 500 = -290,
        // which is not merely off: every widget that asks "am I inside" gets no
        // for the whole length of the scroll, so a chart stops highlighting, a
        // text area puts its caret on the first line and a vertical slider reads
        // its minimum -- while every one of them still receives the event.
        assertEquals(50, local.x(), 1e-6);
        assertEquals(10, local.y(), 1e-6);
        assertEquals(100, local.width(), 1e-6);
        assertEquals(40, local.height(), 1e-6);
        assertTrue(local.fractionY() > 0 && local.fractionY() < 1,
                "and the fraction is inside the box rather than clamped to an end");
    }

    @Test
    @DisplayName("an untransformed box is unaffected, which is every box in an ordinary frame")
    void nothingChangesForTheCommonCase() {
        var router = new PointerRouter();
        var tree = new ElementTree(new Control());
        router.updateRegions(List.of(HitTest.Region.of(tree.root(), 0, 500, 100, 40)));

        router.pointerPressed(50, 510, PointerEvent.Button.PRIMARY, 1);

        var local = seen.getFirst();
        assertEquals(50, local.x(), 1e-6);
        assertEquals(10, local.y(), 1e-6);
    }
}
