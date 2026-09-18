package io.github.digitalsmile.goldberry.qr;

import java.util.Arrays;

/// A growable string of bits, written most-significant first.
///
/// Everything in ISO/IEC 18004 before the matrix is a bit string that is not a
/// whole number of bytes until the very end — a mode indicator is four bits, a
/// character count is nine to sixteen, three digits are ten. So the encoder
/// writes bits and asks for bytes once, which is [#toBytes()].
final class Bits {

    private byte[] packed;
    private int length;

    Bits(int expectedBits) {
        packed = new byte[Math.max(1, (expectedBits + 7) / 8)];
    }

    /// How many bits have been written.
    int length() {
        return length;
    }

    /// Appends the low `count` bits of `value`, most significant first.
    void append(int value, int count) {
        if (count < 0 || count > 31) {
            throw new IllegalArgumentException("a field is 0..31 bits wide, not " + count);
        }
        if (count < 31 && (value >>> count) != 0) {
            throw new IllegalArgumentException(value + " does not fit in " + count + " bits");
        }
        ensure(length + count);
        for (var i = count - 1; i >= 0; i--) {
            var bit = (value >>> i) & 1;
            if (bit != 0) {
                packed[length >>> 3] |= (byte) (0x80 >>> (length & 7));
            }
            length++;
        }
    }

    /// Appends zero bits until the length is a multiple of eight.
    void padToByte() {
        while ((length & 7) != 0) {
            append(0, 1);
        }
    }

    /// The bits as bytes, which requires that there is a whole number of them.
    byte[] toBytes() {
        if ((length & 7) != 0) {
            throw new IllegalStateException(length + " bits is not a whole number of codewords");
        }
        return Arrays.copyOf(packed, length / 8);
    }

    private void ensure(int bits) {
        var needed = (bits + 7) / 8;
        if (needed > packed.length) {
            packed = Arrays.copyOf(packed, Math.max(needed, packed.length * 2));
        }
    }
}
