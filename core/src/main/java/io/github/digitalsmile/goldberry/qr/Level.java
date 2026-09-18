package io.github.digitalsmile.goldberry.qr;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/// How much of a code may be destroyed and still read — ISO/IEC 18004 §7.5.
///
/// Four levels, and the ordering of the enum is the ordering of the
/// specification's tables, not the ordering of the correction they give: the
/// two-bit field written into the format information is [#bits], which is a
/// different order again and is why it is written down rather than derived from
/// [#ordinal()].
///
/// | Level | Recovers about | Costs |
/// | ----- | -------------- | ----- |
/// | `L`   | 7%             | least |
/// | `M`   | 15%            |       |
/// | `Q`   | 25%            |       |
/// | `H`   | 30%            | most  |
///
/// [#M] is the default everywhere in this toolkit, because it is the level every
/// other QR encoder defaults to and the one a phone camera reads off a screen
/// without complaint. The argument for going higher is a code that will be
/// printed, folded and photographed; a code on a display is not that.
public enum Level {

    /// About 7% recovery — the most payload in the smallest code.
    L(0b01),

    /// About 15% recovery. The default.
    M(0b00),

    /// About 25% recovery.
    Q(0b11),

    /// About 30% recovery — the most robust, and the largest.
    H(0b10);

    private final int bits;

    Level(int bits) {
        this.bits = bits;
    }

    /// The two bits this level is spelled as in the format information.
    ///
    /// `01`, `00`, `11`, `10` — deliberately not the enum order. §7.9's table
    /// assigns them so that the four values differ in more than one bit, which
    /// is what lets a decoder that misread one of them still recover the level.
    public int bits() {
        return bits;
    }

    /// The level a document named, case-insensitively; null for anything else.
    ///
    /// Null rather than an exception because the caller is markup: a document is
    /// reloaded on every keystroke while it is being written, and a half-typed
    /// `level="Q"` should fall back rather than take the window down.
    public static @Nullable Level named(@Nullable String keyword) {
        if (keyword == null) {
            return null;
        }
        return switch (keyword.toUpperCase(Locale.ROOT)) {
            case "L" -> L;
            case "M" -> M;
            case "Q" -> Q;
            case "H" -> H;
            default -> null;
        };
    }
}
