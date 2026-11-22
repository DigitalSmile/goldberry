package dev.goldberry.camera.synthetic;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import dev.goldberry.camera.Camera;
import dev.goldberry.camera.CameraFormat;
import dev.goldberry.camera.CameraInfo;
import dev.goldberry.camera.CameraPosition;
import dev.goldberry.camera.CameraStatus;
import dev.goldberry.camera.backend.CameraFeed;
import dev.goldberry.camera.backend.PlanePool;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.picture.Picture;

/// A [Camera] with no device: it paints a test card into NV12 planes, and
/// takes a picture only when it is advanced, on the thread that advances it.
///
/// ```java
/// var camera = SyntheticCamera.of(640, 480);
/// camera.advance(Duration.ofMillis(100));   // three pictures at 30 fps
/// ```
///
/// The card is seven colour bars over a grey ramp, with a dark square that
/// crosses the bars once every two seconds. Each picture is a function of its
/// number alone, so the same pictures come out however the time is cut up.
/// It is BT.709 in limited range, as a camera's would be.
///
/// Its status is the test's to set: it starts [CameraStatus.Active] (or
/// [CameraStatus.Asking], from [#asking]), and [#approve], [#deny] and [#lose]
/// move it as a person or a cable would. Only an active camera takes pictures.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public final class SyntheticCamera implements Camera {

    /// The bars, left to right: white, yellow, cyan, green, magenta, red, blue,
    /// as `{y, cb, cr}` in BT.709 limited range.
    private static final int[][] BARS = {
        {235, 128, 128}, {219, 16, 138}, {188, 154, 16}, {173, 42, 26}, {78, 214, 230}, {63, 102, 240}, {32, 240, 118},
    };

    /// How long the square takes to cross.
    static final Duration CROSSING = Duration.ofSeconds(2);

    /// The square's luma: near black.
    private static final byte SQUARE_LUMA = 40;

    /// Chroma with no colour.
    private static final byte NEUTRAL = (byte) 128;

    private final CameraInfo info;
    private final CameraFormat format;
    private final CameraFeed feed;
    private final PlanePool pool;
    private final long frameNanos;
    private final byte[] barLuma;
    private final byte[] barChroma;
    private final byte[] rampLuma;
    private final byte[] rampChroma;
    private long frames;
    /// Nanoseconds owed to the next picture by earlier advances.
    private long pending;

    /// A camera described by `info`, taking pictures in `format`, starting in
    /// `status`.
    ///
    /// @throws IllegalArgumentException when `format` is compressed or has no
    ///         frame rate
    public SyntheticCamera(CameraInfo info, CameraFormat format, CameraStatus status) {
        this.info = Objects.requireNonNull(info, "info");
        this.format = Objects.requireNonNull(format, "format");
        Objects.requireNonNull(status, "status");
        if (format.compressed() || !(format.framesPerSecond() > 0)) {
            throw new IllegalArgumentException("a synthetic camera paints uncompressed pictures, not " + format);
        }
        this.feed = new CameraFeed(status);
        this.pool = new PlanePool(format.width(), format.height());
        this.frameNanos = Math.round(1e9 / format.framesPerSecond());
        var rows = rows(format.width());
        this.barLuma = rows[0];
        this.barChroma = rows[1];
        this.rampLuma = rows[2];
        this.rampChroma = rows[3];
    }

    /// A front-facing camera taking `width`×`height` at 30 fps, active.
    public static SyntheticCamera of(int width, int height) {
        var format = CameraFormat.of(width, height, 30);
        return new SyntheticCamera(describe(format), format, new CameraStatus.Active());
    }

    /// The same, waiting for [#approve] or [#deny], as a camera does while
    /// the system asks the user.
    public static SyntheticCamera asking(int width, int height) {
        var format = CameraFormat.of(width, height, 30);
        return new SyntheticCamera(describe(format), format, new CameraStatus.Asking());
    }

    private static CameraInfo describe(CameraFormat format) {
        return new CameraInfo(0, "Synthetic camera", CameraPosition.FRONT, List.of(format));
    }

    @Override
    public CameraInfo info() {
        return info;
    }

    @Override
    public CameraFormat format() {
        return format;
    }

    @Override
    public CameraStatus status() {
        return feed.status();
    }

    @Override
    public Optional<Picture> shownPicture() {
        return feed.shown();
    }

    @Override
    public AutoCloseable onPicture(Runnable listener) {
        return feed.onPicture(listener);
    }

    @Override
    public AutoCloseable onStatus(Consumer<? super CameraStatus> listener) {
        return feed.onStatus(listener);
    }

    /// Pictures taken so far.
    public synchronized long frames() {
        return frames;
    }

    /// Takes the pictures `duration` holds at the camera's rate, if it is
    /// active, and answers how many. What is left over a picture waits for the
    /// next call.
    public synchronized int advance(Duration duration) {
        if (duration.isNegative()) {
            throw new IllegalArgumentException("advanced by " + duration);
        }
        if (!(feed.status() instanceof CameraStatus.Active)) {
            return 0;
        }
        var due = pending + duration.toNanos();
        var count = (int) Math.min(Integer.MAX_VALUE, due / frameNanos);
        pending = due % frameNanos;
        for (var picture = 0; picture < count; picture++) {
            var slot = pool.next();
            paint(slot, frames);
            var planes = pool.written(slot, VideoFrame.ColorMatrix.BT709, false, frames * frameNanos);
            frames++;
            feed.show(planes);
        }
        return count;
    }

    /// The user let the camera be used: an asking camera becomes active.
    public void approve() {
        if (feed.status() instanceof CameraStatus.Asking) {
            feed.change(new CameraStatus.Active());
        }
    }

    /// The user refused the camera.
    public void deny() {
        feed.change(new CameraStatus.Denied());
    }

    /// The camera went away.
    public void lose(String reason) {
        feed.change(new CameraStatus.Lost(reason));
    }

    @Override
    public void close() {
        feed.change(new CameraStatus.Closed());
    }

    /// Paints picture `number` into `slot`: each row copied from the bars' or
    /// the ramp's, then the square painted over.
    private void paint(PlanePool.Slot slot, long number) {
        var width = format.width();
        var height = format.height();
        var barsBottom = height * 2 / 3;
        var side = Math.max(2, height / 5) & ~1;
        var crossing = CROSSING.toNanos();
        var travel = Math.max(0, width - side);
        var phase = (double) ((number * frameNanos) % crossing) / crossing;
        var left = (int) Math.round(phase * travel) & ~1;
        var top = Math.max(0, (barsBottom - side) / 2) & ~1;
        var right = Math.min(width, left + side);

        var luma = slot.luma();
        var chroma = slot.chroma();
        for (var row = 0; row < height; row++) {
            var bars = row < barsBottom;
            luma.put(row * slot.lumaStride(), bars ? barLuma : rampLuma);
            if ((row & 1) == 0) {
                chroma.put((row / 2) * slot.chromaStride(), bars ? barChroma : rampChroma);
            }
            if (row >= top && row < top + side) {
                for (var column = left; column < right; column++) {
                    luma.put(row * slot.lumaStride() + column, SQUARE_LUMA);
                }
                if ((row & 1) == 0) {
                    for (var column = left; column < right; column++) {
                        chroma.put((row / 2) * slot.chromaStride() + column, NEUTRAL);
                    }
                }
            }
        }
    }

    /// One row of the bars, or of the ramp: luma, and interleaved chroma.
    private static byte[][] rows(int width) {
        var chromaWidth = (width + 1) / 2 * 2;
        var barLuma = new byte[width];
        var barChroma = new byte[chromaWidth];
        var rampLuma = new byte[width];
        var rampChroma = new byte[chromaWidth];
        for (var column = 0; column < width; column++) {
            var bar = BARS[column * BARS.length / width];
            barLuma[column] = (byte) bar[0];
            var ramp = width == 1 ? 0 : (double) column / (width - 1);
            rampLuma[column] = (byte) Math.round(16 + 219 * ramp);
            if ((column & 1) == 0) {
                barChroma[column] = (byte) bar[1];
                barChroma[column + 1] = (byte) bar[2];
                rampChroma[column] = NEUTRAL;
                rampChroma[column + 1] = NEUTRAL;
            }
        }
        return new byte[][] {barLuma, barChroma, rampLuma, rampChroma};
    }
}
