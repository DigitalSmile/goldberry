package dev.goldberry.widgets.core.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.Theme;
import dev.goldberry.image.Image;
import dev.goldberry.image.anim.MovingPicture;
import dev.goldberry.image.anim.VectorAnimation;
import dev.goldberry.motion.Clock;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.Frame;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widget.semantics.Role;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.controls.TestFont;

/// A vector animation playing on the frame loop, driven by a virtual clock.
class AnimationViewTest {

    /// A blue dot that crosses a 100-unit canvas left to right in one second.
    private static final String CROSSING = """
            {"v":"5.7.4","fr":30,"ip":0,"op":30,"w":100,"h":100,"layers":[
              {"ind":1,"ty":4,"ip":0,"op":30,"st":0,
               "ks":{"p":{"a":1,"k":[
                  {"t":0,"s":[20,50],"o":{"x":[0],"y":[0]},"i":{"x":[1],"y":[1]}},
                  {"t":30,"s":[80,50]}]}},
               "shapes":[
                {"ty":"el","p":{"a":0,"k":[0,0]},"s":{"a":0,"k":[20,20]}},
                {"ty":"fl","c":{"a":0,"k":[0,0,1,1]},"o":{"a":0,"k":100}}]}]}""";

    private static final VectorAnimation DOT = VectorAnimation.of(CROSSING.getBytes(StandardCharsets.UTF_8));

    private static boolean blue(Image image, int x, int y) {
        var argb = image.argb(x, y);
        return (argb & 0xFF) > 200 && ((argb >>> 16) & 0xFF) < 60;
    }

    @Test
    @DisplayName("moves with the frame clock, and keeps asking for frames while it does")
    void advances() {
        try (var strip = Offscreen.of(100, 100)
                .stylesheets(Controls.stylesheets(Theme.NORD_DARK))
                .strip(new AnimationView(DOT, "a dot"))) {
            var first = strip.frame();
            assertTrue(blue(first, 20, 50), "starts on the left");
            assertFalse(blue(first, 50, 50));
            assertTrue(strip.isAnimating(), "asks for the next frame");

            var middle = strip.advance(500).frame();
            assertTrue(blue(middle, 50, 50), "halfway across after half a second");
            assertFalse(blue(middle, 20, 50));
            assertTrue(strip.isAnimating());
        }
    }

    @Test
    @DisplayName("with autoplay off, stands on its first frame and asks for nothing")
    void autoplayOff() {
        try (var strip = Offscreen.of(100, 100)
                .stylesheets(Controls.stylesheets(Theme.NORD_DARK))
                .strip(new AnimationView(DOT, "a dot").autoplay(false))) {
            var first = strip.frame();
            assertTrue(blue(first, 20, 50));
            assertFalse(strip.isAnimating(), "a still picture keeps the loop idle");

            var later = strip.advance(500).frame();
            assertTrue(blue(later, 20, 50), "still where it started");
            assertFalse(blue(later, 50, 50));
        }
    }

    @Test
    @DisplayName("stops asking once a finite animation has played")
    void finishes() {
        try (var strip = Offscreen.of(100, 100)
                .stylesheets(Controls.stylesheets(Theme.NORD_DARK))
                .strip(AnimationView.decorative(DOT.loops(1)))) {
            strip.frame();
            assertTrue(strip.isAnimating());
            var last = strip.advance(1200).frame();
            assertFalse(strip.isAnimating(), "done after its one pass");
            assertTrue(blue(last, 78, 50), "and holds its last frame");
        }
    }

    @Test
    @DisplayName("a user who asked for less movement sees the first frame, and the loop stays idle")
    void reducedMotion() {
        var clock = Clock.virtual();
        var renderer = new WidgetRenderer(Controls.stylesheets(Theme.NORD_DARK), TestFont.get())
                .clock(clock)
                .reducedMotion(true);
        var tree = new ElementTree(new AnimationView(DOT, "a dot"));
        renderer.render(tree);
        clock.advance(500);
        var moment = moment(renderer.render(tree));
        assertEquals(0, moment.elapsed(), "the first frame");
        assertFalse(renderer.isAnimating());

        renderer.reducedMotion(false);
        clock.advance(100);
        assertEquals(0, moment(renderer.render(tree)).elapsed(), "starts from the beginning when it may move");
        clock.advance(250);
        assertEquals(250, moment(renderer.render(tree)).elapsed());
        assertTrue(renderer.isAnimating());
    }

    @Test
    @DisplayName("is sized by its canvas, and is a figure named by its alt text")
    void sizeAndSemantics() {
        var renderer = new WidgetRenderer(Controls.stylesheets(Theme.NORD_DARK), TestFont.get()).clock(Clock.virtual());
        var tree = new ElementTree(new AnimationView(DOT, "a dot"));
        var box = renderer.render(tree);
        var painted = find(box);
        assertTrue(painted != null, "a box paints the animation");
        var figure = tree.root().children().getFirst().widget();
        assertTrue(figure instanceof AnimationFigure part
                && part.role() == Role.FIGURE
                && part.accessibleName().equals("a dot"));
        Widget decorative = AnimationView.decorative(DOT);
        var plain = new ElementTree(decorative);
        renderer.render(plain);
        assertTrue(plain.root().children().getFirst().widget() instanceof AnimationBox, "decoration has no semantics");
    }

    @Test
    @DisplayName("needs alt text unless it is decoration")
    void altRequired() {
        assertThrows(IllegalArgumentException.class, () -> new AnimationView(DOT, " "));
    }

    @Test
    @DisplayName("a new source starts from its first frame")
    void newSource() {
        var clock = Clock.virtual();
        var renderer = new WidgetRenderer(Controls.stylesheets(Theme.NORD_DARK), TestFont.get()).clock(clock);
        var tree = new ElementTree(new AnimationView(DOT, "a dot"));
        renderer.render(tree);
        clock.advance(400);
        assertEquals(400, moment(renderer.render(tree)).elapsed());
        tree.update(new AnimationView(DOT.loops(3), "a dot"));
        assertEquals(0, moment(renderer.render(tree)).elapsed());
    }

    /// A moving picture that is not a Lottie document: red for its first half
    /// second, green after, over a 10-by-10 canvas, and done at one second.
    private record Blink() implements MovingPicture {
        @Override
        public double width() {
            return 10;
        }

        @Override
        public double height() {
            return 10;
        }

        @Override
        public boolean isDoneAt(long elapsedMillis) {
            return elapsedMillis >= 1000;
        }

        @Override
        public void paint(Frame frame, long elapsedMillis, double x, double y, double width, double height) {
            frame.fillRect(
                    (float) x, (float) y, (float) width, (float) height, elapsedMillis < 500 ? 0xFFFF0000 : 0xFF00FF00);
        }
    }

    @Test
    @DisplayName("plays any moving picture, sized by it, and stops when it says it is done")
    void anyMovingPicture() {
        try (var strip = Offscreen.of(10, 10)
                .stylesheets(Controls.stylesheets(Theme.NORD_DARK))
                .strip(AnimationView.decorative(new Blink()))) {
            assertEquals(0xFFFF0000, strip.frame().argb(5, 5));
            assertTrue(strip.isAnimating());
            assertEquals(0xFF00FF00, strip.advance(600).frame().argb(5, 5));
            strip.advance(500).frame();
            assertFalse(strip.isAnimating(), "done");
        }
    }

    private static AnimationPaint.Moment moment(Box box) {
        var found = find(box);
        if (found == null) {
            throw new AssertionError("no animation painted in " + box);
        }
        return found;
    }

    private static AnimationPaint.@Nullable Moment find(Box box) {
        if (box.painting() instanceof AnimationPaint.Moment moment) {
            return moment;
        }
        for (var child : box.children()) {
            var found = find(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
