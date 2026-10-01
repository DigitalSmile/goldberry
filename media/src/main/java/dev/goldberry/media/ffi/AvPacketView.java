package dev.goldberry.media.ffi;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.MemorySegment;

/// The fields of `AVPacket` the Engine reads from the demuxer and writes for the
/// decoder ([FfmpegStructs#AV_PACKET]).
///
/// Written only into a packet whose `buf` is null, which is what makes
/// `avcodec_send_packet` copy the data instead of taking a reference to it. That
/// is the rule that lets the built-in decoder take a [dev.goldberry.media.codec.Packet]
/// from any demuxer.
final class AvPacketView {

    private static final long PTS = offset("pts");
    private static final long DTS = offset("dts");
    private static final long DATA = offset("data");
    private static final long SIZE = offset("size");
    private static final long STREAM_INDEX = offset("stream_index");
    private static final long FLAGS = offset("flags");
    private static final long DURATION = offset("duration");

    private AvPacketView() {}

    static MemorySegment of(MemorySegment packet) {
        return Pointers.struct(packet, FfmpegStructs.AV_PACKET);
    }

    static long pts(MemorySegment packet) {
        return packet.get(JAVA_LONG, PTS);
    }

    static long dts(MemorySegment packet) {
        return packet.get(JAVA_LONG, DTS);
    }

    static MemorySegment data(MemorySegment packet) {
        return packet.get(ADDRESS, DATA);
    }

    static int size(MemorySegment packet) {
        return packet.get(JAVA_INT, SIZE);
    }

    static int streamIndex(MemorySegment packet) {
        return packet.get(JAVA_INT, STREAM_INDEX);
    }

    static int flags(MemorySegment packet) {
        return packet.get(JAVA_INT, FLAGS);
    }

    static long duration(MemorySegment packet) {
        return packet.get(JAVA_LONG, DURATION);
    }

    /// Points `packet` at someone else's bytes, with their timing.
    static void set(
            MemorySegment packet,
            MemorySegment data,
            int size,
            int streamIndex,
            long pts,
            long dts,
            long duration,
            int flags) {
        packet.set(ADDRESS, DATA, data);
        packet.set(JAVA_INT, SIZE, size);
        packet.set(JAVA_INT, STREAM_INDEX, streamIndex);
        packet.set(JAVA_LONG, PTS, pts);
        packet.set(JAVA_LONG, DTS, dts);
        packet.set(JAVA_LONG, DURATION, duration);
        packet.set(JAVA_INT, FLAGS, flags);
    }

    private static long offset(String field) {
        return FfmpegStructs.AV_PACKET.byteOffset(groupElement(field));
    }
}
