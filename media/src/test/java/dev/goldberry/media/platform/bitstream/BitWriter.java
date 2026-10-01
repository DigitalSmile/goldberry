package dev.goldberry.media.platform.bitstream;

import java.io.ByteArrayOutputStream;

/// Writes the bits a [BitReader] reads: how the tests make parameter sets with
/// exactly the fields a case needs.
final class BitWriter {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private int current;
    private int count;

    /// `bits` bits of `value`, most significant first.
    BitWriter bits(int bits, long value) {
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

    BitWriter flag(boolean value) {
        return bits(1, value ? 1 : 0);
    }

    /// An unsigned Exp-Golomb code.
    BitWriter ue(long value) {
        var code = value + 1;
        var length = 64 - Long.numberOfLeadingZeros(code);
        return bits(length - 1, 0).bits(length, code);
    }

    /// A signed Exp-Golomb code.
    BitWriter se(long value) {
        return ue(value > 0 ? 2 * value - 1 : -2 * value);
    }

    /// The RBSP trailing bits, then the bytes.
    byte[] finish() {
        bits(1, 1);
        while (count != 0) {
            bits(1, 0);
        }
        return out.toByteArray();
    }
}
