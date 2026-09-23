package io.github.digitalsmile.goldberry.media.ffi;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.media.MediaError;
import io.github.digitalsmile.goldberry.media.MediaException;
import io.github.digitalsmile.goldberry.media.codec.AudioFrame;
import io.github.digitalsmile.goldberry.media.codec.Decoder;
import io.github.digitalsmile.goldberry.media.codec.Frame;
import io.github.digitalsmile.goldberry.media.codec.Packet;
import io.github.digitalsmile.goldberry.media.codec.PixelFormat;
import io.github.digitalsmile.goldberry.media.codec.Rational;
import io.github.digitalsmile.goldberry.media.codec.Received;
import io.github.digitalsmile.goldberry.media.codec.VideoFrame;

/// The built-in decoder: one `AVCodecContext`, behind the [Decoder] SPI every
/// provider implements (`docs/goldberry-media.md` §5).
///
/// It is the last one the Engine tries for a track, after every
/// [io.github.digitalsmile.goldberry.media.codec.DecoderProvider]. It is opened
/// from the stream's own `AVCodecParameters`, which carry more than a
/// [io.github.digitalsmile.goldberry.media.codec.DecoderRequest] does (block
/// alignment, bits per coded sample), so it is not a provider itself.
///
/// **Packets are copied in.** [#send] points one scratch `AVPacket` at the
/// packet's bytes with no reference (`buf` is null), and `avcodec_send_packet`
/// copies what it keeps. So the packet can come from any demuxer, and it can be
/// closed as soon as `send` returns.
///
/// **Frames are lent out.** An [AudioFrame]'s planes, and a [VideoFrame]'s, are
/// the `AVFrame`'s own buffers, valid until the next call. That is the zero-copy
/// half the frame contract allows.
///
/// **Pictures outside the contract are converted.** A decoder whose output is
/// one of the [PixelFormat]s is lent as it is: 8-bit and 10-bit 4:2:0, which is
/// what VP8, VP9 and dav1d produce for ordinary content. Anything else (4:2:2,
/// 4:4:4, 12-bit, VP9's RGB profile) is converted to [PixelFormat#I420] into a
/// buffer this decoder owns, so the present path knows four layouts and not every
/// one FFmpeg has. The price is one copy, for content that is rare.
///
/// **Colour.** The matrix and range come from the frame's tags. A frame with no
/// matrix tag is BT.709 from 720 rows up and BT.601 below, the convention every
/// player follows for untagged video.
///
/// Video decoders are opened with FFmpeg's automatic thread count, one per core.
/// That changes when frames arrive and never what is in them.
public final class FfmpegDecoder implements Decoder {

    /// Rows of the fallback conversion's planes start on this boundary, which is
    /// what swscale's vector paths like best.
    private static final int ALIGN = 64;

    private final Ffmpeg ffmpeg;
    private final MemorySegment context;
    private final MemorySegment packet;
    private final MemorySegment frame;
    private final Rational timeBase;
    private final boolean video;
    private final Arena arena = Arena.ofShared();
    private @Nullable VideoConverter converter;
    private MemorySegment converted = MemorySegment.NULL;
    private boolean closed;

    private FfmpegDecoder(
            Ffmpeg ffmpeg,
            MemorySegment context,
            MemorySegment packet,
            MemorySegment frame,
            Rational timeBase,
            boolean video) {
        this.ffmpeg = ffmpeg;
        this.context = context;
        this.packet = packet;
        this.frame = frame;
        this.timeBase = timeBase;
        this.video = video;
    }

    /// Whether this build has a decoder for the codec `parameters` describe.
    static boolean supports(Ffmpeg ffmpeg, MemorySegment parameters) {
        return !ffmpeg.codec()
                .findDecoder()
                .call(AvCodecParametersView.codecId(parameters))
                .equals(MemorySegment.NULL);
    }

    /// Opens a decoder for the stream `parameters` describe, whose packets count
    /// `timeBase` ticks.
    ///
    /// @throws MediaException [MediaError.UnsupportedCodec] when this build has no
    ///                        decoder for it, and [MediaError.InvalidData] when the
    ///                        decoder refuses the parameters
    static FfmpegDecoder open(Ffmpeg ffmpeg, MemorySegment parameters, Rational timeBase) {
        var codecId = AvCodecParametersView.codecId(parameters);
        var codec = ffmpeg.codec().findDecoder().call(codecId);
        if (codec.equals(MemorySegment.NULL)) {
            throw new MediaException(new MediaError.UnsupportedCodec(List.of(ffmpeg.codecName(codecId))));
        }
        var context = ffmpeg.codec().allocContext3().call(codec);
        if (context.equals(MemorySegment.NULL)) {
            throw new OutOfMemoryError("avcodec_alloc_context3 failed");
        }
        var packet = MemorySegment.NULL;
        var frame = MemorySegment.NULL;
        try {
            check(
                    ffmpeg,
                    "avcodec_parameters_to_context",
                    ffmpeg.codec().parametersToContext().call(context, parameters));
            var view = AvCodecContextView.of(context);
            AvCodecContextView.packetTimeBase(view, timeBase);
            var video = AvCodecParametersView.codecType(parameters)
                    == ffmpeg.constants().mediaTypeVideo();
            if (video) {
                AvCodecContextView.threadCount(view, 0);
            }
            check(ffmpeg, "avcodec_open2", ffmpeg.codec().open2().call(context, codec, MemorySegment.NULL));
            packet = ffmpeg.codec().packetAlloc().call();
            frame = ffmpeg.util().frameAlloc().call();
            if (packet.equals(MemorySegment.NULL) || frame.equals(MemorySegment.NULL)) {
                throw new OutOfMemoryError("av_packet_alloc or av_frame_alloc failed");
            }
            return new FfmpegDecoder(
                    ffmpeg,
                    context,
                    AvPacketView.of(packet),
                    AvFrameView.of(frame),
                    timeBase,
                    AvCodecParametersView.codecType(parameters)
                            == ffmpeg.constants().mediaTypeVideo());
        } catch (RuntimeException | Error e) {
            Pointers.freeThrough(frame, ffmpeg.util().frameFree()::call);
            Pointers.freeThrough(packet, ffmpeg.codec().packetFree()::call);
            Pointers.freeThrough(context, ffmpeg.codec().freeContext()::call);
            throw e;
        }
    }

    @Override
    public boolean send(Packet input) {
        ensureOpen();
        var flags = input.keyframe() ? ffmpeg.constants().pktFlagKey() : 0;
        AvPacketView.set(
                packet,
                input.data(),
                Math.toIntExact(input.data().byteSize()),
                input.streamIndex(),
                ffmpegTimestamp(input.pts()),
                ffmpegTimestamp(input.dts()),
                input.duration(),
                flags);
        var result = ffmpeg.codec().sendPacket().call(context, packet);
        // The scratch packet must not keep pointing at memory the caller frees.
        AvPacketView.set(
                packet,
                MemorySegment.NULL,
                0,
                0,
                ffmpeg.constants().noPtsValue(),
                ffmpeg.constants().noPtsValue(),
                0,
                0);
        if (result == ffmpeg.constants().averrorEagain()) {
            return false;
        }
        check(ffmpeg, "avcodec_send_packet", result);
        return true;
    }

    @Override
    public void sendEnd() {
        ensureOpen();
        var result = ffmpeg.codec().sendPacket().call(context, MemorySegment.NULL);
        // EOF: already draining. Sending the end twice is not an error here.
        if (result != ffmpeg.constants().averrorEof()) {
            check(ffmpeg, "avcodec_send_packet", result);
        }
    }

    @Override
    public Received receive() {
        ensureOpen();
        ffmpeg.util().frameUnref().call(frame);
        var result = ffmpeg.codec().receiveFrame().call(context, frame);
        var constants = ffmpeg.constants();
        if (result == constants.averrorEagain()) {
            return Received.NEEDS_INPUT;
        }
        if (result == constants.averrorEof()) {
            return Received.ENDED;
        }
        check(ffmpeg, "avcodec_receive_frame", result);
        return new Received.Decoded(video ? videoFrame() : audioFrame());
    }

    @Override
    public void flush() {
        ensureOpen();
        ffmpeg.util().frameUnref().call(frame);
        ffmpeg.codec().flushBuffers().call(context);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        Pointers.freeThrough(frame, ffmpeg.util().frameFree()::call);
        Pointers.freeThrough(packet, ffmpeg.codec().packetFree()::call);
        Pointers.freeThrough(context, ffmpeg.codec().freeContext()::call);
        if (converter != null) {
            converter.close();
        }
        arena.close();
    }

    private VideoFrame videoFrame() {
        var video = ffmpeg.constants().video();
        var width = AvFrameView.width(frame);
        var height = AvFrameView.height(frame);
        var avFormat = AvFrameView.format(frame);
        var matrix = video.matrix(AvFrameView.colorSpace(frame))
                .orElse(height >= 720 ? VideoFrame.ColorMatrix.BT709 : VideoFrame.ColorMatrix.BT601);
        var fullRange = AvFrameView.colorRange(frame) == video.rangeJpeg();
        var pts = ptsNanos();
        var format = video.pixelFormat(avFormat);
        if (format.isPresent() && lendable(format.get())) {
            var planes = new ArrayList<MemorySegment>(format.get().planes());
            var strides = new ArrayList<Integer>(format.get().planes());
            for (var plane = 0; plane < format.get().planes(); plane++) {
                var stride = AvFrameView.lineSize(frame, plane);
                var size = (long) stride * (format.get().planeRows(plane, height) - 1)
                        + format.get().planeRowBytes(plane, width);
                planes.add(Pointers.array(AvFrameView.data(frame, plane), JAVA_BYTE, size));
                strides.add(stride);
            }
            return new VideoFrame(format.get(), width, height, planes, strides, matrix, fullRange, pts);
        }
        return convertedFrame(avFormat, width, height, matrix, fullRange, pts);
    }

    /// Whether the `AVFrame` can be lent as `format`: every plane there, and rows
    /// that run downwards. A negative line size is a bottom-up picture, which the
    /// contract has no way to say.
    private boolean lendable(PixelFormat format) {
        for (var plane = 0; plane < format.planes(); plane++) {
            if (AvFrameView.lineSize(frame, plane) <= 0
                    || AvFrameView.data(frame, plane).equals(MemorySegment.NULL)) {
                return false;
            }
        }
        return true;
    }

    /// A picture outside the contract, converted to I420 in this decoder's own
    /// buffer, which the next call overwrites exactly as it would a lent frame.
    private VideoFrame convertedFrame(
            int avFormat, int width, int height, VideoFrame.ColorMatrix matrix, boolean fullRange, long pts) {
        if (ffmpeg.pixelFormatName(avFormat).isEmpty()) {
            throw new MediaException(new MediaError.InvalidData(
                    "the decoder produced pixel format #" + avFormat + ", which this build does not know"));
        }
        var format = PixelFormat.I420;
        var strides = new ArrayList<Integer>(3);
        var offsets = new long[3];
        var total = 0L;
        for (var plane = 0; plane < 3; plane++) {
            var stride = align(format.planeRowBytes(plane, width));
            strides.add(stride);
            offsets[plane] = total;
            total += (long) stride * format.planeRows(plane, height);
        }
        if (converted.byteSize() < total) {
            converted = arena.allocate(total, ALIGN);
        }
        var planes = new ArrayList<MemorySegment>(3);
        for (var plane = 0; plane < 3; plane++) {
            planes.add(converted.asSlice(offsets[plane], (long) strides.get(plane) * format.planeRows(plane, height)));
        }
        var sources = new ArrayList<MemorySegment>(4);
        var sourceStrides = new ArrayList<Integer>(4);
        for (var plane = 0; plane < 4; plane++) {
            sources.add(AvFrameView.data(frame, plane));
            sourceStrides.add(AvFrameView.lineSize(frame, plane));
        }
        if (converter == null) {
            converter = new VideoConverter(ffmpeg);
        }
        var video = ffmpeg.constants().video();
        converter.convert(
                width,
                height,
                avFormat,
                sources,
                sourceStrides,
                video.swsColorspace(matrix),
                fullRange,
                video.pixFmtYuv420p(),
                planes,
                strides);
        return new VideoFrame(format, width, height, planes, strides, matrix, fullRange, pts);
    }

    private static int align(int bytes) {
        return (bytes + ALIGN - 1) / ALIGN * ALIGN;
    }

    /// The frame's presentation time, falling back to its packet's decode time
    /// when the decoder left the presentation time unset.
    private long ptsNanos() {
        var noPts = ffmpeg.constants().noPtsValue();
        var pts = AvFrameView.pts(frame);
        if (pts == noPts) {
            pts = AvFrameView.packetDts(frame);
        }
        return pts == noPts ? Frame.NO_PTS : timeBase.toNanos(pts);
    }

    private AudioFrame audioFrame() {
        var constants = ffmpeg.constants();
        var avFormat = AvFrameView.format(frame);
        var format = constants
                .sampleFormat(avFormat)
                .orElseThrow(() -> new MediaException(new MediaError.InvalidData("the decoder produced sample format "
                        + ffmpeg.sampleFormatName(avFormat).orElse("#" + avFormat)
                        + ", which is not audio this engine converts")));
        var channels = AvFrameView.channels(frame);
        var samples = AvFrameView.samples(frame);
        var planes = format.planes(channels);
        if (planes > AvFrameView.DATA_POINTERS) {
            throw new MediaException(new MediaError.InvalidData(
                    channels + " planar channels; more than " + AvFrameView.DATA_POINTERS + " are not played"));
        }
        var planeSize = format.planeSize(channels, samples);
        var data = new ArrayList<MemorySegment>(planes);
        for (var plane = 0; plane < planes; plane++) {
            data.add(Pointers.array(AvFrameView.data(frame, plane), JAVA_BYTE, planeSize));
        }
        var pts = AvFrameView.pts(frame);
        return new AudioFrame(
                format,
                AvFrameView.sampleRate(frame),
                channels,
                samples,
                data,
                pts == constants.noPtsValue() ? Frame.NO_PTS : timeBase.toNanos(pts));
    }

    private long ffmpegTimestamp(long timestamp) {
        return timestamp == Packet.NO_TIMESTAMP ? ffmpeg.constants().noPtsValue() : timestamp;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("decoder closed");
        }
    }

    private static void check(Ffmpeg ffmpeg, String function, int result) {
        if (result < 0) {
            throw new MediaException(
                    new MediaError.InvalidData(function + ": " + ffmpeg.describe(result)),
                    new FfmpegException(function, result, ffmpeg.describe(result)));
        }
    }
}
