package io.github.digitalsmile.goldberry.widgets.panel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.motion.Clock;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.panel.carousel.Carousel;
import io.github.digitalsmile.goldberry.widgets.panel.collapse.Collapse;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// §1.7: **"the frame loop is fully idle when no animation is active."**
///
/// It was false for any window with an open `collapse` or a `carousel` on it, and
/// it was false in a way no picture could show ([ADR-0228]). Both widgets decided
/// at *build* time whether their part was animating — `showing ? this::visibility
/// : null` — so `isAnimating` answered "is the section open" rather than "is it
/// still moving", and only a rebuild took it back out of the loop. An open
/// section is exactly the thing nothing rebuilds.
///
/// The assertion is on the **renderer**, which is the thing the frame loop asks.
/// Each widget's own test says what its part reports; this says what the window
/// does, which is the sentence §1.7 actually writes.
class IdleLoopTest {

    private Clock.Virtual clock;
    private WidgetRenderer renderer;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        clock = Clock.virtual();
        renderer = new WidgetRenderer(
                        List.of(
                                Controls.baseStylesheet(),
                                Theme.NORD_DARK.load(),
                                Stylesheet.parse(CascadeLayer.APPLICATION, "")),
                        TestFont.get())
                .clock(clock);
    }

    /// §1.7's `base`, which is how long a `Phase` runs for.
    private static final long ARRIVAL_MILLIS = 160;

    private void frame(ElementTree tree) {
        tree.flush();
        renderer.render(tree);
    }

    /// Runs the clock well past anything that could be moving, and draws.
    ///
    /// **Well past**, because opening a section also puts `.open` on it, and the
    /// chevron under that class is a CSS `transition` — a perfectly legitimate
    /// second reason for the loop to be awake, and one this test must let finish
    /// before it can say anything about the first.
    ///
    /// **One frame at the current time first**, because a CSS transition starts
    /// on the frame that *observes* the changed style: advancing the clock before
    /// drawing once would start the chevron's rotation after the jump and leave
    /// it running.
    ///
    /// **And only one frame after the jump**, which is the second half of
    /// [ADR-0228]: the renderer asks whether a node animates *after* it has drawn
    /// it, so the frame that finishes an arrival is the last one rather than the
    /// second to last.
    private void settle(ElementTree tree) {
        frame(tree);
        clock.advance(ARRIVAL_MILLIS * 4);
        frame(tree);
    }

    /// The handler for the node a stylesheet calls `cssType`.
    ///
    /// By CSS type rather than by class, because every part in the catalog is
    /// package-private ([ADR-0065]) and this test is about two of them in two
    /// different packages. The type is the name a stylesheet uses, which is a
    /// better thing for a test to depend on anyway.
    private static Handles handlerFor(ElementTree tree, String cssType) {
        return Described.in(tree).stream()
                .filter(w -> w instanceof Styled styled && cssType.equals(styled.cssType()))
                .filter(Handles.class::isInstance)
                .map(Handles.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no handler for a node of type \"" + cssType + "\""));
    }

    @Nested
    @DisplayName("collapse")
    class Collapses {

        private void click(ElementTree tree) {
            handlerFor(tree, "collapse-header")
                    .onPointer(new PointerEvent(PointerEvent.Kind.CLICKED, 0, 0, PointerEvent.Button.PRIMARY, 1, null));
            tree.flush();
        }

        @Test
        @DisplayName("a section that opens asks for frames, and stops when it has arrived")
        void arrivesAndSettles() {
            var tree = new ElementTree(new Collapse("Advanced", new Text("Body")));
            frame(tree);
            assertFalse(renderer.isAnimating(), "a shut section is asking for frames");

            click(tree);
            frame(tree);
            assertTrue(renderer.isAnimating(), "the body is arriving and nothing asked for the frames");

            settle(tree);

            assertFalse(
                    renderer.isAnimating(),
                    "an open section keeps the window awake for ever, which is §1.7's claim inverted");
        }

        @Test
        @DisplayName("a section that started open never asks for a frame")
        void startedOpen() {
            var tree = new ElementTree(new Collapse("Advanced", true, null, new Text("Body")));
            frame(tree);

            assertFalse(renderer.isAnimating(), "a section that was open before the window was is not arriving");
        }

        /// The case `CollapseSection`'s `open` guard exists for: a section shut
        /// half way through its arrival keeps an `ENTERING` phase that nothing
        /// will ever read again, so nothing will ever settle it. A shut section
        /// animates nothing whatever its phase remembers — without the guard this
        /// window would never sleep again.
        @Test
        @DisplayName("a section shut in the middle of arriving goes quiet")
        void shutMidArrival() {
            var tree = new ElementTree(new Collapse("Advanced", new Text("Body")));
            frame(tree);
            click(tree);
            frame(tree);
            clock.advance(ARRIVAL_MILLIS / 2);
            frame(tree);
            assertTrue(renderer.isAnimating(), "half way through an arrival is the one time it should be awake");

            click(tree);
            settle(tree);

            assertFalse(
                    renderer.isAnimating(),
                    "the shut section is holding an unfinished phase that nothing will ever settle");
        }
    }

    @Nested
    @DisplayName("carousel")
    class Carousels {

        private ElementTree carousel() {
            return new ElementTree(new Carousel(new Text("one"), new Text("two"), new Text("three")));
        }

        private void next(ElementTree tree) {
            handlerFor(tree, "carousel")
                    .onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.RIGHT, Modifiers.NONE, false, null));
            tree.flush();
        }

        /// The worst of the two, because it needed no interaction at all: the
        /// viewport was handed a function of the clock that is never null, so it
        /// reported an animation from the first frame and every window with a
        /// carousel on it spun at the refresh rate for ever.
        @Test
        @DisplayName("a carousel nobody has touched never asks for a frame")
        void untouched() {
            var tree = carousel();
            frame(tree);

            assertFalse(renderer.isAnimating(), "a carousel sitting on one slide is spinning the frame loop");
        }

        /// The other half of [ADR-0228], stated on its own: the **frame that
        /// finishes** an arrival is the last one the loop spends. It used not to
        /// be — whether a node animates was read before it was drawn, and drawing
        /// is what advances a phase, so every arrival in the toolkit cost one
        /// frame of nothing happening.
        @Test
        @DisplayName("the frame that finishes an arrival is the last one")
        void noWastedFrame() {
            var tree = carousel();
            frame(tree);
            next(tree);
            frame(tree);

            clock.advance(ARRIVAL_MILLIS);
            frame(tree);

            assertFalse(renderer.isAnimating(), "the loop is spending a frame on an animation that has finished");
        }

        @Test
        @DisplayName("a slide change asks for frames, and stops when it has arrived")
        void arrivesAndSettles() {
            var tree = carousel();
            frame(tree);

            next(tree);
            frame(tree);
            assertTrue(renderer.isAnimating(), "a slide is arriving and nothing asked for the frames");

            settle(tree);

            assertFalse(renderer.isAnimating(), "the carousel never went quiet again");
        }
    }
}
