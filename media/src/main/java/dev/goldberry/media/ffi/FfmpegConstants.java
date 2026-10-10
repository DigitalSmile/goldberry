package dev.goldberry.media.ffi;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.codec.SampleFormat;
import dev.goldberry.media.codec.VideoFrame;

/// The FFmpeg constants the Engine compares against, **read from the layout
/// probe** rather than written down.
///
/// Most of them never change within a major. One of them changes between
/// platforms: `AVERROR(EAGAIN)` is `-EAGAIN`, and `EAGAIN` is 35 on macOS and 11
/// on Linux and Windows. Writing it down would be right on three targets. Reading
/// every constant the same way means there is no list of the ones that happen to
/// be safe to hard-code.
///
/// @param averrorEof             end of stream
/// @param averrorExit            "stop now", the answer to an aborted read
/// @param averrorEagain          "not yet": feed another packet, or read again
/// @param averrorEio             an I/O error
/// @param averrorInvalidData     damaged or unrecognised data
/// @param avseekSize             the `whence` bit that asks a seek callback for
///                               the stream's size
/// @param avseekForce            the `whence` bit that asks for a seek even when
///                               reading forward would be cheaper
/// @param noPtsValue             "no timestamp", `AV_NOPTS_VALUE`
/// @param timeBase               `AV_TIME_BASE`, the ticks per second of an
///                               `AVFormatContext` duration
/// @param profileUnknown         `AV_PROFILE_UNKNOWN`
/// @param levelUnknown           `AV_LEVEL_UNKNOWN`
/// @param mediaTypeVideo         `AVMEDIA_TYPE_VIDEO`
/// @param mediaTypeAudio         `AVMEDIA_TYPE_AUDIO`
/// @param mediaTypeSubtitle      `AVMEDIA_TYPE_SUBTITLE`
/// @param mediaTypeAttachment    `AVMEDIA_TYPE_ATTACHMENT`
/// @param dispositionDefault     `AV_DISPOSITION_DEFAULT`
/// @param dispositionAttachedPic `AV_DISPOSITION_ATTACHED_PIC`
/// @param logQuiet               `AV_LOG_QUIET`
/// @param logWarning             `AV_LOG_WARNING`
/// @param mediaTypeData          `AVMEDIA_TYPE_DATA`
/// @param discardDefault         `AVDISCARD_DEFAULT`: demux the stream
/// @param discardAll             `AVDISCARD_ALL`: skip the stream's packets
/// @param seekFlagBackward       `AVSEEK_FLAG_BACKWARD`
/// @param seekFlagAny            `AVSEEK_FLAG_ANY`: seek to any frame, not only
///                               a keyframe
/// @param pktFlagKey             `AV_PKT_FLAG_KEY`
/// @param pktDataMatroskaBlockAdditional `AV_PKT_DATA_MATROSKA_BLOCKADDITIONAL`:
///                               the packet side data that holds a Matroska
///                               block's BlockAdditional, a WebM track's alpha
/// @param sampleFormats          every `AV_SAMPLE_FMT_*` the Engine converts,
///                               by Goldberry's name for it
/// @param video                  what video decode and CPU present compare
///                               against: pixel formats, colour tags, and
///                               swscale's flags
public record FfmpegConstants(
        int averrorEof,
        int averrorExit,
        int averrorEagain,
        int averrorEio,
        int averrorInvalidData,
        int avseekSize,
        int avseekForce,
        long noPtsValue,
        long timeBase,
        int profileUnknown,
        int levelUnknown,
        int mediaTypeVideo,
        int mediaTypeAudio,
        int mediaTypeSubtitle,
        int mediaTypeAttachment,
        int dispositionDefault,
        int dispositionAttachedPic,
        int logQuiet,
        int logWarning,
        int mediaTypeData,
        int discardDefault,
        int discardAll,
        int seekFlagBackward,
        int seekFlagAny,
        int pktFlagKey,
        int pktDataMatroskaBlockAdditional,
        Map<SampleFormat, Integer> sampleFormats,
        Video video) {

    public FfmpegConstants {
        sampleFormats = Map.copyOf(sampleFormats);
        Objects.requireNonNull(video, "video");
    }

    /// The constants of the picture path (phase 3), kept apart from the audio and
    /// container ones above so that neither list has to be read to find the other.
    ///
    /// @param pixFmtNone        `AV_PIX_FMT_NONE`
    /// @param pixFmtYuv420p     `AV_PIX_FMT_YUV420P`: [PixelFormat#I420]
    /// @param pixFmtNv12        `AV_PIX_FMT_NV12`: [PixelFormat#NV12]
    /// @param pixFmtP010le      `AV_PIX_FMT_P010LE`: [PixelFormat#P010]
    /// @param pixFmtYuv420p10le `AV_PIX_FMT_YUV420P10LE`: [PixelFormat#I010]
    /// @param pixFmtYuva420p    `AV_PIX_FMT_YUVA420P`: [PixelFormat#I420A]
    /// @param pixFmtBgra        `AV_PIX_FMT_BGRA`: what CPU present converts to,
    ///                          which on a little-endian machine is the toolkit's
    ///                          `0xAARRGGBB` in memory
    /// @param spcBt709          `AVCOL_SPC_BT709`
    /// @param spcUnspecified    `AVCOL_SPC_UNSPECIFIED`
    /// @param spcBt470bg        `AVCOL_SPC_BT470BG`: BT.601, 625 lines
    /// @param spcSmpte170m      `AVCOL_SPC_SMPTE170M`: BT.601, 525 lines
    /// @param spcBt2020Ncl      `AVCOL_SPC_BT2020_NCL`
    /// @param spcBt2020Cl       `AVCOL_SPC_BT2020_CL`
    /// @param rangeJpeg         `AVCOL_RANGE_JPEG`: full range
    /// @param swsBilinear       `SWS_BILINEAR`
    /// @param swsAccurateRnd    `SWS_ACCURATE_RND`
    /// @param swsBitexact       `SWS_BITEXACT`: the same bytes on every CPU, which
    ///                          is what makes a golden portable
    /// @param swsFullChrHInt    `SWS_FULL_CHR_H_INT`: chroma interpolated at full
    ///                          horizontal resolution on the way to RGB
    /// @param swsCsItu601       `SWS_CS_ITU601`
    /// @param swsCsItu709       `SWS_CS_ITU709`
    /// @param swsCsBt2020       `SWS_CS_BT2020`
    /// @param hwConfigMethodHwDeviceCtx `AV_CODEC_HW_CONFIG_METHOD_HW_DEVICE_CTX`:
    ///                          the decoder takes a device in `hw_device_ctx`, the
    ///                          one way hardware decode is set up here
    /// @param hwDeviceTypeNone  `AV_HWDEVICE_TYPE_NONE`: what
    ///                          `av_hwdevice_find_type_by_name` answers for a name
    ///                          it does not know
    public record Video(
            int pixFmtNone,
            int pixFmtYuv420p,
            int pixFmtNv12,
            int pixFmtP010le,
            int pixFmtYuv420p10le,
            int pixFmtYuva420p,
            int pixFmtBgra,
            int spcBt709,
            int spcUnspecified,
            int spcBt470bg,
            int spcSmpte170m,
            int spcBt2020Ncl,
            int spcBt2020Cl,
            int rangeJpeg,
            int swsBilinear,
            int swsAccurateRnd,
            int swsBitexact,
            int swsFullChrHInt,
            int swsCsItu601,
            int swsCsItu709,
            int swsCsBt2020,
            int hwConfigMethodHwDeviceCtx,
            int hwDeviceTypeNone) {

        /// FFmpeg's `AVPixelFormat` for a frame-contract format.
        public int avPixelFormat(PixelFormat format) {
            return switch (format) {
                case I420 -> pixFmtYuv420p;
                case NV12 -> pixFmtNv12;
                case P010 -> pixFmtP010le;
                case I010 -> pixFmtYuv420p10le;
                case I420A -> pixFmtYuva420p;
            };
        }

        /// The frame-contract format an `AVPixelFormat` is, or empty for one a
        /// decoder has to convert before handing it over.
        public Optional<PixelFormat> pixelFormat(int avPixelFormat) {
            for (var format : PixelFormat.values()) {
                if (avPixelFormat(format) == avPixelFormat) {
                    return Optional.of(format);
                }
            }
            return Optional.empty();
        }

        /// swscale's coefficient table for a matrix.
        public int swsColorspace(VideoFrame.ColorMatrix matrix) {
            return switch (matrix) {
                case BT601 -> swsCsItu601;
                case BT709 -> swsCsItu709;
                case BT2020 -> swsCsBt2020;
            };
        }

        /// The matrix an `AVColorSpace` names, or empty when the decoder did not
        /// say (`AVCOL_SPC_UNSPECIFIED`, or a space this list does not know).
        public Optional<VideoFrame.ColorMatrix> matrix(int avColorSpace) {
            if (avColorSpace == spcBt709) {
                return Optional.of(VideoFrame.ColorMatrix.BT709);
            }
            if (avColorSpace == spcBt470bg || avColorSpace == spcSmpte170m) {
                return Optional.of(VideoFrame.ColorMatrix.BT601);
            }
            if (avColorSpace == spcBt2020Ncl || avColorSpace == spcBt2020Cl) {
                return Optional.of(VideoFrame.ColorMatrix.BT2020);
            }
            return Optional.empty();
        }
    }

    /// FFmpeg's `AVSampleFormat` for `format`.
    public int avSampleFormat(SampleFormat format) {
        return Objects.requireNonNull(sampleFormats.get(format), format.name());
    }

    /// Goldberry's name for an `AVSampleFormat`, or empty for one it does not
    /// convert (`AV_SAMPLE_FMT_NONE`, or a format newer than this list).
    public Optional<SampleFormat> sampleFormat(int avSampleFormat) {
        for (var entry : sampleFormats.entrySet()) {
            if (entry.getValue() == avSampleFormat) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    /// Reads every constant from `layout`.
    ///
    /// @throws IllegalArgumentException naming every constant the probe did not
    ///                                  report
    public static FfmpegConstants from(FfmpegLayout layout) {
        var read = new Reader(layout);
        var constants = new FfmpegConstants(
                read.integer("AVERROR_EOF"),
                read.integer("AVERROR_EXIT"),
                read.integer("AVERROR_EAGAIN"),
                read.integer("AVERROR_EIO"),
                read.integer("AVERROR_INVALIDDATA"),
                read.integer("AVSEEK_SIZE"),
                read.integer("AVSEEK_FORCE"),
                read.value("AV_NOPTS_VALUE"),
                read.value("AV_TIME_BASE"),
                read.integer("AV_PROFILE_UNKNOWN"),
                read.integer("AV_LEVEL_UNKNOWN"),
                read.integer("AVMEDIA_TYPE_VIDEO"),
                read.integer("AVMEDIA_TYPE_AUDIO"),
                read.integer("AVMEDIA_TYPE_SUBTITLE"),
                read.integer("AVMEDIA_TYPE_ATTACHMENT"),
                read.integer("AV_DISPOSITION_DEFAULT"),
                read.integer("AV_DISPOSITION_ATTACHED_PIC"),
                read.integer("AV_LOG_QUIET"),
                read.integer("AV_LOG_WARNING"),
                read.integer("AVMEDIA_TYPE_DATA"),
                read.integer("AVDISCARD_DEFAULT"),
                read.integer("AVDISCARD_ALL"),
                read.integer("AVSEEK_FLAG_BACKWARD"),
                read.integer("AVSEEK_FLAG_ANY"),
                read.integer("AV_PKT_FLAG_KEY"),
                read.integer("AV_PKT_DATA_MATROSKA_BLOCKADDITIONAL"),
                sampleFormats(read),
                new Video(
                        read.integer("AV_PIX_FMT_NONE"),
                        read.integer("AV_PIX_FMT_YUV420P"),
                        read.integer("AV_PIX_FMT_NV12"),
                        read.integer("AV_PIX_FMT_P010LE"),
                        read.integer("AV_PIX_FMT_YUV420P10LE"),
                        read.integer("AV_PIX_FMT_YUVA420P"),
                        read.integer("AV_PIX_FMT_BGRA"),
                        read.integer("AVCOL_SPC_BT709"),
                        read.integer("AVCOL_SPC_UNSPECIFIED"),
                        read.integer("AVCOL_SPC_BT470BG"),
                        read.integer("AVCOL_SPC_SMPTE170M"),
                        read.integer("AVCOL_SPC_BT2020_NCL"),
                        read.integer("AVCOL_SPC_BT2020_CL"),
                        read.integer("AVCOL_RANGE_JPEG"),
                        read.integer("SWS_BILINEAR"),
                        read.integer("SWS_ACCURATE_RND"),
                        read.integer("SWS_BITEXACT"),
                        read.integer("SWS_FULL_CHR_H_INT"),
                        read.integer("SWS_CS_ITU601"),
                        read.integer("SWS_CS_ITU709"),
                        read.integer("SWS_CS_BT2020"),
                        read.integer("AV_CODEC_HW_CONFIG_METHOD_HW_DEVICE_CTX"),
                        read.integer("AV_HWDEVICE_TYPE_NONE")));
        if (!read.missing.isEmpty()) {
            throw new IllegalArgumentException("the layout file does not report " + read.missing);
        }
        return constants;
    }

    private static Map<SampleFormat, Integer> sampleFormats(Reader read) {
        var formats = new EnumMap<SampleFormat, Integer>(SampleFormat.class);
        for (var format : SampleFormat.values()) {
            formats.put(format, read.integer("AV_SAMPLE_FMT_" + format.ffmpegSuffix()));
        }
        return formats;
    }

    /// Reads constants, collecting the missing ones so they are reported together.
    private static final class Reader {

        private final FfmpegLayout layout;
        private final List<String> missing = new ArrayList<>();

        Reader(FfmpegLayout layout) {
            this.layout = layout;
        }

        long value(String name) {
            var value = layout.constant(name);
            if (value.isEmpty()) {
                missing.add(name);
                return 0;
            }
            return value.getAsLong();
        }

        int integer(String name) {
            return Math.toIntExact(value(name));
        }
    }
}
