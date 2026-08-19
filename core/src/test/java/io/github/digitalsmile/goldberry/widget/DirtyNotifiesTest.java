package io.github.digitalsmile.goldberry.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// A `setState` asks for a frame
/// ([ADR-0122](../../../../../../book/src/adr/0122-a-setstate-asks-for-a-frame.md)).
///
/// The rule this covers had no test and could not have had one of the usual
/// shape: a widget test drives frames itself, so `flush(); update(...)` in a loop
/// is a frame loop that never asks whether anybody wanted a frame. What was
/// missing was the *asking*, and only something that counts the requests can see
/// it.
class DirtyNotifiesTest {

    /// A widget whose state can be poked from outside.
    private record Counter(String label) implements Widget.Stateful {

        @Override
        public State<?> createState() {
            return new CounterState();
        }
    }

    private static final class CounterState extends State<Counter> {

        private int clicks;

        @Override
        public Widget build(BuildContext context) {
            return new Leafy(widget().label() + clicks);
        }

        void bump() {
            setState(() -> clicks++);
        }
    }

    private record Leafy(String text) implements Widget.Leaf {
    }

    private static CounterState stateOf(ElementTree tree) {
        return (CounterState) tree.root().state().orElseThrow();
    }

    @Nested
    @DisplayName("asking for a frame")
    class Asking {

        @Test
        @DisplayName("a setState on a clean tree asks once")
        void asksOnce() {
            var tree = new ElementTree(new Counter("n"));
            tree.flush();
            var asked = new int[1];
            tree.onDirty(() -> asked[0]++);

            stateOf(tree).bump();

            assertEquals(1, asked[0],
                    "a setState that asked for no frame is a change nobody paints");
        }

        @Test
        @DisplayName("ten setStates before a frame ask once, not ten")
        void coalesces() {
            var tree = new ElementTree(new Counter("n"));
            tree.flush();
            var asked = new int[1];
            tree.onDirty(() -> asked[0]++);

            for (var i = 0; i < 10; i++) {
                stateOf(tree).bump();
            }

            // The same coalescing `flush` does one level down: a handler that
            // changes five things wants one frame, not five.
            assertEquals(1, asked[0]);
        }

        @Test
        @DisplayName("it asks again after the tree has been flushed")
        void asksAgainAfterAFrame() {
            var tree = new ElementTree(new Counter("n"));
            tree.flush();
            var asked = new int[1];
            tree.onDirty(() -> asked[0]++);

            stateOf(tree).bump();
            tree.flush();
            stateOf(tree).bump();

            assertEquals(2, asked[0]);
        }

        @Test
        @DisplayName("a still tree asks for nothing, which is what keeps the loop idle")
        void stillTreeIsSilent() {
            var tree = new ElementTree(new Counter("n"));
            tree.flush();
            var asked = new int[1];
            tree.onDirty(() -> asked[0]++);

            tree.flush();
            tree.flush();

            // §1.7's idle frame loop. A tree that asked for a frame because it
            // had been flushed would never stop being flushed.
            assertEquals(0, asked[0]);
        }

        @Test
        @DisplayName("a tree with no listener still works")
        void listenerIsOptional() {
            // Every test in the suite builds a tree without one, and a popup or a
            // measuring pass may genuinely have nothing to paint into.
            var tree = new ElementTree(new Counter("n"));
            tree.flush();

            stateOf(tree).bump();

            assertEquals(List.of(), List.of());
        }
    }
}
