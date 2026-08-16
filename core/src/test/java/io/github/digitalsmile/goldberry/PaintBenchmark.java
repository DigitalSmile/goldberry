package io.github.digitalsmile.goldberry;

import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.backend.DisplayScale;
import io.github.digitalsmile.goldberry.backend.PhysicalSize;
import io.github.digitalsmile.goldberry.backend.PixelBuffer;
import io.github.digitalsmile.goldberry.backend.PixelFormat;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.layout.BoxPainter;
import io.github.digitalsmile.goldberry.natives.yoga.Align;
import io.github.digitalsmile.goldberry.natives.yoga.FlexDirection;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import io.github.digitalsmile.goldberry.text.Font;
import io.github.digitalsmile.goldberry.text.Paragraph;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/// What painting a frame costs, and what Blend2D's workers do to it.
///
/// ADR-0031 measured paint at ~1.3 ms and present at ~10 ms, and parked
/// `thread_count` as "only matters if paint ever becomes the bottleneck".
/// ADR-0037 then measured a frame with text in it: paint 5.10 ms of a 7.86 ms
/// total, with a 14.18 ms p95 against a 16.67 ms budget. On those numbers it had.
///
/// This is the measurement that decides [PaintThreads]. It paints the showcase's
/// own scene — a bar, a sidebar and a wrapped paragraph — at several sizes and
/// several worker counts, because the answer is not one number: threading a
/// small surface loses, and threading a 4K one wins by more than it does here.
///
/// **Tagged `benchmark`, so `check` never runs it.** Nothing here asserts a
/// timing, for the reason `TextBenchmark` gives: the number is the deliverable
/// and it belongs in an ADR where it can be argued with.
///
/// Run with `./gradlew :core:benchmark`.
@Tag("benchmark")
class PaintBenchmark {

    private static final String BODY_TEXT =
            "Yoga proposes a width and this paragraph answers with a height, which is the only"
                    + " thing a flexbox algorithm needs to know about text. The answer comes back"
                    + " through a Java method called from C returning a struct by value — the"
                    + " fiddliest thing the toolkit asks of the Foreign Function & Memory API.\n\n"
                    + "Drag the window's edge. The text is shaped once, when this paragraph is"
                    + " built; every re-wrap after that is arithmetic over the glyphs that shaping"
                    + " already produced.";

    private static final int BACKGROUND = 0xFF2E3440;
    private static final int ACCENT = 0xFF88C0D0;
    private static final int PANEL = 0xFF3B4252;
    private static final int MUTED = 0xFF4C566A;
    private static final int ON_ACCENT = 0xFF2E3440;
    private static final int ON_PANEL = 0xFFECEFF4;

    /// The worker counts to sweep. Zero is the behaviour before ADR-0042 and is
    /// the baseline every other row is read against.
    private static final int[] THREAD_COUNTS = {0, 1, 2, 3, 4, 6, 8};

    private static final int WARMUP = 60;
    private static final int RUNS = 200;

    private Font titleFont;
    private Font bodyFont;

    @BeforeEach
    void openFonts() {
        RendererRequirement.enforce();
        titleFont = Font.bundled(BundledFont.UI, 18);
        bodyFont = Font.bundled(BundledFont.UI, 15);
    }

    @AfterEach
    void closeFonts() {
        if (titleFont != null) {
            titleFont.close();
        }
        if (bodyFont != null) {
            bodyFont.close();
        }
    }

    /// Why an in-app frame costs several times what this benchmark measures.
    ///
    /// The two disagreed by 8× at 960×640 and the borrowed compositor buffer was
    /// the suspect. It is not: painting into a heap buffer in the showcase
    /// measured the same as painting into the platform's. This is the other
    /// candidate — that a tight loop over **one** buffer keeps 2.4 MB of pixels
    /// in L3, where a real frame starts cold because the compositor, the event
    /// pump and everything else have run in between (ADR-0045).
    @Test
    @DisplayName("the same frame, painted hot and painted cold")
    void paintCostWhenTheBufferIsNotAlreadyInCache() {
        var title = Paragraph.of(titleFont, "Goldberry");
        var body = Paragraph.of(bodyFont, BODY_TEXT);
        var scene = scene(title, body);
        var size = new PhysicalSize(960, 640);
        var scale = new DisplayScale(1.0f);

        // Warm the JIT without warming a conclusion.
        var warm = PixelBuffer.allocate(size, PixelFormat.BGRA32_PREMULTIPLIED);
        for (var i = 0; i < WARMUP; i++) {
            paintOnce(warm, scale, 4, scene);
        }

        // Enough buffers that the working set cannot sit in any level of cache:
        // 24 x 960 x 640 x 4 is about 59 MB, comfortably past the 32 MB L3 this
        // was measured on. Round-robin, so each frame finds its own pixels
        // evicted -- which is the situation every real frame is in.
        var rotation = new PixelBuffer[24];
        for (var i = 0; i < rotation.length; i++) {
            rotation[i] = PixelBuffer.allocate(size, PixelFormat.BGRA32_PREMULTIPLIED);
        }

        for (var threads : new int[] {0, 4}) {
            var hot = new long[RUNS];
            for (var i = 0; i < RUNS; i++) {
                var start = System.nanoTime();
                paintOnce(warm, scale, threads, scene);
                hot[i] = System.nanoTime() - start;
            }

            var cold = new long[RUNS];
            for (var i = 0; i < RUNS; i++) {
                var target = rotation[i % rotation.length];
                var start = System.nanoTime();
                paintOnce(target, scale, threads, scene);
                cold[i] = System.nanoTime() - start;
            }

            Arrays.sort(hot);
            Arrays.sort(cold);
            System.out.printf(
                    "    960x640, %d threads:  one buffer (hot) %7.3f ms   %d buffers (cold) %7.3f ms"
                            + "   ratio %4.1fx%n",
                    threads,
                    hot[hot.length / 2] / 1_000_000.0,
                    rotation.length,
                    cold[cold.length / 2] / 1_000_000.0,
                    (double) cold[cold.length / 2] / hot[hot.length / 2]);
        }
    }

    @Test
    @DisplayName("what three stroked icons add to a frame")
    void paintCostOfIcons() {
        // The other thing the showcase gained since the numbers diverged. A
        // stroked path is more expensive than a filled rectangle, and three of
        // them are what the sidebar now draws.
        var title = Paragraph.of(titleFont, "Goldberry");
        var body = Paragraph.of(bodyFont, BODY_TEXT);
        var scene = scene(title, body);
        var size = new PhysicalSize(960, 640);
        var scale = new DisplayScale(1.0f);
        var buffer = PixelBuffer.allocate(size, PixelFormat.BGRA32_PREMULTIPLIED);

        try (var dashboard = Icon.bundled("layout-dashboard", 24);
                var type = Icon.bundled("type", 24);
                var palette = Icon.bundled("palette", 24)) {

            var icons = List.of(dashboard, type, palette);
            for (var withIcons : new boolean[] {false, true}) {
                for (var i = 0; i < WARMUP; i++) {
                    paintScene(buffer, scale, scene, withIcons ? icons : List.of());
                }
                var samples = new long[RUNS];
                for (var i = 0; i < RUNS; i++) {
                    var start = System.nanoTime();
                    paintScene(buffer, scale, scene, withIcons ? icons : List.of());
                    samples[i] = System.nanoTime() - start;
                }
                Arrays.sort(samples);
                System.out.printf("    960x640, %-11s median %7.3f ms%n",
                        withIcons ? "with icons" : "no icons",
                        samples[samples.length / 2] / 1_000_000.0);
            }
        }
    }

    private void paintScene(PixelBuffer buffer, DisplayScale scale, Box scene, List<Icon> icons) {
        var frame = new Frame(buffer, scale, 4);
        try {
            BoxPainter.paint(frame, scene);
            for (var i = 0; i < icons.size(); i++) {
                icons.get(i).draw(frame, 32, 64 + i * 40, 0xFFECEFF4);
            }
        } finally {
            frame.end();
        }
    }

    @Test
    @DisplayName("a showcase frame, swept across Blend2D worker counts")
    void paintCostByThreadCount() {
        System.out.printf("%navailableProcessors = %d, PaintThreads.automatic() = %d%n%n",
                Runtime.getRuntime().availableProcessors(), PaintThreads.automatic());

        // Discarded. Without it the first size measured carries the JIT of the
        // whole paint path and reads as the slowest of the four, which is a
        // warm-up artefact and not a size effect.
        sweep(960, 640, 1.0f, false);

        // 960x640 is the size ADR-0037 measured, so its numbers are comparable.
        // The others bracket it: popup- and dialog-sized surfaces where the band
        // scheduler may cost more than it saves, and a 4K one where it has
        // something to divide.
        sweep(240, 120, 1.0f, true);
        sweep(400, 300, 1.0f, true);
        sweep(640, 480, 1.0f, true);
        sweep(960, 640, 1.0f, true);
        sweep(1920, 1080, 1.0f, true);
        sweep(3840, 2160, 1.0f, true);
    }

    private void sweep(int width, int height, float scale, boolean report) {
        var title = Paragraph.of(titleFont, "Goldberry");
        var body = Paragraph.of(bodyFont, BODY_TEXT);
        var scene = scene(title, body);

        if (report) {
            System.out.printf("  %dx%d at %.1fx%n", width, height, scale);
        }
        for (var threads : THREAD_COUNTS) {
            report(width, height, scale, threads, scene, report);
        }
        if (report) {
            System.out.println();
        }
    }

    /// Paints `scene` into a fresh frame `RUNS` times and reports the spread.
    ///
    /// A whole frame per sample, `Frame` construction and `end()` included,
    /// because that is the unit `Window.paint` actually pays — and with workers
    /// attached, `end()` is where the calling thread waits for the bands. Timing
    /// only the draw calls would report an asynchronous context as free.
    ///
    /// The buffer is allocated once and reused: a per-sample allocation would
    /// measure the allocator, and the platform lends the same surface back every
    /// frame anyway.
    private void report(int width, int height, float scale, int threads, Box scene, boolean print) {
        var buffer = PixelBuffer.allocate(
                new PhysicalSize(width, height), PixelFormat.BGRA32_PREMULTIPLIED);
        var displayScale = new DisplayScale(scale);

        var actual = -1;
        for (var i = 0; i < WARMUP; i++) {
            actual = paintOnce(buffer, displayScale, threads, scene);
        }

        var samples = new long[RUNS];
        for (var i = 0; i < RUNS; i++) {
            var start = System.nanoTime();
            actual = paintOnce(buffer, displayScale, threads, scene);
            samples[i] = System.nanoTime() - start;
        }

        var total = 0L;
        for (var sample : samples) {
            total += sample;
        }
        Arrays.sort(samples);

        if (!print) {
            return;
        }

        // p95 as well as the median, because the tail is what M1's 60 fps claim
        // turns on: ADR-0037's frame fit the budget on the median and not at p95.
        System.out.printf(
                "    threads %d (got %d)  median %7.3f ms   p95 %7.3f ms   mean %7.3f ms"
                        + "   min %7.3f ms   over 16.67ms: %d/%d%n",
                threads, actual,
                samples[samples.length / 2] / 1_000_000.0,
                samples[(int) (samples.length * 0.95)] / 1_000_000.0,
                total / (double) RUNS / 1_000_000.0,
                samples[0] / 1_000_000.0,
                Arrays.stream(samples).filter(s -> s > 16_670_000L).count(),
                RUNS);
    }

    /// One frame. Returns the worker count the context actually got, so a
    /// request Blend2D refused shows up in the table rather than being read as a
    /// threaded result that happened to be slow.
    private int paintOnce(PixelBuffer buffer, DisplayScale scale, int threads, Box scene) {
        var frame = new Frame(buffer, scale, threads);
        try {
            BoxPainter.paint(frame, scene);
        } finally {
            frame.end();
        }
        return frame.threadCount();
    }

    private static Box scene(Paragraph title, Paragraph body) {
        return Box.of()
                .direction(FlexDirection.COLUMN)
                .background(BACKGROUND)
                .children(
                        Box.of()
                                .background(ACCENT)
                                .size(StyleLength.UNDEFINED, StyleLength.points(32))
                                .alignItems(Align.CENTER)
                                .padding(StyleLength.points(16))
                                .children(Box.text(title, ON_ACCENT)),
                        Box.of()
                                .grow(1)
                                .direction(FlexDirection.ROW)
                                .padding(StyleLength.points(16))
                                .gap(StyleLength.points(16))
                                .children(
                                        Box.filled(PANEL).size(
                                                StyleLength.percent(25), StyleLength.UNDEFINED),
                                        Box.of()
                                                .grow(1)
                                                .direction(FlexDirection.COLUMN)
                                                .background(MUTED)
                                                .padding(StyleLength.points(16))
                                                .children(Box.text(body, ON_PANEL))));
    }
}
