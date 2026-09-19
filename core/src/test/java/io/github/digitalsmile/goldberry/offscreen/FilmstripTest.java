package io.github.digitalsmile.goldberry.offscreen;

import static io.github.digitalsmile.goldberry.offscreen.Scene.RED;
import static io.github.digitalsmile.goldberry.offscreen.Scene.classed;
import static io.github.digitalsmile.goldberry.offscreen.Scene.panel;
import static io.github.digitalsmile.goldberry.offscreen.Scene.pixels;
import static io.github.digitalsmile.goldberry.offscreen.Scene.sheet;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.image.Image;
import io.github.digitalsmile.goldberry.input.handler.Measured;
import io.github.digitalsmile.goldberry.input.hit.Extent;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.text.font.Fonts;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// An animation strip — ADR-0424.
///
/// The claim being tested is not "it renders", which [OffscreenTest] already
/// covers for the same pipeline. It is that a strip has a **lifetime**: that the
/// tree is mounted once and stays mounted, that advancing the clock moves the
/// picture rather than restarting it, and that two frames never share a buffer.
/// The last of those is the one that would be invisible until a caller held both
/// pictures at once.
@DisplayName("a filmstrip")
class FilmstripTest {

    /// A tree whose colour is a pure function of the clock, with no interaction
    /// and no state change in it.
    ///
    /// `@keyframes` rather than a `transition`, because a transition needs
    /// something to change and a strip is asked to photograph the passage of time
    /// on its own. Linear over 200 ms from red to blue, so the halfway frame is a
    /// colour neither end has.
    private static final String FADING = """
            @keyframes fade { from { background: #ff0000 } to { background: #0000ff } }
            .root { width: 16px; height: 16px; background: #ff0000;
                    animation: fade 200ms linear; }
            """;

    private static final String STILL = ".root { width: 16px; height: 16px; background: #ff0000 }";

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    @Nested
    @DisplayName("the lifetime")
    class Lifetime {

        @Test
        @DisplayName("mounts the tree once, however many frames are taken")
        void mountsOnce() {
            // The whole difference from four `Offscreen.render` calls at four
            // settle times. Those are four *first* frames; this is one animation.
            var created = new AtomicInteger();
            var disposed = new AtomicBoolean();

            record Counted(AtomicInteger created, AtomicBoolean disposed) implements Widget.Stateful {

                @Override
                public State<?> createState() {
                    created.incrementAndGet();
                    return new State<Counted>() {

                        @Override
                        public Widget build(BuildContext context) {
                            return panel("root");
                        }

                        @Override
                        protected void dispose() {
                            widget().disposed().set(true);
                        }
                    };
                }
            }

            try (var strip = Offscreen.of(16, 16).stylesheets(sheet(STILL)).strip(new Counted(created, disposed))) {
                for (var i = 0; i < 5; i++) {
                    strip.frame();
                    strip.advance(16);
                }
                assertEquals(1, created.get(), "one State for five frames");
                assertFalse(disposed.get(), "and it is still alive between them");
                assertEquals(5, strip.frames());
            }

            assertTrue(disposed.get(), "closing the strip unmounts the tree");
        }

        @Test
        @DisplayName("state a widget keeps survives from one frame to the next")
        void keepsState() {
            // The consequence of mounting once, stated as the thing a caller would
            // actually notice: a counter incremented in build counts frames rather
            // than resetting to one every time.
            var builds = new AtomicInteger();
            var poke = new AtomicReference<Runnable>();

            record Ticking(AtomicInteger builds, AtomicReference<Runnable> poke) implements Widget.Stateful {

                @Override
                public State<?> createState() {
                    return new State<Ticking>() {

                        /// Only this object can see it. A fresh `State` would start
                        /// it at zero again, which is exactly what the assertion
                        /// below would catch.
                        private int seen;

                        @Override
                        protected void initState() {
                            widget().poke().set(() -> setState(() -> {}));
                        }

                        @Override
                        public Widget build(BuildContext context) {
                            seen++;
                            widget().builds().set(seen);
                            return panel("root");
                        }
                    };
                }
            }

            try (var strip = Offscreen.of(16, 16).stylesheets(sheet(STILL)).strip(new Ticking(builds, poke))) {

                strip.frame();
                var afterFirst = builds.get();
                poke.get().run();
                strip.frame();
                assertEquals(afterFirst + 1, builds.get(), "the same State object went on counting");

                poke.get().run();
                strip.frame();
                assertEquals(afterFirst + 2, builds.get());
            }
        }
    }

    @Nested
    @DisplayName("driving the clock")
    class Driving {

        @Test
        @DisplayName("each frame is further through the animation than the last")
        void advancingMovesThePicture() {
            try (var strip = Offscreen.of(16, 16).stylesheets(sheet(FADING)).strip(panel("root"))) {
                var start = strip.frame().argb(8, 8);
                var quarter = strip.advance(50).frame().argb(8, 8);
                var half = strip.advance(50).frame().argb(8, 8);
                var most = strip.advance(90).frame().argb(8, 8);

                assertEquals(RED, start, "frame zero is the start of the animation, not the end of it");
                assertNotEquals(start, half, "and the clock moved the picture");

                // Monotonic rather than exact: what a strip has to get right is
                // that frame n+1 is *further through* the animation than frame n,
                // and the interpolation itself is ADR-0067's to be tested.
                assertTrue(
                        blue(start) < blue(quarter) && blue(quarter) < blue(half) && blue(half) < blue(most),
                        "blue rises: " + blue(start) + " " + blue(quarter) + " " + blue(half) + " " + blue(most));
                assertTrue(
                        red(start) > red(quarter) && red(quarter) > red(half) && red(half) > red(most),
                        "and red falls");
            }
        }

        @Test
        @DisplayName("past the end, the base style is back — which is what a window would draw too")
        void photographsTheEndAsAWindowWould() {
            // `animation-fill-mode` defaults to `none`, so an animation that has
            // finished holds nothing and the element is its plain self again. Worth
            // pinning: a strip that quietly held the last keyframe would disagree
            // with the window it exists to stand in for.
            try (var strip = Offscreen.of(16, 16).stylesheets(sheet(FADING)).strip(panel("root"))) {
                assertEquals(RED, strip.advance(400).frame().argb(8, 8));
            }
        }

        private static int blue(int argb) {
            return argb & 0xFF;
        }

        private static int red(int argb) {
            return (argb >> 16) & 0xFF;
        }

        @Test
        @DisplayName("two advances with no frame between them are one advance")
        void advancesAreCumulative() {
            try (var jumped = Offscreen.of(16, 16).stylesheets(sheet(FADING)).strip(panel("root"));
                    var stepped =
                            Offscreen.of(16, 16).stylesheets(sheet(FADING)).strip(panel("root"))) {

                var inOne = jumped.advance(120).frame();
                var inThree = stepped.advance(40).advance(40).advance(40).frame();

                assertEquals(120.0, jumped.nowMillis(), 0.001);
                assertEquals(120.0, stepped.nowMillis(), 0.001);
                assertArrayEquals(pixels(inOne), pixels(inThree), "the clock is the only input");
            }
        }

        @Test
        @DisplayName("the same strip taken twice is the same strip")
        void isDeterministic() {
            // A strip that depended on when it was run would be useless for the
            // thing a strip is for -- a committed reference of an animation.
            assertArrayEquals(fadeAt(64), fadeAt(64), "no wall clock anywhere in it");
        }

        private int[] fadeAt(int millis) {
            try (var strip = Offscreen.of(16, 16).stylesheets(sheet(FADING)).strip(panel("root"))) {
                return pixels(strip.advance(millis).frame());
            }
        }

        @Test
        @DisplayName("says when there is no point taking another frame")
        void reportsWhetherAnythingIsMoving() {
            try (var strip = Offscreen.of(16, 16).stylesheets(sheet(FADING)).strip(panel("root"))) {
                strip.frame();
                assertTrue(strip.isAnimating(), "a 200ms animation at 0ms is moving");
                strip.advance(400).frame();
                assertFalse(strip.isAnimating(), "and is not, once it is over");
            }

            try (var still = Offscreen.of(16, 16).stylesheets(sheet(STILL)).strip(panel("root"))) {
                still.frame();
                assertFalse(still.isAnimating(), "a tree with no animation in it never was");
            }
        }
    }

    @Nested
    @DisplayName("the buffers")
    class Buffers {

        @Test
        @DisplayName("a frame already handed out is not repainted by the next one")
        void everyFrameGetsItsOwnBuffer() {
            // The defect a reused buffer would cause, and the reason it would have
            // gone unnoticed: an `Image` is a view over the pixels it was rendered
            // into and never a copy (ADR-0283), so a strip that kept one buffer
            // would hand out ten references to the tenth picture.
            try (var strip = Offscreen.of(16, 16).stylesheets(sheet(FADING)).strip(panel("root"))) {
                var first = strip.frame();
                var atStart = pixels(first);

                strip.advance(200).frame();

                assertArrayEquals(atStart, pixels(first), "the first picture is still the first picture");
                assertEquals(RED, first.argb(8, 8));
            }
        }

        @Test
        @DisplayName("pictures outlive the strip that took them")
        void framesSurviveClosing() {
            var frames = new ArrayList<int[]>();
            Image last;
            try (var strip = Offscreen.of(16, 16).stylesheets(sheet(FADING)).strip(panel("root"))) {
                frames.add(pixels(strip.frame()));
                last = strip.advance(100).frame();
            }
            // Closing unmounts the tree and closes the font book; it does not touch
            // a buffer already handed over.
            assertNotEquals(RED, last.argb(8, 8), "the mid-animation frame is readable after the close");
            assertEquals(RED, frames.getFirst()[8 * 16 + 8], "and so is the first one");
        }

        @Test
        @DisplayName("a background fills every frame, not only the first")
        void fillsTheBackgroundEachFrame() {
            try (var strip = Offscreen.of(16, 16)
                    .background(Scene.GREEN)
                    .stylesheets(sheet(".root { width: 4px; height: 4px; background: #ff0000 }"))
                    .strip(panel("root"))) {
                strip.frame();
                var second = strip.advance(16).frame();
                assertEquals(Scene.GREEN, second.argb(15, 15), "past the 4x4 root");
            }
        }
    }

    @Nested
    @DisplayName("the mount pass")
    class Mounting {

        @Test
        @DisplayName("frame zero is drawn against regions that have been fed back")
        void feedsTheRegionsBeforeTheFirstFrame() {
            // The half of ADR-0284's three-pass argument a strip keeps. A widget
            // that learns its own width from the frame it was laid out into must
            // not spend frame zero showing a first guess -- so the strip lays the
            // tree out once at mount, with the clock still at zero.
            var told = new AtomicReference<Extent>();

            record Ruler(Attributes attributes, AtomicReference<Extent> told)
                    implements Widget.Leaf, Styled, Paints, Measured {

                @Override
                public Set<String> classes() {
                    return attributes.classes();
                }

                @Override
                public Box render(ComputedStyle style, List<Box> boxes, Context context) {
                    return Box.of().style(style);
                }

                @Override
                public void measured(Extent bounds, Extent part) {
                    told.set(bounds);
                }
            }

            try (var strip = Offscreen.of(40, 20)
                    .stylesheets(sheet(".ruler { background: #00ff00; width: 24px; height: 12px }"))
                    .strip(new Ruler(classed("ruler"), told))) {

                assertEquals(24f, told.get().width(), 0.01f, "told before a single frame was taken");
                assertEquals(0.0, strip.nowMillis(), 0.001, "and the clock never moved to do it");
            }
        }

        @Test
        @DisplayName("the settle time is not a strip's business")
        void ignoresTheSettleTime() {
            // `settle` is how far a still picture jumps to get past the entrance
            // animations. A strip is a request to photograph them.
            try (var strip =
                    Offscreen.of(16, 16).settle(500).stylesheets(sheet(FADING)).strip(panel("root"))) {
                assertEquals(0.0, strip.nowMillis(), 0.001);
                assertEquals(RED, strip.frame().argb(8, 8), "still at the start of the animation");
            }
        }
    }

    @Nested
    @DisplayName("what it refuses")
    class Refusals {

        @Test
        @DisplayName("a clock that runs backwards")
        void refusesANegativeAdvance() {
            try (var strip = Offscreen.of(8, 8).stylesheets(sheet(STILL)).strip(panel("root"))) {
                assertThrows(IllegalArgumentException.class, () -> strip.advance(-1));
                assertEquals(0.0, strip.nowMillis(), 0.001, "and the clock did not move");
            }
        }

        @Test
        @DisplayName("another frame after it has been closed")
        void refusesAfterClosing() {
            var strip = Offscreen.of(8, 8).stylesheets(sheet(STILL)).strip(panel("root"));
            strip.frame();
            strip.close();

            var refused = assertThrows(IllegalStateException.class, strip::frame);
            assertTrue(refused.getMessage().contains("closed"), refused.getMessage());
            assertThrows(IllegalStateException.class, () -> strip.advance(1));
            strip.close();
        }

        @Test
        @DisplayName("a single font, which would ignore the cascade a strip photographs")
        void refusesASingleFont() {
            try (var fonts = Fonts.bundled(List.of())) {
                var one = fonts.of(BundledFont.UI, 12);
                var offscreen = Offscreen.of(8, 8).font(one).stylesheets(sheet(STILL));

                var refused = assertThrows(IllegalStateException.class, () -> offscreen.strip(panel("root")));
                assertTrue(refused.getMessage().contains("font book"), refused.getMessage());
            }
        }

        @Test
        @DisplayName("nothing to mount")
        void refusesNulls() {
            var offscreen = Offscreen.of(8, 8);
            assertThrows(NullPointerException.class, () -> offscreen.strip(null));
        }
    }
}
