package io.github.digitalsmile.goldberry.qr;

/// Arithmetic in GF(256), the field ISO/IEC 18004 §7.5.2 does Reed–Solomon in.
///
/// A codeword is a byte and a byte is an element of a 256-element field, so
/// addition is XOR and multiplication is a polynomial product reduced modulo the
/// primitive polynomial `x⁸ + x⁴ + x³ + x² + 1` — `0x11D`. That single constant
/// is the whole of what makes this field *this* field rather than one of the
/// other thirty; a QR encoder that used a different one would produce parity
/// nothing in the world could check.
///
/// ## Why there are tables
///
/// Multiplying two bytes by shifting and reducing is twenty-odd operations and
/// happens once per data codeword per parity codeword — about two hundred
/// thousand times for a large code. So the field is tabulated once: [#EXP] is
/// `2ⁱ` and [#LOG] is its inverse, and a product becomes two lookups, an add and
/// a third lookup. The tables are built by the same shift-and-reduce this
/// replaces, which is why there is no third copy of `0x11D` anywhere.
final class GaloisField {

    /// The field's primitive polynomial, `x⁸ + x⁴ + x³ + x² + 1`.
    private static final int PRIMITIVE = 0x11D;

    /// `EXP[i]` is `2ⁱ` in the field, for `i` in `0..254`, repeated once so a
    /// sum of two logarithms never has to be reduced modulo 255.
    private static final int[] EXP = new int[512];

    /// `LOG[x]` is the `i` with `EXP[i] == x`. `LOG[0]` is meaningless — zero has
    /// no logarithm — and is never read, because [#multiply] short-circuits.
    private static final int[] LOG = new int[256];

    static {
        var x = 1;
        for (var i = 0; i < 255; i++) {
            EXP[i] = x;
            LOG[x] = i;
            x <<= 1;
            if ((x & 0x100) != 0) {
                x ^= PRIMITIVE;
            }
        }
        for (var i = 255; i < 512; i++) {
            EXP[i] = EXP[i - 255];
        }
    }

    private GaloisField() {}

    /// `a × b` in the field.
    static int multiply(int a, int b) {
        if (a == 0 || b == 0) {
            return 0;
        }
        return EXP[LOG[a] + LOG[b]];
    }

    /// `2ⁱ` in the field, for `i` in `0..254`.
    static int power(int i) {
        return EXP[i];
    }
}
