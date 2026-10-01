package dev.goldberry.media.ffi;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.MemorySegment;
import java.util.Objects;

/// The public fields at the head of `AVCodec` and `AVInputFormat`, read to list
/// what this build decodes and demuxes ([FfmpegStructs#AV_CODEC],
/// [FfmpegStructs#AV_INPUT_FORMAT]).
final class AvCodecView {

    private static final long CODEC_NAME = FfmpegStructs.AV_CODEC.byteOffset(groupElement("name"));
    private static final long CODEC_TYPE = FfmpegStructs.AV_CODEC.byteOffset(groupElement("type"));
    private static final long CODEC_ID = FfmpegStructs.AV_CODEC.byteOffset(groupElement("id"));
    private static final long FORMAT_NAME = FfmpegStructs.AV_INPUT_FORMAT.byteOffset(groupElement("name"));

    private AvCodecView() {}

    /// The decoder's own name, such as `libdav1d`, which is not the codec's.
    static String name(MemorySegment codec) {
        var sized = Pointers.struct(codec, FfmpegStructs.AV_CODEC);
        return Objects.requireNonNullElse(Pointers.string(sized.get(ADDRESS, CODEC_NAME)), "");
    }

    static int type(MemorySegment codec) {
        return Pointers.struct(codec, FfmpegStructs.AV_CODEC).get(JAVA_INT, CODEC_TYPE);
    }

    static int id(MemorySegment codec) {
        return Pointers.struct(codec, FfmpegStructs.AV_CODEC).get(JAVA_INT, CODEC_ID);
    }

    /// A demuxer's name: one or more comma-separated format names,
    /// `matroska,webm`.
    static String formatName(MemorySegment inputFormat) {
        var sized = Pointers.struct(inputFormat, FfmpegStructs.AV_INPUT_FORMAT);
        return Objects.requireNonNullElse(Pointers.string(sized.get(ADDRESS, FORMAT_NAME)), "");
    }
}
