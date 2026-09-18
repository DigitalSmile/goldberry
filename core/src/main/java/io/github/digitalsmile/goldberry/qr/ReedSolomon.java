package io.github.digitalsmile.goldberry.qr;

/// The error correction codewords for a block — ISO/IEC 18004 §7.5.2.
///
/// Reed–Solomon over [GaloisField]: the data codewords are read as a polynomial,
/// multiplied by `xⁿ` and divided by a generator polynomial, and the remainder
/// is the parity. `n` is how many parity codewords the version and level ask
/// for, and the generator is `(x − 2⁰)(x − 2¹)…(x − 2ⁿ⁻¹)` — so there is one
/// generator per `n` and it is built rather than tabulated.
///
/// This encodes and does not decode. A QR *decoder* is the interesting half of
/// Reed–Solomon — syndromes, the Berlekamp–Massey algorithm, Chien search — and
/// nothing in this toolkit reads a code.
final class ReedSolomon {

    private ReedSolomon() {}

    /// The `degree` error correction codewords for `data`.
    static byte[] remainder(byte[] data, int degree) {
        var generator = generator(degree);
        var result = new byte[degree];
        for (var b : data) {
            // Long division, one codeword at a time. The leading term of the
            // running remainder decides what multiple of the generator to
            // subtract -- and subtraction in this field is XOR, so there is no
            // borrow and no sign anywhere in here.
            var factor = (b ^ result[0]) & 0xFF;
            System.arraycopy(result, 1, result, 0, degree - 1);
            result[degree - 1] = 0;
            for (var i = 0; i < degree; i++) {
                result[i] ^= (byte) GaloisField.multiply(generator[i], factor);
            }
        }
        return result;
    }

    /// The coefficients of `(x − 2⁰)(x − 2¹)…(x − 2^(degree−1))`, highest power
    /// first and with the implicit leading 1 left off.
    ///
    /// Built by multiplying the roots in one at a time, which is `degree²`
    /// operations for a degree of at most 30 — cheaper than the table of 31
    /// polynomials it would otherwise be, and impossible to mistype.
    static int[] generator(int degree) {
        if (degree < 1 || degree > 255) {
            throw new IllegalArgumentException("a generator polynomial is 1..255 terms, not " + degree);
        }
        var result = new int[degree];
        result[degree - 1] = 1;
        var root = 1;
        for (var i = 0; i < degree; i++) {
            for (var j = 0; j < degree; j++) {
                result[j] = GaloisField.multiply(result[j], root);
                if (j + 1 < degree) {
                    result[j] ^= result[j + 1];
                }
            }
            root = GaloisField.multiply(root, 2);
        }
        return result;
    }
}
