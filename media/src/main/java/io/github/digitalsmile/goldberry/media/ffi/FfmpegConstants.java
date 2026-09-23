package io.github.digitalsmile.goldberry.media.ffi;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.github.digitalsmile.goldberry.media.codec.SampleFormat;

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
/// @param sampleFormats          every `AV_SAMPLE_FMT_*` the Engine converts,
///                               by Goldberry's name for it
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
        Map<SampleFormat, Integer> sampleFormats) {

    public FfmpegConstants {
        sampleFormats = Map.copyOf(sampleFormats);
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
                sampleFormats(read));
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
