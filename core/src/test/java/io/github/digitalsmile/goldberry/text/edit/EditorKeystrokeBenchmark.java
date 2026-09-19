package io.github.digitalsmile.goldberry.text.edit;

import java.util.function.LongSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.text.Paragraph;
import io.github.digitalsmile.goldberry.text.flow.TextAlign;
import io.github.digitalsmile.goldberry.text.font.Font;

/// What one keystroke into an [Editor] costs, at three sizes — [ADR-0411].
///
/// Nothing here asserts a timing. [EditorDocumentTest] is the guard, in counts;
/// this is the number that goes into an ADR where it can be argued with, which is
/// the habit ADR-0031 established and `TextAreaFrameBenchmark` follows one layer
/// up.
///
/// ## The three rows
///
/// **`paragraph`** is what an `Editor` did before ADR-0411: shape the whole text,
/// lay it out at the wrap width, ask where the caret is. It is written out here
/// rather than measured on the old checkout so that both rows can be read off one
/// run of one machine, and it is the old code's work rather than an imitation of
/// it — `Editor.paragraph()` built exactly this, per keystroke, because every
/// keystroke makes a different string.
///
/// **`document`** is the same keystroke through the editor as it is now, and then
/// the **opening** — the first frame, which shapes every line whichever way the
/// glyphs are held, and is the cost this change does not remove.
///
/// Run with `./gradlew :core:benchmark`.
@Tag("benchmark")
class EditorKeystrokeBenchmark {

    private static final double WRAP = 640;

    private static final int WARMUP = 20;

    private static final int RUNS = 60;

    private Font font;

    @BeforeEach
    void openFont() {
        RendererRequirement.enforce();
        font = Font.bundled(BundledFont.UI, 13);
    }

    @AfterEach
    void closeFont() {
        if (font != null) {
            font.close();
        }
    }

    /// A text of about `size` characters, every line different — a text of one
    /// repeated line would let any cache under this flatter the number.
    private static String note(int size) {
        var out = new StringBuilder(size + 128);
        var line = 1;
        while (out.length() < size) {
            out.append("Line ")
                    .append(line++)
                    .append(": an editor on a canvas holds a sticky, a label or a whole document,")
                    .append(" and the question is what one keystroke into it costs.\n");
        }
        return out.toString();
    }

    @Test
    @DisplayName("a keystroke into a text of 2 kB, 50 kB and 500 kB")
    void keystrokeCost() {
        for (var size : new int[] {2_000, 50_000, 500_000}) {
            var text = note(size);
            System.out.printf("%n  --- %d characters ---%n", text.length());

            // The old path: a new string every keystroke, shaped whole, laid out
            // whole, and a caret measured against it.
            // Each iteration is one keystroke and the text keeps it, which is what
            // typing is: the point of the old path is that every keystroke made a
            // *different* string and paid for all of it again.
            var typed = new StringBuilder(text);
            report("paragraph: shape the text, wrap it, place the caret", () -> {
                typed.append('x');
                var paragraph = Paragraph.of(font, typed.toString());
                var layout = paragraph.layout(WRAP);
                return (long)
                        TextGeometry.caretAt(paragraph, layout, paragraph.text().length(), WRAP, TextAlign.START)
                                .line();
            });

            var editor = new Editor(font).multiline(true).wrapWidth(WRAP).text(text);
            // Opened once, outside the loop, because opening is what it is: every
            // line has to be shaped before anything knows how tall the text is.
            editor.document();
            report("document:  the same keystroke, a hard line at a time", () -> {
                editor.onText("x");
                return editor.caret().line();
            });

            // And what neither path avoids: the first frame. Where every line
            // breaks and how tall the text is are facts about every line, and
            // nothing knows a line's height without shaping it — so opening is
            // proportional to the text either way. Few runs, because each of them
            // shapes the lot and then throws it away.
            report("document:  opening it, which is still every line", 2, 5, () -> {
                var opening = new Editor(font).multiline(true).wrapWidth(WRAP).text(text);
                return opening.lines().size();
            });
        }
    }

    // --- harness --------------------------------------------------------------

    /// Times `work` and prints the median, the mean and the minimum.
    ///
    /// The median as well as the mean because the JIT, a GC pause and this
    /// machine's other tenants all skew the mean upwards, and the median is what a
    /// frame actually experiences most of the time. The result is consumed so that
    /// nothing under test can be optimised away.
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
                "  %-52s median %8.3f ms   mean %8.3f ms   min %8.3f ms   (n=%d, sink=%d)%n",
                what,
                samples[samples.length / 2] / 1_000_000.0,
                total / (double) runs / 1_000_000.0,
                samples[0] / 1_000_000.0,
                runs,
                sink);
    }
}
