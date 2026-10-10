package dev.goldberry.media.codec;

import java.lang.foreign.MemorySegment;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;

/// One compressed frame from the demuxer, on its way to a [Decoder].
///
/// The data is native memory, so a provider can decode straight from it without
/// a copy. It belongs to whoever made the packet.
/// The Engine closes each packet once its decoder has taken it, and a decoder
/// that needs the bytes after [Decoder#send] returns copies them.
///
/// Timestamps count ticks of [#timeBase()]. [#NO_TIMESTAMP] marks one the
/// container did not give.
///
/// A packet of a picture with transparency may carry its [#alpha()] beside the
/// data: in WebM, a VP9 track's alpha is a second VP9 stream, one frame of it in
/// each block's BlockAdditional, which the built-in decoder decodes beside the
/// picture.
///
/// Read more: [Bringing a codec](https://goldberry.dev/docs/components/media.html#bringing-a-codec).
public final class Packet implements AutoCloseable {

    /// Marks a timestamp the container did not give.
    public static final long NO_TIMESTAMP = Long.MIN_VALUE;

    private final MemorySegment data;
    private final int streamIndex;
    private final long pts;
    private final long dts;
    private final long duration;
    private final boolean keyframe;
    private final Rational timeBase;
    private final MemorySegment alpha;
    private final AtomicReference<@Nullable Runnable> release;

    private Packet(
            MemorySegment data,
            MemorySegment alpha,
            int streamIndex,
            long pts,
            long dts,
            long duration,
            boolean keyframe,
            Rational timeBase,
            @Nullable Runnable release) {
        this.data = Objects.requireNonNull(data, "data");
        this.alpha = Objects.requireNonNull(alpha, "alpha");
        this.streamIndex = streamIndex;
        this.pts = pts;
        this.dts = dts;
        this.duration = duration;
        this.keyframe = keyframe;
        this.timeBase = Objects.requireNonNull(timeBase, "timeBase");
        this.release = new AtomicReference<>(release);
    }

    /// A packet over `data`, which the caller keeps alive and frees.
    public static Packet of(
            MemorySegment data,
            int streamIndex,
            long pts,
            long dts,
            long duration,
            boolean keyframe,
            Rational timeBase) {
        return new Packet(data, MemorySegment.NULL, streamIndex, pts, dts, duration, keyframe, timeBase, null);
    }

    /// A packet that frees its memory with `release` when it is closed: what a
    /// demuxer hands over.
    public static Packet owning(
            MemorySegment data,
            int streamIndex,
            long pts,
            long dts,
            long duration,
            boolean keyframe,
            Rational timeBase,
            Runnable release) {
        return new Packet(
                data,
                MemorySegment.NULL,
                streamIndex,
                pts,
                dts,
                duration,
                keyframe,
                timeBase,
                Objects.requireNonNull(release, "release"));
    }

    /// This packet carrying `alpha` as well: the same data, timing and owner.
    ///
    /// What this packet would have freed on [#close()], the packet returned frees
    /// instead, so `alpha` may be memory this packet owns. This packet is spent:
    /// closing it frees nothing.
    public Packet withAlpha(MemorySegment alpha) {
        Objects.requireNonNull(alpha, "alpha");
        return new Packet(data, alpha, streamIndex, pts, dts, duration, keyframe, timeBase, release.getAndSet(null));
    }

    /// The compressed bytes.
    public MemorySegment data() {
        return data;
    }

    /// The alpha of this packet's picture, compressed as a stream of its own in
    /// the picture's codec, or [MemorySegment#NULL] when the packet has none.
    /// It lives as long as [#data()].
    public MemorySegment alpha() {
        return alpha;
    }

    /// The container stream this packet belongs to.
    public int streamIndex() {
        return streamIndex;
    }

    /// The presentation timestamp, in [#timeBase()] ticks.
    public long pts() {
        return pts;
    }

    /// The decoding timestamp, in [#timeBase()] ticks.
    public long dts() {
        return dts;
    }

    /// How long this packet plays, in [#timeBase()] ticks, or 0 when unknown.
    public long duration() {
        return duration;
    }

    /// Whether decoding can start at this packet.
    public boolean keyframe() {
        return keyframe;
    }

    /// The unit the timestamps count.
    public Rational timeBase() {
        return timeBase;
    }

    /// [#pts()] in nanoseconds, or [Frame#NO_PTS].
    public long ptsNanos() {
        return pts == NO_TIMESTAMP ? Frame.NO_PTS : timeBase.toNanos(pts);
    }

    /// Frees the packet's memory, if the packet owns it. Idempotent.
    @Override
    public void close() {
        var action = release.getAndSet(null);
        if (action != null) {
            action.run();
        }
    }
}
