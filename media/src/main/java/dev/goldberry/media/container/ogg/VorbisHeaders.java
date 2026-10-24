package dev.goldberry.media.container.ogg;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/// The two Vorbis headers a container reads for itself (Vorbis I §4.2): the
/// identification header for the rate, the channels and the two block sizes,
/// and the setup header for the modes, which is how long each audio packet
/// is.
///
/// A packet's length is not written anywhere; it follows from the block sizes
/// of this packet and the one before, each chosen by the mode number in the
/// packet's first byte. The modes are the last thing in the setup header,
/// after codebooks, floors, residues and mappings that take pages of
/// specification to walk; so they are read backwards from the framing bit at
/// the end, as FFmpeg's parser does: each mode is 41 bits, the block flag then
/// a window type and a transform type that are always zero and a mapping
/// number, and the six bits before the first one say how many there are.
public final class VorbisHeaders {

    private static final byte[] MAGIC = "vorbis".getBytes(StandardCharsets.US_ASCII);

    /// The identification header, packet type 1.
    static final int IDENTIFICATION = 1;

    /// The comment header, packet type 3.
    static final int COMMENT = 3;

    /// The setup header, packet type 5.
    static final int SETUP = 5;

    private static final int MODE_BITS = 41;

    private VorbisHeaders() {}

    /// What the identification header states.
    ///
    /// @param channels   the channel count
    /// @param rate       samples per second
    /// @param blocksize0 the short block, in samples
    /// @param blocksize1 the long block, in samples
    public record Identification(int channels, int rate, int blocksize0, int blocksize1) {}

    /// The modes of a setup header, and how a packet picks one.
    ///
    /// @param longBlock whether each mode uses the long block
    @SuppressWarnings("ArrayRecordComponent")
    public record Modes(boolean[] longBlock) {

        /// How many bits of a packet's first byte, after the type bit, hold the
        /// mode number: `ilog(count - 1)`.
        int bits() {
            return 32 - Integer.numberOfLeadingZeros(longBlock.length - 1);
        }

        /// Whether `packet`, an audio packet, uses the long block.
        ///
        /// @throws IllegalArgumentException for a header packet, an empty one,
        ///                                  or a mode number past the count
        boolean isLong(byte[] packet) {
            if (packet.length == 0 || (packet[0] & 0x01) != 0) {
                throw new IllegalArgumentException("not a Vorbis audio packet");
            }
            var mode = ((packet[0] & 0xFF) >>> 1) & ((1 << bits()) - 1);
            if (mode >= longBlock.length) {
                throw new IllegalArgumentException("mode " + mode + " of " + longBlock.length);
            }
            return longBlock[mode];
        }
    }

    /// Whether `packet` is a Vorbis header of `type`.
    static boolean isHeader(byte[] packet, int type) {
        return packet.length >= 7 && (packet[0] & 0xFF) == type && Arrays.equals(packet, 1, 7, MAGIC, 0, MAGIC.length);
    }

    /// Reads the identification header.
    ///
    /// @throws IllegalArgumentException when `packet` is not one, or states a
    ///                                  stream Vorbis I cannot
    public static Identification identification(byte[] packet) {
        if (!isHeader(packet, IDENTIFICATION) || packet.length < 30) {
            throw new IllegalArgumentException("not a Vorbis identification header");
        }
        var version = u32(packet, 7);
        var channels = packet[11] & 0xFF;
        var rate = u32(packet, 12);
        var exponents = packet[28] & 0xFF;
        var exponent0 = exponents & 0x0F;
        var exponent1 = exponents >>> 4;
        if (version != 0) {
            throw new IllegalArgumentException("Vorbis version " + version);
        }
        if (channels == 0 || rate <= 0 || rate > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("a Vorbis stream of " + channels + " channels at " + rate + " Hz");
        }
        if (exponent0 < 6 || exponent0 > 13 || exponent1 < 6 || exponent1 > 13 || exponent0 > exponent1) {
            throw new IllegalArgumentException("Vorbis block sizes 2^" + exponent0 + " and 2^" + exponent1);
        }
        return new Identification(channels, (int) rate, 1 << exponent0, 1 << exponent1);
    }

    /// Reads the modes at the end of the setup header.
    ///
    /// @throws IllegalArgumentException when `packet` is not a setup header, or
    ///                                  no run of modes ends at its framing bit
    public static Modes modes(byte[] packet) {
        if (!isHeader(packet, SETUP)) {
            throw new IllegalArgumentException("not a Vorbis setup header");
        }
        // The framing bit is the last bit written; what follows it in the final
        // byte is zero padding.
        var framing = -1L;
        for (var i = packet.length - 1; i >= 7 && framing < 0; i--) {
            var b = packet[i] & 0xFF;
            if (b != 0) {
                framing = i * 8L + (31 - Integer.numberOfLeadingZeros(b));
            }
        }
        if (framing < 0) {
            throw new IllegalArgumentException("a Vorbis setup header with no framing bit");
        }
        // Walk back one mode at a time while what is there still looks like a
        // mode, and take the longest run whose count field agrees with it: the
        // mapping numbers before the modes can look like a mode too, and FFmpeg's
        // choice of the longest agreeing run is the one every encoder's output
        // satisfies.
        var count = 0;
        for (var k = 1; k <= 64; k++) {
            var start = framing - (long) k * MODE_BITS;
            if (start < 6 + 7 * 8L) {
                break;
            }
            var window = bits(packet, start + 1, 16);
            var transform = bits(packet, start + 17, 16);
            var mapping = bits(packet, start + 33, 8);
            if (window != 0 || transform != 0 || mapping > 63) {
                break;
            }
            if (bits(packet, start - 6, 6) + 1 == k) {
                count = k;
            }
        }
        if (count == 0) {
            throw new IllegalArgumentException("a Vorbis setup header whose modes cannot be found");
        }
        var longBlock = new boolean[count];
        for (var i = 0; i < count; i++) {
            var start = framing - (long) (count - i) * MODE_BITS;
            longBlock[i] = bits(packet, start, 1) == 1;
        }
        return new Modes(longBlock);
    }

    /// `count` bits of `packet` from bit `position`, least significant bit
    /// first within each byte, as Vorbis packs them.
    private static int bits(byte[] packet, long position, int count) {
        var value = 0;
        for (var i = 0; i < count; i++) {
            var bit = position + i;
            var b = packet[(int) (bit >>> 3)] & 0xFF;
            value |= (b >>> (bit & 7) & 1) << i;
        }
        return value;
    }

    private static long u32(byte[] bytes, int at) {
        return (bytes[at] & 0xFFL)
                | (bytes[at + 1] & 0xFFL) << 8
                | (bytes[at + 2] & 0xFFL) << 16
                | (bytes[at + 3] & 0xFFL) << 24;
    }
}
