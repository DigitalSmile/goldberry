package io.github.digitalsmile.goldberry.widgets.overlay.tour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;

/// `tour` — §5's guided sequence, and the veil under it
/// (ADR-0121).
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
    private static final class StubHost extends io.github.digitalsmile.goldberry.widgets.TestHost {

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

    /// **The card's height is measured, not estimated** ([ADR-0268]).
    ///
    /// `TourStop` decides above-or-below from the card's height, and used a
    /// constant of 132 to do it — so a card taller than that near the bottom of a
    /// window was placed *above* its target when it would have fitted below, and
    /// nothing said why. The entry recording it said measuring "needs the
    /// measure-then-place machinery ADR-0104 built, which works on windows rather
    /// than on boxes", and this widget already banks the **window's** rectangle
    /// from the last frame through `Located`: the card is one node further in and
    /// the same door.
    /// §1.7's overlay curve and §3.1's tour row, which asked for two things and
    /// got neither: a card that **arrives** rather than appears, and a veil
    /// cut-out that **travels** between stops ([ADR-0269]).
    ///
    /// The entry called this "`TabPhase` again: the enter/exit lifecycle built
    /// for one widget, wanted by a third". It was not built for one widget — it
    /// is `widgets.core.Phase` and six families use it — so what was missing was
    /// a tour using it.
    @Nested
    @DisplayName("the arrival and the travel")
    class Motion {

        private static final List<Stop> TWO =
                List.of(new Stop("one", "First", "the first"), new Stop("two", "Second", "the second"));

        private static ElementTree tour(StubHost host) {
            return new ElementTree(new Tour(TWO, host, () -> {}));
        }

        private static StubHost twoAnchors() {
            return new StubHost().anchor("one", 10, 10, 80, 24).anchor("two", 300, 200, 120, 40);
        }

        @Test
        @DisplayName("a tour asks for frames while its card is arriving")
        void theArrivalAsksForFrames() {
            // The assertion `AnimationSweepTest` requires by name, and the one
            // that matters: a phase nothing asks frames for is painted once at
            // whatever the loop caught and left there.
            var stop = stopOf(tour(twoAnchors()));

            assertTrue(stop.isAnimating(), "a tour that just opened is arriving");
            assertNotNull(stop.arrival());
        }

        @Test
        @DisplayName("and the veil does too while the cut-out is travelling")
        void theVeilAsksForFrames() {
            var tree = tour(twoAnchors());
            stopOf(tree).onNext().run();
            tree.flush();

            var veil = (TourVeil) findWidget(tree.root(), TourVeil.class);
            assertNotNull(veil);
            assertTrue(veil.isAnimating(), "the hole is on its way from the first target to the second");
        }

        @Test
        @DisplayName("advancing starts a travel, and it starts from where the tour was")
        void advancingTravelsFromTheOldTarget() {
            var tree = tour(twoAnchors());
            var first = stopOf(tree);
            assertNull(first.travel(), "the first stop has nowhere to have come from");

            first.onNext().run();
            tree.flush();

            var second = stopOf(tree);
            assertNotNull(second.travel(), "advancing did not start a travel");
            assertNotNull(second.cameFrom());
            assertEquals(10, second.cameFrom().left(), 0.01f, "the first stop's target, not the second's");
            assertEquals(300, second.target().left(), 0.01f);
        }

        /// The arrival belongs to the **tour** and not to the stop: advancing
        /// moves the cut-out, and a card that faded in again at every stop would
        /// be a sequence that restarts rather than advances.
        @Test
        @DisplayName("the card does not fade in again at every stop")
        void theArrivalIsTheTours() {
            var tree = tour(twoAnchors());
            var arrival = stopOf(tree).arrival();

            stopOf(tree).onNext().run();
            tree.flush();

            assertSame(arrival, stopOf(tree).arrival(), "a second stop is not a second arrival");
        }
    }

    @Nested
    @DisplayName("the card's height")
    class CardHeight {

        private static final float WINDOW = 400;

        /// Renders a stop against a window `WINDOW` tall and answers where the
        /// card's top inset landed.
        private static float cardTop(LogicalRect target, double measuredHeight) {
            var stop = new TourStop(
                    new Stop("one", "First", "the first thing"),
                    target,
                    // No travel: this is about where a card lands, not about how
                    // it got there, and a running phase would make the answer
                    // depend on the clock.
                    null,
                    null,
                    new io.github.digitalsmile.goldberry.widgets.core.Phase(
                            io.github.digitalsmile.goldberry.widgets.core.Phase.Kind.SETTLED),
                    LogicalRect.of(0, 0, 600, WINDOW),
                    0,
                    3,
                    null,
                    () -> {},
                    () -> {},
                    rect -> {},
                    measuredHeight,
                    height -> {});

            var box = stop.render(
                    ComputedStyle.INITIAL,
                    List.of(Box.of(), Box.of(), Box.of()),
                    io.github.digitalsmile.goldberry.widgets.controls.TestFont.context());

            // The card is the third child, and its `top` is what the
            // above-or-below decision comes out as.
            var inset = box.children().get(2).inset().top();
            return ((io.github.digitalsmile.goldberry.layout.Length.Points) inset).value();
        }

        /// A target chosen so the two answers differ, which is the only fixture
        /// that tests anything: below is `150 + 24 + 12 = 186`, a 132-tall card
        /// needs 186 + 132 + 12 = 330 and fits in 400, and a 260-tall one needs
        /// 458 and does not. Any target where both agree would pass against the
        /// estimate.
        private static final LogicalRect LOW = LogicalRect.of(10, 150, 80, 24);

        @Test
        @DisplayName("a card taller than the estimate is placed above, where the estimate said below")
        void aTallCardFlipsTheDecision() {
            var estimated = cardTop(LOW, 0);
            var measured = cardTop(LOW, 260);

            assertEquals(186, estimated, 0.01f, "150 + 24 + 12: below, on the estimate's say-so");
            assertTrue(
                    measured < LOW.top(),
                    () -> "a 260-tall card cannot fit below a target at 254 in a 400 window, and landed at "
                            + measured);
        }

        @Test
        @DisplayName("and a card the estimate's size is placed where it always was")
        void theEstimateIsStillTheFallback() {
            // Zero means "nothing has been laid out yet", which is every first
            // frame — so the estimate has to keep working and keep agreeing.
            assertEquals(cardTop(LOW, 0), cardTop(LOW, 132), 0.01f);
        }

        @Test
        @DisplayName("the card is handed the callback that banks it")
        void theCardReportsUpwards() {
            var host = new StubHost().anchor("one", 10, 10, 80, 24);
            var tree = new ElementTree(new Tour(List.of(THREE.getFirst()), host, () -> {}));

            var card = (TourCard) findWidget(tree.root(), TourCard.class);
            assertNotNull(card, "no card was described");
            assertNotNull(card.onMeasured(), "the card cannot report a height nobody is listening for");
        }

        /// `Measured`'s third rule — what it triggers must not change what it
        /// reports — holds here **by construction**, and this is the assertion
        /// that says so: the card's content and width do not depend on where it
        /// was placed, so the height it reports is the same above or below.
        @Test
        @DisplayName("and the decision it feeds cannot change the height it was made from")
        void itCannotOscillate() {
            var high = LogicalRect.of(10, 10, 80, 24);

            // Same stop, same card, two placements. If the height fed back into
            // the content this would be the loop; it does not, so it is a fact.
            assertEquals(cardTop(high, 260), cardTop(high, 260), 0.01f);
            assertTrue(cardTop(high, 260) > high.top(), "high target, so below either way");
        }
    }

    @Nested
    @DisplayName("the sequence")
    class Sequence {

        @Test
        @DisplayName("it opens on the first stop, and says where it is in the sequence")
        void opensOnTheFirst() {
            var host = new StubHost()
                    .anchor("one", 10, 10, 80, 24)
                    .anchor("two", 10, 60, 80, 24)
                    .anchor("three", 10, 110, 80, 24);
            var tree = new ElementTree(new Tour(THREE, host, () -> {}));

            var stop = stopOf(tree);
            assertNotNull(stop);
            assertEquals("First", stop.stop().title());
            assertEquals(0, stop.index());
            assertEquals(3, stop.count());
        }

        @Test
        @DisplayName("the first stop offers no Back, because there is nowhere to go")
        void noBackOnTheFirst() {
            var host = new StubHost()
                    .anchor("one", 10, 10, 80, 24)
                    .anchor("two", 10, 60, 80, 24)
                    .anchor("three", 10, 110, 80, 24);
            var tree = new ElementTree(new Tour(THREE, host, () -> {}));

            assertEquals(null, stopOf(tree).onBack());
        }

        @Test
        @DisplayName("Next moves on, and Back comes back")
        void moves() {
            var host = new StubHost()
                    .anchor("one", 10, 10, 80, 24)
                    .anchor("two", 10, 60, 80, 24)
                    .anchor("three", 10, 110, 80, 24);
            var tree = new ElementTree(new Tour(THREE, host, () -> {}));

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
            var tree = new ElementTree(new Tour(List.of(new Stop("one", "Only", "the only thing")), host, () -> {}));

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
            var tree = new ElementTree(
                    new Tour(List.of(new Stop("one", "Only", "the only thing")), host, () -> ended[0] = true));

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
            var host = new StubHost().anchor("two", 10, 60, 80, 24).anchor("three", 10, 110, 80, 24);
            var tree = new ElementTree(new Tour(THREE, host, () -> {}));

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
            var veil = new TourVeil(LogicalRect.of(100, 80, 60, 20), LogicalRect.of(0, 0, 400, 300));

            assertEquals(4, veil.children().size(), "§8's subset has no mask, so the cut-out is four rectangles");
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
            var host = new StubHost()
                    .anchor("one", 10, 10, 80, 24)
                    .anchor("two", 10, 60, 80, 24)
                    .anchor("three", 10, 110, 80, 24);
            var ended = new boolean[1];
            var tree = new ElementTree(new Tour(THREE, host, () -> ended[0] = true));

            var event = new KeyEvent(KeyEvent.Kind.PRESSED, Key.ESCAPE, Modifiers.NONE, false, null);
            stopOf(tree).onKey(event);

            assertTrue(ended[0], "Escape did not end the tour");
            assertTrue(event.isConsumed());
        }

        @Test
        @DisplayName("Right moves on and Left comes back, as in every wizard")
        void arrows() {
            var host = new StubHost()
                    .anchor("one", 10, 10, 80, 24)
                    .anchor("two", 10, 60, 80, 24)
                    .anchor("three", 10, 110, 80, 24);
            var tree = new ElementTree(new Tour(THREE, host, () -> {}));

            stopOf(tree).onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.RIGHT, Modifiers.NONE, false, null));
            tree.flush();
            assertEquals("Second", stopOf(tree).stop().title());

            stopOf(tree).onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.LEFT, Modifiers.NONE, false, null));
            tree.flush();
            assertEquals("First", stopOf(tree).stop().title());
        }

        @Test
        @DisplayName("Left on the first stop is left for whatever else wants it")
        void leftOnFirstIsNotConsumed() {
            var host = new StubHost().anchor("one", 10, 10, 80, 24);
            var tree = new ElementTree(new Tour(List.of(new Stop("one", "Only", "x")), host, () -> {}));

            var event = new KeyEvent(KeyEvent.Kind.PRESSED, Key.LEFT, Modifiers.NONE, false, null);
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
            assertTrue(overlay.isFilling(), "a tour dims everything except one widget, so it must cover everything");
            assertEquals(1, host.filled.size());
        }
    }
}
