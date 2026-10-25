package dev.goldberry.media.bitstream.vp9;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// A VP9 superframe (Annex B): several frames in one packet, as an encoder
/// puts a hidden alternate reference frame together with the frame shown
/// after it, so that every packet the container stores shows one picture.
///
/// The frames are laid end to end and followed by an index: a marker byte
/// `110bbnnn` (`bb` + 1 bytes for each size, `nnn` + 1 frames), each frame's
/// size little-endian, and the marker byte again. A packet whose last byte is
/// not such a marker, or whose index does not open with the same byte, is one
/// frame.
///
/// @param bytesPerFrameSize `bytes_per_framesize_minus_1` + 1: 1 to 4
/// @param frames            each frame, in decoding order
/// @param indexSize         the bytes of the index at the end of the packet
public record Superframe(int bytesPerFrameSize, List<Frame> frames, int indexSize) {

    /// The most frames one index lists.
    public static final int MAX_FRAMES = 8;

    /// One frame of a packet: the bytes a decoder is handed for it.
    ///
    /// @param offset from the start of the packet
    /// @param size   in bytes
    public record Frame(int offset, int size) {}

    public Superframe {
        frames = List.copyOf(frames);
    }

    /// The superframe index at the end of `packet`, if it has one.
    ///
    /// @throws IllegalArgumentException when the index lists frames that run
    ///                                  past the packet, or the packet is
    ///                                  2 GiB or more
    public static Optional<Superframe> parse(MemorySegment packet) {
        var length = packet.byteSize();
        if (length > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("a VP9 packet of " + length + " bytes");
        }
        if (length == 0) {
            return Optional.empty();
        }
        var marker = packet.get(JAVA_BYTE, length - 1) & 0xFF;
        if ((marker & 0xE0) != 0xC0) {
            return Optional.empty();
        }
        var bytes = ((marker >> 3) & 3) + 1;
        var count = (marker & 7) + 1;
        var indexSize = 2 + bytes * count;
        if (indexSize > length || (packet.get(JAVA_BYTE, length - indexSize) & 0xFF) != marker) {
            return Optional.empty();
        }
        var frames = new ArrayList<Frame>(count);
        var at = length - indexSize + 1;
        var offset = 0L;
        for (var i = 0; i < count; i++) {
            var size = 0L;
            for (var b = 0; b < bytes; b++) {
                size |= (long) (packet.get(JAVA_BYTE, at++) & 0xFF) << (8 * b);
            }
            if (offset + size > length - indexSize) {
                throw new IllegalArgumentException("frame " + i + " of a superframe, " + size + " bytes at " + offset
                        + ", runs past the " + (length - indexSize) + " bytes before its index");
            }
            frames.add(new Frame((int) offset, (int) size));
            offset += size;
        }
        return Optional.of(new Superframe(bytes, frames, indexSize));
    }

    /// [#parse] of a packet in a byte array.
    public static Optional<Superframe> parse(byte[] packet) {
        return parse(MemorySegment.ofArray(packet));
    }

    /// The frames of `packet`: its superframe's, or the whole packet as one.
    ///
    /// @throws IllegalArgumentException when an index lists frames that run past
    ///                                  the packet, or the packet is 2 GiB or
    ///                                  more
    public static List<Frame> frames(MemorySegment packet) {
        return parse(packet).map(Superframe::frames).orElseGet(() -> List.of(new Frame(0, (int) packet.byteSize())));
    }

    /// [#frames] of a packet in a byte array.
    public static List<Frame> frames(byte[] packet) {
        return frames(MemorySegment.ofArray(packet));
    }
}
