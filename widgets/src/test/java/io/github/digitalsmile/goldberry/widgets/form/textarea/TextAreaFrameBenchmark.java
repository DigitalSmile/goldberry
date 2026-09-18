package io.github.digitalsmile.goldberry.widgets.form.textarea;

import java.util.List;
import java.util.Set;
import java.util.function.LongSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.event.TextEvent;
import io.github.digitalsmile.goldberry.input.hit.Extent;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;

/// What a keystroke into a long `text-area` costs, stage by stage.
///
/// `docs/gaps.md` G44 arrived as a table from a downstream note editor: one
/// `text-area` in edit mode, gutter on, one character typed per frame, and a
/// style span that grew with the note rather than with the tree. The tree is the
/// same size in every row here too — one control — so anything that grows is
/// growing with the string.
///
/// The stages are the launcher's, in its order and with its meanings
/// ([io.github.digitalsmile.goldberry.Launcher] `paint`):
///
/// - **build** — `ElementTree.flush`, the rebuild a `setState` asked for.
/// - **style** — `WidgetRenderer.render`, which is the cascade **and** the boxes.
///   A widget's own `render` runs inside it, so a paragraph shaped there is in
///   this number. That is why G44's "style" grows: it is not only the cascade.
/// - **layout** — Yoga, against the retained render tree.
/// - **raster** — Blend2D.
///
/// **Tagged `benchmark`, so `check` never runs it.** Nothing here asserts a
/// timing; [TextAreaKeystrokeCostTest] is the guard, and it asserts counts.
/// Run with `./gradlew :widgets:benchmark --tests '*TextAreaFrameBenchmark*'`.
@Tag("benchmark")
class TextAreaFrameBenchmark {

    /// Frames typed before the clock starts, and frames measured.
    ///
    /// Small next to [io.github.digitalsmile.goldberry.widgets.core.FrameBenchmark]'s
    /// thousands, because every one of these appends a character: a run of 2 000
    /// would grow a 500 kB note by nothing measurable but would take minutes at
    /// the rates this used to report.
    private static final int WARMUP = 25;

    private static final int RUNS = 200;

    /// How wide the control is laid out, in logical points. A note editor's
    /// column: wide enough that prose wraps a few times per hard line.
    private static final double WIDTH = 640;

    private static final double HEIGHT = 600;

    private TestHost host;
    private TestFrames.Target synchronous;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        host = new TestHost();
        // Zero workers, so the rasterization is inside the timing rather than
        // queued behind it — FrameBenchmark's reasoning, and the same trap.
        synchronous = TestFrames.of((int) WIDTH, (int) HEIGHT, 1.0f, 0);
    }

    @AfterEach
    void tearDown() {
        if (synchronous != null) {
            synchronous.end();
        }
    }

    /// A note of about `bytes` characters, in paragraphs of prose.
    ///
    /// Prose rather than one long line, because a note is paragraphs and because
    /// the hard-line count is what the gutter is about. Roughly seventy
    /// characters to a line, which wraps two or three times in the column above.
    private static String note(int bytes) {
        // Every line different, and that is not decoration: the paragraph cache
        // is keyed by the text, so a note made of one repeated line would shape
        // once and hit for ever after — a note nobody has, flattering exactly
        // the number under test.
        var out = new StringBuilder(bytes + 128);
        var line = 1;
        while (out.length() < bytes) {
            out.append("Line ")
                    .append(line++)
                    .append(": a note editor holds one text-area and nothing else, and the question")
                    .append(" is what one keystroke into it costs.\n");
        }
        return out.toString();
    }

    private WidgetRenderer renderer() {
        return new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get());
    }

    /// The editor the entry describes: one area, gutter on, `class="mono"`,
    /// filling its window.
    private static TextArea editor(String text) {
        return new TextArea(text, value -> {})
                .gutter(true)
                .fill(true)
                .withAttributes(new Attributes(null, Set.of("mono"), null));
    }

    private static TextAreaBox box(ElementTree tree) {
        return (TextAreaBox) tree.root().children().getFirst().widget();
    }

    @Test
    @DisplayName("a keystroke into a 2 kB, a 50 kB and a 500 kB note")
    void keystrokeCost() {
        for (var bytes : new int[] {2_000, 50_000, 500_000}) {
            System.out.printf("%n  --- %d kB note ---%n", bytes / 1000);
            measure(note(bytes));
        }
    }

    /// One note, typed into, with the four stages timed separately.
    private void measure(String text) {
        var renderer = renderer();
        var tree = new ElementTree(editor(text), host);
        try (var render = RenderTree.create()) {
            // Mount: two frames, because the first has no measurement to wrap
            // against — every measured control in this catalog makes that bargain.
            tree.flush();
            var first = renderer.render(tree);
            render.update(synchronous.frame(), first);
            box(tree).measured(new Extent((float) WIDTH, (float) HEIGHT), new Extent((float) WIDTH, (float) HEIGHT));
            tree.flush();
            render.update(synchronous.frame(), renderer.render(tree));

            System.out.printf("  %-42s %d%n", "characters in the note", text.length());

            // Every stage of the same frame, in the launcher's order, so what is
            // reported is one real frame split up rather than four independent
            // experiments. The typing happens outside every timer.
            var build = new long[RUNS];
            var style = new long[RUNS];
            var layout = new long[RUNS];
            var raster = new long[RUNS];
            var whole = new long[RUNS];
            for (var i = -WARMUP; i < RUNS; i++) {
                box(tree).onText(new TextEvent("x", null));

                var began = System.nanoTime();
                tree.flush();
                var built = System.nanoTime();
                var boxes = renderer.render(tree);
                var styled = System.nanoTime();
                render.update(synchronous.frame(), boxes);
                var laid = System.nanoTime();
                render.paint(synchronous.frame());
                var rastered = System.nanoTime();
                if (i >= 0) {
                    build[i] = built - began;
                    style[i] = styled - built;
                    layout[i] = laid - styled;
                    raster[i] = rastered - laid;
                    whole[i] = rastered - began;
                }
            }
            report("build", build);
            report("style (cascade + boxes + shaping)", style);
            report("layout", layout);
            report("raster", raster);
            report("whole frame", whole);

            // And the thing G44 actually claims: restyling an area whose text
            // did **not** change. If this row tracks the note's size too then
            // the cost is not the shaping.
            var settled = renderer.render(tree);
            time(
                    "style only, nothing typed",
                    () -> renderer.render(tree).children().size());
            time("layout only, nothing typed", () -> {
                render.update(synchronous.frame(), settled);
                return 1;
            });
        }
    }

    // --- harness --------------------------------------------------------------
    //
    // FrameBenchmark's, median and mean both, for the reason TextBenchmark gives:
    // the JIT and this machine's other tenants skew the mean, and the median is
    // what a frame actually experiences.

    private static void time(String what, LongSupplier work) {
        var sink = 0L;
        for (var i = 0; i < WARMUP; i++) {
            sink += work.getAsLong();
        }

        var samples = new long[RUNS];
        for (var i = 0; i < RUNS; i++) {
            var start = System.nanoTime();
            sink += work.getAsLong();
            samples[i] = System.nanoTime() - start;
        }
        report(what, samples);
    }

    /// One stage's samples, as the median, the mean and the worst.
    ///
    /// The median as well as the mean because a frame that collected garbage is
    /// in the mean and is not what a keystroke costs; the worst as well, because
    /// G44's table reports one and a budget is spent against it.
    private static void report(String what, long[] taken) {
        var total = 0L;
        for (var sample : taken) {
            total += sample;
        }
        var samples = taken.clone();
        java.util.Arrays.sort(samples);
        System.out.printf(
                "  %-42s median %8.3f ms   mean %8.3f ms   worst %8.3f ms   (n=%d)%n",
                what,
                samples[samples.length / 2] / 1e6,
                total / (double) samples.length / 1e6,
                samples[samples.length - 1] / 1e6,
                samples.length);
    }

    /// What one keystroke shapes, counted rather than timed.
    ///
    /// **Characters, not paragraphs**, and the difference is the whole entry: a
    /// keystroke into a 500 kB note has always missed the paragraph cache
    /// exactly once, and before ADR-0388 that one miss was half a megabyte.
    /// [TextAreaKeystrokeCostTest] is the assertion; this prints the numbers.
    @Test
    @DisplayName("what one keystroke shapes, per note size")
    void shapedPerKeystroke() {
        for (var bytes : new int[] {2_000, 50_000, 500_000}) {
            var text = note(bytes);
            var renderer = renderer();
            var tree = new ElementTree(editor(text), host);
            var openedAt = System.nanoTime();
            tree.flush();
            renderer.render(tree);
            box(tree).measured(new Extent((float) WIDTH, (float) HEIGHT), new Extent((float) WIDTH, (float) HEIGHT));
            tree.flush();
            renderer.render(tree);
            var opened = System.nanoTime() - openedAt;

            var paragraphs = renderer.paragraphs();
            // What opening the note cost — the part that stays proportional to
            // it, because nothing knows how tall a line is until it is shaped.
            System.out.printf(
                    "  %-42s %d characters shaped in %.3f ms%n",
                    (bytes / 1000) + " kB note, opened",
                    paragraphs == null ? 0 : paragraphs.shapedCharacters(),
                    opened / 1e6);
            var misses = paragraphs == null ? 0 : paragraphs.misses();
            var shaped = paragraphs == null ? 0 : paragraphs.shapedCharacters();
            box(tree).onText(new TextEvent("x", null));
            tree.flush();
            var boxes = renderer.render(tree);

            System.out.printf(
                    "  %-42s %d paragraphs, %d characters shaped; %d drawn; note is %d%n",
                    (bytes / 1000) + " kB note, one keystroke",
                    (paragraphs == null ? 0 : paragraphs.misses()) - misses,
                    (paragraphs == null ? 0 : paragraphs.shapedCharacters()) - shaped,
                    drawn(boxes),
                    text.length());
        }
    }

    /// Characters handed to the painter anywhere in the tree — what the raster
    /// stage is proportional to.
    private static long drawn(io.github.digitalsmile.goldberry.paint.Box box) {
        var total = box.text() == null ? 0L : box.text().paragraph().text().length();
        for (var child : box.children()) {
            total += drawn(child);
        }
        return total;
    }

    /// What a settled frame — nothing typed — has to shape.
    ///
    /// Zero is the only right answer, and it was not always the answer: a widget
    /// that builds a fresh string per frame misses the cache every frame however
    /// still the screen is.
    @Test
    @DisplayName("what a settled frame shapes")
    void settledFrameShapes() {
        var text = note(500_000);
        var renderer = renderer();
        var tree = new ElementTree(editor(text), host);
        tree.flush();
        renderer.render(tree);
        box(tree).measured(new Extent((float) WIDTH, (float) HEIGHT), new Extent((float) WIDTH, (float) HEIGHT));
        tree.flush();
        renderer.render(tree);
        renderer.render(tree);

        var paragraphs = renderer.paragraphs();
        var before = paragraphs == null ? 0 : paragraphs.shapedCharacters();
        renderer.render(tree);
        var after = paragraphs == null ? 0 : paragraphs.shapedCharacters();
        System.out.printf("  %-42s %d characters%n", "500 kB note, settled frame, shaped", after - before);
    }
}
