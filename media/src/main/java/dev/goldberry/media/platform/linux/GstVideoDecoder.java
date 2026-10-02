package dev.goldberry.media.platform.linux;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.Frame;
import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.codec.TrackParams;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.platform.bitstream.ParameterSets;

/// One H.264 or HEVC track decoded by GStreamer.
///
/// Pictures come out in whichever of the frame contract's four layouts the
/// decoder produced: I420 or I010 from a software decoder such as `avdec_h264`,
/// NV12 or P010 from a hardware one. `videoconvert` passes those through
/// untouched and converts anything else. The planes are where the caps' default
/// layout puts them: `appsink` does not offer `GstVideoMeta`, so a decoder that
/// decodes into padded buffers copies into that layout before handing them on.
final class GstVideoDecoder extends GstDecoder {

    /// A picture's layout, read from its caps: the same for every picture until
    /// the stream changes.
    ///
    /// @param format    the frame contract's layout
    /// @param width     the visible width
    /// @param height    the visible height
    /// @param offsets   where each plane starts in the buffer
    /// @param strides   bytes from one row of each plane to the next
    /// @param matrix    the YUV→RGB matrix
    /// @param fullRange whether luma spans 0–255
    record Layout(
            PixelFormat format,
            int width,
            int height,
            List<Long> offsets,
            List<Integer> strides,
            VideoFrame.ColorMatrix matrix,
            boolean fullRange) {

        Layout {
            offsets = List.copyOf(offsets);
            strides = List.copyOf(strides);
        }
    }

    private final int trackWidth;
    private final int trackHeight;
    private final ParameterSets.Signal signal;
    private final MemorySegment info;
    /// The caps [#layout] was read from, compared by address: a decoder hands out
    /// the same caps object until the stream changes.
    private MemorySegment layoutCaps = MemorySegment.NULL;
    private @Nullable Layout layout;

    GstVideoDecoder(GStreamer gs, GstCodec codec, DecoderRequest request, List<Gst.Candidate> candidates) {
        this(gs, codec, request, candidates, MAX_QUEUED);
    }

    /// One that queues at most `maxQueued` packets on `appsrc`.
    GstVideoDecoder(
            GStreamer gs, GstCodec codec, DecoderRequest request, List<Gst.Candidate> candidates, int maxQueued) {
        // Checked before any pipeline is built (JEP 513), so a wrong track costs
        // nothing to refuse.
        if (!(request.params() instanceof TrackParams.Video video)) {
            throw new IllegalArgumentException(request.codecName() + " is not a video track");
        }
        this.trackWidth = video.width();
        this.trackHeight = video.height();
        this.signal = ParameterSets.of(request)
                .map(configuration -> configuration.shape().signal())
                .orElse(ParameterSets.Signal.UNSPECIFIED);
        super(gs, codec, request, candidates, maxQueued);
        this.info = arena.allocate(GstLayout.VIDEO_INFO);
    }

    @Override
    Frame describe(MemorySegment sample, MemorySegment buffer, MemorySegment data) {
        var caps = gs.gst().sampleCaps(sample);
        var layout = this.layout;
        if (layout == null || !caps.equals(layoutCaps)) {
            layout = read(caps);
            this.layout = layout;
            layoutCaps = caps;
        }
        var planes = new ArrayList<MemorySegment>(layout.offsets().size());
        for (var offset : layout.offsets()) {
            planes.add(data.asSlice(offset));
        }
        return new VideoFrame(
                layout.format(),
                layout.width(),
                layout.height(),
                planes,
                layout.strides(),
                layout.matrix(),
                layout.fullRange(),
                ptsNanos(buffer));
    }

    private Layout read(MemorySegment caps) {
        var name = gs.gst().capsString(caps, "format").orElse("");
        var format = format(name)
                .orElseThrow(() -> new GstException("GStreamer's " + decoder() + " decoded " + codecName + " to '"
                        + name + "', which the frame contract has no layout for"));
        gs.video().infoFromCaps(info, caps);
        var offsets = new ArrayList<Long>(format.planes());
        var strides = new ArrayList<Integer>(format.planes());
        for (var plane = 0; plane < format.planes(); plane++) {
            offsets.add(info.get(JAVA_LONG, GstLayout.VIDEO_INFO_OFFSET + 8L * plane));
            strides.add(info.get(JAVA_INT, GstLayout.VIDEO_INFO_STRIDE + 4L * plane));
        }
        var width = visible(info.get(JAVA_INT, GstLayout.VIDEO_INFO_WIDTH), trackWidth);
        var height = visible(info.get(JAVA_INT, GstLayout.VIDEO_INFO_HEIGHT), trackHeight);
        return new Layout(
                format,
                width,
                height,
                offsets,
                strides,
                matrix(signal, info.get(JAVA_INT, GstLayout.VIDEO_INFO_MATRIX), height),
                fullRange(signal, info.get(JAVA_INT, GstLayout.VIDEO_INFO_RANGE)));
    }

    /// The frame contract's layout for GStreamer's format `name`, or empty for
    /// one it has none for.
    static Optional<PixelFormat> format(String name) {
        return switch (name) {
            case "NV12" -> Optional.of(PixelFormat.NV12);
            case "I420" -> Optional.of(PixelFormat.I420);
            case "P010_10LE" -> Optional.of(PixelFormat.P010);
            case "I420_10LE" -> Optional.of(PixelFormat.I010);
            default -> Optional.empty();
        };
    }

    /// The picture's matrix: what the stream signals, else what GStreamer's caps
    /// say (`GstVideoColorMatrix` `gstMatrix`), else the built-in decoder's
    /// default, BT.709 from 720 rows up and BT.601 below.
    static VideoFrame.ColorMatrix matrix(ParameterSets.Signal signal, int gstMatrix, int height) {
        return switch (signal.matrixCoefficients()) {
            case 1 -> VideoFrame.ColorMatrix.BT709;
            case 5, 6 -> VideoFrame.ColorMatrix.BT601;
            case 9, 10 -> VideoFrame.ColorMatrix.BT2020;
            default ->
                switch (gstMatrix) {
                    case GstLayout.MATRIX_BT709 -> VideoFrame.ColorMatrix.BT709;
                    case GstLayout.MATRIX_BT601 -> VideoFrame.ColorMatrix.BT601;
                    case GstLayout.MATRIX_BT2020 -> VideoFrame.ColorMatrix.BT2020;
                    default -> height >= 720 ? VideoFrame.ColorMatrix.BT709 : VideoFrame.ColorMatrix.BT601;
                };
        };
    }

    /// Whether the picture is full range: what the stream signals, else what
    /// GStreamer's caps say (`GstVideoColorRange` `gstRange`), else limited.
    ///
    /// The stream first, because not every decoder passes the flag on: `nvh264dec`
    /// and `openh264dec` report a full-range H.264 stream as limited.
    static boolean fullRange(ParameterSets.Signal signal, int gstRange) {
        return switch (signal.range()) {
            case FULL -> true;
            case LIMITED -> false;
            case UNSPECIFIED -> gstRange == GstLayout.RANGE_FULL;
        };
    }

    /// What is shown of a decoded dimension: a track's own size, where it has one,
    /// is the most.
    static int visible(int decoded, int track) {
        return track > 0 ? Math.min(decoded, track) : decoded;
    }
}
