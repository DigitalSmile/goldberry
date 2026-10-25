package dev.goldberry.media.bitstream;

import java.io.ByteArrayOutputStream;

/// Writes the bits a [BitReader] reads: how the tests make parameter sets and
/// headers with exactly the fields a case needs.
public final class BitWriter {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private int current;
    private int count;

    /// `bits` bits of `value`, most significant first.
    public BitWriter bits(int bits, long value) {
        for (var i = bits - 1; i >= 0; i--) {
            current = (current << 1) | (int) ((value >>> i) & 1);
            if (++count == 8) {
                out.write(current);
                current = 0;
                count = 0;
            }
        }
        return this;
    }

    public BitWriter flag(boolean value) {
        return bits(1, value ? 1 : 0);
    }

    /// An unsigned Exp-Golomb code.
    public BitWriter ue(long value) {
        var code = value + 1;
        var length = 64 - Long.numberOfLeadingZeros(code);
        return bits(length - 1, 0).bits(length, code);
    }

    /// A signed Exp-Golomb code.
    public BitWriter se(long value) {
        return ue(value > 0 ? 2 * value - 1 : -2 * value);
    }

    /// VP9's `su(n)`: `bits` bits of magnitude, then the sign.
    public BitWriter signMagnitude(int bits, int value) {
        return bits(bits, Math.abs(value)).flag(value < 0);
    }

    /// The RBSP trailing bits, then the bytes.
    public byte[] finish() {
        bits(1, 1);
        return aligned();
    }

    /// Zeros to the next byte boundary, as VP9's `trailing_bits()` writes
    /// them, then the bytes.
    public byte[] aligned() {
        while (count != 0) {
            bits(1, 0);
        }
        return out.toByteArray();
    }
}
