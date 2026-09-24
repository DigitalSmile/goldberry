package io.github.digitalsmile.goldberry.media.platform.macos;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.log.Logs;
import io.github.digitalsmile.goldberry.media.codec.CodecId;
import io.github.digitalsmile.goldberry.media.codec.Decoder;
import io.github.digitalsmile.goldberry.media.codec.DecoderRequest;
import io.github.digitalsmile.goldberry.media.codec.Packet;
import io.github.digitalsmile.goldberry.media.codec.PixelFormat;
import io.github.digitalsmile.goldberry.media.codec.Received;
import io.github.digitalsmile.goldberry.media.codec.TrackParams;
import io.github.digitalsmile.goldberry.media.codec.VideoFrame;

/// One H.264 or HEVC track decoded by a VideoToolbox decompression session.
///
/// **In:** each packet becomes a sample buffer described by the track's format
/// description, which is made from the parameter sets of the container's `avcC`
/// or `hvcC`: the packets are already in the length-prefixed form VideoToolbox
/// reads, so nothing is rewritten. Decoding is synchronous, on the Engine's
/// decode thread.
///
/// **Out:** VideoToolbox calls back with each picture in decoding order. The
/// callback takes a reference and queues it; the decode thread moves the queue
/// into a [ReorderBuffer], which releases pictures in presentation order once
/// the stream's reorder depth is exceeded (`ParameterSets`).
///
/// **Lent, not copied:** the picture handed out is the pixel buffer itself,
/// locked for the CPU, as NV12 or P010, the layouts VideoToolbox produces
/// natively. It stays locked and referenced until the decoder's next call, which
/// is the [io.github.digitalsmile.goldberry.media.codec.Frame] contract.
///
/// **Failures:** a packet the decoder rejects is dropped and logged, as FFmpeg's
/// decoders drop one; a run of them fails the decoder, and the Engine walks its
/// fallback ladder. A session that goes invalid (the Mac slept, or the GPU
/// changed) is replaced, and decoding picks up at the next keyframe.
final class VideoToolboxDecoder implements Decoder {

    private static final Logger LOG = Logs.of(VideoToolboxDecoder.class);

    /// Packets in a row the decoder may reject before it is given up on.
    static final int MAX_CONSECUTIVE_FAILURES = 30;

    /// Packets a replaced session waits for a keyframe before it decodes whatever
    /// comes, in case a container marks none.
    private static final int MAX_KEYFRAME_WAIT = 300;

    private static final MethodHandle OUTPUT;

    static {
        try {
            OUTPUT = MethodHandles.lookup()
                    .findVirtual(
                            VideoToolboxDecoder.class,
                            "output",
                            MethodType.methodType(
                                    void.class,
                                    MemorySegment.class,
                                    MemorySegment.class,
                                    int.class,
                                    int.class,
                                    MemorySegment.class,
                                    MemorySegment.class,
                                    MemorySegment.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /// A decoded picture: a pixel buffer this decoder holds a reference to.
    private record Picture(MemorySegment buffer, long ptsNanos) {}

    private final Frameworks fw;
    private final String codecName;
    private final int codecType;
    private final int displayWidth;
    private final int displayHeight;
    /// Shared: VideoToolbox may call back on a thread of its own.
    private final Arena arena = Arena.ofShared();

    private final MemorySegment callbackRecord;
    private final MemorySegment infoFlags;
    private final ReorderBuffer<Picture> reorder;
    /// Filled by the callback, emptied by the decode thread.
    private final ConcurrentLinkedQueue<Picture> decoded = new ConcurrentLinkedQueue<>();

    private MemorySegment format = MemorySegment.NULL;
    private MemorySegment attributes = MemorySegment.NULL;
    private MemorySegment session = MemorySegment.NULL;
    private @Nullable Picture lent;
    private boolean ending;
    private int keyframeWait;
    private int consecutiveFailures;
    private volatile int lastCallbackStatus = OsStatus.OK;
    private volatile @Nullable Throwable callbackError;

    /// Opens a session for `request`, an H.264 or HEVC video track whose
    /// configuration record read as `configuration`.
    ///
    /// @throws OsStatus.Failure when the system has no decoder for the stream
    @SuppressWarnings("restricted")
    VideoToolboxDecoder(Frameworks fw, DecoderRequest request, ParameterSets.Configuration configuration) {
        this.fw = Objects.requireNonNull(fw, "fw");
        this.codecName = request.codecName();
        this.codecType = request.codec() == CodecId.HEVC ? VideoToolbox.CODEC_HEVC : VideoToolbox.CODEC_H264;
        if (!(request.params() instanceof TrackParams.Video video)) {
            throw new IllegalArgumentException(request.codecName() + " is not a video track");
        }
        this.displayWidth = video.width();
        this.displayHeight = video.height();
        var shape = configuration.shape();
        this.reorder = new ReorderBuffer<>(shape.reorderDepth(), Picture::ptsNanos);
        this.infoFlags = arena.allocate(JAVA_INT);
        try {
            var stub = Linker.nativeLinker().upcallStub(OUTPUT.bindTo(this), VideoToolbox.OUTPUT_CALLBACK, arena);
            this.callbackRecord = arena.allocate(VideoToolbox.OUTPUT_CALLBACK_RECORD);
            callbackRecord.set(ADDRESS, 0, stub);
            this.format = fw.cm()
                    .videoFormatDescription(
                            codecType == VideoToolbox.CODEC_HEVC,
                            configuration.parameterSets(),
                            configuration.nalLengthSize());
            this.attributes = imageAttributes(shape.bitDepth() > 8);
            this.session = fw.vt().createSession(format, attributes, callbackRecord);
        } catch (RuntimeException e) {
            close();
            throw e;
        }
        LOG.debug(
                "VideoToolbox opened {} at {}×{}, holding {} pictures back, {}",
                codecName,
                displayWidth,
                displayHeight,
                shape.reorderDepth(),
                fw.vt().hardwareDecodes(codecType) ? "on the media engine" : "in software");
    }

    @Override
    public boolean send(Packet packet) {
        throwIfCallbackFailed();
        if (keyframeWait > 0) {
            if (!packet.keyframe()) {
                keyframeWait--;
                return true;
            }
            keyframeWait = 0;
        }
        var status = decode(packet);
        if (status == OsStatus.VT_INVALID_SESSION) {
            LOG.info("the VideoToolbox session for {} went invalid; opening a new one", codecName);
            replaceSession();
            if (packet.keyframe()) {
                status = decode(packet);
            } else {
                keyframeWait = MAX_KEYFRAME_WAIT;
                status = OsStatus.OK;
            }
        }
        var callbackStatus = lastCallbackStatus;
        lastCallbackStatus = OsStatus.OK;
        if (status == OsStatus.OK && callbackStatus != OsStatus.OK) {
            status = callbackStatus;
        }
        if (status == OsStatus.OK) {
            consecutiveFailures = 0;
        } else if (++consecutiveFailures > MAX_CONSECUTIVE_FAILURES) {
            throw new OsStatus.Failure(
                    "VideoToolbox rejected " + consecutiveFailures + " packets of " + codecName + " in a row; the last",
                    status);
        } else {
            LOG.debug("VideoToolbox dropped a packet of {}: {}", codecName, OsStatus.describe(status));
        }
        collect();
        return true;
    }

    @Override
    public void sendEnd() {
        throwIfCallbackFailed();
        fw.vt().finish(session);
        collect();
        ending = true;
    }

    @Override
    public Received receive() {
        releaseLent();
        throwIfCallbackFailed();
        var next = reorder.poll();
        if (next == null && ending) {
            next = reorder.drain();
        }
        if (next != null) {
            return new Received.Decoded(lend(next));
        }
        return ending ? Received.ENDED : Received.NEEDS_INPUT;
    }

    @Override
    public void flush() {
        releaseLent();
        // Whatever the session still holds comes out now, and goes with the rest.
        fw.vt().finish(session);
        collect();
        releaseAll(reorder.clear());
        ending = false;
        keyframeWait = 0;
        consecutiveFailures = 0;
    }

    @Override
    public void close() {
        releaseLent();
        if (!session.equals(MemorySegment.NULL)) {
            fw.vt().invalidate(session);
            fw.cf().release(session);
            session = MemorySegment.NULL;
        }
        // Invalidated: no callback runs from here on, so the stub can go.
        collect();
        releaseAll(reorder.clear());
        fw.cf().release(attributes);
        attributes = MemorySegment.NULL;
        fw.cf().release(format);
        format = MemorySegment.NULL;
        if (arena.scope().isAlive()) {
            arena.close();
        }
    }

    /// The decompression session's output callback. Runs on the thread that
    /// decodes, or on one of VideoToolbox's; nothing may be thrown back into
    /// native code, so everything is caught and rethrown on the decode thread.
    @SuppressWarnings({"unused", "PMD.AvoidCatchingThrowable"}) // Called through OUTPUT.
    private void output(
            MemorySegment refCon,
            MemorySegment frameRefCon,
            int status,
            int flags,
            MemorySegment imageBuffer,
            MemorySegment pts,
            MemorySegment duration) {
        try {
            if (status != OsStatus.OK) {
                lastCallbackStatus = status;
                return;
            }
            if ((flags & VideoToolbox.INFO_FRAME_DROPPED) != 0 || imageBuffer.equals(MemorySegment.NULL)) {
                return;
            }
            fw.cv().retain(imageBuffer);
            decoded.add(new Picture(imageBuffer, CoreMedia.nanos(pts)));
        } catch (Throwable t) {
            callbackError = t;
        }
    }

    private int decode(Packet packet) {
        var sample = fw.cm().sampleBuffer(packet.data(), format, packet.ptsNanos());
        try {
            return fw.vt().decode(session, sample, infoFlags);
        } finally {
            fw.cf().release(sample);
        }
    }

    private void replaceSession() {
        fw.vt().invalidate(session);
        fw.cf().release(session);
        session = MemorySegment.NULL;
        collect();
        releaseAll(reorder.clear());
        session = fw.vt().createSession(format, attributes, callbackRecord);
    }

    /// Moves what the callback queued into the reorder buffer, in the order it
    /// arrived: decoding order.
    private void collect() {
        for (var picture = decoded.poll(); picture != null; picture = decoded.poll()) {
            reorder.add(picture);
        }
    }

    /// Locks `picture` and describes its planes. The picture is [#lent] from here
    /// until [#releaseLent], whether or not describing it succeeds.
    @SuppressWarnings("restricted")
    private VideoFrame lend(Picture picture) {
        lent = picture;
        var cv = fw.cv();
        var buffer = picture.buffer();
        cv.lock(buffer);
        var pixelFormat = cv.pixelFormat(buffer);
        PixelFormat layout;
        boolean fullRange;
        if (pixelFormat == CoreVideo.NV12_VIDEO_RANGE || pixelFormat == CoreVideo.NV12_FULL_RANGE) {
            layout = PixelFormat.NV12;
            fullRange = pixelFormat == CoreVideo.NV12_FULL_RANGE;
        } else if (pixelFormat == CoreVideo.P010_VIDEO_RANGE || pixelFormat == CoreVideo.P010_FULL_RANGE) {
            layout = PixelFormat.P010;
            fullRange = pixelFormat == CoreVideo.P010_FULL_RANGE;
        } else {
            throw new IllegalStateException("VideoToolbox decoded " + codecName + " to '" + OsStatus.fourCc(pixelFormat)
                    + "', which is neither NV12 nor P010");
        }
        // The buffer is the cropped picture; a track's own size, where it has
        // one, is the most that is shown.
        var width = visible(cv.width(buffer), displayWidth);
        var height = visible(cv.height(buffer), displayHeight);
        var planes = new ArrayList<MemorySegment>(2);
        var strides = new ArrayList<Integer>(2);
        for (var plane = 0; plane < 2; plane++) {
            var stride = cv.bytesPerRow(buffer, plane);
            var rows = cv.planeHeight(buffer, plane);
            planes.add(cv.planeAddress(buffer, plane).reinterpret((long) stride * rows));
            strides.add(stride);
        }
        var matrix =
                switch (cv.matrix(buffer)) {
                    case BT601 -> VideoFrame.ColorMatrix.BT601;
                    case BT709 -> VideoFrame.ColorMatrix.BT709;
                    case BT2020 -> VideoFrame.ColorMatrix.BT2020;
                    // What the built-in decoder assumes of an untagged picture.
                    case UNSPECIFIED -> height >= 720 ? VideoFrame.ColorMatrix.BT709 : VideoFrame.ColorMatrix.BT601;
                };
        return new VideoFrame(layout, width, height, planes, strides, matrix, fullRange, picture.ptsNanos());
    }

    private void releaseLent() {
        var picture = lent;
        if (picture == null) {
            return;
        }
        lent = null;
        try {
            fw.cv().unlock(picture.buffer());
        } catch (OsStatus.Failure e) {
            // Never locked: describing it failed first. The reference still goes.
            LOG.trace("unlocking a picture that was not locked", e);
        } finally {
            fw.cv().release(picture.buffer());
        }
    }

    private void releaseAll(List<Picture> pictures) {
        for (var picture : pictures) {
            fw.cv().release(picture.buffer());
        }
    }

    private void throwIfCallbackFailed() {
        var error = callbackError;
        if (error != null) {
            throw new IllegalStateException("VideoToolbox's callback failed for " + codecName, error);
        }
    }

    /// The pixel buffers asked for: NV12 for 8-bit, P010 for 10-bit, each in
    /// either range, so VideoToolbox keeps the range the stream was coded in
    /// rather than converting it.
    private MemorySegment imageAttributes(boolean tenBit) {
        var cf = fw.cf();
        var video = cf.number(tenBit ? CoreVideo.P010_VIDEO_RANGE : CoreVideo.NV12_VIDEO_RANGE);
        var full = cf.number(tenBit ? CoreVideo.P010_FULL_RANGE : CoreVideo.NV12_FULL_RANGE);
        var formats = MemorySegment.NULL;
        try {
            formats = cf.array(video, full);
            return cf.dictionary(new MemorySegment[] {fw.cv().pixelFormatTypeKey}, new MemorySegment[] {formats});
        } finally {
            cf.release(formats);
            cf.release(full);
            cf.release(video);
        }
    }

    private static int visible(int decoded, int track) {
        return track > 0 ? Math.min(decoded, track) : decoded;
    }
}
