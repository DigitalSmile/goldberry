package dev.goldberry.media.ffi;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.MemorySegment;
import java.util.Optional;

import dev.goldberry.media.codec.FrameRate;

/// The fields of `AVStream` the Engine reads ([FfmpegStructs#AV_STREAM]).
final class AvStreamView {

    private static final long INDEX = offset("index");
    private static final long CODECPAR = offset("codecpar");
    private static final long TIME_BASE_NUM =
            FfmpegStructs.AV_STREAM.byteOffset(groupElement("time_base"), groupElement("num"));
    private static final long TIME_BASE_DEN =
            FfmpegStructs.AV_STREAM.byteOffset(groupElement("time_base"), groupElement("den"));
    private static final long DURATION = offset("duration");
    private static final long NB_FRAMES = offset("nb_frames");
    private static final long DISPOSITION = offset("disposition");
    private static final long DISCARD = offset("discard");
    private static final long METADATA = offset("metadata");

    private AvStreamView() {}

    /// `stream`, sized to the struct.
    static MemorySegment of(MemorySegment stream) {
        return Pointers.struct(stream, FfmpegStructs.AV_STREAM);
    }

    /// The stream's index in its container.
    static int index(MemorySegment stream) {
        return stream.get(JAVA_INT, INDEX);
    }

    /// The stream's `AVCodecParameters`, sized to the struct.
    static MemorySegment codecParameters(MemorySegment stream) {
        return AvCodecParametersView.of(stream.get(ADDRESS, CODECPAR));
    }

    /// The numerator of the unit [#duration] is counted in.
    static int timeBaseNum(MemorySegment stream) {
        return stream.get(JAVA_INT, TIME_BASE_NUM);
    }

    /// The denominator of the unit [#duration] is counted in.
    static int timeBaseDen(MemorySegment stream) {
        return stream.get(JAVA_INT, TIME_BASE_DEN);
    }

    /// The stream's duration in its time base, or `AV_NOPTS_VALUE`.
    static long duration(MemorySegment stream) {
        return stream.get(JAVA_LONG, DURATION);
    }

    /// How many pictures the stream holds, when the container records it (MP4's
    /// sample table does, Matroska does not); 0 when it does not.
    static long frameCount(MemorySegment stream) {
        return stream.get(JAVA_LONG, NB_FRAMES);
    }

    /// The average frame rate, from the container or measured by
    /// `avformat_find_stream_info`.
    static Optional<FrameRate> averageFrameRate(MemorySegment stream) {
        return AvRationalView.frameRate(stream, FfmpegStructs.AV_STREAM, "avg_frame_rate");
    }

    /// The real base frame rate: the lowest rate every timestamp falls on, a
    /// guess FFmpeg makes from the first packets.
    static Optional<FrameRate> baseFrameRate(MemorySegment stream) {
        return AvRationalView.frameRate(stream, FfmpegStructs.AV_STREAM, "r_frame_rate");
    }

    /// The `AV_DISPOSITION_*` bits.
    static int disposition(MemorySegment stream) {
        return stream.get(JAVA_INT, DISPOSITION);
    }

    /// The stream's metadata dictionary (`AVDictionary *`), or null for none.
    static MemorySegment metadata(MemorySegment stream) {
        return stream.get(ADDRESS, METADATA);
    }

    /// Sets `AVDISCARD_*`: whether the demuxer hands this stream's packets over at
    /// all. A stream nobody plays is discarded, so its packets are never read into
    /// memory.
    static void discard(MemorySegment stream, int discard) {
        stream.set(JAVA_INT, DISCARD, discard);
    }

    private static long offset(String field) {
        return FfmpegStructs.AV_STREAM.byteOffset(groupElement(field));
    }
}
