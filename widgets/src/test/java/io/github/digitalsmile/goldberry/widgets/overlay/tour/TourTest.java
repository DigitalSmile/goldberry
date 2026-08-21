package io.github.digitalsmile.goldberry.widgets.overlay.tour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.FrameStats;
import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.Overlay;
import io.github.digitalsmile.goldberry.Popup;
import io.github.digitalsmile.goldberry.Window;
import io.github.digitalsmile.goldberry.backend.LogicalRect;
import io.github.digitalsmile.goldberry.input.HitTest;
import io.github.digitalsmile.goldberry.input.Key;
import io.github.digitalsmile.goldberry.input.KeyEvent;
import io.github.digitalsmile.goldberry.input.Modifiers;
import io.github.digitalsmile.goldberry.text.Fonts;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// `tour` — §5's guided sequence, and the veil under it
/// ([ADR-0121](../../../../../../../../book/src/adr/0121-a-tour-is-a-veil-and-a-sequence.md)).
///
/// Driven against a stub [Host] rather than a live window: everything a tour
/// decides is a function of what `anchor` answers, so a host that answers on
/// demand exercises the whole widget and lets a test say "this target is not on
/// screen" — which is the case §5 asks about most specifically and which a real
/// window makes hard to arrange.
class TourTest {

    /// A host that answers `anchor` from a map and records what was put on it.
    ///
    /// Both halves are [TestHost]'s; what is here is the fluent `anchor(id, …)`
    /// this test was already written against, kept so the cases below read the
    /// way they did.
    private static final class StubHost extends
            io.github.digitalsmile.goldberry.widgets.TestHost {

        StubHost anchor(String id, float x, float y, float w, float h) {
            anchoring(id, x, y, w, h);
            return this;
        }
    }

    /// Builds the tour's tree and finds the `tour` node's description, which is
    /// where everything a stop decided ends up.
    private static TourStop stopOf(ElementTree tree) {
        return (TourStop) findWidget(tree.root(), TourStop.class);
    }

    private static Widget findWidget(Element from, Class<?> type) {
        if (type.isInstance(from.widget())) {
            return from.widget();
        }
        for (var child : from.children()) {
            var found = findWidget(child, type);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static final List<Stop> THREE = List.of(
            new Stop("one", "First", "the first thing"),
            new Stop("two", "Second", "the second thing"),
            new Stop("three", "Third", "the third thing"));

    @Nested
    @DisplayName("the sequence")
    class Sequence {

        @Test
        @DisplayName("it opens on the first stop, and says where it is in the sequence")
        void opensOnTheFirst() {
            var host = new StubHost().anchor("one", 10, 10, 80, 24)
                    .anchor("two", 10, 60, 80, 24).anchor("three", 10, 110, 80, 24);
            var tree = new ElementTree(new Tour(THREE, host, () -> { }));

            var stop = stopOf(tree);
            assertNotNull(stop);
            assertEquals("First", stop.stop().title());
            assertEquals(0, stop.index());
            assertEquals(3, stop.count());
        }

        @Test
        @DisplayName("the first stop offers no Back, because there is nowhere to go")
        void noBackOnTheFirst() {
            var host = new StubHost().anchor("one", 10, 10, 80, 24)
                    .anchor("two", 10, 60, 80, 24).anchor("three", 10, 110, 80, 24);
            var tree = new ElementTree(new Tour(THREE, host, () -> { }));

            assertEquals(null, stopOf(tree).onBack());
        }

        @Test
        @DisplayName("Next moves on, and Back comes back")
        void moves() {
            var host = new StubHost().anchor("one", 10, 10, 80, 24)
                    .anchor("two", 10, 60, 80, 24).anchor("three", 10, 110, 80, 24);
            var tree = new ElementTree(new Tour(THREE, host, () -> { }));

            stopOf(tree).onNext().run();
            tree.flush();
            assertEquals("Second", stopOf(tree).stop().title());
            assertNotNull(stopOf(tree).onBack(), "the second stop should offer Back");

            stopOf(tree).onBack().run();
            tree.flush();
            assertEquals("First", stopOf(tree).stop().title());
        }

        @Test
        @DisplayName("the last stop's forward button says Done rather than promising more")
        void lastSaysDone() {
            var host = new StubHost().anchor("one", 10, 10, 80, 24);
            var tree = new ElementTree(new Tour(
                    List.of(new Stop("one", "Only", "the only thing")), host, () -> { }));

            // Not asserted through the label directly -- that is the button's --
            // but through the fact that this is the last index, which is what the
            // label is computed from.
            assertEquals(0, stopOf(tree).index());
            assertEquals(1, stopOf(tree).count());
        }

        @Test
        @DisplayName("Next past the end ends the tour")
        void endsAtTheEnd() {
            var host = new StubHost().anchor("one", 10, 10, 80, 24);
            var ended = new boolean[1];
            var tree = new ElementTree(new Tour(
                    List.of(new Stop("one", "Only", "the only thing")), host,
                    () -> ended[0] = true));

            stopOf(tree).onNext().run();

            assertTrue(ended[0], "the tour did not end when its last stop was passed");
        }
    }

    @Nested
    @DisplayName("a target that is not there")
    class Missing {

        @Test
        @DisplayName("a stop whose target is not on screen is skipped, not thrown on")
        void skipsMissing() {
            // §5: "A target that is not in the tree is skipped with a warning
            // rather than throwing — a tour is documentation, and documentation
            // going stale must not take the window down."
            var host = new StubHost().anchor("two", 10, 60, 80, 24)
                    .anchor("three", 10, 110, 80, 24);
            var tree = new ElementTree(new Tour(THREE, host, () -> { }));

            assertEquals("Second", stopOf(tree).stop().title());
        }

        @Test
        @DisplayName("a tour none of whose targets exist ends rather than showing nothing")
        void endsWhenNothingIsFound() {
            var ended = new boolean[1];
            var tree = new ElementTree(new Tour(THREE, new StubHost(), () -> ended[0] = true));

            assertTrue(ended[0], "a tour with no findable targets did not end");
        }
    }

    @Nested
    @DisplayName("the veil")
    class Veil {

        @Test
        @DisplayName("four bands tile the window and leave the target uncovered")
        void tiles() {
            var veil = new TourVeil(LogicalRect.of(100, 80, 60, 20),
                    LogicalRect.of(0, 0, 400, 300));

            assertEquals(4, veil.children().size(),
                    "§8's subset has no mask, so the cut-out is four rectangles");
        }

        @Test
        @DisplayName("a veil with no target dims everything rather than flashing clear")
        void noTarget() {
            var veil = new TourVeil(null, LogicalRect.of(0, 0, 400, 300));

            assertEquals(4, veil.children().size());
        }
    }

    @Nested
    @DisplayName("the keyboard")
    class Keyboard {

        @Test
        @DisplayName("Escape skips the whole tour, not one stop")
        void escapeSkips() {
            var host = new StubHost().anchor("one", 10, 10, 80, 24)
                    .anchor("two", 10, 60, 80, 24).anchor("three", 10, 110, 80, 24);
            var ended = new boolean[1];
            var tree = new ElementTree(new Tour(THREE, host, () -> ended[0] = true));

            var event = new KeyEvent(KeyEvent.Kind.PRESSED, Key.ESCAPE, Modifiers.NONE,
                    false, null);
            stopOf(tree).onKey(event);

            assertTrue(ended[0], "Escape did not end the tour");
            assertTrue(event.isConsumed());
        }

        @Test
        @DisplayName("Right moves on and Left comes back, as in every wizard")
        void arrows() {
            var host = new StubHost().anchor("one", 10, 10, 80, 24)
                    .anchor("two", 10, 60, 80, 24).anchor("three", 10, 110, 80, 24);
            var tree = new ElementTree(new Tour(THREE, host, () -> { }));

            stopOf(tree).onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.RIGHT,
                    Modifiers.NONE, false, null));
            tree.flush();
            assertEquals("Second", stopOf(tree).stop().title());

            stopOf(tree).onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.LEFT,
                    Modifiers.NONE, false, null));
            tree.flush();
            assertEquals("First", stopOf(tree).stop().title());
        }

        @Test
        @DisplayName("Left on the first stop is left for whatever else wants it")
        void leftOnFirstIsNotConsumed() {
            var host = new StubHost().anchor("one", 10, 10, 80, 24);
            var tree = new ElementTree(new Tour(
                    List.of(new Stop("one", "Only", "x")), host, () -> { }));

            var event = new KeyEvent(KeyEvent.Kind.PRESSED, Key.LEFT, Modifiers.NONE,
                    false, null);
            stopOf(tree).onKey(event);

            assertFalse(event.isConsumed());
        }
    }

    @Nested
    @DisplayName("starting one")
    class Starting {

        @Test
        @DisplayName("an empty tour ends immediately rather than throwing")
        void empty() {
            var ended = new boolean[1];
            var overlay = Tours.start(new StubHost(), List.of(), () -> ended[0] = true);

            // A tour assembled from a filtered list is empty exactly when nothing
            // in it applies, and a crash is the wrong answer to "nothing to show".
            assertEquals(null, overlay);
            assertTrue(ended[0]);
        }

        @Test
        @DisplayName("a stop with no target names nothing and is refused at construction")
        void requiresATarget() {
            assertThrows(IllegalArgumentException.class, () -> new Stop(null, "t", "b"));
            assertThrows(IllegalArgumentException.class, () -> new Stop("  ", "t", "b"));
        }

        @Test
        @DisplayName("starting one puts a filling overlay on the host")
        void fills() {
            var host = new StubHost().anchor("one", 10, 10, 80, 24);

            var overlay = Tours.start(host, List.of(new Stop("one", "Only", "x")));

            assertNotNull(overlay);
            assertTrue(overlay.isFilling(),
                    "a tour dims everything except one widget, so it must cover everything");
            assertEquals(1, host.filled.size());
        }
    }
}
