package io.github.digitalsmile.goldberry.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// Where a drag started, which only the router can answer
/// ([ADR-0075](../../../../../../book/src/adr/0075-a-gestures-origin-is-the-routers.md)).
///
/// A gesture is a sequence of events; a widget is a value rebuilt every frame.
/// The widget that sees the release is a different instance from the one that saw
/// the press, so it cannot have remembered anything. The router already spans
/// exactly that interval — it takes an implicit capture on the press and drops it
/// on the release — so it reports the offset and no widget holds state.
///
/// Written against a bare widget in `:core` rather than against `toggle`, for the
/// reason [FocusScopeTest] is: `slider`, `knob`, `split-pane` and a scrollbar all
/// want this and none of them will look like a switch.
class DragOriginTest {

    private final List<String> log = new ArrayList<>();

    private class Node implements Widget.Leaf, Styled, Handles {
        private final String name;

        Node(String name) {
            this.name = name;
        }

        @Override
        public String cssType() {
            return name;
        }

        /// `ENTERED` and `EXITED` are deliberately not logged. They are hover
        /// events derived from where the pointer *is*, not steps in a gesture —
        /// the router raises them from `updateHover` whether a button is down or
        /// not — so they carry no origin, and letting them into these assertions
        /// would be asserting on hover flow in a test about drags.
        @Override
        public void onPointer(PointerEvent event) {
            if (event.kind() == PointerEvent.Kind.ENTERED || event.kind() == PointerEvent.Kind.EXITED) {
                return;
            }
            log.add(event.kind() + " drag " + event.dragX() + "," + event.dragY());
        }
    }

    private PointerRouter router;

    @BeforeEach
    void buildTree() {
        router = new PointerRouter();
        var tree = new ElementTree(new Node("node"));
        router.updateRegions(List.of(HitTest.Region.of(tree.root(), 0, 0, 100, 100)));
    }

    @Nested
    @DisplayName("while a button is held")
    class Held {

        /// The press reports a zero drag rather than `NaN`, so a handler that
        /// reads `dragX` on every pointer event does not have to special-case the
        /// first one — the origin is recorded before the dispatch for exactly
        /// this.
        @Test
        @DisplayName("the press itself reports a zero drag")
        void pressIsZero() {
            router.pointerPressed(30, 40, PointerEvent.Button.PRIMARY, 1);

            assertEquals(List.of("PRESSED drag 0.0,0.0"), log);
        }

        @Test
        @DisplayName("a move reports the offset from the press, signed")
        void moveReportsTheOffset() {
            router.pointerPressed(30, 40, PointerEvent.Button.PRIMARY, 1);
            log.clear();

            router.pointerMoved(45, 32);

            assertEquals(List.of("MOVED drag 15.0,-8.0"), log);
        }

        /// The whole point of taking it from the router: capture means a drag
        /// that leaves the node still arrives, and the offset has to survive
        /// that or a switch dragged past its own track would lose the gesture.
        @Test
        @DisplayName("the offset survives the pointer leaving the node")
        void offsetSurvivesLeaving() {
            router.pointerPressed(30, 40, PointerEvent.Button.PRIMARY, 1);
            log.clear();

            router.pointerMoved(300, 40);

            assertEquals(List.of("MOVED drag 270.0,0.0"), log);
        }

        @Test
        @DisplayName("the release and the click both carry the origin")
        void releaseAndClickCarryIt() {
            router.pointerPressed(30, 40, PointerEvent.Button.PRIMARY, 1);
            log.clear();

            router.pointerReleased(38, 40, PointerEvent.Button.PRIMARY, 1);

            // Both, and in that order: a control reading the drag on either one
            // gets the same answer, and the click is dispatched after the origin
            // has been cleared from the router's own field.
            assertEquals(List.of("RELEASED drag 8.0,0.0", "CLICKED drag 8.0,0.0"), log);
        }
    }

    @Nested
    @DisplayName("with no button held")
    class NotHeld {

        /// `NaN` rather than zero, and the reason is the arithmetic rather than
        /// the convention: zero is a *real* answer — it is what a press with no
        /// movement gives — so a widget could not tell "did not move" from "no
        /// gesture". `Math.abs(NaN) >= threshold` is `false`, so the wrong
        /// reading of `NaN` is the right behaviour, which is not true of zero.
        @Test
        @DisplayName("a move with no press reports NaN, which reads as \"not a drag\"")
        void moveWithNoPressIsNaN() {
            router.pointerMoved(45, 32);

            assertEquals(List.of("MOVED drag NaN,NaN"), log);
            assertFalse(Math.abs(Float.NaN) >= 8, "a NaN drag must not read as past any threshold");
        }

        @Test
        @DisplayName("the origin is cleared by the release, so the next move is NaN again")
        void clearedByTheRelease() {
            router.pointerPressed(30, 40, PointerEvent.Button.PRIMARY, 1);
            router.pointerReleased(38, 40, PointerEvent.Button.PRIMARY, 1);
            log.clear();

            router.pointerMoved(90, 90);

            assertEquals(List.of("MOVED drag NaN,NaN"), log);
        }

        @Test
        @DisplayName("a wheel carries no origin even mid-drag — it is not part of the gesture")
        void wheelCarriesNone() {
            router.pointerPressed(30, 40, PointerEvent.Button.PRIMARY, 1);
            log.clear();

            router.pointerWheel(30, 40, 0, 1);

            assertTrue(log.getFirst().startsWith("WHEEL drag NaN"), () -> "log was " + log);
        }
    }
}
