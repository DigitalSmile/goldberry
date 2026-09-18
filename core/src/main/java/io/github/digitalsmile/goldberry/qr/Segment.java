package io.github.digitalsmile.goldberry.qr;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/// One run of payload in one [Mode] — ISO/IEC 18004 §7.4.
///
/// A code may in principle carry several of these, switching modes part way
/// through. This encoder emits exactly one, and the choice of which is
/// [#of(String)]: the narrowest mode that covers the **whole** payload.
///
/// ## Why one segment and not the optimal split
///
/// Splitting optimally is a shortest-path problem over the payload — every
/// position may or may not be a mode switch, and a switch costs a fresh mode
/// indicator and count field, so a two-digit run inside a byte payload is never
/// worth leaving byte mode for. It is a real algorithm with a real chance of
/// being subtly wrong, and what it buys on the payloads this exists for is
/// nothing: `tg://login?token=…` is lower-case and base64, so it is byte mode
/// from the first character to the last however it is split.
///
/// What the narrowest-covering-mode rule does buy is the case that occurs beside
/// it: a numeric pairing code, or an upper-case `HTTPS://…/ABC` device link.
/// Those drop a version or two, which at a fixed pixel size is a visibly coarser
/// and more scannable code.
///
/// A class rather than a record because one of its two payload fields is an
/// array, and a record component that is an array has equality nobody means.
final class Segment {

    private final Mode mode;

    /// The payload, for the two character modes.
    private final String text;

    /// The payload's UTF-8, for byte mode.
    private final byte[] data;

    private Segment(Mode mode, String text, byte[] data) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.text = Objects.requireNonNull(text, "text");
        this.data = Objects.requireNonNull(data, "data").clone();
    }

    /// The segment for `payload`: the narrowest mode that covers all of it.
    static Segment of(String payload) {
        var utf8 = payload.getBytes(StandardCharsets.UTF_8);
        if (Mode.NUMERIC.covers(payload)) {
            return new Segment(Mode.NUMERIC, payload, utf8);
        }
        if (Mode.ALPHANUMERIC.covers(payload)) {
            return new Segment(Mode.ALPHANUMERIC, payload, utf8);
        }
        return new Segment(Mode.BYTE, payload, utf8);
    }

    /// The segment for bytes nobody claims are text.
    static Segment of(byte[] payload) {
        return new Segment(Mode.BYTE, "", payload);
    }

    Mode mode() {
        return mode;
    }

    /// How many characters the count field reports.
    ///
    /// **Bytes** for byte mode and **characters** for the other two, which is the
    /// standard's rule and the one place the two halves of this are not
    /// interchangeable: a payload with an é in it is one character and two
    /// codewords, and a count of 1 would truncate it.
    int count() {
        return mode == Mode.BYTE ? data.length : text.length();
    }

    /// How many bits this segment occupies inside a code of `version` —
    /// indicator, count field and payload.
    int bitLength(int version) {
        return 4 + mode.countBits(version) + payloadBits();
    }

    private int payloadBits() {
        return switch (mode) {
            // Three digits in ten bits; a trailing pair takes seven and a
            // trailing single four, which is 3.33 bits a digit however it ends.
            case NUMERIC -> {
                var groups = text.length() / 3;
                var rest = text.length() % 3;
                yield groups * 10 + (rest == 0 ? 0 : rest * 3 + 1);
            }
            // Two characters in eleven bits, a trailing single in six.
            case ALPHANUMERIC -> text.length() / 2 * 11 + (text.length() % 2) * 6;
            case BYTE -> data.length * 8;
        };
    }

    /// Writes the indicator, the count and the payload into `bits`.
    void writeTo(Bits bits, int version) {
        bits.append(mode.indicator(), 4);
        bits.append(count(), mode.countBits(version));
        switch (mode) {
            case NUMERIC -> writeNumeric(bits);
            case ALPHANUMERIC -> writeAlphanumeric(bits);
            case BYTE -> {
                for (var b : data) {
                    bits.append(b & 0xFF, 8);
                }
            }
        }
    }

    private void writeNumeric(Bits bits) {
        var i = 0;
        while (i + 3 <= text.length()) {
            bits.append(Integer.parseInt(text, i, i + 3, 10), 10);
            i += 3;
        }
        var rest = text.length() - i;
        if (rest > 0) {
            bits.append(Integer.parseInt(text, i, text.length(), 10), rest * 3 + 1);
        }
    }

    private void writeAlphanumeric(Bits bits) {
        var i = 0;
        while (i + 2 <= text.length()) {
            var pair = value(text.charAt(i)) * 45 + value(text.charAt(i + 1));
            bits.append(pair, 11);
            i += 2;
        }
        if (i < text.length()) {
            bits.append(value(text.charAt(i)), 6);
        }
    }

    private static int value(char c) {
        return Mode.ALPHANUMERIC_TABLE.indexOf(c);
    }
}
