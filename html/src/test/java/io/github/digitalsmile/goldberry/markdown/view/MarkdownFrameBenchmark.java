package io.github.digitalsmile.goldberry.markdown.view;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.markdown.Markdown;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.core.scroll.Scroll;

/// What a keystroke costs a `markdown-view`, stage by stage — `docs/gaps.md` G45.
///
/// The entry that asked for this was a measurement taken **downstream**: a note
/// editor with a preview beside it, rebuilt with a new `Document` on every
/// keystroke, whose layout stage grew with the whole note rather than with the
/// paragraph the reader was typing in. A number from somebody else's application
/// is a number nobody here can re-take, so this is the same shape of measurement
/// living in the repository that has to answer for it (ADR-0045, and ADR-0350 for
/// what happens when it does not reproduce).
///
/// ## What one iteration is
///
/// A keystroke: one character inserted into one paragraph in the middle of the
/// note, the bound property set, and then the four stages a frame is made of —
/// [io.github.digitalsmile.goldberry.stats.FrameStats]' own split.
///
/// | stage | what it is here |
/// |---|---|
/// | build | `ElementTree.flush()` — which includes the md4c re-parse, because the view parses in `build` |
/// | style | `WidgetRenderer.render` — the cascade, its cache, and the box tree |
/// | layout | `RenderTree.update` — Yoga over the retained render tree |
/// | raster | `RenderTree.paint` — Blend2D, on a synchronous target |
///
/// The parse is timed on its own as well, because the entry claims most of the
/// build is the *view* rather than md4c and that is worth checking rather than
/// repeating.
///
/// **Tagged `benchmark`, so `check` never runs it.** Nothing here asserts a
/// timing. `BlockReuseTest` asserts the counts instead, which is what stays true
/// on a machine somebody else is also using. Run this with
/// `./gradlew :html:benchmark --tests '*MarkdownFrameBenchmark*'`.
///
/// The md4c row is a **control**: it is the same code before and after any change
/// to the view, so two runs whose parse times disagree are two runs on two
/// differently loaded machines and their other rows should not be compared.
@Tag("benchmark")
class MarkdownFrameBenchmark {

    /// The window the preview is in — a note preview beside an editor, which is
    /// narrower than it is tall and wraps every paragraph.
    private static final int WIDTH = 560;

    private static final int HEIGHT = 720;

    private TestFrames.Target target;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        MarkdownViews.requireLibrary();
        TestFonts.get();
        // Zero workers, so `paint` rasterizes on this thread rather than queuing:
        // a timing loop around a threaded context measures submission (see
        // `FrameBenchmark`).
        target = TestFrames.of(WIDTH, HEIGHT, 1.0f, 0);
    }

    @AfterEach
    void tearDown() {
        if (target != null) {
            target.end();
        }
    }

    @Test
    @DisplayName("a keystroke in a 2 kB note")
    void small() {
        keystrokes(2_000, 40, 200, false);
    }

    @Test
    @DisplayName("a keystroke in a 50 kB note")
    void medium() {
        keystrokes(50_000, 10, 60, false);
    }

    @Test
    @DisplayName("a keystroke in a 500 kB note")
    void large() {
        keystrokes(500_000, 3, 15, false);
    }

    /// The keystroke that **adds and removes a word**, which is the one a fold
    /// that numbers words by position cannot match past.
    @Test
    @DisplayName("a keystroke in a 50 kB note that splits a word in two")
    void splitting() {
        keystrokes(50_000, 10, 60, true);
    }

    /// One note, typed in.
    ///
    /// @param bytes     roughly how large the note is
    /// @param warmup    iterations before the clock starts
    /// @param runs      iterations measured
    /// @param splitting whether what is typed is a space, so the word count moves
    private void keystrokes(int bytes, int warmup, int runs, boolean splitting) {
        var note = Notes.of(bytes);
        var source = Property.of(note.text());
        var tree = new ElementTree(root(source));
        var renderer = renderer();

        System.out.printf(
                Locale.ROOT,
                "%n  %s%s (%.1f kB, %d blocks, %d elements)%n",
                describe(bytes),
                splitting ? ", typing a space" : "",
                note.text().length() / 1000.0,
                Markdown.parse(note.text()).blocks().size(),
                size(tree));

        try (var render = RenderTree.create()) {
            // A first frame, so nothing below is measuring the fonts opening or
            // the render tree being created.
            tree.flush();
            render.update(target.frame(), renderer.render(tree));
            render.paint(target.frame());

            var build = new Samples(runs);
            var style = new Samples(runs);
            var layout = new Samples(runs);
            var raster = new Samples(runs);
            var parse = new Samples(runs);

            for (var i = 0; i < warmup + runs; i++) {
                var measured = i >= warmup;
                var text = splitting ? note.split(i) : note.typed(i);

                // Outside every clock below: what an editor does with the string
                // is the editor's business, and the entry is about the view.
                source.set(text);

                var began = System.nanoTime();
                tree.flush();
                var built = System.nanoTime();
                var box = renderer.render(tree);
                var styled = System.nanoTime();
                render.update(target.frame(), box);
                var laid = System.nanoTime();
                render.paint(target.frame());
                var painted = System.nanoTime();

                var parseBegan = System.nanoTime();
                var blocks = Markdown.parse(text).blocks().size();
                var parsed = System.nanoTime();
                if (blocks == 0) {
                    throw new IllegalStateException("the note parsed to nothing");
                }

                if (measured) {
                    build.add(built - began);
                    style.add(styled - built);
                    layout.add(laid - styled);
                    raster.add(painted - laid);
                    parse.add(parsed - parseBegan);
                }
            }

            build.report("build (flush, re-parse included)");
            style.report("style (cascade + boxes)");
            layout.report("layout (Yoga)");
            raster.report("raster (Blend2D)");
            parse.report("  of which md4c, timed alone");
            Samples.total("frame (build + style + layout + raster)", build, style, layout, raster);
        }
    }

    private static String describe(int bytes) {
        return bytes >= 1000 ? bytes / 1000 + " kB note" : bytes + " B note";
    }

    /// The preview as an application builds it: a view that follows the editor's
    /// text, inside a `scroll`, which is the shape the entry measured.
    private static Widget root(Property<String> source) {
        return new Scroll(MarkdownView.following(source).id("note"));
    }

    private WidgetRenderer renderer() {
        return new WidgetRenderer(
                List.of(
                        Controls.baseStylesheet(),
                        MarkdownStyles.stylesheet(),
                        Theme.NORD_DARK.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, """
                                scroll { flex-grow: 1; padding: 12px; background: var(--gb-bg) }
                                """)),
                TestFonts.get());
    }

    private static int size(ElementTree tree) {
        return count(tree.root());
    }

    private static int count(io.github.digitalsmile.goldberry.widget.Element element) {
        var total = 1;
        for (var child : element.children()) {
            total += count(child);
        }
        return total;
    }

    // --- the harness ----------------------------------------------------------
    //
    // Mean and worst rather than mean and median, because the entry's budget is a
    // *worst* frame: "at 50 kB the mean frame is inside 100 ms and the worst frame
    // is not". A median would hide exactly the number being argued about.

    private static final class Samples {

        private final long[] values;

        private int count;

        Samples(int capacity) {
            this.values = new long[capacity];
        }

        void add(long nanos) {
            values[count++] = nanos;
        }

        double mean() {
            var total = 0L;
            for (var i = 0; i < count; i++) {
                total += values[i];
            }
            return count == 0 ? 0 : total / (double) count / 1_000_000.0;
        }

        double worst() {
            var worst = 0L;
            for (var i = 0; i < count; i++) {
                worst = Math.max(worst, values[i]);
            }
            return worst / 1_000_000.0;
        }

        void report(String what) {
            System.out.printf(Locale.ROOT, "    %-40s mean %8.2f ms   worst %8.2f ms%n", what, mean(), worst());
        }

        static void total(String what, Samples... stages) {
            var mean = 0.0;
            var worst = 0.0;
            for (var stage : stages) {
                mean += stage.mean();
                worst += stage.worst();
            }
            System.out.printf(Locale.ROOT, "    %-40s mean %8.2f ms   worst %8.2f ms%n", what, mean, worst);
        }
    }
}
