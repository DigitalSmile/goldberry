package io.github.digitalsmile.goldberry.qr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The field the parity is computed in — ISO/IEC 18004 §7.5.2.
///
/// [GaloisField] multiplies with logarithm tables because it is asked to a
/// hundred thousand times per large code. The reference it is checked against
/// here is the definition rather than another table: shift and reduce, one bit
/// at a time, which is what a field multiplication *is* and what the tables are
/// an optimisation of.
class GaloisFieldTest {

    /// `a × b` the slow way: schoolbook multiplication of two polynomials over
    /// GF(2), reduced modulo `x⁸ + x⁴ + x³ + x² + 1` as it goes.
    private static int schoolbook(int a, int b) {
        var result = 0;
        for (var i = 7; i >= 0; i--) {
            result = (result << 1) ^ ((result >>> 7) * 0x11D);
            if (((b >>> i) & 1) != 0) {
                result ^= a;
            }
        }
        return result & 0xFF;
    }

    @Test
    @DisplayName("every product in the field is the one the definition gives")
    void multiplicationMatchesTheDefinition() {
        for (var a = 0; a < 256; a++) {
            for (var b = 0; b < 256; b++) {
                assertEquals(schoolbook(a, b), GaloisField.multiply(a, b), a + " × " + b);
            }
        }
    }

    @Test
    @DisplayName("the powers of two run through all 255 non-zero elements")
    void twoIsAPrimitiveElement() {
        var seen = new boolean[256];
        for (var i = 0; i < 255; i++) {
            var value = GaloisField.power(i);
            assertFalse(seen[value], "2^" + i + " repeats a value");
            seen[value] = true;
        }

        assertFalse(seen[0], "zero is not a power of two");
        assertEquals(1, GaloisField.power(0));
        // The table is doubled so that a sum of two logarithms never has to be
        // reduced, which means 2^255 has to read as 2^0 rather than run off it.
        assertEquals(1, GaloisField.power(255));
    }

    @Test
    @DisplayName("a generator polynomial has the roots it is built from")
    void generatorHasItsRoots() {
        for (var degree : new int[] {7, 10, 13, 17, 30}) {
            var generator = ReedSolomon.generator(degree);

            // Evaluate the polynomial at each of 2^0 … 2^(degree-1). Every one
            // of them is a root by construction, and a polynomial whose roots
            // are wrong produces parity nothing can check.
            for (var i = 0; i < degree; i++) {
                var root = GaloisField.power(i);
                // The leading coefficient is implicitly 1 and is left off the
                // array, so evaluation starts from it.
                var value = 1;
                for (var coefficient : generator) {
                    value = GaloisField.multiply(value, root) ^ coefficient;
                }
                assertEquals(0, value, "2^" + i + " is not a root of the degree-" + degree + " generator");
            }
        }
    }

    @Test
    @DisplayName("parity added to data leaves a remainder of zero")
    void theCodewordIsDivisibleByTheGenerator() {
        var data = new byte[] {16, 32, 12, 86, 97, (byte) 128, (byte) 236, 17, (byte) 236, 17};
        var parity = ReedSolomon.remainder(data, 10);

        var codeword = new byte[data.length + parity.length];
        System.arraycopy(data, 0, codeword, 0, data.length);
        System.arraycopy(parity, 0, codeword, data.length, parity.length);

        assertArrayEquals(new byte[10], ReedSolomon.remainder(codeword, 10));
    }
}
