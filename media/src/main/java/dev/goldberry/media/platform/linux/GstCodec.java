package dev.goldberry.media.platform.linux;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.TrackParams;

/// The five codecs the GStreamer providers decode, and the pipeline each one is
/// decoded by.
///
/// Every pipeline has the same shape:
///
/// ```
/// appsrc (the container's configuration as caps) ! parser ! decoder ! converter ! appsink
/// ```
///
/// - The **caps** give the stream in the form MP4 and Matroska store it:
///   length-prefixed NAL units with the `avcC` or `hvcC` as `codec_data`, raw AAC
///   with its `AudioSpecificConfig`, framed AC-3.
/// - The **parser** turns that into whatever form the decoder the system chose
///   wants: Annex B for `openh264dec`, for example.
/// - The **decoder** is the one GStreamer itself would plug, the highest ranked
///   for the codec (`GStreamer#decoders`): `avdec_h264` from gst-libav, a VA-API
///   decoder where the system has one, `faad`, `a52dec`.
/// - The **converter** passes through what the frame contract takes (NV12, I420,
///   P010, I010; interleaved f32) and converts anything else.
///
/// The distribution holds the licences for what it installs, as Apple does on
/// macOS: the providers ship no codec.
enum GstCodec {
    H264("h264parse", "video/x-h264", true),
    HEVC("h265parse", "video/x-h265", true),
    AAC("aacparse", "audio/mpeg, mpegversion=(int)4", false),
    AC3("ac3parse", "audio/x-ac3", false),
    EAC3("ac3parse", "audio/x-eac3", false);

    /// What `appsink` takes of video: the four layouts of the frame contract.
    static final String VIDEO_SINK_CAPS = "video/x-raw, format=(string){ NV12, I420, P010_10LE, I420_10LE }";

    /// What `appsink` takes of audio: interleaved 32-bit float, in GStreamer's
    /// channel order, which is FFmpeg's.
    static final String AUDIO_SINK_CAPS = "audio/x-raw, format=(string)F32LE, layout=(string)interleaved";

    /// How many decoded buffers `appsink` holds before the pipeline waits for the
    /// decode thread to take one: enough to keep a threaded decoder busy, few
    /// enough that 4K pictures do not pile up.
    static final int VIDEO_SINK_BUFFERS = 4;

    static final int AUDIO_SINK_BUFFERS = 8;

    /// A GStreamer element name: what a pipeline description may name without
    /// quoting.
    private static final Pattern ELEMENT_NAME = Pattern.compile("[A-Za-z0-9_-]+");

    private final String parser;
    private final String decoderCaps;
    private final boolean video;

    GstCodec(String parser, String decoderCaps, boolean video) {
        this.parser = parser;
        this.decoderCaps = decoderCaps;
        this.video = video;
    }

    /// The codec GStreamer decodes `codec` as, or empty for one the providers do
    /// not claim.
    static Optional<GstCodec> of(CodecId codec) {
        return switch (codec) {
            case H264 -> Optional.of(H264);
            case HEVC -> Optional.of(HEVC);
            case AAC -> Optional.of(AAC);
            case AC3 -> Optional.of(AC3);
            case EAC3 -> Optional.of(EAC3);
            default -> Optional.empty();
        };
    }

    /// The parser element between `appsrc` and the decoder.
    String parser() {
        return parser;
    }

    /// The caps a decoder for `request`'s track must take, in any stream format:
    /// what the registry is asked for, since the parser converts between the
    /// formats. A video track's size is part of it, as `decodebin` makes it
    /// part: a hardware decoder advertises the sizes its device decodes, and
    /// NVIDIA's decodes no HEVC under 144×144.
    String decoderCaps(DecoderRequest request) {
        if (request.params() instanceof TrackParams.Video v && v.width() > 0 && v.height() > 0) {
            return decoderCaps + ", width=(int)" + v.width() + ", height=(int)" + v.height();
        }
        return decoderCaps;
    }

    /// Whether this is a video codec.
    boolean video() {
        return video;
    }

    /// The caps `appsrc` announces for `request`'s track: the codec in the form
    /// the demuxer hands it over, with the container's configuration.
    String sourceCaps(DecoderRequest request) {
        var configuration = HexFormat.of().formatHex(request.extradata().toArray(JAVA_BYTE));
        var caps = new StringBuilder();
        switch (this) {
            case H264 -> caps.append("video/x-h264, stream-format=(string)avc, alignment=(string)au");
            case HEVC -> caps.append("video/x-h265, stream-format=(string)hvc1, alignment=(string)au");
            case AAC -> caps.append("audio/mpeg, mpegversion=(int)4, stream-format=(string)raw");
            case AC3 -> caps.append("audio/x-ac3, framed=(boolean)true");
            case EAC3 -> caps.append("audio/x-eac3, framed=(boolean)true");
        }
        if (!configuration.isEmpty()) {
            caps.append(", codec_data=(buffer)").append(configuration);
        }
        switch (request.params()) {
            case TrackParams.Video v
            when v.width() > 0 && v.height() > 0 ->
                caps.append(", width=(int)")
                        .append(v.width())
                        .append(", height=(int)")
                        .append(v.height());
            case TrackParams.Audio a
            when a.sampleRate() > 0 && a.channels() > 0 ->
                caps.append(", rate=(int)")
                        .append(a.sampleRate())
                        .append(", channels=(int)")
                        .append(a.channels());
            default -> {
                // Nothing the parser would not learn from the stream anyway.
            }
        }
        return caps.toString();
    }

    /// The pipeline that decodes `request`'s track with the element `decoder`.
    ///
    /// @throws IllegalArgumentException when `decoder` is not an element name
    String pipeline(DecoderRequest request, String decoder) {
        if (!ELEMENT_NAME.matcher(decoder).matches()) {
            throw new IllegalArgumentException("'" + decoder + "' is not a GStreamer element name");
        }
        var converter = video ? "videoconvert" : "audioconvert";
        var sinkCaps = video ? VIDEO_SINK_CAPS : AUDIO_SINK_CAPS;
        var sinkBuffers = video ? VIDEO_SINK_BUFFERS : AUDIO_SINK_BUFFERS;
        return "appsrc name=src format=time caps=\"" + sourceCaps(request) + "\" ! " + parser + " ! " + decoder + " ! "
                + converter + " ! appsink name=sink sync=false max-buffers=" + sinkBuffers + " caps=\"" + sinkCaps
                + "\"";
    }
}
