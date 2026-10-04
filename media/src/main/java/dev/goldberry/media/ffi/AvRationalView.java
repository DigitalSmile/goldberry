package dev.goldberry.media.ffi;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.util.Optional;

import dev.goldberry.media.codec.FrameRate;

/// An `AVRational` embedded in another struct ([FfmpegStructs#AV_RATIONAL]),
/// read where it lies.
final class AvRationalView {

    private static final long NUM = FfmpegStructs.AV_RATIONAL.byteOffset(groupElement("num"));
    private static final long DEN = FfmpegStructs.AV_RATIONAL.byteOffset(groupElement("den"));

    private AvRationalView() {}

    /// The field `field` of `struct`, an `AVRational` in `segment`, as a frame
    /// rate: empty for FFmpeg's `0/0`, which says the rate is unknown.
    static Optional<FrameRate> frameRate(MemorySegment segment, StructLayout struct, String field) {
        var at = struct.byteOffset(groupElement(field));
        return FrameRate.known(segment.get(JAVA_INT, at + NUM), segment.get(JAVA_INT, at + DEN));
    }
}
