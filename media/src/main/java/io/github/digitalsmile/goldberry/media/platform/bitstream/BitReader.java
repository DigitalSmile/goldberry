package io.github.digitalsmile.goldberry.media.platform.bitstream;

import java.io.ByteArrayOutputStream;
import java.util.Objects;

/// Reads the payload of an H.264 or HEVC NAL unit bit by bit: fixed-width fields
/// and the Exp-Golomb codes both codecs write their parameter sets in.
///
/// Reads an RBSP, the payload with its emulation-prevention bytes taken out by
/// [#unescape]. Running off the end is [IllegalArgumentException]: a parameter
/// set that ends early is malformed, and the caller falls back to what needs no
/// parameter set at all.
final class BitReader {

    private final byte[] data;
    private long position;

    /// A reader at the first bit of `rbsp`.
    BitReader(byte[] rbsp) {
        this.data = Objects.requireNonNull(rbsp, "rbsp");
    }

    /// `length` bytes of a NAL unit from `from`, with every emulation-prevention
    /// byte removed: the `03` of each `00 00 03` that an encoder inserts so that
    /// the payload never looks like a start code.
    static byte[] unescape(byte[] nal, int from, int length) {
        Objects.checkFromIndexSize(from, length, nal.length);
        var out = new ByteArrayOutputStream(length);
        var zeros = 0;
        for (var i = from; i < from + length; i++) {
            var value = nal[i] & 0xFF;
            if (zeros >= 2 && value == 0x03) {
                zeros = 0;
                continue;
            }
            zeros = value == 0 ? zeros + 1 : 0;
            out.write(value);
        }
        return out.toByteArray();
    }

    /// One bit.
    int bit() {
        if (position >= (long) data.length * Byte.SIZE) {
            throw new IllegalArgumentException("the parameter set ends at bit " + position);
        }
        var value = (data[(int) (position >>> 3)] >>> (7 - (int) (position & 7))) & 1;
        position++;
        return value;
    }

    /// Whether the next bit is set.
    boolean flag() {
        return bit() == 1;
    }

    /// `count` bits, most significant first, as an unsigned value.
    ///
    /// @param count 0 to 32; a 32-bit value comes back in an `int`'s bits
    int bits(int count) {
        if (count < 0 || count > Integer.SIZE) {
            throw new IllegalArgumentException("cannot read " + count + " bits into an int");
        }
        var value = 0;
        for (var i = 0; i < count; i++) {
            value = (value << 1) | bit();
        }
        return value;
    }

    /// Passes over `count` bits.
    void skip(long count) {
        if (count < 0 || position + count > (long) data.length * Byte.SIZE) {
            throw new IllegalArgumentException("cannot skip " + count + " bits at bit " + position);
        }
        position += count;
    }

    /// An unsigned Exp-Golomb code, `ue(v)`.
    int ue() {
        var zeros = 0;
        while (bit() == 0) {
            if (++zeros > 31) {
                throw new IllegalArgumentException("an Exp-Golomb code longer than 32 bits at bit " + position);
            }
        }
        return (int) ((1L << zeros) - 1 + (bits(zeros) & 0xFFFF_FFFFL));
    }

    /// A signed Exp-Golomb code, `se(v)`: 1, −1, 2, −2, … for 1, 2, 3, 4, ….
    int se() {
        var code = ue() & 0xFFFF_FFFFL;
        return (int) ((code & 1) == 1 ? (code + 1) / 2 : -(code / 2));
    }

    /// The bits read so far.
    long position() {
        return position;
    }
}
