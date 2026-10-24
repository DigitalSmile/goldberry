package dev.goldberry.media.container.matroska;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import org.jspecify.annotations.Nullable;

import dev.goldberry.media.bitstream.ParameterSets;
import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.FrameRate;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.codec.TrackParams;

/// Matroska's `CodecID` strings and codec-private records, read into the
/// Decoder SPI's vocabulary: a [CodecId], the name a track goes by, and the
/// [TrackParams] a probe reports.
///
/// The names are FFmpeg's, because [CodecId] is written in them and so is
/// every application that read a track's `codecName` before: `vp9`, `opus`,
/// `h264`. A codec this list does not know keeps its Matroska ID as its name,
/// `V_MS/VFW/FOURCC`, and is [CodecId#UNKNOWN].
public final class MatroskaCodecs {

    private MatroskaCodecs() {}

    /// The codec and the name for a `CodecID`.
    ///
    /// @param codec the codec, or [CodecId#UNKNOWN]
    /// @param name  FFmpeg's name, or the Matroska ID when there is none
    public record Named(CodecId codec, String name) {}

    /// What `codecId` is.
    public static Named of(String codecId) {
        var codec = switch (codecId) {
            case "V_AV1" -> CodecId.AV1;
            case "V_VP9" -> CodecId.VP9;
            case "V_VP8" -> CodecId.VP8;
            case "V_MPEG4/ISO/AVC" -> CodecId.H264;
            case "V_MPEGH/ISO/HEVC" -> CodecId.HEVC;
            case "V_MPEG2" -> CodecId.MPEG2_VIDEO;
            case "V_MPEG4/ISO/ASP", "V_MPEG4/ISO/SP", "V_MPEG4/ISO/AP" -> CodecId.MPEG4;
            case "A_OPUS" -> CodecId.OPUS;
            case "A_VORBIS" -> CodecId.VORBIS;
            case "A_FLAC" -> CodecId.FLAC;
            case "A_MPEG/L3" -> CodecId.MP3;
            case "A_AC3" -> CodecId.AC3;
            case "A_EAC3" -> CodecId.EAC3;
            case "S_TEXT/UTF8" -> CodecId.SUBRIP;
            case "S_TEXT/ASS", "S_TEXT/SSA", "S_ASS", "S_SSA" -> CodecId.ASS;
            case "S_TEXT/WEBVTT" -> CodecId.WEBVTT;
            default -> codecId.startsWith("A_AAC") ? CodecId.AAC : CodecId.UNKNOWN;
        };
        return new Named(codec, codec == CodecId.UNKNOWN ? codecId : codec.ffmpegName());
    }

    /// The PCM codec for `A_PCM/INT/LIT` and `A_PCM/FLOAT/IEEE` at `bitDepth`.
    public static Named pcm(String codecId, int bitDepth) {
        var codec = switch (codecId) {
            case "A_PCM/INT/LIT" ->
                switch (bitDepth) {
                    case 16 -> CodecId.PCM_S16LE;
                    case 24 -> CodecId.PCM_S24LE;
                    default -> CodecId.UNKNOWN;
                };
            case "A_PCM/FLOAT/IEEE" -> bitDepth == 32 ? CodecId.PCM_F32LE : CodecId.UNKNOWN;
            default -> CodecId.UNKNOWN;
        };
        return new Named(codec, codec == CodecId.UNKNOWN ? codecId : codec.ffmpegName());
    }

    /// Whether `codecId` is raw PCM, whose codec depends on the bit depth.
    public static boolean isPcm(String codecId) {
        return codecId.startsWith("A_PCM/");
    }

    /// The parameters of a video track.
    ///
    /// @param defaultDurationNanos `DefaultDuration`, or 0
    public static TrackParams.Video video(
            CodecId codec, int width, int height, byte[] codecPrivate, long defaultDurationNanos, boolean alpha) {
        var pixelFormat = switch (codec) {
            case VP8 -> Optional.of("yuv420p");
            case VP9 -> vp9PixelFormat(codecPrivate);
            case AV1 -> av1PixelFormat(codecPrivate);
            case H264, HEVC -> ParameterSets.pixelFormat(codec, codecPrivate);
            default -> Optional.<String>empty();
        };
        return new TrackParams.Video(
                width,
                height,
                pixelFormat,
                profile(codec, codecPrivate),
                level(codec, codecPrivate),
                OptionalLong.empty(),
                frameRate(defaultDurationNanos),
                alpha);
    }

    /// The parameters of an audio track.
    public static TrackParams.Audio audio(CodecId codec, double samplingFrequency, int channels, int bitDepth) {
        var sampleFormat = switch (codec) {
            case OPUS, VORBIS, MP3, AAC, AC3, EAC3 -> Optional.of("fltp");
            case FLAC -> Optional.of(bitDepth > 16 ? "s32" : "s16");
            case PCM_S16LE -> Optional.of("s16");
            case PCM_S24LE -> Optional.of("s32");
            case PCM_F32LE -> Optional.of("flt");
            default -> Optional.<String>empty();
        };
        // Opus is always 48 kHz out, whatever the input was sampled at, and
        // that is the rate the container states too.
        var rate = (int) Math.round(samplingFrequency);
        return new TrackParams.Audio(
                Math.max(rate, 0), Math.max(channels, 0), sampleFormat, OptionalInt.empty(), OptionalLong.empty());
    }

    /// The frame rate a `DefaultDuration` states: one frame per that many
    /// nanoseconds, reduced.
    public static Optional<FrameRate> frameRate(long defaultDurationNanos) {
        if (defaultDurationNanos <= 0 || defaultDurationNanos > Integer.MAX_VALUE) {
            return Optional.empty();
        }
        var num = 1_000_000_000L;
        var den = defaultDurationNanos;
        var gcd = gcd(num, den);
        return FrameRate.known((int) (num / gcd), (int) (den / gcd));
    }

    /// FFmpeg's name for the pixel format a VP9 track's codec-private record
    /// states (the WebM "VP9 codec feature metadata": `id`, `length`, `value`
    /// triples for the profile, level, bit depth and chroma subsampling), or
    /// empty for a track without one; the decoder says then.
    static Optional<String> vp9PixelFormat(byte[] record) {
        var depth = 8;
        var subsampling = -1;
        var at = 0;
        while (at + 2 <= record.length) {
            var id = record[at] & 0xFF;
            var length = record[at + 1] & 0xFF;
            at += 2;
            if (at + length > record.length) {
                break;
            }
            if (length == 1) {
                var value = record[at] & 0xFF;
                if (id == 3) {
                    depth = value;
                } else if (id == 4) {
                    subsampling = value;
                }
            }
            at += length;
        }
        if (subsampling < 0) {
            return Optional.empty();
        }
        var base = switch (subsampling) {
            case 0, 1 -> "yuv420p";
            case 2 -> "yuv422p";
            case 3 -> "yuv444p";
            default -> null;
        };
        return name(base, depth);
    }

    /// FFmpeg's name for the pixel format an AV1 track's `av1C` record states
    /// (AV1 Codec ISO Media File Format Binding §2.3), or empty for a record
    /// that is not one.
    static Optional<String> av1PixelFormat(byte[] av1C) {
        if (av1C.length < 4 || (av1C[0] & 0x80) == 0) {
            return Optional.empty();
        }
        var flags = av1C[2] & 0xFF;
        var highBitDepth = (flags & 0x40) != 0;
        var twelveBit = (flags & 0x20) != 0;
        var monochrome = (flags & 0x10) != 0;
        var subsamplingX = (flags & 0x08) != 0;
        var subsamplingY = (flags & 0x04) != 0;
        var depth = twelveBit ? 12 : highBitDepth ? 10 : 8;
        String base;
        if (monochrome) {
            base = "gray";
        } else if (subsamplingX && subsamplingY) {
            base = "yuv420p";
        } else if (subsamplingX) {
            base = "yuv422p";
        } else {
            base = "yuv444p";
        }
        return name(base, depth);
    }

    private static Optional<String> name(@Nullable String base, int depth) {
        if (base == null) {
            return Optional.empty();
        }
        return switch (depth) {
            case 8 -> Optional.of(base);
            case 10, 12 -> Optional.of(base + depth + "le");
            default -> Optional.empty();
        };
    }

    private static OptionalInt profile(CodecId codec, byte[] record) {
        return switch (codec) {
            case H264 -> record.length > 1 ? OptionalInt.of(record[1] & 0xFF) : OptionalInt.empty();
            case HEVC -> record.length > 1 ? OptionalInt.of(record[1] & 0x1F) : OptionalInt.empty();
            case AV1 ->
                record.length > 1 && (record[0] & 0x80) != 0
                        ? OptionalInt.of((record[1] >>> 5) & 0x07)
                        : OptionalInt.empty();
            case VP9 -> vp9Feature(record, 1);
            default -> OptionalInt.empty();
        };
    }

    private static OptionalInt level(CodecId codec, byte[] record) {
        return switch (codec) {
            case H264 -> record.length > 3 ? OptionalInt.of(record[3] & 0xFF) : OptionalInt.empty();
            case AV1 ->
                record.length > 1 && (record[0] & 0x80) != 0 ? OptionalInt.of(record[1] & 0x1F) : OptionalInt.empty();
            case VP9 -> vp9Feature(record, 2);
            default -> OptionalInt.empty();
        };
    }

    private static OptionalInt vp9Feature(byte[] record, int wanted) {
        var at = 0;
        while (at + 2 <= record.length) {
            var id = record[at] & 0xFF;
            var length = record[at + 1] & 0xFF;
            at += 2;
            if (at + length > record.length) {
                break;
            }
            if (id == wanted && length == 1) {
                return OptionalInt.of(record[at] & 0xFF);
            }
            at += length;
        }
        return OptionalInt.empty();
    }

    /// The kind of track a `TrackType` is, for the ones that are played.
    public static Optional<MediaType> mediaType(long trackType) {
        if (trackType == MatroskaIds.TRACK_TYPE_VIDEO) {
            return Optional.of(MediaType.VIDEO);
        }
        if (trackType == MatroskaIds.TRACK_TYPE_AUDIO) {
            return Optional.of(MediaType.AUDIO);
        }
        if (trackType == MatroskaIds.TRACK_TYPE_SUBTITLE) {
            return Optional.of(MediaType.SUBTITLE);
        }
        return Optional.empty();
    }

    /// Whether an attachment's media type is a picture the probe reports as
    /// cover art, and which codec it is.
    public static Optional<CodecId> pictureCodec(String mediaType, String fileName) {
        var type = mediaType.toLowerCase(Locale.ROOT);
        var name = fileName.toLowerCase(Locale.ROOT);
        if (type.equals("image/png") || name.endsWith(".png")) {
            return Optional.of(CodecId.PNG);
        }
        if (type.equals("image/jpeg") || type.equals("image/jpg") || name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return Optional.of(CodecId.MJPEG);
        }
        return Optional.empty();
    }

    private static long gcd(long a, long b) {
        while (b != 0) {
            var t = a % b;
            a = b;
            b = t;
        }
        return a;
    }
}
