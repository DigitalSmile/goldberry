package dev.goldberry.media.bitstream;

import java.io.ByteArrayOutputStream;
import java.util.Objects;

/// Reads a bitstream bit by bit, most significant bit first: the payload of an
/// H.264 or HEVC NAL unit, with its fixed-width fields and Exp-Golomb codes,
/// and the headers of VP9 and AV1.
///
/// Reads an RBSP, the payload with its emulation-prevention bytes taken out by
/// [#unescape], or any byte array as it is. Running off the end is
/// [IllegalArgumentException]: a header that ends early is malformed, and the
/// caller falls back to what needs no header at all.
public final class BitReader {

    private final byte[] data;
    private final int from;
    private final long limit;
    private long position;

    /// A reader at the first bit of `rbsp`.
    public BitReader(byte[] rbsp) {
        this(rbsp, 0, rbsp.length);
    }

    /// A reader of the `length` bytes of `data` from `from`, at their first bit:
    /// one unit of a larger buffer, read where it lies. Its bit positions count
    /// from `from`, and its end is `from + length`.
    ///
    /// @throws IndexOutOfBoundsException when the bytes are not all in `data`
    public BitReader(byte[] data, int from, int length) {
        Objects.checkFromIndexSize(from, length, data.length);
        this.data = data;
        this.from = from;
        this.limit = (long) length * Byte.SIZE;
    }

    /// `length` bytes of a NAL unit from `from`, with every emulation-prevention
    /// byte removed: the `03` of each `00 00 03` that an encoder inserts so that
    /// the payload never looks like a start code.
    public static byte[] unescape(byte[] nal, int from, int length) {
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
    public int bit() {
        if (position >= limit) {
            throw new IllegalArgumentException("the parameter set ends at bit " + position);
        }
        var value = (data[from + (int) (position >>> 3)] >>> (7 - (int) (position & 7))) & 1;
        position++;
        return value;
    }

    /// Whether the next bit is set.
    public boolean flag() {
        return bit() == 1;
    }

    /// `count` bits, most significant first, as an unsigned value.
    ///
    /// @param count 0 to 32; a 32-bit value comes back in an `int`'s bits
    public int bits(int count) {
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
    public void skip(long count) {
        if (count < 0 || position + count > limit) {
            throw new IllegalArgumentException("cannot skip " + count + " bits at bit " + position);
        }
        position += count;
    }

    /// An unsigned Exp-Golomb code, `ue(v)`.
    public int ue() {
        var zeros = 0;
        while (bit() == 0) {
            if (++zeros > 31) {
                throw new IllegalArgumentException("an Exp-Golomb code longer than 32 bits at bit " + position);
            }
        }
        return (int) ((1L << zeros) - 1 + (bits(zeros) & 0xFFFF_FFFFL));
    }

    /// A signed Exp-Golomb code, `se(v)`: 1, −1, 2, −2, … for 1, 2, 3, 4, ….
    public int se() {
        var code = ue() & 0xFFFF_FFFFL;
        return (int) ((code & 1) == 1 ? (code + 1) / 2 : -(code / 2));
    }

    /// The bits read so far.
    public long position() {
        return position;
    }

    /// `count` bits, most significant first, as an unsigned value of up to 63
    /// bits.
    public long bitsLong(int count) {
        if (count < 0 || count > 63) {
            throw new IllegalArgumentException("cannot read " + count + " bits into a long");
        }
        var value = 0L;
        for (var i = 0; i < count; i++) {
            value = (value << 1) | bit();
        }
        return value;
    }

    /// How many bits are left.
    public long remaining() {
        return limit - position;
    }

    /// Whether the reader is at a byte boundary.
    public boolean byteAligned() {
        return (position & 7) == 0;
    }

    /// Passes over the bits to the next byte boundary, if not at one.
    public void byteAlign() {
        position = (position + 7) & ~7L;
        if (position > limit) {
            throw new IllegalArgumentException("the data ends before its last byte boundary");
        }
    }

    /// H.264 and HEVC's `more_rbsp_data()`: whether anything but the RBSP's
    /// trailing bits, a one and then zeros to the end, is left.
    public boolean moreRbspData() {
        if (position >= limit) {
            return false;
        }
        // The last set bit of the data is the stop bit.
        var last = limit - 1;
        while (last >= position && ((data[from + (int) (last >>> 3)] >>> (7 - (int) (last & 7))) & 1) == 0) {
            last--;
        }
        return last > position;
    }

    /// An unsigned value of `count` bytes, little-endian: AV1's `le(n)`.
    public long littleEndian(int count) {
        var value = 0L;
        for (var i = 0; i < count; i++) {
            value |= bitsLong(8) << (8 * i);
        }
        return value;
    }

    /// AV1's `leb128()`: an unsigned value in groups of seven bits, least
    /// significant first, at most eight bytes.
    public long leb128() {
        var value = 0L;
        for (var i = 0; i < 8; i++) {
            var b = bits(8);
            value |= (long) (b & 0x7F) << (7 * i);
            if ((b & 0x80) == 0) {
                return value;
            }
        }
        throw new IllegalArgumentException("a leb128 longer than eight bytes");
    }

    /// AV1's `su(n)`: an `n`-bit two's-complement value.
    public int signed(int count) {
        var value = bits(count);
        var sign = 1 << (count - 1);
        return (value & sign) != 0 ? value - 2 * sign : value;
    }

    /// VP9's `su(n)`: `n` bits of magnitude, then a sign bit, set for a
    /// negative value. `n + 1` bits in all.
    public int signMagnitude(int count) {
        var magnitude = bits(count);
        return bit() == 1 ? -magnitude : magnitude;
    }

    /// AV1's `ns(n)`: a value below `n` in as few bits as it needs.
    public int nonSymmetric(int n) {
        var w = 0;
        for (var x = n; x != 0; x >>= 1) {
            w++;
        }
        var m = (1 << w) - n;
        var v = bits(w - 1);
        if (v < m) {
            return v;
        }
        return (v << 1) - m + bit();
    }

    /// AV1's `uvlc()`.
    public long uvlc() {
        var zeros = 0;
        while (bit() == 0) {
            zeros++;
        }
        if (zeros >= 32) {
            return (1L << 32) - 1;
        }
        return bitsLong(zeros) + (1L << zeros) - 1;
    }
}
