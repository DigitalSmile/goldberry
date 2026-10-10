package dev.goldberry.image.anim;

import java.nio.ByteBuffer;
import java.util.Objects;

import dev.goldberry.image.Image;
import dev.goldberry.image.ImageDecodeException;
import dev.goldberry.image.lottie.LottieRenderer;
import dev.goldberry.paint.Frame;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.model.DisplayScale;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.render.model.PixelFormat;

/// A vector animation, drawn at whatever size it is asked for: a Lottie
/// document, gzipped as a Telegram `tgs` sticker or plain JSON.
///
/// ```java
/// var sticker = VectorAnimation.of(Files.readAllBytes(file));      // tgs or json
/// Image still = sticker.imageAt(elapsedMillis, 128, 128);
/// new Canvas((frame, size) -> sticker.paint(frame, elapsedMillis, 0, 0, size.width(), size.height()));
/// ```
///
/// ## A description, not frames
///
/// [Animation] is a list of pictures and how long each is shown, which is what
/// a GIF is. This is the other kind: shapes, colours and easing curves, which
/// [#imageAt] and [#paint] draw on demand through the toolkit's own painter. So
/// the same document is crisp in a 32-pixel reply chip and a 256-pixel row,
/// and nothing is decoded ahead of time but the description.
///
/// Like [Animation] it holds no clock. What to draw is a function of how long
/// the caller says it has been playing; `AnimationView` in the widget
/// catalogue is the thing that plays one on the frame loop.
///
/// ## What is drawn, and what is refused
///
/// The subset Telegram allows in a sticker, and somewhat more: shape, null,
/// solid and precomp layers with parenting; groups and their transforms;
/// rectangles, ellipses, stars, polygons and paths; flat and gradient fills and
/// strokes, dashed or not; trims, mattes and masks; held, eased and spatial
/// keyframes, and paths that change shape.
///
/// [#of] **refuses** a document with an expression in it (code for a player to
/// run) or an image layer (a picture rather than a description), with an
/// [ImageDecodeException] that says which — Telegram refuses both in a sticker
/// too. Anything else it does not know, a text layer or a layer effect or a
/// merge, is passed over and the rest is drawn. Bytes that are not a Lottie
/// document at all are the same exception, so one `catch` covers a sticker
/// that cannot be shown.
///
/// ## Time
///
/// A Lottie document says how many frames a second it runs at and which frames
/// it spans; [#durationMillis()] is one pass of them. A document says nothing
/// about how often to play, and every player plays it for ever, so an
/// animation is **endless** until [#loops(int)] says otherwise — which is how a
/// chat that plays a sticker once and then holds its last frame asks for it.
///
/// Between frames the picture is interpolated rather than held, so an animation
/// written at 30 frames a second moves smoothly on a 120 Hz display.
///
/// Immutable and safe to share between threads: the document is read once, in
/// [#of], and every drawing keeps its state to itself.
///
/// Read more: [Text, fonts and icons](https://goldberry.dev/docs/guide/text.html#images).
public final class VectorAnimation implements MovingPicture {

    private final LottieRenderer renderer;
    private final int loopCount;

    private VectorAnimation(LottieRenderer renderer, int loopCount) {
        this.renderer = renderer;
        this.loopCount = loopCount;
    }

    /// Reads a Lottie document from `lottie`'s position to its limit, which are
    /// left where they were.
    ///
    /// Gzipped (a `tgs`) or plain JSON, told apart by gzip's magic number.
    ///
    /// @throws ImageDecodeException if the bytes are not a Lottie document, or
    ///         are one with an expression or an image layer in it
    public static VectorAnimation of(ByteBuffer lottie) {
        Objects.requireNonNull(lottie, "lottie");
        return new VectorAnimation(LottieRenderer.read(lottie), 0);
    }

    /// [#of(ByteBuffer)] over an array.
    ///
    /// @throws ImageDecodeException if the bytes are not a Lottie document, or
    ///         are one with an expression or an image layer in it
    public static VectorAnimation of(byte[] lottie) {
        Objects.requireNonNull(lottie, "lottie");
        return of(ByteBuffer.wrap(lottie));
    }

    /// The canvas's width, in the document's own units — the size it was
    /// drawn at, and the shape every drawing of it keeps.
    @Override
    public double width() {
        return renderer.width();
    }

    /// The canvas's height, in the document's own units.
    @Override
    public double height() {
        return renderer.height();
    }

    /// The frames a second the document was written at.
    public double frameRate() {
        return renderer.frameRate();
    }

    /// How long one pass takes, in milliseconds.
    public long durationMillis() {
        return Math.round(renderer.frames() / renderer.frameRate() * 1000);
    }

    /// How many times to play, or **0 for ever** — [Animation#loopCount()]'s
    /// convention.
    public int loopCount() {
        return loopCount;
    }

    /// This animation, played `count` times and then held on its last frame,
    /// or for ever when `count` is 0.
    ///
    /// @throws IllegalArgumentException if `count` is negative
    public VectorAnimation loops(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("an animation plays 0 (for ever) or more times, not " + count);
        }
        return count == loopCount ? this : new VectorAnimation(renderer, count);
    }

    /// Whether this ever stops: true unless [#loops(int)] said how often.
    public boolean isEndless() {
        return loopCount == 0;
    }

    /// How long the whole thing takes, in milliseconds, or -1 when it never
    /// finishes — what a caller asks to know whether to keep requesting
    /// frames, as on [Animation#totalMillis()].
    public long totalMillis() {
        return isEndless() ? -1 : durationMillis() * loopCount;
    }

    /// Whether it has stopped moving `elapsedMillis` in: never for an endless
    /// one, and from [#totalMillis()] on for one that is not.
    @Override
    public boolean isDoneAt(long elapsedMillis) {
        return !isEndless() && elapsedMillis >= totalMillis();
    }

    /// The document's frame number `elapsedMillis` after it started: within a
    /// pass while it plays, and the last frame once it has finished.
    ///
    /// Fractional, because the picture between two frames is interpolated
    /// rather than held.
    ///
    /// @param elapsedMillis milliseconds since the start; negative reads as 0
    public double frameAt(long elapsedMillis) {
        var elapsed = Math.max(0, elapsedMillis);
        if (isDoneAt(elapsed)) {
            return Math.max(renderer.inPoint(), renderer.outPoint() - 1);
        }
        var frames = elapsed * renderer.frameRate() / 1000;
        return renderer.inPoint() + frames % renderer.frames();
    }

    /// The picture `elapsedMillis` in, rasterised at `width` × `height` pixels.
    ///
    /// The canvas is scaled to fit inside the size and centred, keeping its
    /// shape, and the rest is transparent. A new image each call: it is drawn
    /// from the description, not looked up.
    ///
    /// @throws IllegalArgumentException if either side is not positive
    public Image imageAt(long elapsedMillis, int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "an image needs a positive size, and " + width + "x" + height + " is not one");
        }
        var pixels = PixelBuffer.allocate(new PhysicalSize(width, height), PixelFormat.BGRA32_PREMULTIPLIED);
        var frame = Frame.over(pixels, DisplayScale.ONE);
        try {
            var scale = Math.min(width / width(), height / height());
            var drawnWidth = width() * scale;
            var drawnHeight = height() * scale;
            renderer.paint(
                    frame,
                    frameAt(elapsedMillis),
                    (width - drawnWidth) / 2,
                    (height - drawnHeight) / 2,
                    drawnWidth,
                    drawnHeight);
        } finally {
            frame.end();
        }
        return Image.of(pixels);
    }

    /// Draws the picture `elapsedMillis` in onto `frame`, its canvas stretched
    /// over the rectangle `(x, y, width, height)` and clipped to it.
    ///
    /// Stretched, as [Frame#drawImage(Image, double, double, double, double)]
    /// stretches: a caller that wants it to keep its shape gives a rectangle of
    /// its shape, because that caller is doing layout and knows both sizes.
    /// Drawn as vectors, under the frame's transform and at its scale, so it is
    /// as sharp as the display allows.
    @Override
    public void paint(Frame frame, long elapsedMillis, double x, double y, double width, double height) {
        Objects.requireNonNull(frame, "frame");
        renderer.paint(frame, frameAt(elapsedMillis), x, y, width, height);
    }

    @Override
    public String toString() {
        return "VectorAnimation[" + width() + "x" + height() + ", " + frameRate() + " fps, " + durationMillis() + " ms"
                + (isEndless() ? ", endless" : ", " + loopCount + " loops") + "]";
    }
}
