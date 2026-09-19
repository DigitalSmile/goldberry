package io.github.digitalsmile.goldberry.frame;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.input.handler.Located;
import io.github.digitalsmile.goldberry.input.handler.Measured;
import io.github.digitalsmile.goldberry.input.hit.Extent;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.paint.Frame;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.render.PixelBuffer;
import io.github.digitalsmile.goldberry.render.model.DisplayScale;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.render.model.PhysicalSize;
import io.github.digitalsmile.goldberry.render.model.PixelFormat;
import io.github.digitalsmile.goldberry.text.font.Fonts;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The steps a frame runs, and the order they run in — ADR-0423.
///
/// `Launcher` and `Offscreen` ran this list from two copies of it, and the tests
/// that existed were about what each *caller* produced: a window's goldens and an
/// offscreen render's pixels. Neither asserted on the order itself, which is how
/// the golden harness went for months missing [ElementTree#flush] and committing
/// nine pictures no window ever drew (ADR-0284).
///
/// So these are about the order, directly. Each one names the step that would go
/// missing and the symptom of it going missing.
@DisplayName("the frame sequence")
class FrameSequenceTest {

    private Fonts fonts;

    private RenderTree render;

    private PixelBuffer buffer;

    private Frame frame;

    private PointerRouter router;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        fonts = Fonts.bundled(List.of());
        render = RenderTree.create();
        buffer = PixelBuffer.allocate(new PhysicalSize(40, 20), PixelFormat.BGRA32_PREMULTIPLIED);
        frame = Frame.over(buffer, DisplayScale.ONE);
        router = new PointerRouter();
    }

    @AfterEach
    void tearDown() {
        // The order ADR-0284 records: the frame joins Blend2D's workers, and they
        // are still holding what the tree lent them.
        frame.end();
        render.close();
        fonts.close();
    }

    private WidgetRenderer renderer(String css) {
        return new WidgetRenderer(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, css)), fonts);
    }

    private record Panel(Attributes attributes) implements Widget.Leaf, Styled, Paints {

        @Override
        public Set<String> classes() {
            return attributes.classes();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            return Box.of().style(style);
        }
    }

    private static Attributes classed(String name) {
        return new Attributes(null, Set.of(name), null);
    }

    @Test
    @DisplayName("flushes what a first build dirtied, which is the step the goldens lost")
    void flushesBeforeItStyles() {
        // ADR-0284's exact bug, as a unit test rather than as nine pictures. A
        // `setState` during the first build leaves the tree dirty the moment it is
        // constructed; a sequence that styled without flushing would render the
        // pre-setState description.
        var builds = new AtomicInteger();
        record Settling(AtomicInteger builds) implements Widget.Stateful {

            @Override
            public State<?> createState() {
                return new State<Settling>() {

                    private boolean corrected;

                    @Override
                    public Widget build(BuildContext context) {
                        widget().builds().incrementAndGet();
                        if (!corrected) {
                            // Legal, and ElementTree deliberately keeps the mark
                            // rather than clearing it after the first build.
                            setState(() -> corrected = true);
                        }
                        return new Panel(classed(corrected ? "after" : "before"));
                    }
                };
            }
        }

        var tree = new ElementTree(new Settling(builds));
        assertTrue(tree.needsBuild(), "a setState during the first build leaves the tree dirty");

        var sequence = FrameSequence.over(tree, render, router);
        sequence.layOut(frame, renderer(".before { width: 4px } .after { width: 8px }"));

        assertEquals(2, builds.get(), "the flush ran the correction before anything was styled");
        assertFalse(tree.needsBuild(), "and nothing was left waiting");
    }

    @Test
    @DisplayName("hands the tree the cascade before it builds, not after")
    void preparesBeforeItBuilds() {
        // ADR-0254. A build that asks about a custom property must find a resolver
        // already there -- a virtualized `list` deciding how many rows to make is
        // the real case, and a resolver handed over after the flush makes it build
        // at its default and correct itself one frame later.
        //
        // What `prepare` cannot fix is the build inside the `ElementTree`
        // constructor, which happens before any renderer has seen the tree at all;
        // `BuildContext.token` documents that and this pins both halves.
        var seen = new ArrayList<Double>();
        record Asking(List<Double> seen) implements Widget.Stateful {

            @Override
            public State<?> createState() {
                return new State<Asking>() {

                    private boolean again = true;

                    @Override
                    public Widget build(BuildContext context) {
                        widget().seen().add(context.token("--row-height", -1));
                        if (again) {
                            setState(() -> again = false);
                        }
                        return new Panel(classed("row"));
                    }
                };
            }
        }

        var tree = new ElementTree(new Asking(seen));
        assertEquals(-1.0, seen.getFirst(), 0.001, "the constructor's build has no cascade to ask, and says so");

        var sequence = FrameSequence.over(tree, render, router);
        sequence.layOut(frame, renderer(":root { --row-height: 24px } .row { width: 4px }"));

        assertTrue(seen.size() > 1, "the flush rebuilt it");
        assertEquals(24.0, seen.getLast(), 0.001, "and prepare ran before that build, so the cascade was there");
    }

    @Test
    @DisplayName("captures the regions and tells a self-measuring widget")
    void capturesTheRegions() {
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

        var tree = new ElementTree(new Ruler(classed("ruler"), told));
        var sequence = FrameSequence.over(tree, render, router);
        sequence.layOut(frame, renderer(".ruler { background: #00ff00; width: 24px; height: 12px }"));
        var regions = sequence.captureRegions(frame);

        assertFalse(regions.isEmpty(), "something was laid out to capture");
        assertNotNull(told.get(), "and the widget was told what it came out as");
        assertEquals(24f, told.get().width(), 0.01f);
    }

    @Test
    @DisplayName("sets the window bounds with the regions, so nothing clips to a zero rectangle")
    void setsTheWindowBoundsWithTheRegions() {
        // ADR-0119: "nothing clips me" has to resolve to a real rectangle, and it
        // is the frame's. The router starts at a 0x0 rectangle, so a sequence that
        // captured without setting them would pin every `affix` against nothing.
        var clip = new AtomicReference<LogicalRect>();
        record Pinned(Attributes attributes, AtomicReference<LogicalRect> clip)
                implements Widget.Leaf, Styled, Paints, Located {

            @Override
            public Set<String> classes() {
                return attributes.classes();
            }

            @Override
            public Box render(ComputedStyle style, List<Box> boxes, Context context) {
                return Box.of().style(style);
            }

            @Override
            public void located(LogicalRect self, LogicalRect clipped) {
                clip.set(clipped);
            }
        }

        var tree = new ElementTree(new Pinned(classed("root"), clip));
        var sequence = FrameSequence.over(tree, render, router);
        sequence.layOut(frame, renderer(".root { background: #ff0000; width: 20px; height: 10px }"));
        sequence.captureRegions(frame);

        assertNotNull(clip.get(), "the widget was told what clips it");
        assertEquals(40f, clip.get().width(), 0.01f, "the frame's own width, not the origin rectangle");
        assertEquals(20f, clip.get().height(), 0.01f);
    }

    @Test
    @DisplayName("refuses to capture regions from a tree it has never laid out")
    void refusesToCaptureBeforeLayingOut() {
        var tree = new ElementTree(new Panel(classed("root")));
        var sequence = FrameSequence.over(tree, render, router);

        // Not a pedantic guard. A capture of a tree with no layout in it is a list
        // of rectangles at the origin, and nothing further down treats that as an
        // error -- a menu would simply open in the corner.
        var refused = assertThrows(IllegalStateException.class, () -> sequence.captureRegions(frame));
        assertTrue(refused.getMessage().contains("layOut"), refused.getMessage());
    }

    @Test
    @DisplayName("the stages come back in order, from the start the caller named")
    void reportsTheStagesInOrder() {
        var tree = new ElementTree(new Panel(classed("root")));
        var sequence = FrameSequence.over(tree, render, router);

        var beganAt = System.nanoTime();
        var stages = sequence.layOut(frame, renderer(".root { width: 8px; height: 8px }"), beganAt);

        assertEquals(beganAt, stages.beganAt(), "the frame started when the caller said it did");
        assertTrue(stages.beganAt() <= stages.builtAt(), "built after began");
        assertTrue(stages.builtAt() <= stages.styledAt(), "styled after built");
        assertTrue(stages.styledAt() <= stages.laidOutAt(), "laid out after styled");
        assertEquals(stages.builtAt() - stages.beganAt(), stages.buildNanos());
        assertEquals(stages.styledAt() - stages.builtAt(), stages.styleNanos());
        assertEquals(stages.laidOutAt() - stages.styledAt(), stages.layoutNanos());
    }

    @Test
    @DisplayName("a second frame over the same tree re-lays out rather than rebuilding")
    void keepsTheRetainedTree() {
        // What makes the sequence safe to hold beside a window rather than build
        // per frame (ADR-0069): the render tree is retained, so a frame where
        // nothing changed re-lays out nothing.
        var builds = new AtomicInteger();
        record Counting(AtomicInteger builds) implements Widget.Stateful {

            @Override
            public State<?> createState() {
                return new State<Counting>() {

                    @Override
                    public Widget build(BuildContext context) {
                        widget().builds().incrementAndGet();
                        return new Panel(classed("root"));
                    }
                };
            }
        }

        var tree = new ElementTree(new Counting(builds));
        var sequence = FrameSequence.over(tree, render, router);
        var css = renderer(".root { background: #ff0000; width: 8px; height: 8px }");
        sequence.layOut(frame, css);
        var afterFirst = builds.get();
        sequence.layOut(frame, css);

        assertEquals(afterFirst, builds.get(), "nothing was dirty, so nothing was rebuilt");
    }

    @Test
    @DisplayName("nothing is optional")
    void refusesNulls() {
        var tree = new ElementTree(new Panel(classed("root")));
        assertThrows(NullPointerException.class, () -> FrameSequence.over(null, render, router));
        assertThrows(NullPointerException.class, () -> FrameSequence.over(tree, null, router));
        assertThrows(NullPointerException.class, () -> FrameSequence.over(tree, render, null));

        var sequence = FrameSequence.over(tree, render, router);
        assertThrows(NullPointerException.class, () -> sequence.layOut(null, renderer("")));
        assertThrows(NullPointerException.class, () -> sequence.layOut(frame, null));
    }

    @Test
    @DisplayName("the capture it returns is the one the router was given")
    void handsBackWhatItFedIn() {
        var tree = new ElementTree(new Panel(classed("root")));
        var sequence = FrameSequence.over(tree, render, router);
        sequence.layOut(frame, renderer(".root { background: #ff0000; width: 20px; height: 10px }"));
        var regions = sequence.captureRegions(frame);

        // The launcher keeps this list so `anchor(id)` answers from the frame that
        // was painted rather than from a fresh layout (ADR-0054). If the return
        // value and the router's copy ever differed, a menu would open somewhere
        // nothing was drawn.
        assertTrue(HitTest.at(regions, 5, 5).isPresent(), "the painted rectangle is in the capture");
    }
}
