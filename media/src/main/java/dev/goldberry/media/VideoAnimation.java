package dev.goldberry.media;

import java.lang.foreign.MemorySegment;
import java.lang.ref.Cleaner;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.image.Image;
import dev.goldberry.image.anim.MovingPicture;
import dev.goldberry.log.Logs;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.codec.Received;
import dev.goldberry.media.codec.TrackParams;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.ffi.Decoders;
import dev.goldberry.media.ffi.Demuxer;
import dev.goldberry.media.ffi.FfmpegLibraries;
import dev.goldberry.media.ffi.VideoConverter;
import dev.goldberry.media.io.Source;
import dev.goldberry.paint.Frame;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.render.model.PixelFormat;

/// A short video that plays as a picture: a video sticker, VP9 with its
/// transparency in WebM, as Telegram sends one.
///
/// ```java
/// var sticker = VideoAnimation.of(bytes);                          // a webm sticker
/// new AnimationView(sticker, "🎉")                                  // plays, loops, silent
/// AnimationView.decorative(sticker).autoplay(false)                 // its first picture
/// ```
///
/// ## A moving picture, not a player
///
/// It is a [MovingPicture], so `AnimationView` plays it as it plays a Lottie
/// sticker: on the frame loop, from its first picture when first drawn,
/// standing on that picture with `autoplay(false)` or when the user asks for
/// less movement. Nothing here holds a clock or a thread. [#paint] decodes the
/// picture for the moment it is asked for, on the thread that asks, going on
/// from the picture before, and starts again from the first picture when asked
/// for an earlier moment. Sound is never decoded. A [MediaPlayer] is the other
/// way to play a video, with threads, sound and controls of its own.
///
/// ## Transparency
///
/// A WebM track that says it has alpha (`AlphaMode`) carries it beside each
/// picture as a second VP9 stream, which is decoded too. The picture is drawn
/// over what is beneath it: a sticker's transparent pixels let the background
/// through, and its half-transparent ones blend with it. A video without alpha
/// is drawn opaque.
///
/// ## Time
///
/// One pass is the track's duration, else the container's, else how long its
/// packets last. Like a Lottie sticker it plays for ever until [#loops(int)]
/// says how often, and then holds its last picture.
///
/// ## Native memory
///
/// The decoders are FFmpeg's, in native memory, and are freed by [#close()],
/// or once nothing refers to the animation any more. The copies [#loops(int)]
/// makes share them, and one moment is decoded at a time: two views that show
/// one animation at two moments decode it twice over, so a view of its own
/// wants an animation of its own.
///
/// Read more: [A player](https://goldberry.dev/docs/components/media.html#a-player).
public final class VideoAnimation implements MovingPicture, AutoCloseable {

    private static final Logger LOG = Logs.of(VideoAnimation.class);

    /// Frees what nobody closed.
    private static final Cleaner CLEANER = Cleaner.create();

    private final Reel reel;
    private final int loopCount;

    private VideoAnimation(Reel reel, int loopCount) {
        this.reel = reel;
        this.loopCount = loopCount;
    }

    /// Reads a video from `webm`'s position to its limit, which are left where
    /// they were. The bytes are copied.
    ///
    /// @throws MediaException with [MediaError.NativesUnavailable] when FFmpeg is
    ///                        not loaded, and [MediaError.InvalidData],
    ///                        [MediaError.UnsupportedContainer] or
    ///                        [MediaError.UnsupportedCodec] when the bytes are not
    ///                        a video this build decodes
    public static VideoAnimation of(ByteBuffer webm) {
        Objects.requireNonNull(webm, "webm");
        var bytes = new byte[webm.remaining()];
        webm.duplicate().get(bytes);
        return new VideoAnimation(Reel.open(bytes), 0);
    }

    /// [#of(ByteBuffer)] over an array, which is copied.
    ///
    /// @throws MediaException as [#of(ByteBuffer)] does
    public static VideoAnimation of(byte[] webm) {
        Objects.requireNonNull(webm, "webm");
        return of(ByteBuffer.wrap(webm));
    }

    /// The picture's width in pixels, which is drawn as that many logical
    /// pixels when nothing says otherwise.
    @Override
    public double width() {
        return reel.width;
    }

    /// The picture's height in pixels.
    @Override
    public double height() {
        return reel.height;
    }

    /// Whether the video carries alpha beside its pictures.
    public boolean hasAlpha() {
        return reel.alpha;
    }

    /// How long one pass takes, in milliseconds.
    public long durationMillis() {
        return Duration.ofNanos(reel.durationNanos).toMillis();
    }

    /// How many times to play, or **0 for ever**.
    public int loopCount() {
        return loopCount;
    }

    /// This animation, played `count` times and then held on its last picture,
    /// or for ever when `count` is 0. It shares this one's decoders.
    ///
    /// @throws IllegalArgumentException if `count` is negative
    public VideoAnimation loops(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("an animation plays 0 (for ever) or more times, not " + count);
        }
        return count == loopCount ? this : new VideoAnimation(reel, count);
    }

    /// Whether this ever stops: true unless [#loops(int)] said how often.
    public boolean isEndless() {
        return loopCount == 0;
    }

    /// How long the whole thing takes, in milliseconds, or -1 when it never
    /// finishes.
    public long totalMillis() {
        return isEndless() ? -1 : durationMillis() * loopCount;
    }

    /// Whether it has stopped moving `elapsedMillis` in: never for an endless
    /// one, and from [#totalMillis()] on for one that is not.
    @Override
    public boolean isDoneAt(long elapsedMillis) {
        return !isEndless() && elapsedMillis >= totalMillis();
    }

    /// The picture shown `elapsedMillis` in, as an image of its own: what a
    /// caller that keeps a still asks for. Premultiplied, its transparent pixels
    /// transparent.
    ///
    /// @throws MediaException when the video cannot be decoded that far
    /// @throws IllegalStateException after [#close()]
    public Image imageAt(long elapsedMillis) {
        synchronized (reel) {
            var shown = reel.at(streamNanos(elapsedMillis));
            var copy = PixelBuffer.allocate(shown.size(), PixelFormat.BGRA32_PREMULTIPLIED);
            copy.pixels().duplicate().put(shown.pixels().pixels().duplicate().clear());
            return Image.of(copy);
        }
    }

    /// Draws the picture shown `elapsedMillis` in, stretched over the rectangle
    /// `(x, y, width, height)` and blended over what is already there.
    ///
    /// A video that fails to decode part way draws the last picture it decoded,
    /// and says so once in the log, rather than fail the frame it is drawn in.
    /// After [#close()] nothing is drawn.
    @Override
    public void paint(Frame frame, long elapsedMillis, double x, double y, double width, double height) {
        Objects.requireNonNull(frame, "frame");
        if (!(width > 0) || !(height > 0)) {
            return;
        }
        synchronized (reel) {
            var image = reel.paintable(streamNanos(elapsedMillis));
            if (image != null) {
                frame.drawImage(image, x, y, width, height);
            }
        }
    }

    /// Frees the decoders, for this animation and every copy [#loops(int)] made
    /// of it. Idempotent.
    @Override
    public void close() {
        synchronized (reel) {
            reel.close();
        }
    }

    /// Where in one pass `elapsedMillis` falls: within the pass while it plays,
    /// and the last moment of it once it has finished.
    private long streamNanos(long elapsedMillis) {
        var elapsed = Math.max(0, elapsedMillis);
        if (isDoneAt(elapsed)) {
            return reel.durationNanos - 1;
        }
        return Math.multiplyExact(elapsed, 1_000_000L) % reel.durationNanos;
    }

    @Override
    public String toString() {
        return "VideoAnimation[" + reel.width + "x" + reel.height + ", " + durationMillis() + " ms"
                + (reel.alpha ? ", alpha" : "") + (isEndless() ? ", endless" : ", " + loopCount + " loops") + "]";
    }

    /// The decoding half: the demuxer and decoder over the bytes, the picture
    /// shown, and the one decoded after it. Read and written under its own lock.
    private static final class Reel {

        private final Natives natives;
        private final Cleaner.Cleanable cleanable;
        private final int width;
        private final int height;
        private final boolean alpha;
        private final long durationNanos;
        private final PixelBuffer pixels;
        private final Image image;
        /// The time of the picture in `pixels` from the stream's first, or -1.
        private long shownNanos = -1;
        /// The picture decoded after it, lent by the decoder until its next call.
        private @Nullable VideoFrame next;
        /// The time of the stream's first picture, which every time counts from.
        private long originNanos = VideoFrame.NO_PTS;
        private boolean draining;
        private boolean ended;
        private boolean failed;

        private Reel(Natives natives, int width, int height, boolean alpha, long durationNanos) {
            this.natives = natives;
            this.cleanable = CLEANER.register(this, natives);
            this.width = width;
            this.height = height;
            this.alpha = alpha;
            this.durationNanos = durationNanos;
            this.pixels = PixelBuffer.allocate(new PhysicalSize(width, height), PixelFormat.BGRA32_PREMULTIPLIED);
            this.image = Image.of(pixels);
        }

        /// Opens a demuxer and a decoder over `bytes` and reads what they hold.
        static Reel open(byte[] bytes) {
            var ffmpeg = FfmpegLibraries.get();
            var source = new Source(URI.create("memory:///sticker.webm"), Map.of(), Source.DEFAULT_TIMEOUT);
            var io = new BytesIO(bytes);
            var demuxer = Demuxer.open(ffmpeg, source, io);
            try {
                var info = demuxer.info();
                var track = info.defaultTrack(MediaType.VIDEO)
                        .orElseThrow(() -> new MediaException(new MediaError.InvalidData("the bytes hold no video")));
                if (!(track.params() instanceof TrackParams.Video params)
                        || params.width() <= 0
                        || params.height() <= 0) {
                    throw new MediaException(new MediaError.InvalidData("the video has no size"));
                }
                demuxer.select(Set.of(track.index()));
                var durationNanos = track.duration()
                        .or(info::duration)
                        .map(Duration::toNanos)
                        .filter(nanos -> nanos > 0)
                        .orElseGet(() -> lengthOfPackets(demuxer));
                var decoder = Decoders.open(ffmpeg, demuxer, track.index(), List.of(), 0)
                        .decoder();
                return new Reel(
                        new Natives(demuxer, decoder, new VideoConverter(ffmpeg, false)),
                        params.width(),
                        params.height(),
                        params.alpha(),
                        durationNanos);
            } catch (RuntimeException | Error e) {
                demuxer.close();
                throw e;
            }
        }

        /// How long the selected track's packets last, read through once: for a
        /// container that records no duration.
        private static long lengthOfPackets(Demuxer demuxer) {
            var first = Long.MAX_VALUE;
            var last = Long.MIN_VALUE;
            for (var read = demuxer.read(); read != null; read = demuxer.read()) {
                try (var packet = read) {
                    if (packet.pts() == Packet.NO_TIMESTAMP) {
                        continue;
                    }
                    var start = packet.ptsNanos();
                    first = Math.min(first, start);
                    last = Math.max(last, start + packet.timeBase().toNanos(Math.max(packet.duration(), 0)));
                }
            }
            demuxer.seek(0);
            if (first == Long.MAX_VALUE || last <= first) {
                throw new MediaException(new MediaError.InvalidData("the video has no pictures"));
            }
            return last - first;
        }

        /// The image to draw for `nanos` into a pass, or null when there is none:
        /// closed, or failed before its first picture.
        @Nullable
        Image paintable(long nanos) {
            if (natives.closed) {
                return null;
            }
            if (!failed) {
                try {
                    advanceTo(nanos);
                } catch (MediaException e) {
                    failed = true;
                    LOG.warn("a video animation stopped decoding and keeps its last picture", e);
                }
            }
            return shownNanos < 0 ? null : image;
        }

        /// The image of the picture shown `nanos` into a pass.
        Image at(long nanos) {
            if (natives.closed) {
                throw new IllegalStateException("the animation is closed");
            }
            advanceTo(nanos);
            if (shownNanos < 0) {
                throw new MediaException(new MediaError.InvalidData("the video has no pictures"));
            }
            return image;
        }

        /// Makes the picture shown the last one whose time is at or before
        /// `nanos`, or the first: decoding on from the picture shown, or from the
        /// start for an earlier moment.
        private void advanceTo(long nanos) {
            if (shownNanos > nanos) {
                rewind();
            }
            while (true) {
                if (next == null && !ended) {
                    next = decodeNext();
                }
                var candidate = next;
                if (candidate == null) {
                    return;
                }
                var at = timeOf(candidate);
                if (shownNanos >= 0 && at > nanos) {
                    return;
                }
                natives.converter.toBgra(candidate, MemorySegment.ofBuffer(pixels.pixels()), pixels.stride());
                shownNanos = Math.max(at, 0);
                next = null;
            }
        }

        /// When `frame` is shown, from the stream's first picture.
        private long timeOf(VideoFrame frame) {
            if (frame.ptsNanos() == VideoFrame.NO_PTS) {
                return shownNanos < 0 ? 0 : shownNanos + 1;
            }
            if (originNanos == VideoFrame.NO_PTS) {
                originNanos = frame.ptsNanos();
            }
            return frame.ptsNanos() - originNanos;
        }

        private void rewind() {
            natives.demuxer.seek(0);
            natives.decoder.flush();
            next = null;
            draining = false;
            ended = false;
            shownNanos = -1;
        }

        /// The next picture out of the decoder, fed packets as it asks, or null at
        /// the end of the stream.
        private @Nullable VideoFrame decodeNext() {
            while (true) {
                switch (natives.decoder.receive()) {
                    case Received.Decoded(var frame) -> {
                        if (frame instanceof VideoFrame picture) {
                            return picture;
                        }
                    }
                    case Received.Ended _ -> {
                        ended = true;
                        return null;
                    }
                    case Received.NeedsInput _ -> feed();
                }
            }
        }

        private void feed() {
            if (draining) {
                ended = true;
                throw new MediaException(new MediaError.InvalidData("the decoder asked for input after the end"));
            }
            var packet = natives.demuxer.read();
            if (packet == null) {
                natives.decoder.sendEnd();
                draining = true;
                return;
            }
            try (packet) {
                if (!natives.decoder.send(packet)) {
                    throw new MediaException(
                            new MediaError.InvalidData("the decoder refused a packet after asking for one"));
                }
            }
        }

        void close() {
            cleanable.clean();
        }
    }

    /// What a [Reel] frees, and the [Cleaner]'s action for it, which holds no
    /// reference to the reel: the demuxer over the bytes, the video track's
    /// decoder, and what converts its pictures for drawing.
    private static final class Natives implements Runnable {

        private final Demuxer demuxer;
        private final Decoder decoder;
        private final VideoConverter converter;
        private volatile boolean closed;

        Natives(Demuxer demuxer, Decoder decoder, VideoConverter converter) {
            this.demuxer = demuxer;
            this.decoder = decoder;
            this.converter = converter;
        }

        @Override
        public void run() {
            closed = true;
            try {
                converter.close();
                decoder.close();
            } finally {
                demuxer.close();
            }
        }
    }
}
