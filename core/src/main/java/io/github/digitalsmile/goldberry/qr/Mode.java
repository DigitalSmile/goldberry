package io.github.digitalsmile.goldberry.qr;

/// How a run of the payload is spelled in bits — ISO/IEC 18004 §7.4.
///
/// Three of the standard's modes, and the two that are not [#BYTE] are the whole
/// reason a short link fits a small code. `tg://login?token=…` is bytes and there
/// is nothing to be done about it, but a six-digit pairing code is 20 bits in
/// [#NUMERIC] and 48 in `BYTE`, and an upper-case URL is 5.5 bits a character in
/// [#ALPHANUMERIC] against 8.
///
/// Kanji mode is **not** here. It is Shift-JIS, which means carrying a
/// transcoding table for a payload this toolkit has no way to be handed — the
/// API takes a `String` and encodes its UTF-8, and UTF-8 Japanese is bytes.
public enum Mode {

    /// Digits only, three at a time in ten bits.
    NUMERIC(0b0001, 10, 12, 14),

    /// The 45 characters of §7.4.4's table, two at a time in eleven bits.
    ALPHANUMERIC(0b0010, 9, 11, 13),

    /// Anything, eight bits at a time. What a UTF-8 payload is.
    BYTE(0b0100, 8, 16, 16);

    /// §7.4.4's alphanumeric table, in value order: the index of a character in
    /// this string is the value the standard gives it.
    static final String ALPHANUMERIC_TABLE = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:";

    private final int indicator;
    private final int small;
    private final int medium;
    private final int large;

    Mode(int indicator, int small, int medium, int large) {
        this.indicator = indicator;
        this.small = small;
        this.medium = medium;
        this.large = large;
    }

    /// The four-bit mode indicator that opens a segment.
    public int indicator() {
        return indicator;
    }

    /// How many bits the character count takes at `version` — §7.4.1's table.
    ///
    /// Three widths for three version bands, because the count field has to be
    /// wide enough for the largest code in its band and no wider. This is the
    /// one piece of a segment's length that depends on the version, which is why
    /// [Version#smallestFor] has to try versions rather than compute one.
    public int countBits(int version) {
        if (version <= 9) {
            return small;
        }
        return version <= 26 ? medium : large;
    }

    /// Whether every character of `text` can be spelled in this mode.
    public boolean covers(String text) {
        for (var i = 0; i < text.length(); i++) {
            if (!covers(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean covers(char c) {
        return switch (this) {
            case NUMERIC -> c >= '0' && c <= '9';
            case ALPHANUMERIC -> ALPHANUMERIC_TABLE.indexOf(c) >= 0;
            case BYTE -> true;
        };
    }
}
