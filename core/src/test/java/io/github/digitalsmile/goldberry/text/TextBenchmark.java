package io.github.digitalsmile.goldberry.text;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.layout.BoxPainter;
import io.github.digitalsmile.goldberry.TestFrames;
import io.github.digitalsmile.goldberry.natives.yoga.MeasureCallback;
import io.github.digitalsmile.goldberry.natives.yoga.MeasureMode;
import io.github.digitalsmile.goldberry.natives.yoga.MeasureProbe;
import io.github.digitalsmile.goldberry.natives.yoga.MeasuredSize;
import io.github.digitalsmile.goldberry.natives.yoga.FlexDirection;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/// What the text path costs, measured rather than assumed.
///
/// The question M1 asks is whether a measure callback is affordable *from inside
/// a layout pass* — Yoga calls it from C, several times per node per pass, and a
/// pass runs per frame while a window is being dragged. ADR-0031 established the
/// habit these follow: get the number before optimising anything, because the
/// obvious candidate is usually not where the time goes.
///
/// **Tagged `benchmark`, so `check` never runs it.** Nothing here asserts a
/// timing. A threshold that passes on a workstation and fails on a shared CI
/// runner teaches nobody anything, and the number itself is the deliverable —
/// it goes into an ADR, where it can be argued with.
///
/// Run with `./gradlew :core:benchmark`.
@Tag("benchmark")
class TextBenchmark {

    /// Long enough to wrap into a real paragraph rather than a line or two.
    private static final String PROSE =
            "Yoga proposes a width and this paragraph answers with a height, which is the only"
                    + " thing a flexbox algorithm needs to know about text. The answer comes back"
                    + " through a Java method called from C returning a struct by value, which is"
                    + " the fiddliest thing the toolkit asks of the Foreign Function and Memory"
                    + " API. Shaping happens once, when the paragraph is built; every wrap after"
                    + " that is arithmetic over the glyphs that shaping already produced, which is"
                    + " what makes it affordable to answer from inside a layout pass at all.";

    private static final int WARMUP = 2_000;
    private static final int RUNS = 20_000;

    private Font font;

    @BeforeEach
    void openFont() {
        RendererRequirement.enforce();
        font = Font.bundled(BundledFont.UI, 14);
    }

    @AfterEach
    void closeFont() {
        if (font != null) {
            font.close();
        }
    }

    @Test
    @DisplayName("the upcall crossing, and what a measure call costs around it")
    void measureCallbackCost() {
        var paragraph = Paragraph.of(font, PROSE);
        var measure = paragraph.measureFunction();

        // The crossing on its own: a Java upcall invoked from C, returning YGSize
        // by value, doing nothing. Whatever a measure call costs, this is the
        // floor under it -- and the number ADR-0017 never put a figure to.
        try (var empty = MeasureCallback.of((w, wm, h, hm) -> new MeasuredSize(0, 0))) {
            report("upcall crossing, empty callback", () -> {
                MeasureProbe.measure(empty, 400, MeasureMode.AT_MOST, Float.NaN, MeasureMode.UNDEFINED);
                return 1;
            });
        }

        // The same crossing, with a real paragraph on the other end of it and
        // the memo hitting -- which is what Yoga sees for every call after the
        // first at a given width.
        try (var real = MeasureCallback.of(measure)) {
            report("upcall crossing, memoised paragraph", () -> {
                MeasureProbe.measure(real, 400, MeasureMode.AT_MOST, Float.NaN, MeasureMode.UNDEFINED);
                return 1;
            });
        }

        // Measured again, after the one above has warmed MeasureProbe itself.
        // The first `report` of any crossing pays for the JIT of the probe, and
        // reading that as "an empty callback is slower than a real one" would be
        // reading a warm-up artefact as a result.
        try (var empty = MeasureCallback.of((w, wm, h, hm) -> new MeasuredSize(0, 0))) {
            report("upcall crossing, empty callback (warm)", () -> {
                MeasureProbe.measure(empty, 400, MeasureMode.AT_MOST, Float.NaN, MeasureMode.UNDEFINED);
                return 1;
            });
        }

        // Creating the stub, rather than calling through it. BoxPainter builds a
        // fresh Yoga tree per frame, so this is paid per text box per frame --
        // and an upcall stub is an Arena and a MethodHandle bound into native
        // code, which is not the kind of thing to do sixty times a second.
        report("MeasureCallback.of + close (an upcall stub)", 200, 5_000, () -> {
            try (var stub = MeasureCallback.of(measure)) {
                return stub.hashCode();
            }
        });

        // In Java, no crossing: the wrap itself, hitting the memo.
        report("wrap, memo hit", () -> {
            var layout = paragraph.layout(400);
            return layout.lineCount();
        });

        // And missing it. Alternating widths defeats the one-entry memo, which
        // is exactly what a resize does.
        var widths = new double[] {380, 390, 400, 410, 420, 430, 440, 450};
        var index = new int[1];
        report("wrap, memo miss (alternating widths)", () -> {
            var layout = paragraph.layout(widths[index[0]++ % widths.length]);
            return layout.lineCount();
        });
    }

    @Test
    @DisplayName("shaping, which is the thing a cache would avoid")
    void shapingCost() {
        // Building a Paragraph shapes the whole text. This is the cost a
        // cross-paragraph cache would save, and the number that decides whether
        // one is worth having: if it is small next to a wrap, caching shaped
        // paragraphs is optimising the wrong thing.
        report("Paragraph.of (shapes the text)", () -> Paragraph.of(font, PROSE).text().length());

        report("Font.shape alone", () -> font.shape(PROSE).length());

        // What the cache turns that into.
        var cache = ParagraphCache.create();
        cache.paragraph(font, PROSE);
        report("ParagraphCache.paragraph (hit)",
                () -> cache.paragraph(font, PROSE).text().length());

        // Loading a face, for scale: this is what a Font costs, and why one is
        // held for the life of a window rather than built per frame.
        report("Font.bundled (parses the face twice)", 50, 200, () -> {
            try (var loaded = Font.bundled(BundledFont.UI, 14)) {
                return loaded.unitsPerEm();
            }
        });

        // And what a second size costs once the face already exists. The
        // difference between these two rows is what ADR-0044 bought: the parse
        // and the two copies of the file happen once per face rather than once
        // per size.
        report("FontFace.bundled (the parse on its own)", 50, 200, () -> {
            try (var face = FontFace.bundled(BundledFont.UI)) {
                return face.unitsPerEm();
            }
        });

        try (var face = FontFace.bundled(BundledFont.UI)) {
            report("Font.on (another size over a face that exists)", 50, 2_000, () -> {
                try (var loaded = Font.on(face, 14)) {
                    return loaded.unitsPerEm();
                }
            });
        }
    }

    @Test
    @DisplayName("a whole layout pass, with text in it and without")
    void layoutPassCost() {
        var target = TestFrames.of(800, 600, 1.0f);
        var paragraph = Paragraph.of(font, PROSE);

        var withText = Box.of()
                .direction(FlexDirection.COLUMN)
                .children(Box.text(paragraph, 0xFFFFFFFF));
        var withoutText = Box.of()
                .direction(FlexDirection.COLUMN)
                .children(Box.filled(0xFF000000).grow(1));

        // The comparison that matters: how much of a layout pass is the text?
        // Everything else in both trees is identical, so the difference is the
        // measure callback and the wrap behind it.
        report("layout pass, no text", 200, 2_000, () -> {
            var count = new int[1];
            BoxPainter.forEachBox(target.frame(), withoutText, (box, layout) -> count[0]++);
            return count[0];
        });

        report("layout pass, one paragraph", 200, 2_000, () -> {
            var count = new int[1];
            BoxPainter.forEachBox(target.frame(), withText, (box, layout) -> count[0]++);
            return count[0];
        });

        target.end();
    }

    // --- harness --------------------------------------------------------------

    private static void report(String what, LongSupplier work) {
        report(what, WARMUP, RUNS, work);
    }

    /// Times `work` and prints the median and the mean.
    ///
    /// The median as well as the mean because the JIT, a GC pause and this
    /// machine's other tenants all skew the mean upwards, and the median is what
    /// a frame actually experiences most of the time. The result is consumed so
    /// nothing under test can be optimised away.
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
                "  %-42s median %8.3f us   mean %8.3f us   min %8.3f us   (n=%d, sink=%d)%n",
                what,
                samples[samples.length / 2] / 1000.0,
                total / (double) runs / 1000.0,
                samples[0] / 1000.0,
                runs,
                sink);
    }
}
