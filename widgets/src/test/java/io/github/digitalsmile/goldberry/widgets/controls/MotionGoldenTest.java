package io.github.digitalsmile.goldberry.widgets.controls;

import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widgets.core.Row;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Selector;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.layout.BoxPainter;
import io.github.digitalsmile.goldberry.motion.Clock;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// A frame **in the middle of a transition**, asserted pixel by pixel.
///
/// This is the test the virtual clock exists for. Against a wall clock it is not
/// merely flaky, it is impossible: the test would have to sleep and would then be
/// asserting on whatever the scheduler happened to give it, which on a loaded CI
/// runner is a different frame every run. `clock.advance(50)` gives exactly the
/// frame at 50 ms of a 100 ms hover, on every machine.
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites the images.
class MotionGoldenTest {

    private Clock.Virtual clock;
    private WidgetRenderer renderer;
    private ElementTree tree;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        clock = Clock.virtual();
        tree = new ElementTree(new Row(
                List.of(
                        new Button("Resting", null, null, false, id("a")),
                        new Button("Hovering", null, null, false, id("b")),
                        new Button("Hovered", null, null, false, id("c"))),
                id("row")));
        renderer = new WidgetRenderer(
                List.of(
                        Controls.baseStylesheet(),
                        Theme.NORD_DARK.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, """
                                #row { padding: 12px; gap: 12px; align-items: center;
                                       background: var(--gb-bg) }
                                """)),
                TestFont.get()).clock(clock);
    }

    private static Attributes id(String id, String... classes) {
        return new Attributes(id, Set.of(classes), id);
    }

    /// Renders one frame at the clock's current time, without painting it.
    private void frame() {
        renderer.render(tree);
    }

    @Test
    @DisplayName("a hover caught halfway is between the two colours")
    void midTransition() {
        // Frame 1 establishes the resting style. Nothing transitions on a first
        // frame: a control appearing is not a control changing, or a window
        // would fade every control in from black when it opened.
        frame();
        assertFalse(renderer.isAnimating());

        // The third button is hovered and taken all the way past the end, so it
        // is sitting at the finished colour.
        tree.root().children().get(2).setPseudoClass(Selector.PseudoClass.HOVER, true);
        frame();
        assertTrue(renderer.isAnimating(), "a declared transition started");
        clock.advance(150);
        frame();
        assertFalse(renderer.isAnimating(), "and finished");

        // The second is hovered and caught exactly 50 ms into its 100 ms fade.
        // So the image is the start, the middle and the end of one transition,
        // side by side in a single frame -- which is a picture no wall clock can
        // take.
        tree.root().children().get(1).setPseudoClass(Selector.PseudoClass.HOVER, true);
        frame();
        clock.advance(50);
        var midway = renderer.render(tree);
        assertTrue(renderer.isAnimating());

        var resting = midway.children().getFirst().background();
        var moving = midway.children().get(1).background();
        var arrived = midway.children().get(2).background();
        assertNotEquals(resting, moving, "the middle one has left");
        assertNotEquals(arrived, moving, "and has not got there");

        GoldenImage.assertMatches("button-hover-midway", 460, 56, 1.0f,
                frame -> BoxPainter.paint(frame, midway));
    }

    @Test
    @DisplayName("the frame loop goes idle when the transition ends")
    void settles() {
        // §1.7: "the frame loop is fully idle when no animation is active -- no
        // polling, no battery cost". An application asks for another frame only
        // while `isAnimating`, so this is the whole of that promise.
        frame();
        tree.root().children().get(1).setPseudoClass(Selector.PseudoClass.HOVER, true);
        frame();
        assertTrue(renderer.isAnimating());

        clock.advance(99);
        frame();
        assertTrue(renderer.isAnimating(), "still moving at 99ms of 100");

        clock.advance(2);
        frame();
        assertFalse(renderer.isAnimating(), "and idle once it has arrived");
    }

    @Test
    @DisplayName("a press applies in 0ms and its release fades")
    void pressIsInstant() {
        // §1.7's rule 1, and §3.1's "press: instant in, fast out". The pressed
        // rule declares a zero duration and the resting rule declares the fade,
        // so entering `:active` snaps and leaving it eases -- because the timing
        // that applies is the one on the style being moved *to*.
        var button = tree.root().children().getFirst();
        frame();

        button.setPseudoClass(Selector.PseudoClass.ACTIVE, true);
        frame();
        assertFalse(renderer.isAnimating(),
                "input feedback is instant: a press that faded in would feel disconnected");

        button.setPseudoClass(Selector.PseudoClass.ACTIVE, false);
        frame();
        assertTrue(renderer.isAnimating(), "and the release fades out");
    }

    @Test
    @DisplayName("reduced motion reaches the same state with no frames in between")
    void reducedMotion() {
        // §1.7's rule 6. The declarations are kept at zero duration rather than
        // dropped, so a reduced-motion user takes the same route through the
        // toolkit and simply arrives at once.
        renderer.reducedMotion(true);
        frame();
        tree.root().children().get(1).setPseudoClass(Selector.PseudoClass.HOVER, true);

        var painted = renderer.render(tree);
        assertFalse(renderer.isAnimating());

        // And it is at the hover colour already, not the resting one.
        renderer.reducedMotion(false);
        var hovered = renderer.render(tree);
        assertEquals(
                background(hovered, 1),
                background(painted, 1),
                "reduced motion arrives at the same colour, immediately");
    }

    @Test
    @DisplayName("it starts at the resting token and ends at the hover one")
    void endpointsAreTheTokens() {
        // The reversal semantics are asserted in
        // `io.github.digitalsmile.goldberry.motion.MotionTest`, against black and
        // white: Nord's resting and hover surfaces differ by about ten per
        // channel, which is too little for a "did it jump" assertion here to see
        // anything. What *this* level can see is that the transition runs between
        // the two theme tokens and not between something else.
        var button = tree.root().children().get(1);
        frame();
        var resting = background(renderer.render(tree), 1);
        assertEquals(0xFF434C5E, resting, "nord2, --gb-button-bg on the dark theme");

        button.setPseudoClass(Selector.PseudoClass.HOVER, true);
        frame();
        clock.advance(50);
        var midway = background(renderer.render(tree), 1);

        clock.advance(60);
        var arrived = background(renderer.render(tree), 1);
        assertEquals(0xFF4C566A, arrived, "nord3, --gb-button-bg-hover");

        assertNotEquals(resting, midway, "and halfway is neither end");
        assertNotEquals(arrived, midway);
    }

    private static int background(io.github.digitalsmile.goldberry.layout.Box root, int child) {
        return root.children().get(child).background();
    }

    /// The largest per-channel difference between two colours.
    private static int distance(int a, int b) {
        var most = 0;
        for (var shift : new int[] {16, 8, 0}) {
            most = Math.max(most, Math.abs(((a >>> shift) & 0xFF) - ((b >>> shift) & 0xFF)));
        }
        return most;
    }
}
