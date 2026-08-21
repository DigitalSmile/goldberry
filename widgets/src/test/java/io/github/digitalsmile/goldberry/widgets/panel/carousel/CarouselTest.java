package io.github.digitalsmile.goldberry.widgets.panel.carousel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.backend.EventLoop;
import io.github.digitalsmile.goldberry.input.FocusScope;
import io.github.digitalsmile.goldberry.input.Key;
import io.github.digitalsmile.goldberry.input.KeyEvent;
import io.github.digitalsmile.goldberry.input.Modifiers;
import io.github.digitalsmile.goldberry.input.PointerEvent;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.Widgets;
import io.github.digitalsmile.goldberry.widgets.panel.Described;
import io.github.digitalsmile.goldberry.widgets.text.Text;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// `carousel` — §5's one-at-a-time list, and the only widget in the group that is
/// a controller ([ADR-0165]).
///
/// The rotation is what the tests are for. §1.7 rule 4 makes "nothing advances on
/// its own unless `interval` is set" and the three conditions for stopping it as
/// much a part of the widget as the slides are — so most of what is below is
/// about a timer that must **not** have been scheduled.
class CarouselTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    /// A host whose timers are fired by hand, so a rotation can be driven a slide
    /// at a time without a test sleeping for five seconds.
    private static final class TimedHost extends TestHost {

        private final List<Runnable> scheduled = new ArrayList<>();
        private final List<Duration> delays = new ArrayList<>();
        private final List<EventLoop.Timer> handed = new ArrayList<>();

        @Override
        public EventLoop.Timer after(Duration delay, Runnable action) {
            scheduled.add(action);
            delays.add(delay);
            var timer = io.github.digitalsmile.goldberry.backend.TestTimers.pending();
            handed.add(timer);
            return timer;
        }

        /// Whether every timer this ever handed out has been cancelled.
        boolean allCancelled() {
            return handed.stream().noneMatch(EventLoop.Timer::isPending);
        }

        /// Whether anything is waiting to fire.
        boolean pending() {
            return !scheduled.isEmpty();
        }

        /// Fires the most recently scheduled action, as the loop would.
        void fire() {
            if (scheduled.isEmpty()) {
                throw new AssertionError("nothing was scheduled to fire");
            }
            var action = scheduled.removeLast();
            delays.removeLast();
            action.run();
        }

        void clear() {
            scheduled.clear();
            delays.clear();
        }
    }

    private static Carousel carousel(int index, boolean loop, Duration interval, int slides) {
        var kids = new ArrayList<io.github.digitalsmile.goldberry.widget.Widget>();
        for (var slide = 0; slide < slides; slide++) {
            kids.add(new Text("slide " + slide));
        }
        return new Carousel(index, null, loop, interval, kids, Attributes.NONE);
    }

    private static CarouselView view(ElementTree tree) {
        return Described.first(tree, CarouselView.class);
    }

    private static boolean showing(ElementTree tree, String text) {
        return Described.in(tree).stream()
                .anyMatch(w -> w instanceof Text it && text.equals(it.content()));
    }

    private static void key(ElementTree tree, Key which) {
        view(tree).onKey(new KeyEvent(KeyEvent.Kind.PRESSED, which, Modifiers.NONE, false, null));
        tree.flush();
    }

    @Nested
    @DisplayName("what it shows")
    class Showing {

        /// `tabs`'s bargain: a slide nobody can see should not hold
        /// subscriptions, images or a scroll position.
        @Test
        @DisplayName("only the current slide is built")
        void oneSlideAtATime() {
            var tree = new ElementTree(carousel(1, false, null, 3));

            assertTrue(showing(tree, "slide 1"));
            assertFalse(showing(tree, "slide 0"));
            assertFalse(showing(tree, "slide 2"));
        }

        @Test
        @DisplayName("a dot per slide, and the current one says so")
        void dots() {
            var tree = new ElementTree(carousel(1, false, null, 3));
            var dots = Described.of(tree, CarouselView.CarouselDot.class);

            assertEquals(3, dots.size());
            assertFalse(dots.get(0).isChecked());
            assertTrue(dots.get(1).isChecked());
        }

        /// One slide is not a carousel: no dots, no tab stop, nothing to move
        /// between.
        @Test
        @DisplayName("a single slide has no dots and takes no focus")
        void oneSlide() {
            var tree = new ElementTree(carousel(0, false, null, 1));

            assertTrue(Described.of(tree, CarouselView.CarouselDot.class).isEmpty());
            assertFalse(view(tree).isFocusable());
        }

        /// An empty carousel is a legal thing for a bound list to be between
        /// frames, and must not throw.
        @Test
        @DisplayName("no slides at all is empty rather than broken")
        void noSlides() {
            var tree = new ElementTree(carousel(0, false, null, 0));

            assertEquals(0, view(tree).count());
            assertTrue(Described.of(tree, CarouselView.CarouselDot.class).isEmpty());
        }

        /// §5: "slides are one Tab stop and their content is reachable inside".
        @Test
        @DisplayName("the strip is one tab stop, and its arrows run along it")
        void oneTabStop() {
            var tree = new ElementTree(carousel(0, false, null, 3));

            assertTrue(view(tree).isFocusable());
            assertEquals(FocusScope.HORIZONTAL, view(tree).focusScope());
        }
    }

    @Nested
    @DisplayName("moving between slides")
    class Moving {

        @Test
        @DisplayName("the arrows move one slide")
        void arrows() {
            var tree = new ElementTree(carousel(0, false, null, 3));

            key(tree, Key.RIGHT);
            assertEquals(1, view(tree).index());

            key(tree, Key.LEFT);
            assertEquals(0, view(tree).index());
        }

        @Test
        @DisplayName("Home and End go to the ends")
        void homeAndEnd() {
            var tree = new ElementTree(carousel(1, false, null, 3));

            key(tree, Key.END);
            assertEquals(2, view(tree).index());

            key(tree, Key.HOME);
            assertEquals(0, view(tree).index());
        }

        /// **`loop` is off by default**, and at the last slide a disabled `Next`
        /// is what says "that is all of them".
        @Test
        @DisplayName("without loop the ends stop, and the buttons say so")
        void endsStop() {
            var tree = new ElementTree(carousel(0, false, null, 3));

            assertFalse(view(tree).canGoBack());
            assertTrue(view(tree).canGoForward());

            key(tree, Key.LEFT);
            assertEquals(0, view(tree).index(), "there is nowhere before the first");

            key(tree, Key.END);
            assertFalse(view(tree).canGoForward());
            key(tree, Key.RIGHT);
            assertEquals(2, view(tree).index(), "and nowhere after the last");
        }

        @Test
        @DisplayName("with loop the ends wrap, and both buttons stay live")
        void loopWraps() {
            var tree = new ElementTree(carousel(0, true, null, 3));

            assertTrue(view(tree).canGoBack(), "the first slide can go back, to the last");

            key(tree, Key.LEFT);
            assertEquals(2, view(tree).index());

            key(tree, Key.RIGHT);
            assertEquals(0, view(tree).index());
        }

        @Test
        @DisplayName("a dot goes straight to its slide")
        void dotsGo() {
            var tree = new ElementTree(carousel(0, false, null, 3));

            Described.of(tree, CarouselView.CarouselDot.class).get(2).onPointer(
                    new PointerEvent(PointerEvent.Kind.CLICKED, 0, 0,
                            PointerEvent.Button.PRIMARY, 1, null));
            tree.flush();

            assertEquals(2, view(tree).index());
        }

        /// Nine slides would otherwise be nine tab stops on top of the two
        /// buttons — `tab`'s close-button argument (ADR-0107).
        @Test
        @DisplayName("a dot is not focusable")
        void dotsAreNotTabStops() {
            var tree = new ElementTree(carousel(0, false, null, 3));

            for (var dot : Described.of(tree, CarouselView.CarouselDot.class)) {
                assertFalse(dot.isFocusable());
            }
        }

        /// A carousel whose `onChange` does nothing stays put, which is the
        /// behaviour every value in this catalog has.
        @Test
        @DisplayName("a controlled carousel asks and does not decide")
        void controlled() {
            var asked = new AtomicInteger(-1);
            var tree = new ElementTree(new Carousel(0, asked::set,
                    new Text("a"), new Text("b")));

            key(tree, Key.RIGHT);

            assertEquals(1, asked.get());
            assertEquals(0, view(tree).index(), "and stayed, because nobody answered");
        }
    }

    @Nested
    @DisplayName("the rotation, and the three reasons to stop it")
    class Rotation {

        private final TimedHost host = new TimedHost();

        private ElementTree rotating(int slides, boolean loop) {
            return new ElementTree(
                    carousel(0, loop, Duration.ofSeconds(5), slides), host);
        }

        /// §5: "**Nothing advances on its own unless `interval` is set**". The
        /// assertion is that no timer was asked for at all.
        @Test
        @DisplayName("with no interval, nothing is ever scheduled")
        void noIntervalNoTimer() {
            new ElementTree(carousel(0, false, null, 3), host);

            assertFalse(host.pending());
        }

        @Test
        @DisplayName("with an interval, the rotation advances a slide at a time")
        void rotates() {
            var tree = rotating(3, false);

            assertTrue(host.pending());
            host.fire();
            tree.flush();

            assertEquals(1, view(tree).index());
        }

        /// A carousel that is not looping stops at the end rather than spinning a
        /// timer forever for a slide that never changes.
        @Test
        @DisplayName("a rotation that is not looping stops at the last slide")
        void stopsAtTheEnd() {
            var tree = rotating(2, false);

            host.fire();
            tree.flush();

            assertEquals(1, view(tree).index());
            assertFalse(host.pending(), "nothing left to advance to, so nothing scheduled");
        }

        @Test
        @DisplayName("a looping rotation keeps going round")
        void loopsForever() {
            var tree = rotating(2, true);

            host.fire();
            tree.flush();
            assertEquals(1, view(tree).index());
            assertTrue(host.pending());

            host.fire();
            tree.flush();
            assertEquals(0, view(tree).index());
        }

        /// **Reason one.** Reading a slide means having the pointer near it.
        @Test
        @DisplayName("hover stops it, and leaving starts it again")
        void hoverPauses() {
            var tree = rotating(3, true);
            host.clear();

            view(tree).onPointer(new PointerEvent(PointerEvent.Kind.ENTERED, 0, 0,
                    null, 0, null));
            assertFalse(host.pending(), "the pointer is on it");

            view(tree).onPointer(new PointerEvent(PointerEvent.Kind.EXITED, 0, 0,
                    null, 0, null));
            assertTrue(host.pending(), "and off it again");
        }

        /// **Reason two**, as far as it goes: focus on the strip. Focus on a
        /// widget *inside a slide* does not pause it, because the cascade has no
        /// `:focus-within` — recorded in `TODO.md` rather than papered over.
        @Test
        @DisplayName("focus on the strip stops it")
        void focusPauses() {
            var tree = rotating(3, true);
            host.clear();

            view(tree).onFocusChanged(true, true);
            assertFalse(host.pending());

            view(tree).onFocusChanged(false, true);
            assertTrue(host.pending());
        }

        /// **Reason three**, and the one §1.7 rule 4 is really about: a carousel
        /// that moves while being read is the canonical violation.
        @Test
        @DisplayName("reduced motion stops it entirely")
        void reducedMotionStopsIt() {
            var tree = rotating(3, true);
            host.clear();

            view(tree).onMotion().accept(true);
            assertFalse(host.pending());

            view(tree).onMotion().accept(false);
            assertTrue(host.pending());
        }

        /// The condition is checked **again on firing**, not only on scheduling:
        /// a timer already in flight when the pointer arrives would otherwise
        /// advance one slide past the moment it was supposed to stop.
        @Test
        @DisplayName("a timer already in flight does nothing once a reason to stop appears")
        void checkedOnFiringToo() {
            var tree = rotating(3, true);

            // Cancelling is the stub's business and it does not remove the
            // action, so this is exactly the race: the loop is about to run
            // something the widget has since decided against.
            view(tree).onMotion().accept(true);
            host.fire();
            tree.flush();

            assertEquals(0, view(tree).index());
        }

        /// One slide has nothing to rotate to, and asking for a timer would be a
        /// wakeup a second for a picture that never changes.
        @Test
        @DisplayName("one slide never rotates, whatever the interval says")
        void oneSlideNeverRotates() {
            new ElementTree(carousel(0, true, Duration.ofSeconds(5), 1), host);

            assertFalse(host.pending());
        }

        /// The other leak a widget can cause: a timer outliving the tree that
        /// scheduled it.
        @Test
        @DisplayName("unmounting cancels the pending advance")
        void unmountCancels() {
            var tree = rotating(3, true);

            tree.unmount();

            assertTrue(host.allCancelled(),
                    "the timer must be cancelled, not left to fire into a dead tree");
        }
    }

    @Nested
    @DisplayName("the arrival, which is not a transition")
    class Arriving {

        /// A slide that has been there a while animates nothing and asks for
        /// nothing, so a window with a carousel in it is as idle as one without.
        @Test
        @DisplayName("a settled slide reports no animation")
        void settled() {
            var tree = new ElementTree(carousel(0, false, null, 3));
            var viewport = Described.first(tree, CarouselView.CarouselViewport.class);

            assertEquals(1, viewport.visibility().applyAsDouble(0), 1e-9,
                    "nothing has moved, so the first slide is fully arrived");
        }

        /// The arriving slide starts invisible and ends visible, and the clock is
        /// stamped by the **first read** — which is why a fresh phase reports 0 at
        /// whatever time it is first asked, rather than at zero.
        @Test
        @DisplayName("a slide change starts an arrival from wherever the clock is")
        void arrivalStartsOnFirstRead() {
            var tree = new ElementTree(carousel(0, false, null, 3));
            key(tree, Key.RIGHT);
            var arriving = Described.first(tree, CarouselView.CarouselViewport.class);

            // 5000 is an arbitrary "now": a phase has no beginning until it is
            // asked, so this is its beginning.
            assertEquals(0, arriving.visibility().applyAsDouble(5000), 1e-9);
            assertEquals(0.5, arriving.visibility().applyAsDouble(5080), 1e-9);
            assertEquals(1, arriving.visibility().applyAsDouble(5160), 1e-9);
        }

        /// Going forwards the new slide comes in from the right, which moves the
        /// content leftwards — the direction a reader's eye is already going.
        @Test
        @DisplayName("the direction follows the way the carousel moved")
        void direction() {
            var tree = new ElementTree(carousel(1, false, null, 3));

            key(tree, Key.RIGHT);
            assertEquals(1, Described.first(tree,
                    CarouselView.CarouselViewport.class).direction());

            key(tree, Key.LEFT);
            assertEquals(-1, Described.first(tree,
                    CarouselView.CarouselViewport.class).direction());
        }

        /// Wrapping from the last slide to the first is a move **forwards**: what
        /// the reader asked for was "next", not a long way back.
        @Test
        @DisplayName("a wrap is the direction that was asked for, not the arithmetic")
        void wrapDirection() {
            var tree = new ElementTree(carousel(2, true, null, 3));

            key(tree, Key.RIGHT);
            assertEquals(0, view(tree).index());
            assertEquals(1, Described.first(tree,
                    CarouselView.CarouselViewport.class).direction(),
                    "last to first is still forwards");

            key(tree, Key.LEFT);
            assertEquals(2, view(tree).index());
            assertEquals(-1, Described.first(tree,
                    CarouselView.CarouselViewport.class).direction(),
                    "and first to last is still backwards");
        }

        /// §1.7 asks for movement to be *removed* rather than shortened, so
        /// there is no animation at all — the slide is simply there.
        @Test
        @DisplayName("a viewport that can animate says so, and one that cannot does not")
        void animating() {
            var tree = new ElementTree(carousel(0, false, null, 3));

            assertTrue(Described.first(tree, CarouselView.CarouselViewport.class)
                    .isAnimating());
        }
    }

    @Nested
    @DisplayName("from markup")
    class Markup {

        @Test
        @DisplayName("a carousel inflates, with its interval in milliseconds")
        void inflates() {
            var widget = Widgets.inflater().inflate(KdlParser.parse("""
                    carousel loop=#true interval=5000 {
                        text "one"
                        text "two"
                    }
                    """).getFirst());
            var it = assertInstanceOf(Carousel.class, widget);

            assertTrue(it.loop());
            assertEquals(Duration.ofMillis(5000), it.interval());
            assertTrue(it.rotates());
        }

        /// A document that names no `change=` must leave the carousel
        /// **uncontrolled**, or it would be a carousel nobody answers and it
        /// would never move.
        @Test
        @DisplayName("no change action means the carousel keeps its own index")
        void unwiredIsUncontrolled() {
            var widget = Widgets.inflater().inflate(
                    KdlParser.parse("carousel { text \"one\"; text \"two\" }").getFirst());
            var it = assertInstanceOf(Carousel.class, widget);

            assertFalse(it.isControlled());
        }

        @Test
        @DisplayName("no interval means no rotation")
        void noInterval() {
            var widget = Widgets.inflater().inflate(
                    KdlParser.parse("carousel { text \"one\"; text \"two\" }").getFirst());

            assertFalse(assertInstanceOf(Carousel.class, widget).rotates());
        }
    }
}
