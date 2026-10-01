package dev.goldberry.media.platform.windows;

import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.Frame;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.codec.Received;
import dev.goldberry.media.codec.TrackParams;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.platform.bitstream.AnnexB;
import dev.goldberry.media.platform.bitstream.ParameterSets;

/// One H.264 or HEVC track decoded by a Media Foundation decoder MFT.
///
/// **In:** each packet is rewritten from the container's length-prefixed form
/// into the Annex B byte stream the Microsoft decoders read, with the
/// configuration record's parameter sets before each keyframe
/// ([AnnexB]), into a new input sample timed in 100-nanosecond units.
///
/// **Out:** the decoders reorder internally and hand pictures over in
/// presentation order, each carrying its input's time, so there is no reorder
/// buffer here. The output type is NV12 for 8-bit streams and P010 for 10-bit,
/// chosen from the types the MFT offers, and chosen again whenever it signals a
/// stream change, as it does once it has read the first parameter sets and
/// knows the size.
///
/// **Lent, not copied:** the picture handed out is the output buffer itself,
/// locked, its two planes found by [PictureLayout]. It stays locked until the
/// decoder's next call, which is the
/// [dev.goldberry.media.codec.Frame] contract.
///
/// **Failures:** a packet the decoder rejects is dropped and logged, as FFmpeg's
/// decoders drop one; a run of them fails the decoder, and the Engine walks its
/// fallback ladder.
final class MediaFoundationVideoDecoder implements Decoder {

    private static final Logger LOG = Logs.of(MediaFoundationVideoDecoder.class);

    /// Packets in a row the decoder may reject before it is given up on.
    static final int MAX_CONSECUTIVE_FAILURES = 30;

    /// Output type changes in a row without a picture before the decoder is
    /// taken to be going round in circles.
    private static final int MAX_TYPE_CHANGES = 4;

    /// The output type set, as read.
    ///
    /// @param frameWidth    the buffer's width, `MF_MT_FRAME_SIZE`
    /// @param frameHeight   the buffer's height, `MF_MT_FRAME_SIZE`
    /// @param defaultStride `MF_MT_DEFAULT_STRIDE`, where set
    /// @param aperture      `MF_MT_MINIMUM_DISPLAY_APERTURE`, where set
    /// @param matrix        `MF_MT_YUV_MATRIX`, where set
    /// @param range         `MF_MT_VIDEO_NOMINAL_RANGE`, where set
    private record OutputFormat(
            int frameWidth,
            int frameHeight,
            OptionalInt defaultStride,
            Optional<PictureLayout.Area> aperture,
            OptionalInt matrix,
            OptionalInt range) {}

    private final MediaFoundation mf;
    private final String codecName;
    private final int trackWidth;
    private final int trackHeight;
    private final List<byte[]> parameterSets;
    private final int nalLengthSize;
    private final PixelFormat pixelFormat;
    private final Guid outputSubtype;
    private final int bytesPerSample;
    private final Transform transform;

    private OutputFormat format;
    /// The picture lent out: the MFT's sample (`NULL` when it is the transform's
    /// own), its buffer, and the buffer's 2D interface when it has one.
    private MemorySegment lentSample = MemorySegment.NULL;
    private MemorySegment lentBuffer = MemorySegment.NULL;
    private MemorySegment lentBuffer2d = MemorySegment.NULL;
    private boolean lentLocked;
    private boolean ending;
    private boolean ended;
    private int consecutiveFailures;

    /// Opens a decoder for `request`, an H.264 or HEVC video track whose
    /// configuration record read as `configuration`.
    ///
    /// @throws IllegalStateException when the system has no decoder that takes
    ///                               the stream
    MediaFoundationVideoDecoder(MediaFoundation mf, DecoderRequest request, ParameterSets.Configuration configuration) {
        this.mf = Objects.requireNonNull(mf, "mf");
        this.codecName = request.codecName();
        if (!(request.params() instanceof TrackParams.Video video)) {
            throw new IllegalArgumentException(request.codecName() + " is not a video track");
        }
        this.trackWidth = video.width();
        this.trackHeight = video.height();
        this.parameterSets = configuration.parameterSets();
        this.nalLengthSize = configuration.nalLengthSize();
        var tenBit = configuration.shape().bitDepth() > 8;
        this.pixelFormat = tenBit ? PixelFormat.P010 : PixelFormat.NV12;
        this.outputSubtype = tenBit ? MfGuids.MFVideoFormat_P010 : MfGuids.MFVideoFormat_NV12;
        this.bytesPerSample = tenBit ? 2 : 1;
        this.format = new OutputFormat(
                trackWidth,
                trackHeight,
                OptionalInt.empty(),
                Optional.empty(),
                OptionalInt.empty(),
                OptionalInt.empty());

        mf.enterThread();
        var subtype = MediaFoundationVideoProvider.subtype(request.codec());
        var inputType = mf.mfplat().createMediaType();
        try {
            MfAttributes.setGuid(inputType, MfGuids.MF_MT_MAJOR_TYPE, MfGuids.MFMediaType_Video);
            MfAttributes.setGuid(inputType, MfGuids.MF_MT_SUBTYPE, subtype);
            MfAttributes.setUint64(
                    inputType, MfGuids.MF_MT_FRAME_SIZE, PictureLayout.frameSize(trackWidth, trackHeight));
            // What Chromium tells the decoder of a stream it has not read yet.
            MfAttributes.setUint32(inputType, MfGuids.MF_MT_INTERLACE_MODE, MfGuids.INTERLACE_MIXED);
            this.transform = Transform.open(
                    mf,
                    MfGuids.MFT_CATEGORY_VIDEO_DECODER,
                    MfGuids.MFMediaType_Video,
                    subtype,
                    inputType,
                    request.codec() == CodecId.HEVC ? "HEVC" : "H.264");
        } finally {
            Com.release(inputType);
        }
        try {
            chooseOutputType();
            transform.begin();
        } catch (RuntimeException e) {
            transform.close();
            throw e;
        }
        LOG.debug("Media Foundation opened {} at {}×{} as {}", codecName, trackWidth, trackHeight, pixelFormat);
    }

    @Override
    public boolean send(Packet packet) {
        mf.enterThread();
        releaseLent();
        MemorySegment sample;
        try {
            var data = packet.data();
            var keyframe = packet.keyframe();
            sample = transform.inputSample(
                    AnnexB.size(data, nalLengthSize, parameterSets, keyframe),
                    target -> AnnexB.rewrite(data, nalLengthSize, parameterSets, keyframe, target),
                    packet.ptsNanos());
        } catch (IllegalArgumentException e) {
            // A length that runs past the packet: nothing a decoder could read.
            rejected(HResult.E_INVALIDARG, e.getMessage());
            return true;
        }
        int hr;
        try {
            hr = transform.input(sample);
        } finally {
            Com.release(sample);
        }
        if (hr == HResult.MF_E_NOTACCEPTING) {
            return false;
        }
        if (HResult.failed(hr)) {
            rejected(hr, null);
        } else {
            consecutiveFailures = 0;
        }
        return true;
    }

    @Override
    public void sendEnd() {
        mf.enterThread();
        releaseLent();
        if (!ending) {
            transform.message(MfTransform.MESSAGE_NOTIFY_END_OF_STREAM);
            transform.message(MfTransform.MESSAGE_COMMAND_DRAIN);
            ending = true;
        }
    }

    @Override
    public Received receive() {
        mf.enterThread();
        releaseLent();
        if (ended) {
            return Received.ENDED;
        }
        for (var changes = 0; ; ) {
            switch (transform.output(minimumOutputBytes())) {
                case Transform.Output.Produced(var sample, var own) -> {
                    return new Received.Decoded(lend(sample, own));
                }
                case Transform.Output.NeedsInput _ -> {
                    if (ending) {
                        ended = true;
                        return Received.ENDED;
                    }
                    return Received.NEEDS_INPUT;
                }
                case Transform.Output.TypeChanged _ -> {
                    if (++changes > MAX_TYPE_CHANGES) {
                        throw new IllegalStateException("Media Foundation changed the output type of " + codecName + " "
                                + changes + " times without a picture");
                    }
                    chooseOutputType();
                }
                case Transform.Output.Failed(var hr) -> {
                    if (ending) {
                        throw new HResult.Failure("IMFTransform::ProcessOutput draining " + codecName, hr);
                    }
                    rejected(hr, null);
                    return Received.NEEDS_INPUT;
                }
            }
        }
    }

    @Override
    public void flush() {
        mf.enterThread();
        releaseLent();
        transform.message(MfTransform.MESSAGE_COMMAND_FLUSH);
        ending = false;
        ended = false;
        consecutiveFailures = 0;
    }

    @Override
    public void close() {
        mf.enterThread();
        try {
            releaseLent();
        } finally {
            transform.close();
        }
    }

    /// Picks NV12 or P010 from the types the MFT offers, sets it, and reads what
    /// the output type then says of the pictures.
    private void chooseOutputType() {
        var type = transform
                .findOutputType(offered -> MfAttributes.getGuid(offered, MfGuids.MF_MT_SUBTYPE)
                        .filter(outputSubtype::equals)
                        .isPresent())
                .orElseThrow(() -> new IllegalStateException("Media Foundation's " + transform.description()
                        + " decoder offers no " + pixelFormat + " output"));
        try {
            transform.setOutputType(type);
        } finally {
            Com.release(type);
        }
        var current = transform.outputType();
        try {
            var size = MfAttributes.getUint64(current, MfGuids.MF_MT_FRAME_SIZE);
            var width = size.isPresent() ? PictureLayout.frameWidth(size.getAsLong()) : trackWidth;
            var height = size.isPresent() ? PictureLayout.frameHeight(size.getAsLong()) : trackHeight;
            format = new OutputFormat(
                    width,
                    height,
                    MfAttributes.getUint32(current, MfGuids.MF_MT_DEFAULT_STRIDE),
                    MfAttributes.getBlob(current, MfGuids.MF_MT_MINIMUM_DISPLAY_APERTURE)
                            .flatMap(PictureLayout::aperture),
                    MfAttributes.getUint32(current, MfGuids.MF_MT_YUV_MATRIX),
                    MfAttributes.getUint32(current, MfGuids.MF_MT_VIDEO_NOMINAL_RANGE));
        } finally {
            Com.release(current);
        }
        LOG.debug("Media Foundation decodes {} to {} in {}", codecName, pixelFormat, format);
    }

    /// A buffer for a whole frame, with its rows and height padded to 16, for an
    /// MFT that does not say what it needs.
    private long minimumOutputBytes() {
        var stride = (long) ((format.frameWidth() + 15) & ~15) * bytesPerSample;
        return PictureLayout.twoPlaneBytes((int) stride, (format.frameHeight() + 15) & ~15);
    }

    /// Locks the output sample's buffer and describes its planes. The picture is
    /// lent from here until [#releaseLent], whether or not describing it
    /// succeeds.
    @SuppressWarnings("restricted")
    private VideoFrame lend(MemorySegment sample, boolean own) {
        lentSample = own ? MemorySegment.NULL : sample;
        lentBuffer = MfSample.contiguousBuffer(sample);
        lentBuffer2d = Com.queryInterface(lentBuffer, MfGuids.IID_IMF2DBuffer);
        MemorySegment base;
        OptionalInt pitch;
        long length;
        if (!lentBuffer2d.equals(MemorySegment.NULL)) {
            var locked = MfBuffer.lock2D(lentBuffer2d);
            lentLocked = true;
            base = locked.scanline0();
            pitch = OptionalInt.of(locked.pitch());
            length = -1;
        } else {
            var locked = MfBuffer.lock(lentBuffer);
            lentLocked = true;
            base = locked.data();
            pitch = OptionalInt.empty();
            length = locked.currentLength();
        }
        var stride = PictureLayout.stride(pitch, format.defaultStride(), format.frameWidth(), bytesPerSample);
        var rows = PictureLayout.bufferRows(format.frameHeight(), stride, length);
        var visible = PictureLayout.visible(
                format.aperture(), format.frameWidth(), format.frameHeight(), trackWidth, trackHeight);
        var planes = PictureLayout.planes(visible, stride, rows, bytesPerSample);
        if (length >= 0 && planes.end() > length) {
            throw new IllegalStateException("Media Foundation decoded " + codecName + " into " + length
                    + " bytes, and a " + format.frameWidth() + "×" + rows + " " + pixelFormat + " picture at stride "
                    + stride + " needs " + planes.end());
        }
        var whole = base.reinterpret(planes.end());
        var luma = whole.asSlice(planes.lumaOffset(), planes.lumaSize());
        var chroma = whole.asSlice(planes.chromaOffset(), planes.chromaSize());
        var time = MfSample.time(sample);
        var pts = time.isPresent() ? Transform.fromHundredNanos(time.getAsLong()) : Frame.NO_PTS;
        return new VideoFrame(
                pixelFormat,
                visible.width(),
                visible.height(),
                List.of(luma, chroma),
                List.of(stride, stride),
                PictureLayout.matrix(format.matrix(), visible.height()),
                PictureLayout.fullRange(format.range()),
                pts);
    }

    private void releaseLent() {
        try {
            if (lentLocked) {
                lentLocked = false;
                if (!lentBuffer2d.equals(MemorySegment.NULL)) {
                    MfBuffer.unlock2D(lentBuffer2d);
                } else {
                    MfBuffer.unlock(lentBuffer);
                }
            }
        } finally {
            Com.release(lentBuffer2d);
            Com.release(lentBuffer);
            Com.release(lentSample);
            lentBuffer2d = MemorySegment.NULL;
            lentBuffer = MemorySegment.NULL;
            lentSample = MemorySegment.NULL;
        }
    }

    /// Counts a packet the decoder could not take, and gives up on a run of
    /// them.
    private void rejected(int hr, @Nullable String detail) {
        if (++consecutiveFailures > MAX_CONSECUTIVE_FAILURES) {
            throw new HResult.Failure(
                    "Media Foundation rejected " + consecutiveFailures + " packets of " + codecName
                            + " in a row; the last",
                    hr);
        }
        LOG.debug(
                "Media Foundation dropped a packet of {}: {}",
                codecName,
                detail != null ? detail : HResult.describe(hr));
    }
}
