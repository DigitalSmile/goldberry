package dev.goldberry.media.engine;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.FfmpegRequirement;
import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.ffi.FfmpegLibraries;
import dev.goldberry.media.ffi.VideoConverter;
import dev.goldberry.media.picture.PictureForm;

/// What the video thread pays to prepare one 4K picture in each [PictureForm]:
/// the plane copy the planes form makes, against the swscale pass to BGRA the
/// converted form makes (`docs/gpu-plan.md`, D8).
///
/// Each of the frame contract's four layouts at 3840×2160, prepared into a
/// [FrameQueue.Slot] as [VideoWorker] prepares it: once with the decoder's rows
/// as long as the slot's, which is one copy a plane, and once with 32 bytes of
/// padding a row, which is a copy a row. The conversion is `SWS_BITEXACT`, as
/// CPU present's is.
///
/// **Tagged `benchmark`, so `check` never runs it.** Run with
/// `./gradlew :media:benchmark --tests '*PlaneCopyBenchmark'`. Needs FFmpeg.
@Tag("benchmark")
class PlaneCopyBenchmark {

    private static final int WIDTH = 3840;
    private static final int HEIGHT = 2160;
    private static final int WARMUP = 5;
    private static final int RUNS = 30;
    /// A picture's time at 60 fps.
    private static final double BUDGET_MS = 1000.0 / 60;

    @BeforeEach
    void requireFfmpeg() {
        FfmpegRequirement.enforce();
    }

    /// A 4K picture of `format` whose rows are `padding` bytes longer than they
    /// need be, filled with a pattern so no plane is all one value.
    private static VideoFrame frame(Arena arena, PixelFormat format, int padding) {
        var planes = new ArrayList<MemorySegment>();
        var strides = new ArrayList<Integer>();
        for (var plane = 0; plane < format.planes(); plane++) {
            var stride = format.planeRowBytes(plane, WIDTH) + padding;
            var bytes = (long) stride * format.planeRows(plane, HEIGHT);
            var segment = arena.allocate(bytes, 64);
            for (long i = 0; i < bytes; i += 8) {
                segment.set(ValueLayout.JAVA_LONG_UNALIGNED, i, i * 0x9E3779B97F4A7C15L);
            }
            planes.add(segment);
            strides.add(stride);
        }
        return new VideoFrame(format, WIDTH, HEIGHT, planes, strides, VideoFrame.ColorMatrix.BT709, false, 0);
    }

    /// The median of `RUNS` timings of `body`, in milliseconds, after warming up.
    private static double medianMillis(Runnable body) {
        for (var i = 0; i < WARMUP; i++) {
            body.run();
        }
        var times = new double[RUNS];
        for (var i = 0; i < RUNS; i++) {
            var started = System.nanoTime();
            body.run();
            times[i] = (System.nanoTime() - started) / 1e6;
        }
        Arrays.sort(times);
        return times[RUNS / 2];
    }

    @Test
    void prepareA4kPicture() {
        System.out.printf(
                "Preparing one %d×%d picture on the video thread, median of %d (a picture at 60 fps is %.1f ms)%n",
                WIDTH, HEIGHT, RUNS, BUDGET_MS);
        try (var arena = Arena.ofConfined();
                var converter = new VideoConverter(FfmpegLibraries.get())) {
            for (var format : PixelFormat.values()) {
                var tight = frame(arena, format, 0);
                var padded = frame(arena, format, 32);
                var planes = new FrameQueue.Slot(FrameQueue.Shape.of(tight, PictureForm.PLANES));
                var bgra = new FrameQueue.Slot(FrameQueue.Shape.of(tight, PictureForm.CONVERTED));
                var bytes = 0L;
                for (var plane = 0; plane < format.planes(); plane++) {
                    bytes += (long) format.planeRowBytes(plane, WIDTH) * format.planeRows(plane, HEIGHT);
                }

                var copy = medianMillis(() -> planes.copyPlanes(tight));
                var copyRows = medianMillis(() -> planes.copyPlanes(padded));
                var convert = medianMillis(() -> converter.toBgra(tight, bgra.segment(0), bgra.stride(0)));
                System.out.printf(
                        "  %-4s %5.1f MB: planes copied %6.2f ms (%4.1f GB/s), row by row %6.2f ms;"
                                + " converted to BGRA %6.2f ms, %4.1f× the copy%n",
                        format, bytes / 1e6, copy, bytes / copy / 1e6, copyRows, convert, convert / copy);
            }
        }
    }
}
