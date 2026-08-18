package io.github.digitalsmile.goldberry.widgets.core;

import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;

import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import io.github.digitalsmile.goldberry.widgets.core.Spacer;
import io.github.digitalsmile.goldberry.widgets.panel.Panel;
import io.github.digitalsmile.goldberry.widgets.text.Text;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.TestFrames;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.layout.BoxPainter;
import io.github.digitalsmile.goldberry.layout.RenderTree;
import io.github.digitalsmile.goldberry.text.Fonts;
import java.util.List;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/// What a frame of a real widget tree costs, split by stage.
///
/// [io.github.digitalsmile.goldberry.text.TextBenchmark] measures the text path
/// in isolation and ADR-0037 wrote its numbers down. This one measures the thing
/// an application actually pays: element tree → cascade → boxes → Yoga → Blend2D,
/// per frame, on a tree the size of the showcase's.
///
/// It exists because ADR-0053 said the case for retained render objects "should
/// be made with a measurement", and because ADR-0045 exists precisely to stop
/// this repository optimising against a number it has not taken.
///
/// **Tagged `benchmark`, so `check` never runs it.** Nothing here asserts a
/// timing. Run with `./gradlew :core:benchmark --tests '*FrameBenchmark*'`.
@Tag("benchmark")
class FrameBenchmark {

    private static final int WARMUP = 100;
    private static final int RUNS = 2_000;

    /// Long enough to wrap, because a wrapped paragraph is the case that costs.
    private static final String PROSE =
            "A retained render tree keeps one render object per visual node, and each of those"
                    + " owns the Yoga node that lays it out. Without it, every frame builds a"
                    + " fresh tree, binds a fresh upcall stub for every measured leaf, and shapes"
                    + " every paragraph again from the beginning — arriving at exactly the glyphs"
                    + " the last frame arrived at.";

    private Fonts fonts;
    private TestFrames.Target target;

    /// A frame Blend2D rasterizes on the calling thread.
    ///
    /// The threaded context queues its work and only blocks when the frame ends,
    /// so a timing loop around `paint` on one of those measures *submitting* a
    /// frame rather than drawing it — 6.8 us for a 960x640 window, which is not a
    /// number anybody should believe. Pinned to zero workers so the whole-frame
    /// rows below include the rasterization they claim to.
    private TestFrames.Target synchronous;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        fonts = Fonts.bundled();
        fonts.of(BundledFont.UI, 13);
        target = TestFrames.of(960, 640, 1.0f);
        synchronous = TestFrames.of(960, 640, 1.0f, 0);
    }

    @AfterEach
    void tearDown() {
        if (target != null) {
            target.end();
        }
        if (synchronous != null) {
            synchronous.end();
        }
        if (fonts != null) {
            fonts.close();
        }
    }

    /// One class and nothing else, which is how every node in the sheet below
    /// is selected.
    private static Attributes classed(String name) {
        return new Attributes(null, java.util.Set.of(name), null);
    }

    /// The showcase's shape: a bar, a sidebar, wrapped prose and a row of
    /// labelled panels. Eight text nodes, which is a modest real window.
    private static Widget showcaseTree() {
        return new Column(List.of(
                new Panel(List.of(
                        new Row(List.of(
                                new Text("Goldberry"),
                                new Spacer(),
                                new Text("Nord dark")),
                                Attributes.NONE)),
                        classed("bar")),
                new Row(List.of(
                        new Panel(List.of(
                                new Column(List.of(
                                        new Text("Overview"),
                                        new Text("Controls"),
                                        new Text("Motion")),
                                        Attributes.NONE)),
                                classed("sidebar")),
                        new Column(List.of(
                                new Text("Retained rendering"),
                                new Text(PROSE)),
                                classed("body"))),
                        Attributes.NONE)),
                classed("root"));
    }

    private static Stylesheet sheet() {
        return Stylesheet.parse(CascadeLayer.TOOLKIT_BASE, """
                column.root { background: var(--gb-bg); padding: 12px; gap: 10px }
                panel.bar { background: var(--gb-surface); padding: 8px }
                panel.sidebar { background: var(--gb-surface-2); width: 160px; padding: 8px }
                column.body { flex-grow: 1; gap: 8px }
                row { gap: 10px; flex-grow: 1 }
                text { color: var(--gb-text) }
                """);
    }

    private WidgetRenderer renderer() {
        return new WidgetRenderer(List.of(sheet(), Theme.NORD_DARK.load()), fonts);
    }

    @Test
    @DisplayName("a frame of the showcase, stage by stage")
    void frameCost() {
        var renderer = renderer();
        var tree = new ElementTree(showcaseTree());

        // The three stages an application pays every frame, measured separately
        // so the answer is not "a frame costs N" but "and here is which part".
        report("render (cascade + boxes)", () -> renderer.render(tree).children().size());

        var box = renderer.render(tree);
        report("layout (Yoga tree, built and freed)", () -> {
            var count = new int[1];
            BoxPainter.forEachBox(target.frame(), box, (b, layout) -> count[0]++);
            return count[0];
        });

        report("paint (layout + Blend2D)", () -> {
            BoxPainter.paint(synchronous.frame(), box);
            return 1;
        });

        // And the whole thing end to end, which is the number a frame budget is
        // spent against.
        report("whole frame (render + paint)", () -> {
            BoxPainter.paint(synchronous.frame(), renderer.render(tree));
            return 1;
        });
    }

    @Test
    @DisplayName("retained against throwaway, which is the question ADR-0053 left")
    void retainedAgainstThrowaway() {
        var renderer = renderer();
        var tree = new ElementTree(showcaseTree());
        var box = renderer.render(tree);

        // The throwaway path: a Yoga tree built, laid out, walked and freed, with
        // a fresh upcall stub bound for every one of the seven measured leaves.
        report("layout+walk, throwaway tree", () -> {
            var count = new int[1];
            BoxPainter.forEachBox(target.frame(), box, (b, layout) -> count[0]++);
            return count[0];
        });

        // The same work against a tree that survives. The box handed in is the
        // *same instance* every iteration, which is the best case and is also
        // what a static window actually does -- nothing changed, so nothing
        // should be re-set and Yoga should skip the pass entirely.
        try (var retained = RenderTree.create()) {
            retained.update(target.frame(), box);
            report("layout+walk, retained (nothing changed)", () -> {
                var count = new int[1];
                retained.update(target.frame(), box);
                retained.forEachPlacedBox(placed -> count[0]++);
                return count[0];
            });
        }

        // And the honest case: a *different* box tree every frame, as a real
        // application produces after a rebuild. The Yoga nodes and the measure
        // callbacks are still reused -- the callbacks because `ParagraphCache`
        // hands back the same `Paragraph` -- but every style has to be compared
        // again, so none of the guards in `RenderObject.apply` are free.
        //
        // Rendered up front and cycled rather than rendered inside the loop:
        // putting the cascade in the timed section would make this row measure
        // render+layout while the row above it measures layout, and comparing
        // the two would be comparing different work. That mistake is what
        // ADR-0045 is about.
        var rebuilt = new Box[8];
        for (var i = 0; i < rebuilt.length; i++) {
            rebuilt[i] = renderer.render(tree);
        }
        try (var retained = RenderTree.create()) {
            retained.update(target.frame(), rebuilt[0]);
            var index = new int[1];
            report("layout+walk, retained (fresh boxes each frame)", () -> {
                var count = new int[1];
                retained.update(target.frame(), rebuilt[index[0]++ % rebuilt.length]);
                retained.forEachPlacedBox(placed -> count[0]++);
                return count[0];
            });
        }

        // Everything a frame does **except rasterize**: describe, cascade, lay
        // out, walk. This is the pair that isolates what retention and the style
        // cache actually changed -- rasterization is identical either way, and
        // including it in the headline would dilute a 40x by a constant nobody
        // touched. The whole-frame rows below put it back.
        report("frame CPU before raster, throwaway", () -> {
            var count = new int[1];
            BoxPainter.forEachBox(target.frame(), renderer.render(tree),
                    (b, layout) -> count[0]++);
            return count[0];
        });

        try (var retained = RenderTree.create()) {
            retained.update(target.frame(), renderer.render(tree));
            report("frame CPU before raster, retained", () -> {
                var count = new int[1];
                retained.update(target.frame(), renderer.render(tree));
                retained.forEachPlacedBox(placed -> count[0]++);
                return count[0];
            });
        }

        // End to end, which is what a frame budget is actually spent against --
        // on the synchronous target, so the Blend2D work is inside the timing
        // rather than queued behind it.
        report("whole frame, throwaway", () -> {
            BoxPainter.paint(synchronous.frame(), renderer.render(tree));
            return 1;
        });

        try (var retained = RenderTree.create()) {
            retained.update(synchronous.frame(), renderer.render(tree));
            report("whole frame, retained", () -> {
                retained.update(synchronous.frame(), renderer.render(tree));
                retained.paint(synchronous.frame());
                return 1;
            });
        }
    }

    @Test
    @DisplayName("what a clipped repaint buys, against repainting the window")
    void partialRepaint() {
        // The half of damage tracking that saves rasterization rather than
        // upload. The scene is the showcase's, and the thing that changes is one
        // panel's colour -- which is what a hover is: a small region of a large
        // window.
        var renderer = renderer();
        var tree = new ElementTree(showcaseTree());

        var frames = new Box[8];
        for (var i = 0; i < frames.length; i++) {
            // One small box changing colour, over an otherwise identical tree.
            frames[i] = Box.of()
                    .direction(io.github.digitalsmile.goldberry.natives.yoga.FlexDirection.COLUMN)
                    .children(
                            renderer.render(tree),
                            Box.filled(0xFF000000 | (i * 0x101010))
                                    .size(io.github.digitalsmile.goldberry.natives.yoga.StyleLength
                                            .points(60),
                                            io.github.digitalsmile.goldberry.natives.yoga.StyleLength
                                            .points(24)));
        }

        try (var render = RenderTree.create()) {
            render.update(synchronous.frame(), frames[0]);
            render.paint(synchronous.frame());
            render.damage(synchronous.frame());

            var index = new int[1];
            report("repaint the whole frame", 50, 300, () -> {
                render.update(synchronous.frame(), frames[index[0]++ % frames.length]);
                render.damage(synchronous.frame());
                render.paint(synchronous.frame());
                return 1;
            });
        }

        try (var render = RenderTree.create()) {
            render.update(synchronous.frame(), frames[0]);
            render.paint(synchronous.frame());
            render.damage(synchronous.frame());

            var index = new int[1];
            var area = new int[1];
            report("repaint only the damage", 50, 300, () -> {
                render.update(synchronous.frame(), frames[index[0]++ % frames.length]);
                var damage = render.damage(synchronous.frame());
                area[0] = damage.stream().mapToInt(r -> r.width() * r.height()).sum();
                render.paint(synchronous.frame(), damage);
                return 1;
            });
            System.out.printf("  %-42s %d of %d px%n", "damaged area on the last frame",
                    area[0], 960 * 640);
        }
    }

    @Test
    @DisplayName("what layer promotion buys a fading group")
    void fadingGroup() {
        // §1.7's claim for layer promotion, as a number. I would not make the
        // change without it: a layer costs an allocation and a blit, so reusing
        // its raster has to beat re-rasterizing the subtree by more than that.
        //
        // The subtree is the showcase's, wrapped in a group at 45% -- which is
        // `:disabled` on a real control (§2.1) and is what actually fades in this
        // toolkit today.
        var renderer = renderer();
        var tree = new ElementTree(showcaseTree());

        // Sixteen steps of a fade, rendered up front and cycled, so the cascade
        // is not inside the timed section (ADR-0045, the second time).
        var frames = new Box[16];
        for (var i = 0; i < frames.length; i++) {
            frames[i] = Box.of()
                    .opacity(0.3 + i * 0.04)
                    .children(renderer.render(tree));
        }

        try (var render = RenderTree.create()) {
            render.update(synchronous.frame(), frames[0]);
            render.paint(synchronous.frame());

            var index = new int[1];
            report("fading group, raster reused", 50, 400, () -> {
                render.update(synchronous.frame(), frames[index[0]++ % frames.length]);
                render.paint(synchronous.frame());
                return render.layersRepainted();
            });
            System.out.printf("  %-42s %d of %d%n", "layers rasterized in the last frame",
                    render.layersRepainted(), render.layersComposited());
        }

        // The comparison: the same group, with something inside it changing too,
        // so the raster is invalid every frame and the subtree is drawn again.
        var changing = new Box[16];
        for (var i = 0; i < changing.length; i++) {
            changing[i] = Box.of()
                    .opacity(0.3 + i * 0.04)
                    .background(0x01000000 | i)
                    .children(renderer.render(tree));
        }
        try (var render = RenderTree.create()) {
            render.update(synchronous.frame(), changing[0]);
            render.paint(synchronous.frame());

            var index = new int[1];
            report("fading group, raster rebuilt each frame", 50, 400, () -> {
                render.update(synchronous.frame(), changing[index[0]++ % changing.length]);
                render.paint(synchronous.frame());
                return render.layersRepainted();
            });
        }
    }

    @Test
    @DisplayName("inside the cascade, which is now the largest term in a frame")
    void cascadeCost() {
        // `render` is 135 us of a 148 us frame once layout is retained
        // (ADR-0069), so this is where the next answer has to come from. Split
        // three ways, because "the cascade is slow" is not actionable and
        // "selector matching is 80% of it" is.
        var tree = new ElementTree(showcaseTree());
        var renderer = renderer();
        renderer.render(tree);

        var resolver = new io.github.digitalsmile.goldberry.css.StyleResolver(
                List.of(sheet(), Theme.NORD_DARK.load()));

        // Every element in the tree, so the numbers below are per *frame* rather
        // than per node -- which is what a frame budget is spent in.
        var elements = new java.util.ArrayList<Element>();
        collect(tree.root(), elements);
        System.out.printf("  %-42s %d%n", "elements in the tree", elements.size());

        report("resolve: selector matching + var()", 100, 500, () -> {
            var total = 0;
            for (var element : elements) {
                total += resolver.resolve(element).size();
            }
            return total;
        });

        // The half of `resolve` that walks to the root at every node, collecting
        // custom properties. It calls `cascade` once per ancestor, and `cascade`
        // matches every selector in every sheet.
        report("resolve: custom properties alone", 100, 500, () -> {
            var total = 0;
            for (var element : elements) {
                total += resolver.customPropertiesFor(element).size();
            }
            return total;
        });

        // Turning the winning declarations into typed values -- parsing lengths
        // and colours out of tokens. Separate from matching, because they would
        // be fixed by different things.
        var declarations = new java.util.ArrayList<java.util.Map<String,
                List<io.github.digitalsmile.goldberry.css.Token>>>();
        for (var element : elements) {
            declarations.add(resolver.resolve(element));
        }
        report("ComputedStyle.of: tokens to typed values", 100, 500, () -> {
            var total = 0;
            for (var declared : declarations) {
                total += io.github.digitalsmile.goldberry.css.ComputedStyle.of(
                        declared,
                        io.github.digitalsmile.goldberry.css.CssLength.Context.DEFAULT).hashCode();
            }
            return total;
        });

        report("the whole render, for comparison",
                () -> renderer.render(tree).children().size());
    }

    private static void collect(Element element, List<Element> into) {
        into.add(element);
        for (var child : element.children()) {
            collect(child, into);
        }
    }

    @Test
    @DisplayName("what the throwaway path re-does that it need not")
    void wastedWork() {
        var renderer = renderer();
        var tree = new ElementTree(showcaseTree());

        // Eight text nodes in the tree above. ADR-0037 measured shaping at 56 us
        // and an upcall stub at 11 us, both per text node per frame -- so this
        // is where the argument for retention has to be won or lost.
        var box = renderer.render(tree);
        var textBoxes = new int[1];
        BoxPainter.forEachBox(target.frame(), box, (b, layout) -> {
            if (b.text() != null) {
                textBoxes[0]++;
            }
        });
        System.out.printf("  %-42s %d%n", "measured leaves in the tree", textBoxes[0]);

        // Rendering alone, which includes a fresh Paragraph.of per text node --
        // and therefore a fresh shaping of text that has not changed.
        report("render only (re-shapes every paragraph)",
                () -> renderer.render(tree).children().size());
    }

    // --- harness --------------------------------------------------------------
    //
    // Deliberately the same shape as TextBenchmark's, median and mean both, for
    // the reason given there: the JIT and this machine's other tenants skew the
    // mean and the median is what a frame actually experiences.

    private static void report(String what, LongSupplier work) {
        report(what, WARMUP, RUNS, work);
    }

    private static void report(String what, int warmup, int runs, LongSupplier work) {
        var sink = 0L;
        for (var i = 0; i < warmup; i++) {
            sink += work.getAsLong();
        }

        var samples = new long[runs];
        for (var i = 0; i < runs; i++) {
            var start = System.nanoTime();
            sink += work.getAsLong();
            samples[i] = System.nanoTime() - start;
        }

        var total = 0L;
        for (var sample : samples) {
            total += sample;
        }
        java.util.Arrays.sort(samples);

        System.out.printf(
                "  %-42s median %8.3f us   mean %8.3f us   p95 %8.3f us   (n=%d, sink=%d)%n",
                what,
                samples[samples.length / 2] / 1000.0,
                total / (double) runs / 1000.0,
                samples[(int) (samples.length * 0.95)] / 1000.0,
                runs,
                sink);
    }
}
