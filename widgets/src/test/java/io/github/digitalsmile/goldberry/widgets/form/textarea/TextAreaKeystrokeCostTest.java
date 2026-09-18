package io.github.digitalsmile.goldberry.widgets.form.textarea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.event.TextEvent;
import io.github.digitalsmile.goldberry.input.hit.Extent;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;

/// What one keystroke into a `text-area` costs, **counted** — `docs/gaps.md`
/// G44, [ADR-0388].
///
/// The guard for a cost rather than for a feature, so every assertion here is a
/// count and none is a duration. A style-budget test that reads 3.6 ms alone and
/// 20 ms under a parallel Gradle is a test that fails for reasons that have
/// nothing to do with the code; how many paragraphs a frame shaped is the same
/// number on every machine.
///
/// The rule all of it is about: **a keystroke into a long note must cost what a
/// keystroke into a short one costs.** Each test below measures the same thing
/// for a 2 kB note and a 500 kB note and asserts they are comparable — not that
/// either is small, which would be a threshold somebody would have to tune.
///
/// ## Characters, not paragraphs
///
/// The count that matters is how many **characters** were shaped and drawn, not
/// how many paragraphs. Before [ADR-0388] a keystroke into a 500 kB note shaped
/// exactly one paragraph, the same as a keystroke into a 2 kB note — and that
/// one paragraph was half a megabyte. Every counter this toolkit had said the
/// frame was cheap, which is the whole reason G44 arrived as a stopwatch
/// reading from downstream rather than as a red test here.
///
/// [TextAreaFrameBenchmark] is where the milliseconds are, and it asserts
/// nothing.
class TextAreaKeystrokeCostTest {

    /// A short note and a long one, and nothing in between: the entry's table
    /// had three rows and the two ends are what an invariant needs.
    private static final int SHORT = 2_000;

    private static final int LONG = 500_000;

    private static final double WIDTH = 640;

    private static final double HEIGHT = 600;

    private final TestHost host = new TestHost();

    /// A note of about `bytes` characters, in paragraphs of prose.
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

    /// One editor, mounted and measured, with **one** renderer behind it.
    ///
    /// One renderer and not one per frame, because the paragraph cache is the
    /// renderer's: a fresh one per render would miss on everything and the
    /// counts below would all read the same.
    private record Mounted(ElementTree tree, WidgetRenderer renderer) {

        TextAreaBox box() {
            return (TextAreaBox) tree.root().children().getFirst().widget();
        }

        Box render() {
            tree.flush();
            return renderer.render(tree);
        }

        void type(String text) {
            box().onText(new TextEvent(text, null));
        }

        /// Characters shaped by one frame.
        long shapedByOneFrame() {
            var before = shaped();
            render();
            return shaped() - before;
        }

        private long shaped() {
            var cache = renderer.paragraphs();
            return cache == null ? 0 : cache.shapedCharacters();
        }
    }

    private Mounted mounted(int bytes) {
        var area = new TextArea(note(bytes), value -> {})
                .gutter(true)
                .fill(true)
                .withAttributes(new Attributes(null, Set.of("mono"), null));
        var renderer = new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get());
        var mounted = new Mounted(new ElementTree(area, host), renderer);
        mounted.render();
        mounted.box().measured(new Extent((float) WIDTH, (float) HEIGHT), new Extent((float) WIDTH, (float) HEIGHT));
        // Twice more: the first frame has no measurement to wrap against, and
        // the one after it settles the scroll.
        mounted.render();
        mounted.render();
        return mounted;
    }

    @Test
    @DisplayName("a keystroke shapes the line it changed, whatever the note weighs")
    void aKeystrokeShapesOneLine() {
        var light = mounted(SHORT);
        var heavy = mounted(LONG);
        light.type("x");
        heavy.type("x");

        var lightShaped = light.shapedByOneFrame();
        var heavyShaped = heavy.shapedByOneFrame();

        // Not equal to the character — the numbers in the long note's gutter are
        // wider, so there are more of them to shape, and the rows in view are
        // not the same rows. Within a line of each other, which is the
        // invariant: a keystroke shapes the rows on screen and nothing else.
        assertTrue(
                heavyShaped < lightShaped + 400,
                "typing into a 500 kB note shapes about what typing into a 2 kB note shapes: " + lightShaped
                        + " against " + heavyShaped);
        // And an absolute bound, so "close to each other" cannot be satisfied by
        // both of them being enormous.
        assertTrue(heavyShaped < 8_000, "and that is a screenful, not a document: " + heavyShaped + " characters");
    }

    @Test
    @DisplayName("a frame in which nothing was typed shapes nothing")
    void aSettledFrameShapesNothing() {
        var area = mounted(LONG);
        assertEquals(0, area.shapedByOneFrame(), "a still 500 kB note re-shapes nothing at all");
    }

    @Test
    @DisplayName("the glyphs drawn are the rows on screen, not the lines in the note")
    void onlyTheVisibleLinesAreDrawn() {
        var light = mounted(SHORT);
        var heavy = mounted(LONG);

        var lightDrawn = drawnCharacters(light.render());
        var heavyDrawn = drawnCharacters(heavy.render());

        // The 2 kB note is not much longer than a screenful at this size, so it
        // draws most of itself; the 500 kB note draws a screenful of a document
        // two hundred and fifty times longer. What is asserted is that the
        // second is not the larger, which is what "only the visible lines"
        // means.
        assertTrue(
                heavyDrawn <= lightDrawn + 500,
                "a 500 kB note draws about what a 2 kB note draws: " + lightDrawn + " against " + heavyDrawn);
        assertTrue(heavyDrawn < 8_000, "and that is a screenful, not a document: " + heavyDrawn + " characters");
    }

    /// The suspect `docs/gaps.md` G44 named first, and it was never the cause —
    /// kept because a ruled-out suspect is worth keeping ruled out.
    @Test
    @DisplayName("the cascade walks the same tree, whatever the note weighs")
    void theTreeIsTheSameSize() {
        var light = mounted(SHORT);
        var heavy = mounted(LONG);
        light.render();
        heavy.render();

        assertEquals(
                count(light.tree().root()),
                count(heavy.tree().root()),
                "the tree G44 measured is one control, and it is the same tree either way");
    }

    /// Characters handed to the painter, anywhere under the control.
    ///
    /// The painter draws every line of every paragraph it is given and the clip
    /// only decides what survives, so this is the work rather than the ink.
    private static int drawnCharacters(Box area) {
        var count = area.text() == null ? 0 : area.text().paragraph().text().length();
        for (var child : area.children()) {
            count += drawnCharacters(child);
        }
        return count;
    }

    /// Elements in the tree — what the cascade walks.
    private static int count(Element element) {
        var total = 1;
        for (var child : element.children()) {
            total += count(child);
        }
        return total;
    }
}
