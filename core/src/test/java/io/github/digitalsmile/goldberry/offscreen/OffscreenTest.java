package io.github.digitalsmile.goldberry.offscreen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.image.Image;
import io.github.digitalsmile.goldberry.input.handler.Measured;
import io.github.digitalsmile.goldberry.input.hit.Extent;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.render.model.PhysicalSize;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// Rendering with no window — ADR-0284.
///
/// The pieces this composes were all public and all tested; what was not tested
/// is the **sequence**, because the sequence only existed inside `Launcher` and
/// inside a golden-image harness that copied it. So these assertions are about
/// the order of the dance rather than about the rasterizer: that a tree is built
/// before it is styled, that it is laid out before it is painted, that the
/// regions are fed back so a self-measuring widget hears them, and that nothing
/// is left mounted afterwards.
class OffscreenTest {

    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;
    private static final int GREEN = 0xFF00FF00;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    /// A styled box, defined here rather than taken from a catalog `:core` does
    /// not have — the same fixture `StyleCacheTest` builds, for the same reason.
    private record Panel(List<Widget> children, Attributes attributes) implements Widget.Leaf, Styled, Paints {

        @Override
        public String id() {
            return attributes.id();
        }

        @Override
        public Set<String> classes() {
            return attributes.classes();
        }

        @Override
        public Object key() {
            return attributes.key();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            return Box.of().children(boxes.toArray(Box[]::new)).style(style);
        }
    }

    private static Attributes classed(String... names) {
        return new Attributes(null, Set.of(names), null);
    }

    private static List<Stylesheet> sheet(String css) {
        return List.of(Stylesheet.parse(CascadeLayer.APPLICATION, css));
    }

    @Nested
    @DisplayName("a painter over the whole buffer")
    class PainterPath {

        @Test
        @DisplayName("what the painter drew is what comes back")
        void paintsAPainter() {
            var image = Offscreen.of(20, 10).paint((frame, size) -> {
                assertEquals(20, size.width(), "the painter is told the logical size of the buffer");
                assertEquals(10, size.height());
                frame.fillRect(0, 0, 10, 10, RED);
            });

            assertEquals(new PhysicalSize(20, 10), image.size());
            assertEquals(RED, image.argb(0, 0));
            assertEquals(RED, image.argb(9, 9));
            assertEquals(0, image.argb(10, 0), "and nothing where nothing was drawn");
        }

        @Test
        @DisplayName("the buffer starts transparent, and a background fills it")
        void fillsTheBackground() {
            var bare = Offscreen.of(4, 4).paint((frame, size) -> {});
            assertEquals(0, bare.argb(0, 0), "transparent by default");

            var filled = Offscreen.of(4, 4).background(BLUE).paint((frame, size) -> {});
            assertEquals(BLUE, filled.argb(0, 0));
            assertEquals(BLUE, filled.argb(3, 3));
        }

        @Test
        @DisplayName("a scale buys detail, not size: the raster is what was asked for")
        void honoursTheScale() {
            // 20x10 physical pixels at 2x holds 10x5 LOGICAL ones. A square of
            // five logical units therefore covers ten device pixels, and the file
            // is still twenty pixels wide.
            var image = Offscreen.of(20, 10).scale(2f).paint((frame, size) -> {
                assertEquals(10, size.width(), "half as many logical pixels at twice the scale");
                assertEquals(5, size.height());
                frame.fillRect(0, 0, 5, 5, GREEN);
            });

            assertEquals(new PhysicalSize(20, 10), image.size());
            assertEquals(GREEN, image.argb(9, 9), "five logical units is ten device pixels");
            assertEquals(0, image.argb(10, 0));
        }

        @Test
        @DisplayName("a painter that leaves the frame transformed does not corrupt the next thing")
        void bracketsThePainter() {
            // `Painter`'s contract says a painter may leave anything set, because
            // the toolkit brackets the call. Offscreen paints nothing after it, so
            // what this really pins is that the bracket exists at all -- the frame
            // ends cleanly rather than throwing on a stack that was left pushed.
            var image = Offscreen.of(8, 8).paint((frame, size) -> {
                frame.transform(1, 0, 0, 1, 4, 4);
                frame.fillRect(0, 0, 4, 4, RED);
            });

            assertEquals(RED, image.argb(5, 5), "the translation applied");
            assertEquals(0, image.argb(1, 1));
        }
    }

    @Nested
    @DisplayName("a widget tree, the way a window renders one")
    class WidgetPath {

        @Test
        @DisplayName("the tree is built, styled, laid out and painted")
        void rendersATree() {
            var image = Offscreen.of(40, 20)
                    .stylesheets(sheet("""
                            .root { background: #ff0000; width: 40px; height: 20px; padding: 4px; }
                            .child { background: #0000ff; width: 8px; height: 8px; }
                            """))
                    .render(new Panel(List.of(new Panel(List.of(), classed("child"))), classed("root")));

            // Four things at once, and each of them is a stage: the cascade found
            // the rules (red and blue), layout applied the padding (the child
            // starts at 4,4), the paint order put the child over its parent, and
            // the whole thing landed in a buffer at the size asked for.
            assertEquals(RED, image.argb(0, 0), "the root's background");
            assertEquals(BLUE, image.argb(4, 4), "the child, inside the padding");
            assertEquals(BLUE, image.argb(11, 11));
            assertEquals(RED, image.argb(12, 12), "and the root again past it");
        }

        @Test
        @DisplayName("an unstyled tree renders rather than being refused")
        void rendersWithoutStylesheets() {
            var image = Offscreen.of(8, 8).render(new Panel(List.of(), classed("root")));

            // Nothing is drawn, and that is a legitimate picture: a Box-level
            // scene with no cascade behind it is what a stylesheet-free render is.
            assertEquals(new PhysicalSize(8, 8), image.size());
            assertEquals(0, image.argb(0, 0));
        }

        @Test
        @DisplayName("a self-measuring widget is told what it came out as")
        void feedsTheRegionsBack() {
            // The subtle half of the sequence. `Measured` is delivered by the
            // router from the rectangles a laid-out frame produced, so a render
            // that painted and stopped would leave every `text-area` and `masonry`
            // on its first-frame guess for ever. This is that feedback, asserted
            // without either widget.
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

            Offscreen.of(40, 20)
                    .stylesheets(sheet(".ruler { background: #00ff00; width: 24px; height: 12px; }"))
                    .render(new Ruler(classed("ruler"), told));

            assertNotNull(told.get(), "the widget was told its own size");
            assertEquals(24f, told.get().width(), 0.01f);
            assertEquals(12f, told.get().height(), 0.01f);
        }

        @Test
        @DisplayName("the tree is mounted and unmounted inside the call")
        void leavesNothingMounted() {
            var disposed = new AtomicBoolean();
            var built = new AtomicInteger();

            record Counting(AtomicBoolean disposed, AtomicInteger built) implements Widget.Stateful {

                @Override
                public State<?> createState() {
                    return new State<Counting>() {

                        @Override
                        public Widget build(BuildContext context) {
                            widget().built().incrementAndGet();
                            return new Panel(List.of(), classed("leaf"));
                        }

                        @Override
                        protected void dispose() {
                            widget().disposed().set(true);
                        }
                    };
                }
            }

            Offscreen.of(8, 8).render(new Counting(disposed, built));

            assertTrue(built.get() >= 1, "the state built at least once");
            assertTrue(disposed.get(), "and was disposed when the render finished");
        }

        @Test
        @DisplayName("a render is a PNG one call later")
        void encodesWhatItRendered() {
            var png = Offscreen.of(16, 16)
                    .stylesheets(sheet(".root { background: #0000ff; width: 16px; height: 16px; }"))
                    .render(new Panel(List.of(), classed("root")))
                    .encodePng();

            // The whole of what G5 was asked for, in one line -- and read back by
            // the decoder, which shares no code with the encoder.
            var decoded = Image.decode(png);
            assertEquals(new PhysicalSize(16, 16), decoded.size());
            assertEquals(BLUE, decoded.argb(8, 8));
        }

        @Test
        @DisplayName("the same scene renders the same pixels twice")
        void isDeterministic() {
            // The claim a server-side preview rests on. The clock is virtual, so
            // "when" is not an input -- two requests for one document are one
            // picture.
            var css = sheet(".root { background: #ff0000; width: 12px; height: 12px; }");
            var first = Offscreen.of(12, 12)
                    .stylesheets(css)
                    .render(new Panel(List.of(), classed("root")))
                    .encodePng();
            var second = Offscreen.of(12, 12)
                    .stylesheets(css)
                    .render(new Panel(List.of(), classed("root")))
                    .encodePng();

            assertEquals(-1, java.util.Arrays.mismatch(first, second), "byte for byte, the same PNG");
        }

        @Test
        @DisplayName("a settle of zero is the instant the tree mounted")
        void settlesForAsLongAsItIsTold() {
            // Nothing in `:core` animates on its own, so this pins the argument
            // rather than a transition: zero is accepted, negative is not, and the
            // picture at zero is still a picture.
            var image = Offscreen.of(8, 8)
                    .settle(0)
                    .stylesheets(sheet(".root { background: #00ff00; width: 8px; height: 8px; }"))
                    .render(new Panel(List.of(), classed("root")));

            assertEquals(GREEN, image.argb(4, 4));
        }
    }

    @Nested
    @DisplayName("what it refuses")
    class Refusals {

        @Test
        @DisplayName("a size with no pixels in it")
        void refusesAnEmptySize() {
            assertThrows(IllegalArgumentException.class, () -> Offscreen.of(0, 10));
            assertThrows(IllegalArgumentException.class, () -> Offscreen.of(10, 0));
            assertThrows(IllegalArgumentException.class, () -> Offscreen.of(new PhysicalSize(0, 0)));
        }

        @Test
        @DisplayName("a clock that runs backwards")
        void refusesANegativeSettle() {
            assertThrows(
                    IllegalArgumentException.class, () -> Offscreen.of(4, 4).settle(-1));
        }

        @Test
        @DisplayName("nothing to render")
        void refusesNulls() {
            var offscreen = Offscreen.of(4, 4);
            assertThrows(NullPointerException.class, () -> offscreen.paint(null));
            assertThrows(NullPointerException.class, () -> offscreen.render(null));
            assertThrows(NullPointerException.class, () -> offscreen.stylesheets(null));
            assertThrows(NullPointerException.class, () -> offscreen.scale(null));
        }
    }
}
