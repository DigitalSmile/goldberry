package io.github.digitalsmile.goldberry.media.ffi;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.MemorySegment;

import io.github.digitalsmile.goldberry.media.codec.Rational;

/// The fields of `AVCodecContext` the Engine writes ([FfmpegStructs#AV_CODEC_CONTEXT]).
final class AvCodecContextView {

    private static final long PKT_TIMEBASE_NUM =
            FfmpegStructs.AV_CODEC_CONTEXT.byteOffset(groupElement("pkt_timebase"), groupElement("num"));
    private static final long PKT_TIMEBASE_DEN =
            FfmpegStructs.AV_CODEC_CONTEXT.byteOffset(groupElement("pkt_timebase"), groupElement("den"));

    private static final long THREAD_COUNT = FfmpegStructs.AV_CODEC_CONTEXT.byteOffset(groupElement("thread_count"));

    private static final long GET_FORMAT = FfmpegStructs.AV_CODEC_CONTEXT.byteOffset(groupElement("get_format"));

    private static final long HW_DEVICE_CTX = FfmpegStructs.AV_CODEC_CONTEXT.byteOffset(groupElement("hw_device_ctx"));

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

    /// How many threads the decoder may use; 0 lets FFmpeg choose one per core.
    /// Set before `avcodec_open2`. Threading changes when frames arrive, never
    /// what is in them: every decoder here is bit-exact whatever the count.
    static void threadCount(MemorySegment context, int threads) {
        context.set(JAVA_INT, THREAD_COUNT, threads);
    }

    /// The pixel-format callback: an upcall stub that picks the device's format
    /// when the decoder offers it (phase 5). Set before `avcodec_open2`.
    static void getFormat(MemorySegment context, MemorySegment callback) {
        context.set(ADDRESS, GET_FORMAT, callback);
    }

    /// The device the decoder decodes on: an `AVBufferRef*` the context owns from
    /// here on, and unreferences when it is freed. Set before `avcodec_open2`.
    static void hwDeviceContext(MemorySegment context, MemorySegment device) {
        context.set(ADDRESS, HW_DEVICE_CTX, device);
    }
}
