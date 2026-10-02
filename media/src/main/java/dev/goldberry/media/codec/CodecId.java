package dev.goldberry.media.codec;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/// A codec, in Goldberry's own vocabulary.
///
/// The Decoder SPI is written in these, so a provider never sees an FFmpeg type.
/// The mapping from FFmpeg goes **by codec name**, the string `avcodec_get_name`
/// returns, and never by `AVCodecID` number. The numbers belong to one FFmpeg
/// major, and the names have not changed in fifteen years.
///
/// The list names every codec the published natives decode, and the patented ones
/// they **deliberately do not** decode. Those are listed so that an
/// `UNSUPPORTED_CODEC` error can call them what they are, and so that a
/// DecoderProvider can claim them. Anything else is [#UNKNOWN], and its FFmpeg
/// name travels beside it on the track.
///
/// Read more: [Bringing a codec](https://goldberry.dev/docs/components/media.html#bringing-a-codec).
public enum CodecId {
    // --- Video, royalty-free: decoded by the published natives ----------------
    VP8("vp8", MediaType.VIDEO, true),
    VP9("vp9", MediaType.VIDEO, true),
    AV1("av1", MediaType.VIDEO, true),

    // --- Audio, royalty-free or patent-expired --------------------------------
    OPUS("opus", MediaType.AUDIO, true),
    VORBIS("vorbis", MediaType.AUDIO, true),
    FLAC("flac", MediaType.AUDIO, true),
    MP3("mp3", MediaType.AUDIO, true),
    PCM_S16LE("pcm_s16le", MediaType.AUDIO, true),
    PCM_S24LE("pcm_s24le", MediaType.AUDIO, true),
    PCM_F32LE("pcm_f32le", MediaType.AUDIO, true),

    // --- Subtitles ------------------------------------------------------------
    SUBRIP("subrip", MediaType.SUBTITLE, true),
    ASS("ass", MediaType.SUBTITLE, true),
    WEBVTT("webvtt", MediaType.SUBTITLE, true),
    MOV_TEXT("mov_text", MediaType.SUBTITLE, true),

    // --- Cover art --------------------------------------------------------------
    // An attached picture is one PNG or JPEG packet. It is decoded by the toolkit's
    // own image decoder rather than by FFmpeg, which is why neither decoder is built.
    PNG("png", MediaType.VIDEO, true),
    MJPEG("mjpeg", MediaType.VIDEO, true),

    // --- Patent-pool codecs: recognised, not built ------------------------------
    H264("h264", MediaType.VIDEO, false),
    HEVC("hevc", MediaType.VIDEO, false),
    MPEG2_VIDEO("mpeg2video", MediaType.VIDEO, false),
    MPEG4("mpeg4", MediaType.VIDEO, false),
    AAC("aac", MediaType.AUDIO, false),
    AC3("ac3", MediaType.AUDIO, false),
    EAC3("eac3", MediaType.AUDIO, false),

    /// A codec this list does not name. Its FFmpeg name is on the track.
    UNKNOWN("", MediaType.DATA, false);

    private static final Map<String, CodecId> BY_NAME = Arrays.stream(values())
            .filter(codec -> codec != UNKNOWN)
            .collect(Collectors.toUnmodifiableMap(CodecId::ffmpegName, Function.identity()));

    private final String ffmpegName;
    private final MediaType type;
    private final boolean royaltyFree;

    CodecId(String ffmpegName, MediaType type, boolean royaltyFree) {
        this.ffmpegName = ffmpegName;
        this.type = type;
        this.royaltyFree = royaltyFree;
    }

    /// The codec called `name` by FFmpeg, or [#UNKNOWN].
    public static CodecId fromFfmpegName(String name) {
        return BY_NAME.getOrDefault(name, UNKNOWN);
    }

    /// FFmpeg's name for this codec, as `avcodec_get_name` spells it. Empty for
    /// [#UNKNOWN].
    public String ffmpegName() {
        return ffmpegName;
    }

    /// The kind of stream this codec carries.
    public MediaType type() {
        return type;
    }

    /// Whether this codec is inside Goldberry's codec policy: royalty-free or
    /// patent-expired, and so something the published natives may decode.
    ///
    /// False for a codec that needs an application's own DecoderProvider.
    public boolean royaltyFree() {
        return royaltyFree;
    }
}
