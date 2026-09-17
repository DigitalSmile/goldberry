package io.github.digitalsmile.goldberry.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.motion.Clock;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.text.font.Font;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The two animation mechanisms through the renderer, on a virtual clock:
/// `@starting-style` ([ADR-0352]) and `@keyframes` ([ADR-0353]).
///
/// Opacity throughout, with a `linear` curve, so the value a frame paints is a
/// number to compare rather than a bezier to solve.
class EnteringAndKeyframesTest {

    /// A box with nothing in it but its style.
    private record Plate(Attributes attributes) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "plate";
        }

        @Override
        public Set<String> classes() {
            return attributes.classes();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            return Box.of().style(style);
        }
    }

    private Font font;
    private Clock.Virtual clock;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        font = Font.bundled(BundledFont.UI, 13);
        clock = Clock.virtual();
    }

    @AfterEach
    void tearDown() {
        if (font != null) {
            font.close();
        }
    }

    private WidgetRenderer renderer(String css) {
        return new WidgetRenderer(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, css)), font).clock(clock);
    }

    private static Plate plate(String... classes) {
        return new Plate(Attributes.NONE.classes(classes));
    }

    @Nested
    @DisplayName("@starting-style")
    class Entering {

        private static final String CSS = """
                plate { opacity: 1; transition: opacity 100ms linear }
                @starting-style { plate.float { opacity: 0 } }
                """;

        @Test
        @DisplayName("an element enters from its starting style and transitions to its own")
        void entersFromTheStartingStyle() {
            var renderer = renderer(CSS);
            var tree = new ElementTree(plate("float"));

            assertEquals(0.0, renderer.render(tree).opacity(), 1e-9, "the first frame is the starting style");
            assertTrue(renderer.isAnimating());
            clock.advance(50);
            assertEquals(0.5, renderer.render(tree).opacity(), 1e-9);
            clock.advance(50);
            assertEquals(1.0, renderer.render(tree).opacity(), 1e-9);
            assertFalse(renderer.isAnimating());
        }

        @Test
        @DisplayName("an element that stays mounted does not enter again")
        void onlyOnce() {
            var renderer = renderer(CSS);
            var tree = new ElementTree(plate("float"));
            renderer.render(tree);
            clock.advance(200);
            renderer.render(tree);

            clock.advance(10);
            assertEquals(1.0, renderer.render(tree).opacity(), 1e-9);
            assertFalse(renderer.isAnimating());
        }

        @Test
        @DisplayName("an element no starting rule matches appears at rest, as before")
        void unmatchedAppearsAtRest() {
            var renderer = renderer(CSS);

            assertEquals(1.0, renderer.render(new ElementTree(plate())).opacity(), 1e-9);
            assertFalse(renderer.isAnimating());
        }

        @Test
        @DisplayName("with no transition there is nothing to run, which is CSS's rule")
        void needsATransition() {
            var renderer = renderer("plate { opacity: 1 } @starting-style { plate { opacity: 0 } }");

            assertEquals(1.0, renderer.render(new ElementTree(plate())).opacity(), 1e-9);
        }

        @Test
        @DisplayName("reduced motion arrives at once")
        void reducedMotion() {
            var renderer = renderer(CSS).reducedMotion(true);

            assertEquals(1.0, renderer.render(new ElementTree(plate("float"))).opacity(), 1e-9);
            assertFalse(renderer.isAnimating());
        }
    }

    @Nested
    @DisplayName("@keyframes")
    class Keyframed {

        @Test
        @DisplayName("a named block runs from the frame the name appears, and lets the loop go idle when it ends")
        void runsAndEnds() {
            var renderer = renderer("""
                    plate { opacity: 1; animation: fade 100ms linear }
                    @keyframes fade { from { opacity: 0 } }
                    """);
            var tree = new ElementTree(plate());

            assertEquals(0.0, renderer.render(tree).opacity(), 1e-9);
            assertTrue(renderer.isAnimating());
            clock.advance(40);
            assertEquals(0.4, renderer.render(tree).opacity(), 1e-9, "towards the element's own opacity");
            clock.advance(60);
            assertEquals(1.0, renderer.render(tree).opacity(), 1e-9);
            assertFalse(renderer.isAnimating(), "a finished animation asks for no frames");
        }

        @Test
        @DisplayName("the timing function eases between each pair of keyframes")
        void perSegment() {
            var renderer = renderer("""
                    plate { animation: blink 200ms linear infinite }
                    @keyframes blink { from { opacity: 1 } 50% { opacity: 0 } to { opacity: 1 } }
                    """);
            var tree = new ElementTree(plate());
            renderer.render(tree);

            clock.advance(50);
            assertEquals(0.5, renderer.render(tree).opacity(), 1e-9);
            clock.advance(100);
            assertEquals(0.5, renderer.render(tree).opacity(), 1e-9);
            clock.advance(1000);
            renderer.render(tree);
            assertTrue(renderer.isAnimating(), "a loop keeps the loop awake");
        }

        @Test
        @DisplayName("a fill holds the last frame after the end without asking for frames")
        void fillForwards() {
            var renderer = renderer("""
                    plate { opacity: 1; animation: out 100ms linear forwards }
                    @keyframes out { to { opacity: 0.25 } }
                    """);
            var tree = new ElementTree(plate());
            renderer.render(tree);

            clock.advance(500);
            assertEquals(0.25, renderer.render(tree).opacity(), 1e-9);
            assertFalse(renderer.isAnimating());
        }

        @Test
        @DisplayName("a stagger is a delay, and during it a backwards fill holds the first frame")
        void stagger() {
            var renderer = renderer("""
                    plate { opacity: 1; animation: in 100ms linear both }
                    plate.second { animation-delay: 60ms }
                    @keyframes in { from { opacity: 0 } }
                    """);
            var tree = new ElementTree(plate("second"));

            assertEquals(0.0, renderer.render(tree).opacity(), 1e-9);
            clock.advance(60);
            assertEquals(0.0, renderer.render(tree).opacity(), 1e-9);
            clock.advance(50);
            assertEquals(0.5, renderer.render(tree).opacity(), 1e-9);
        }

        @Test
        @DisplayName("reduced motion runs no keyframes and asks for no frames")
        void reducedMotion() {
            var renderer = renderer("""
                    plate { opacity: 1; animation: blink 200ms infinite }
                    @keyframes blink { 50% { opacity: 0 } }
                    """).reducedMotion(true);
            var tree = new ElementTree(plate());
            clock.advance(100);

            assertEquals(1.0, renderer.render(tree).opacity(), 1e-9);
            assertFalse(renderer.isAnimating());
        }

        @Test
        @DisplayName("a name no sheet declares draws the element as it is")
        void unknownName() {
            var renderer = renderer("plate { opacity: 0.75; animation: nowhere 100ms }");

            assertEquals(0.75, renderer.render(new ElementTree(plate())).opacity(), 1e-9);
        }

        @Test
        @DisplayName("a keyframed width is refused, and the rest of the keyframe still runs")
        void whitelist() {
            var renderer = renderer("""
                    plate { opacity: 1; animation: grow 100ms linear }
                    @keyframes grow { from { width: 10px; opacity: 0 } }
                    """);
            var tree = new ElementTree(plate());
            clock.advance(50);

            var box = renderer.render(tree);
            assertEquals(0.0, box.opacity(), 1e-9, "the first frame at the frame the name appeared");
        }
    }
}
