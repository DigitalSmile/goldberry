package dev.goldberry.media.ffi;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.codec.AudioFrame;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.Frame;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.codec.Rational;
import dev.goldberry.media.codec.Received;
import dev.goldberry.media.codec.VideoFrame;

/// The built-in decoder: one `AVCodecContext`, behind the [Decoder] SPI every
/// provider implements.
///
/// It is the last one the Engine tries for a track, after every
/// [dev.goldberry.media.codec.DecoderProvider]. It is opened
/// from the stream's own `AVCodecParameters`, which carry more than a
/// [dev.goldberry.media.codec.DecoderRequest] does (block
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
/// **Hardware.** Opened with a [Hardware] that is enabled,
/// a video decoder decodes on the platform's device where it can
/// ([HardwareDecoder]), and every picture is copied back to system memory and
/// lent like a software one. On the hardware path a failure is thrown as an
/// [FfmpegException] and not as a [MediaException], so the Engine's fallback
/// ladder reopens the track in software rather than failing the playback. A
/// failure before the first hardware picture also tells the [Hardware] that
/// this device cannot decode this codec.
///
/// **Alpha.** A track whose container says its pictures carry alpha
/// ([dev.goldberry.media.codec.TrackParams.Video#alpha()]) is opened with a
/// second decoder of the same codec, which decodes each packet's
/// [Packet#alpha()]: a stream of its own, as WebM keeps a VP9 sticker's alpha.
/// Its luma is the alpha, and the picture is lent as [PixelFormat#I420A], its
/// colour converted to I420 first when it is anything else. Both decoders run
/// one thread and in software, so a picture and its alpha come out of the same
/// packet together. A picture whose alpha is missing or does not decode is lent
/// opaque, as it would be without the track's flag. FFmpeg's own `vp9` decoder
/// reads no alpha, and libvpx, which does, is not in the build.
///
/// Video decoders are opened with FFmpeg's automatic thread count, one per core,
/// and with one thread on a device, which does the work itself. That changes
/// when frames arrive and never what is in them.
public final class FfmpegDecoder implements Decoder {

    private static final Logger LOG = Logs.of(FfmpegDecoder.class);

    /// Rows of the fallback conversion's planes start on this boundary, which is
    /// what swscale's vector paths like best.
    private static final int ALIGN = 64;

    private final Ffmpeg ffmpeg;
    private final MemorySegment context;
    private final MemorySegment packet;
    private final MemorySegment frame;
    private final Rational timeBase;
    private final boolean video;
    private final @Nullable HardwareDecoder hardware;
    /// The decoder of the alpha stream and the picture it lends, or null for a
    /// track without one.
    private final @Nullable MemorySegment alphaContext;
    private final @Nullable MemorySegment alphaFrame;
    /// Whether an alpha picture the build cannot use has been logged.
    private boolean alphaRefusalLogged;
    /// What [#describe()] answers when the pictures come from the device.
    private final String hardwareName;
    private final Arena arena = Arena.ofShared();
    /// Whether the last picture was decoded on the device, and whether any was.
    /// The first starts true on a device, until a picture says otherwise.
    private boolean hardwareFrames;
    private boolean producedHardwareFrame;
    private @Nullable VideoConverter converter;
    private MemorySegment converted = MemorySegment.NULL;
    private boolean closed;

    private FfmpegDecoder(
            Ffmpeg ffmpeg,
            MemorySegment context,
            MemorySegment packet,
            MemorySegment frame,
            Rational timeBase,
            boolean video,
            @Nullable HardwareDecoder hardware,
            @Nullable MemorySegment alphaContext,
            @Nullable MemorySegment alphaFrame) {
        this.ffmpeg = ffmpeg;
        this.context = context;
        this.packet = packet;
        this.frame = frame;
        this.timeBase = timeBase;
        this.video = video;
        this.hardware = hardware;
        this.alphaContext = alphaContext;
        this.alphaFrame = alphaFrame;
        this.hardwareName = hardware == null ? Decoders.BUILT_IN : describe(hardware.deviceName());
        this.hardwareFrames = hardware != null;
    }

    /// The name a decoder decoding on `deviceName` goes by: `ffmpeg
    /// (videotoolbox)`.
    static String describe(String deviceName) {
        return Decoders.BUILT_IN + " (" + deviceName + ")";
    }

    /// Whether codec `parameters` is video with a hardware path in this build for
    /// one of `policy`'s device types: whether the ladder has a hardware rung for
    /// it. That it failed before does not remove the rung, so the rungs stay put
    /// between the first open and a fallback. The rung then opens in software.
    /// Nothing is opened.
    static boolean hardwareCandidate(Ffmpeg ffmpeg, MemorySegment parameters, Hardware policy) {
        return policy.enabled()
                && AvCodecParametersView.codecType(parameters)
                        == ffmpeg.constants().mediaTypeVideo()
                && HardwareDecoder.choose(ffmpeg, AvCodecParametersView.codecId(parameters), policy, false) != null;
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
        return open(ffmpeg, parameters, timeBase, Hardware.OFF);
    }

    /// Opens a decoder for the stream `parameters` describe, on a device of
    /// `policy`'s when it is video and one opens, and in software otherwise.
    ///
    /// @throws MediaException as [#open(Ffmpeg, MemorySegment, Rational)]
    static FfmpegDecoder open(Ffmpeg ffmpeg, MemorySegment parameters, Rational timeBase, Hardware policy) {
        return open(ffmpeg, parameters, timeBase, policy, false);
    }

    /// Opens a decoder as [#open(Ffmpeg, MemorySegment, Rational, Hardware)] does,
    /// and, for a video track whose pictures carry `alpha` beside them, a second
    /// one for the alpha, both in software whatever `policy` says.
    ///
    /// @throws MediaException as [#open(Ffmpeg, MemorySegment, Rational)]
    static FfmpegDecoder open(
            Ffmpeg ffmpeg, MemorySegment parameters, Rational timeBase, Hardware policy, boolean alpha) {
        var codecId = AvCodecParametersView.codecId(parameters);
        var isVideo = AvCodecParametersView.codecType(parameters)
                == ffmpeg.constants().mediaTypeVideo();
        var withAlpha = isVideo && alpha;
        var choice = isVideo && !withAlpha && policy.enabled()
                ? HardwareDecoder.choose(ffmpeg, codecId, policy, true)
                : null;
        var hardware = choice == null ? null : HardwareDecoder.open(ffmpeg, policy, choice);
        var codec = hardware != null
                ? hardware.codec()
                : ffmpeg.codec().findDecoder().call(codecId);
        if (codec.equals(MemorySegment.NULL)) {
            throw new MediaException(new MediaError.UnsupportedCodec(List.of(ffmpeg.codecName(codecId))));
        }
        var context = ffmpeg.codec().allocContext3().call(codec);
        if (context.equals(MemorySegment.NULL)) {
            if (hardware != null) {
                hardware.close();
            }
            throw new OutOfMemoryError("avcodec_alloc_context3 failed");
        }
        var packet = MemorySegment.NULL;
        var frame = MemorySegment.NULL;
        var alphaContext = MemorySegment.NULL;
        var alphaFrame = MemorySegment.NULL;
        try {
            check(
                    ffmpeg,
                    "avcodec_parameters_to_context",
                    ffmpeg.codec().parametersToContext().call(context, parameters));
            var view = AvCodecContextView.of(context);
            AvCodecContextView.packetTimeBase(view, timeBase);
            if (hardware != null) {
                hardware.attachTo(view);
                AvCodecContextView.threadCount(view, 1);
            } else if (withAlpha) {
                // One thread, so that the picture comes out of the packet that
                // brought it, beside its alpha.
                AvCodecContextView.threadCount(view, 1);
            } else if (isVideo) {
                AvCodecContextView.threadCount(view, 0);
            }
            var opened = ffmpeg.codec().open2().call(context, codec, MemorySegment.NULL);
            if (opened < 0 && hardware != null) {
                hardware.markFailed();
                throw new FfmpegException("avcodec_open2", opened, ffmpeg.describe(opened));
            }
            check(ffmpeg, "avcodec_open2", opened);
            packet = ffmpeg.codec().packetAlloc().call();
            frame = ffmpeg.util().frameAlloc().call();
            if (packet.equals(MemorySegment.NULL) || frame.equals(MemorySegment.NULL)) {
                throw new OutOfMemoryError("av_packet_alloc or av_frame_alloc failed");
            }
            if (withAlpha) {
                alphaContext = openAlpha(ffmpeg, codec, parameters, timeBase);
                alphaFrame = ffmpeg.util().frameAlloc().call();
                if (alphaFrame.equals(MemorySegment.NULL)) {
                    throw new OutOfMemoryError("av_frame_alloc failed");
                }
            }
            return new FfmpegDecoder(
                    ffmpeg,
                    context,
                    AvPacketView.of(packet),
                    AvFrameView.of(frame),
                    timeBase,
                    isVideo,
                    hardware,
                    withAlpha ? alphaContext : null,
                    withAlpha ? AvFrameView.of(alphaFrame) : null);
        } catch (RuntimeException | Error e) {
            Pointers.freeThrough(alphaFrame, ffmpeg.util().frameFree()::call);
            Pointers.freeThrough(alphaContext, ffmpeg.codec().freeContext()::call);
            Pointers.freeThrough(frame, ffmpeg.util().frameFree()::call);
            Pointers.freeThrough(packet, ffmpeg.codec().packetFree()::call);
            Pointers.freeThrough(context, ffmpeg.codec().freeContext()::call);
            // After the context: it may still call the stub while it is freed.
            if (hardware != null) {
                hardware.close();
            }
            throw e;
        }
    }

    /// Opens the second decoder, which decodes a track's alpha stream: the same
    /// codec and parameters as the picture's, one thread, in software.
    private static MemorySegment openAlpha(
            Ffmpeg ffmpeg, MemorySegment codec, MemorySegment parameters, Rational timeBase) {
        var context = ffmpeg.codec().allocContext3().call(codec);
        if (context.equals(MemorySegment.NULL)) {
            throw new OutOfMemoryError("avcodec_alloc_context3 failed");
        }
        try {
            check(
                    ffmpeg,
                    "avcodec_parameters_to_context",
                    ffmpeg.codec().parametersToContext().call(context, parameters));
            var view = AvCodecContextView.of(context);
            AvCodecContextView.packetTimeBase(view, timeBase);
            AvCodecContextView.threadCount(view, 1);
            check(ffmpeg, "avcodec_open2", ffmpeg.codec().open2().call(context, codec, MemorySegment.NULL));
            return context;
        } catch (RuntimeException | Error e) {
            Pointers.freeThrough(context, ffmpeg.codec().freeContext()::call);
            throw e;
        }
    }

    @Override
    public boolean send(Packet input) {
        ensureOpen();
        var result = sendTo(context, input, input.data());
        if (result == ffmpeg.constants().averrorEagain()) {
            return false;
        }
        checkDecode("avcodec_send_packet", result);
        var alphaDecoder = alphaContext;
        if (alphaDecoder != null && !input.alpha().equals(MemorySegment.NULL)) {
            sendAlpha(alphaDecoder, input);
        }
        return true;
    }

    /// Hands `data`, with `input`'s timing, to the decoder `to`, through the
    /// scratch packet.
    private int sendTo(MemorySegment to, Packet input, MemorySegment data) {
        var flags = input.keyframe() ? ffmpeg.constants().pktFlagKey() : 0;
        AvPacketView.set(
                packet,
                data,
                Math.toIntExact(data.byteSize()),
                input.streamIndex(),
                ffmpegTimestamp(input.pts()),
                ffmpegTimestamp(input.dts()),
                input.duration(),
                flags);
        var result = ffmpeg.codec().sendPacket().call(to, packet);
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
        return result;
    }

    /// Decodes `input`'s alpha beside its picture. An alpha picture that was never
    /// taken, because its own picture did not decode, is dropped to make room. A
    /// damaged alpha stream leaves its pictures opaque, and does not stop them.
    private void sendAlpha(MemorySegment alphaDecoder, Packet input) {
        var result = sendTo(alphaDecoder, input, input.alpha());
        if (result == ffmpeg.constants().averrorEagain()) {
            ffmpeg.util().frameUnref().call(Objects.requireNonNull(alphaFrame));
            ffmpeg.codec().receiveFrame().call(alphaDecoder, alphaFrame);
            result = sendTo(alphaDecoder, input, input.alpha());
        }
        if (result < 0 && !alphaRefusalLogged) {
            alphaRefusalLogged = true;
            LOG.debug("the alpha stream did not decode ({}); its pictures are opaque", ffmpeg.describe(result));
        }
    }

    @Override
    public void sendEnd() {
        ensureOpen();
        var result = ffmpeg.codec().sendPacket().call(context, MemorySegment.NULL);
        // EOF: already draining. Sending the end twice is not an error here.
        if (result != ffmpeg.constants().averrorEof()) {
            checkDecode("avcodec_send_packet", result);
        }
        var alphaDecoder = alphaContext;
        if (alphaDecoder != null) {
            // Draining the alpha too; its answer changes nothing about the picture's.
            ffmpeg.codec().sendPacket().call(alphaDecoder, MemorySegment.NULL);
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
        checkDecode("avcodec_receive_frame", result);
        if (!video) {
            return new Received.Decoded(audioFrame());
        }
        var picture = pictureSource();
        var alpha = alphaContext == null ? MemorySegment.NULL : alphaFor(picture);
        return new Received.Decoded(alpha.equals(MemorySegment.NULL) ? videoFrame(picture) : withAlpha(picture, alpha));
    }

    /// The alpha decoder's picture for `picture`: the one it decoded from the same
    /// packet, as an `AVFrame` whose luma is the alpha, or null when it has none
    /// that fits.
    private MemorySegment alphaFor(MemorySegment picture) {
        var alphaDecoder = Objects.requireNonNull(alphaContext);
        var alpha = Objects.requireNonNull(alphaFrame);
        ffmpeg.util().frameUnref().call(alpha);
        if (ffmpeg.codec().receiveFrame().call(alphaDecoder, alpha) < 0) {
            return MemorySegment.NULL;
        }
        var fits = AvFrameView.width(alpha) == AvFrameView.width(picture)
                && AvFrameView.height(alpha) == AvFrameView.height(picture)
                && AvFrameView.pts(alpha) == AvFrameView.pts(picture)
                && AvFrameView.lineSize(alpha, 0) > 0;
        if (!fits) {
            return MemorySegment.NULL;
        }
        if (AvFrameView.format(alpha) != ffmpeg.constants().video().pixFmtYuv420p()) {
            if (!alphaRefusalLogged) {
                alphaRefusalLogged = true;
                LOG.debug(
                        "the alpha stream is {}, and only 8-bit alpha is read; its pictures are opaque",
                        ffmpeg.pixelFormatName(AvFrameView.format(alpha)).orElse("unknown"));
            }
            return MemorySegment.NULL;
        }
        return alpha;
    }

    /// `picture` with `alpha`'s luma as its fourth plane: [PixelFormat#I420A],
    /// the colour lent as it is when it is I420 and converted to I420 otherwise.
    private VideoFrame withAlpha(MemorySegment picture, MemorySegment alpha) {
        var lent = videoFrame(picture);
        var colour = lent.format() == PixelFormat.I420
                ? lent
                : convertedFrame(
                        picture,
                        AvFrameView.format(picture),
                        lent.width(),
                        lent.height(),
                        lent.matrix(),
                        lent.fullRange(),
                        lent.ptsNanos());
        var width = colour.width();
        var height = colour.height();
        var stride = AvFrameView.lineSize(alpha, 0);
        var plane = Pointers.array(
                AvFrameView.data(alpha, 0),
                JAVA_BYTE,
                (long) stride * (PixelFormat.I420A.planeRows(3, height) - 1)
                        + PixelFormat.I420A.planeRowBytes(3, width));
        var planes = new ArrayList<>(colour.planes());
        planes.add(plane);
        var strides = new ArrayList<>(colour.strides());
        strides.add(stride);
        return new VideoFrame(
                PixelFormat.I420A,
                width,
                height,
                planes,
                strides,
                colour.matrix(),
                colour.fullRange(),
                colour.ptsNanos());
    }

    /// What this decoder is: [Decoders#BUILT_IN], or `ffmpeg (videotoolbox)` and
    /// the like while it decodes on a device. It can change after a picture, when
    /// the device declines the stream and FFmpeg decodes it in software instead.
    public String describe() {
        return hardwareFrames ? hardwareName : Decoders.BUILT_IN;
    }

    /// The frame to lend the picture from: the decoded one, or its copy in system
    /// memory when it is a surface on the device.
    private MemorySegment pictureSource() {
        var device = hardware;
        if (device == null) {
            return frame;
        }
        if (device.isHardwareFrame(frame)) {
            var copied = device.copyBack(frame);
            hardwareFrames = true;
            producedHardwareFrame = true;
            return copied;
        }
        // The device declined the stream, and FFmpeg decodes it in software: the
        // next decoder of this codec need not try the device.
        if (!producedHardwareFrame && !device.negotiated()) {
            device.markFailed();
        }
        hardwareFrames = false;
        return frame;
    }

    /// [#check] for the decode calls: on the device a failure is the fallback
    /// ladder's to handle, so it is an [FfmpegException], and one before the first
    /// hardware picture means the device cannot decode this codec.
    private void checkDecode(String function, int result) {
        if (result >= 0) {
            return;
        }
        var device = hardware;
        if (device != null) {
            if (!producedHardwareFrame) {
                device.markFailed();
            }
            throw new FfmpegException(function, result, ffmpeg.describe(result));
        }
        check(ffmpeg, function, result);
    }

    @Override
    public void flush() {
        ensureOpen();
        ffmpeg.util().frameUnref().call(frame);
        ffmpeg.codec().flushBuffers().call(context);
        var alphaDecoder = alphaContext;
        if (alphaDecoder != null) {
            ffmpeg.util().frameUnref().call(Objects.requireNonNull(alphaFrame));
            ffmpeg.codec().flushBuffers().call(alphaDecoder);
        }
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
        if (alphaFrame != null) {
            Pointers.freeThrough(alphaFrame, ffmpeg.util().frameFree()::call);
        }
        if (alphaContext != null) {
            Pointers.freeThrough(alphaContext, ffmpeg.codec().freeContext()::call);
        }
        // After the context, which may call the stub while it is freed.
        if (hardware != null) {
            hardware.close();
        }
        if (converter != null) {
            converter.close();
        }
        arena.close();
    }

    private VideoFrame videoFrame(MemorySegment frame) {
        var video = ffmpeg.constants().video();
        var width = AvFrameView.width(frame);
        var height = AvFrameView.height(frame);
        var avFormat = AvFrameView.format(frame);
        var matrix = video.matrix(AvFrameView.colorSpace(frame))
                .orElse(height >= 720 ? VideoFrame.ColorMatrix.BT709 : VideoFrame.ColorMatrix.BT601);
        var fullRange = AvFrameView.colorRange(frame) == video.rangeJpeg();
        var pts = ptsNanos(frame);
        var format = video.pixelFormat(avFormat);
        if (format.isPresent() && lendable(frame, format.get())) {
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
        return convertedFrame(frame, avFormat, width, height, matrix, fullRange, pts);
    }

    /// Whether the `AVFrame` can be lent as `format`: every plane there, and rows
    /// that run downwards. A negative line size is a bottom-up picture, which the
    /// contract has no way to say.
    private static boolean lendable(MemorySegment frame, PixelFormat format) {
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
            MemorySegment frame,
            int avFormat,
            int width,
            int height,
            VideoFrame.ColorMatrix matrix,
            boolean fullRange,
            long pts) {
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
    private long ptsNanos(MemorySegment frame) {
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
