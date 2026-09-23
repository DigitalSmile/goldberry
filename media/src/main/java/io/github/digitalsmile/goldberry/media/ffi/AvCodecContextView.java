package io.github.digitalsmile.goldberry.media.ffi;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.MemorySegment;

import io.github.digitalsmile.goldberry.media.codec.Rational;

/// The fields of `AVCodecContext` the Engine writes ([FfmpegStructs#AV_CODEC_CONTEXT]).
final class AvCodecContextView {

    private static final long PKT_TIMEBASE_NUM =
            FfmpegStructs.AV_CODEC_CONTEXT.byteOffset(groupElement("pkt_timebase"), groupElement("num"));
    private static final long PKT_TIMEBASE_DEN =
            FfmpegStructs.AV_CODEC_CONTEXT.byteOffset(groupElement("pkt_timebase"), groupElement("den"));

    private AvCodecContextView() {}

    static MemorySegment of(MemorySegment context) {
        return Pointers.struct(context, FfmpegStructs.AV_CODEC_CONTEXT);
    }

    /// The time base packets arrive in. Set before `avcodec_open2`, so decoded
    /// frames carry timestamps in the same unit.
    static void packetTimeBase(MemorySegment context, Rational timeBase) {
        context.set(JAVA_INT, PKT_TIMEBASE_NUM, timeBase.num());
        context.set(JAVA_INT, PKT_TIMEBASE_DEN, timeBase.den());
    }
}
