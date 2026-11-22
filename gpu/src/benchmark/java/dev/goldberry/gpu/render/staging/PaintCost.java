package dev.goldberry.gpu.render.staging;

import java.util.function.Supplier;

import dev.goldberry.assets.BundledFont;
import dev.goldberry.layout.Align;
import dev.goldberry.layout.FlexDirection;
import dev.goldberry.layout.Length;
import dev.goldberry.natives.sdl.gpu.SdlGpuDevice;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTransferUsage;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.BoxPainter;
import dev.goldberry.paint.Frame;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.model.DisplayScale;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.render.model.PixelFormat;
import dev.goldberry.text.Paragraph;
import dev.goldberry.text.font.Font;

/// What Blend2D's painting costs in heap memory and in a mapped SDL upload
/// transfer buffer, on the same scene: D6's second number. Source-over reads
/// what it blends onto, so painting into uncached or write-combined memory is
/// where the in-place path would lose.
final class PaintCost {

    private static final int BACKGROUND = 0xFF2E3440;
    private static final int ACCENT = 0xFF88C0D0;
    private static final int PANEL = 0xCC3B4252;
    private static final int MUTED = 0x994C566A;
    private static final int ON_ACCENT = 0xFF2E3440;
    private static final int ON_PANEL = 0xFFECEFF4;
    private static final String BODY =
            "Yoga proposes a width and this paragraph answers with a height, which is the only"
                    + " thing a flexbox algorithm needs to know about text. Drag the window's edge:"
                    + " the text is shaped once, when this paragraph is built, and every re-wrap"
                    + " after that is arithmetic over the glyphs that shaping already produced.";

    private final SdlGpuDevice device;
    private final int warmup;
    private final int runs;
    private final Box scene;

    PaintCost(SdlGpuDevice device, int warmup, int runs) {
        this.device = device;
        this.warmup = warmup;
        this.runs = runs;
        this.scene = scene();
    }

    /// One row of the table, at `size` with `threads` Blend2D workers (0
    /// synchronous, otherwise what the toolkit chose): heap memory, a transfer
    /// buffer mapped once and kept mapped, and the same buffer mapped and
    /// unmapped around every paint.
    record Row(PhysicalSize size, String threads, Samples heap, Samples mapped, Samples remapped) {

        /// How much slower painting into mapped memory is, in percent: the
        /// memory's own cost, D6's second number.
        double slower() {
            return (mapped.median() / heap.median() - 1) * 100;
        }
    }

    /// Paints the scene at `size` all three ways; `threads` below 0 lets the
    /// toolkit choose.
    Row scene(PhysicalSize size, int threads) {
        int[] got = {0};
        return measure(
                size,
                buffer -> got[0] = paint(buffer, threads),
                () -> threads < 0 ? "auto (" + got[0] + ")" : String.valueOf(threads));
    }

    /// A translucent fill over a 512 by 512 frame, a mebibyte, synchronous:
    /// the smallest paint that reads every pixel it writes, which is what a
    /// probe at device creation would time.
    Row mebibyte() {
        return measure(new PhysicalSize(512, 512), PaintCost::translucent, () -> "0, one fill");
    }

    private interface Painting {
        void paint(PixelBuffer frame);
    }

    /// The three ways interleaved, a sample of each in turn, so a clock that
    /// drifts or a JIT that warms up lands on all three alike.
    private Row measure(PhysicalSize size, Painting painting, Supplier<String> label) {
        var heap = PixelBuffer.allocate(size, PixelFormat.BGRA32_PREMULTIPLIED);
        var stride = heap.stride();
        var onHeap = new long[runs];
        var inMapped = new long[runs];
        var remapped = new long[runs];
        try (var kept = device.createTransferBuffer(SdlGpuTransferUsage.UPLOAD, stride * size.height());
                var lent = device.createTransferBuffer(SdlGpuTransferUsage.UPLOAD, stride * size.height())) {
            var mapped = new PixelBuffer(size, PixelFormat.BGRA32_PREMULTIPLIED, stride, kept.map(false));
            for (var i = 0; i < warmup + runs; i++) {
                var a = time(painting, heap);
                var b = time(painting, mapped);
                var started = System.nanoTime();
                try {
                    painting.paint(new PixelBuffer(size, PixelFormat.BGRA32_PREMULTIPLIED, stride, lent.map(false)));
                } finally {
                    lent.unmap();
                }
                var c = System.nanoTime() - started;
                if (i >= warmup) {
                    onHeap[i - warmup] = a;
                    inMapped[i - warmup] = b;
                    remapped[i - warmup] = c;
                }
            }
            kept.unmap();
        }
        return new Row(size, label.get(), new Samples(onHeap), new Samples(inMapped), new Samples(remapped));
    }

    private static long time(Painting painting, PixelBuffer buffer) {
        var started = System.nanoTime();
        painting.paint(buffer);
        return System.nanoTime() - started;
    }

    private int paint(PixelBuffer buffer, int threads) {
        var scale = new DisplayScale(1.0f);
        var frame = threads < 0 ? Frame.over(buffer, scale) : Frame.over(buffer, scale, threads);
        try {
            frame.fill(BACKGROUND);
            BoxPainter.paint(frame, scene);
        } finally {
            frame.end();
        }
        return frame.threadCount();
    }

    private static void translucent(PixelBuffer buffer) {
        var frame = Frame.over(buffer, new DisplayScale(1.0f), 0);
        try {
            frame.fillRect(0, 0, buffer.size().width(), buffer.size().height(), 0x80FFFFFF);
        } finally {
            frame.end();
        }
    }

    /// The paint benchmark's showcase frame with translucent panels, so more of
    /// it blends over what is already painted.
    private static Box scene() {
        var title = Paragraph.of(Font.bundled(BundledFont.UI, 18), "Goldberry");
        var body = Paragraph.of(Font.bundled(BundledFont.UI, 14), BODY);
        var cards = new Box[6];
        for (var i = 0; i < cards.length; i++) {
            cards[i] = Box.of()
                    .grow(1)
                    .background(MUTED)
                    .padding(Length.points(12))
                    .children(Box.text(body, ON_PANEL));
        }
        return Box.of()
                .direction(FlexDirection.COLUMN)
                .children(
                        Box.of()
                                .background(ACCENT)
                                .size(Length.UNDEFINED, Length.points(32))
                                .alignItems(Align.CENTER)
                                .padding(Length.points(16))
                                .children(Box.text(title, ON_ACCENT)),
                        Box.of()
                                .grow(1)
                                .direction(FlexDirection.ROW)
                                .padding(Length.points(16))
                                .gap(Length.points(16))
                                .children(
                                        Box.filled(PANEL).size(Length.percent(20), Length.UNDEFINED),
                                        Box.of()
                                                .grow(1)
                                                .direction(FlexDirection.COLUMN)
                                                .gap(Length.points(12))
                                                .background(PANEL)
                                                .padding(Length.points(16))
                                                .children(cards)));
    }
}
