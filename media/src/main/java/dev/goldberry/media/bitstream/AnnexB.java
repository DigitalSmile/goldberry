package dev.goldberry.media.bitstream;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Objects;

/// Rewrites a length-prefixed H.264 or HEVC packet, as MP4 and Matroska store
/// it, into the Annex B byte stream a decoder that reads start codes wants.
///
/// Each NAL unit's 1-, 2- or 4-byte length becomes a four-byte start code
/// (`00 00 00 01`). A keyframe is preceded by the stream's parameter sets, which
/// the container keeps in its configuration record rather than in the packets.
/// A decoder that starts at a keyframe after a seek then has them. That is what
/// FFmpeg's `h264_mp4toannexb` and `hevc_mp4toannexb` do.
///
/// The NAL units are not escaped again: emulation prevention bytes are already
/// in them, since both forms carry the same NAL units.
public final class AnnexB {

    /// The start code put before each NAL unit.
    private static final byte[] START_CODE = {0, 0, 0, 1};

    private AnnexB() {}

    /// The bytes [#rewrite] writes for `packet`, so a caller can size its buffer.
    ///
    /// @throws IllegalArgumentException when a length runs past the packet
    public static long size(MemorySegment packet, int nalLengthSize, List<byte[]> parameterSets, boolean keyframe) {
        var total = keyframe ? parameterSetsSize(parameterSets) : 0L;
        var at = 0L;
        while (at < packet.byteSize()) {
            var length = length(packet, at, nalLengthSize);
            total += START_CODE.length + length;
            at += nalLengthSize + length;
        }
        return total;
    }

    /// Writes `packet` into `target` as Annex B, with `parameterSets` first when it
    /// is a keyframe.
    ///
    /// @param packet        the length-prefixed NAL units of one access unit
    /// @param nalLengthSize the bytes of each length: 1, 2 or 4, from the
    ///                      configuration record
    /// @param parameterSets the configuration record's NAL units, in the order a
    ///                      decoder wants them
    /// @param keyframe      whether to put the parameter sets first
    /// @param target        at least [#size] bytes
    /// @return the bytes written
    /// @throws IllegalArgumentException when a length runs past the packet, or
    ///                                  `target` is too small
    public static long rewrite(
            MemorySegment packet,
            int nalLengthSize,
            List<byte[]> parameterSets,
            boolean keyframe,
            MemorySegment target) {
        Objects.requireNonNull(target, "target");
        var needed = size(packet, nalLengthSize, parameterSets, keyframe);
        if (target.byteSize() < needed) {
            throw new IllegalArgumentException(
                    "an Annex B packet of " + needed + " bytes does not fit in " + target.byteSize());
        }
        var out = 0L;
        if (keyframe) {
            for (var unit : parameterSets) {
                out = startCode(target, out);
                MemorySegment.copy(unit, 0, target, JAVA_BYTE, out, unit.length);
                out += unit.length;
            }
        }
        var at = 0L;
        while (at < packet.byteSize()) {
            var length = length(packet, at, nalLengthSize);
            out = startCode(target, out);
            MemorySegment.copy(packet, at + nalLengthSize, target, out, length);
            out += length;
            at += nalLengthSize + length;
        }
        return out;
    }

    /// Whether `data` already starts with a start code, three bytes or four: a
    /// stream in Annex B form, which needs no rewriting.
    public static boolean startsWithStartCode(MemorySegment data) {
        if (data.byteSize() >= 3
                && data.get(JAVA_BYTE, 0) == 0
                && data.get(JAVA_BYTE, 1) == 0
                && data.get(JAVA_BYTE, 2) == 1) {
            return true;
        }
        return data.byteSize() >= 4
                && data.get(JAVA_BYTE, 0) == 0
                && data.get(JAVA_BYTE, 1) == 0
                && data.get(JAVA_BYTE, 2) == 0
                && data.get(JAVA_BYTE, 3) == 1;
    }

    private static long parameterSetsSize(List<byte[]> parameterSets) {
        var total = 0L;
        for (var unit : parameterSets) {
            total += START_CODE.length + unit.length;
        }
        return total;
    }

    private static long startCode(MemorySegment target, long at) {
        MemorySegment.copy(START_CODE, 0, target, JAVA_BYTE, at, START_CODE.length);
        return at + START_CODE.length;
    }

    /// The length at `at`, checked against what is left of the packet.
    private static long length(MemorySegment packet, long at, int nalLengthSize) {
        if (nalLengthSize != 1 && nalLengthSize != 2 && nalLengthSize != 4) {
            throw new IllegalArgumentException("NAL units prefixed by " + nalLengthSize + " bytes");
        }
        if (at + nalLengthSize > packet.byteSize()) {
            throw new IllegalArgumentException(
                    "a NAL unit length at byte " + at + " runs past the packet's " + packet.byteSize() + " bytes");
        }
        var length = 0L;
        for (var i = 0; i < nalLengthSize; i++) {
            length = length << 8 | (packet.get(JAVA_BYTE, at + i) & 0xff);
        }
        if (at + nalLengthSize + length > packet.byteSize()) {
            throw new IllegalArgumentException("a NAL unit of " + length + " bytes at byte " + at
                    + " runs past the packet's " + packet.byteSize() + " bytes");
        }
        return length;
    }
}
