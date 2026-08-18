package io.github.digitalsmile.goldberry.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// What a gesture *started from*, which — like where it started — only the router
/// can answer
/// ([ADR-0089](../../../../../../book/src/adr/0089-a-knobs-gesture-is-a-rate.md)).
///
/// [DragOriginTest]'s sibling, and the same argument one step further. That test
/// is about a gesture's origin as a **point**; this one is about the two facts
/// that are not points — the value the control held when the button went down,
/// and the modifiers that were held with it.
///
/// Written against a bare widget in `:core` rather than against `knob`, for the
/// reason `DragOriginTest` gives: a splitter, a scrollbar and a text selection all
/// want this and none of them will look like a rotary control.
class GestureAnchorTest {

    private final List<String> log = new ArrayList<>();

    /// A node that reports `value` as its anchor and logs what comes back.
    private class Node implements Widget.Leaf, Styled, Handles {
        private final String name;
        private final double value;

        Node(String name, double value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String cssType() {
            return name;
        }

        @Override
        public double gestureAnchor() {
            return value;
        }

        @Override
        public void onPointer(PointerEvent event) {
            if (event.kind() == PointerEvent.Kind.ENTERED || event.kind() == PointerEvent.Kind.EXITED) {
                return;
            }
            log.add(event.kind() + " anchor " + event.anchor()
                    + " gesture " + event.gestureModifiers());
        }
    }

    /// A node that answers nothing, which is every widget in the toolkit but one.
    private class Plain implements Widget.Leaf, Styled, Handles {
        private final String name;

        Plain(String name) {
            this.name = name;
        }

        @Override
        public String cssType() {
            return name;
        }

        @Override
        public void onPointer(PointerEvent event) {
            if (event.kind() == PointerEvent.Kind.ENTERED || event.kind() == PointerEvent.Kind.EXITED) {
                return;
            }
            log.add(event.kind() + " anchor " + event.anchor());
        }
    }

    private PointerRouter routerOver(Widget root) {
        var router = new PointerRouter();
        var tree = new ElementTree(root);
        router.updateRegions(List.of(HitTest.Region.of(tree.root(), 0, 0, 100, 100)));
        return router;
    }

    @Test
    @DisplayName("the anchor is asked once on the press and carried to every event after it")
    void carriedAcrossTheGesture() {
        var router = routerOver(new Node("node", 42));

        router.pointerPressed(30, 40, PointerEvent.Button.PRIMARY, 1);
        router.pointerMoved(30, 60);
        router.pointerReleased(30, 60, PointerEvent.Button.PRIMARY, 1);

        // Every event of the gesture, the synthesized click included: a control
        // reading the anchor on any of them gets the same answer.
        assertEquals(List.of(
                "PRESSED anchor 42.0 gesture none",
                "MOVED anchor 42.0 gesture none",
                "RELEASED anchor 42.0 gesture none",
                "CLICKED anchor 42.0 gesture none"), log);
    }

    /// The half that makes it worth having. `dragY` alone cannot tell a knob what
    /// value to ask for — the same 20px of travel means 62 from an anchor of 42
    /// and 27 from an anchor of 7 — and the widget cannot remember, because the
    /// one that sees the move is a different value from the one that saw the press.
    @Test
    @DisplayName("a different control anchors the same drag differently")
    void theAnchorIsTheControlsOwn() {
        routerOver(new Node("a", 42)).pointerPressed(30, 40, PointerEvent.Button.PRIMARY, 1);
        routerOver(new Node("b", 7)).pointerPressed(30, 40, PointerEvent.Button.PRIMARY, 1);

        assertEquals(List.of(
                "PRESSED anchor 42.0 gesture none",
                "PRESSED anchor 7.0 gesture none"), log);
    }

    /// `NaN` and not zero, which is [PointerEvent#dragX()]'s convention and is
    /// load-bearing: a widget that reads "no gesture" as an anchor of zero would
    /// snap a knob to its minimum on every hover.
    @Test
    @DisplayName("a widget that wants no anchor gets NaN, not zero")
    void noAnchorIsNaN() {
        var router = routerOver(new Plain("plain"));

        router.pointerPressed(30, 40, PointerEvent.Button.PRIMARY, 1);
        router.pointerMoved(30, 60);

        assertEquals(List.of("PRESSED anchor NaN", "MOVED anchor NaN"), log);
    }

    @Test
    @DisplayName("a move with no button held carries no anchor")
    void hoverHasNone() {
        var router = routerOver(new Node("node", 42));

        router.pointerMoved(30, 60);

        assertEquals(List.of("MOVED anchor NaN gesture none"), log);
    }

    @Test
    @DisplayName("the anchor is cleared when the gesture ends, so the next hover has none")
    void clearedOnRelease() {
        var router = routerOver(new Node("node", 42));

        router.pointerPressed(30, 40, PointerEvent.Button.PRIMARY, 1);
        router.pointerReleased(30, 40, PointerEvent.Button.PRIMARY, 1);
        log.clear();

        router.pointerMoved(50, 50);

        assertEquals(List.of("MOVED anchor NaN gesture none"), log);
    }

    /// The whole reason the modifiers are the gesture's rather than the event's.
    ///
    /// The press was made with Shift down and every event after it says so — even
    /// though the moves themselves are delivered with no modifier at all. A knob
    /// reading the live value would rescale travel it had already covered
    /// mid-drag, which draws perfectly and looks like the control slipping.
    @Test
    @DisplayName("the press's modifiers are what the gesture carries, not the move's")
    void modifiersAreSampledAtThePress() {
        var router = routerOver(new Node("node", 42));
        var shift = new Modifiers(true, false, false, false);

        router.pointerPressed(30, 40, PointerEvent.Button.PRIMARY, 1, shift);
        router.pointerMoved(30, 60, Modifiers.NONE);

        assertEquals(List.of(
                "PRESSED anchor 42.0 gesture Shift",
                "MOVED anchor 42.0 gesture Shift"), log);
    }

    /// A press lands on the deepest node under the pointer, which for a real
    /// control is one of its **parts** — a knob's arc, a slider's thumb. The part
    /// has no value to report, so the router keeps walking outwards and the
    /// control that will actually handle the drag is what anchors it.
    @Test
    @DisplayName("a press on a part is anchored by the control around it")
    void aPartDefersToItsControl() {
        var router = new PointerRouter();
        var part = new Plain("part");
        var control = new Node("control", 42);
        var tree = new ElementTree(new Parent(control, part));
        var inner = tree.root().children().getFirst();
        // The part is on top, so it is the deepest region under the pointer --
        // which is what makes this the case that happens rather than a contrived
        // one.
        router.updateRegions(List.of(
                HitTest.Region.of(tree.root(), 0, 0, 100, 100),
                HitTest.Region.of(inner, 0, 0, 100, 100)));

        router.pointerPressed(30, 40, PointerEvent.Button.PRIMARY, 1);

        assertTrue(log.stream().anyMatch(line -> line.contains("anchor 42.0")),
                () -> "the part's press was not anchored by its control: " + log);
    }

    /// A control with one child, so a press can land on the child and bubble.
    private record Parent(Node control, Plain part) implements Widget.Leaf, Styled, Handles {

        @Override
        public String cssType() {
            return control.name;
        }

        @Override
        public double gestureAnchor() {
            return control.value;
        }

        @Override
        public List<Widget> children() {
            return List.of(part);
        }

        @Override
        public void onPointer(PointerEvent event) {
            control.onPointer(event);
        }
    }
}
