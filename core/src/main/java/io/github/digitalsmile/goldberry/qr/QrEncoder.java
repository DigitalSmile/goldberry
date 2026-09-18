package io.github.digitalsmile.goldberry.qr;

import java.util.Objects;

/// ISO/IEC 18004, from a payload to a [QrMatrix].
///
/// ```java
/// var code = QrEncoder.encode("tg://login?token=" + token, Level.M);
/// ```
///
/// ## What it does, in the standard's order
///
/// 1. **Mode.** The narrowest of §7.4's modes that covers the whole payload —
///    numeric, alphanumeric, or the bytes of its UTF-8 ([Segment]).
/// 2. **Version.** The smallest of the forty that holds the segment at the level
///    asked for ([Version]). Not the smallest that holds the *bytes*: the
///    character count field is wider in a larger code, so the question has to be
///    asked version by version.
/// 3. **Codewords.** Terminator, pad to a byte, then alternating `11101100` and
///    `00010001` until the data region is full — §7.4.10's pad codewords, which
///    exist so that the error correction has something to protect rather than a
///    block of zeroes.
/// 4. **Reed–Solomon.** The data split into blocks of two lengths, parity for
///    each ([ReedSolomon]), and the whole lot interleaved: codeword *i* of every
///    block, then codeword *i + 1*, so a scratch across the code damages a
///    little of each block instead of destroying one.
/// 5. **Placement.** Function patterns, then the data in §7.7.3's zigzag
///    ([Grid]).
/// 6. **Mask.** All eight applied and scored by §7.8.3's four penalty rules; the
///    lowest wins, and ties go to the lower mask number.
///
/// ## What it is not
///
/// A decoder. Nothing in this toolkit reads a QR code, and the interesting half
/// of Reed–Solomon is the half that corrects errors rather than the half that
/// produces them.
///
/// Structured append, ECI, and Kanji mode are all absent. The first two are for
/// payloads split across several codes and for declaring a character set other
/// than the default, and the default is what a `String` is once it is UTF-8;
/// Kanji mode is Shift-JIS, which this API has no way to be handed.
public final class QrEncoder {

    /// §7.4.10's two pad codewords, applied alternately.
    private static final int[] PAD = {0xEC, 0x11};

    private QrEncoder() {}

    /// The smallest code holding `payload`'s UTF-8 at `level`.
    ///
    /// @throws IllegalArgumentException if it does not fit in version 40, which
    ///         is the largest code the standard defines
    public static QrMatrix encode(String payload, Level level) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(level, "level");
        return encode(Segment.of(payload), level, payload.length());
    }

    /// The same, for bytes that are not text.
    ///
    /// @throws IllegalArgumentException if they do not fit in version 40
    public static QrMatrix encode(byte[] payload, Level level) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(level, "level");
        return encode(Segment.of(payload), level, payload.length);
    }

    private static QrMatrix encode(Segment segment, Level level, int payloadLength) {
        var version = Version.smallestFor(segment, level);
        if (version < 0) {
            throw new IllegalArgumentException("a payload of " + payloadLength
                    + " does not fit in a QR code at level " + level
                    + ": version 40 holds " + Version.dataCodewords(Version.MAX, level)
                    + " codewords and this needs " + (segment.bitLength(Version.MAX) + 7) / 8);
        }
        var grid = new Grid(version);
        grid.drawFunctionPatterns(level);
        grid.drawCodewords(interleave(codewords(segment, version, level), version, level));
        return chooseMask(grid, level);
    }

    /// The data codewords: the segment, a terminator, and padding to the exact
    /// capacity of the version.
    static byte[] codewords(Segment segment, int version, Level level) {
        var capacity = Version.dataCodewords(version, level) * 8;
        var bits = new Bits(capacity);
        segment.writeTo(bits, version);
        // Up to four zero bits saying there is no further segment. Fewer than
        // four when there is no room, which is legal and is why this is a
        // minimum rather than a fixed field.
        bits.append(0, Math.min(4, capacity - bits.length()));
        bits.padToByte();
        var bytes = bits.toBytes();
        var full = new byte[capacity / 8];
        System.arraycopy(bytes, 0, full, 0, bytes.length);
        for (var i = bytes.length; i < full.length; i++) {
            full[i] = (byte) PAD[(i - bytes.length) % 2];
        }
        return full;
    }

    /// §7.6's block structure: the data split, each block given its parity, and
    /// the result interleaved into the order the modules are laid down in.
    ///
    /// The blocks come in two lengths — the shorter ones first — because the
    /// data rarely divides evenly. Interleaving reads column-wise across the
    /// blocks, and the short blocks simply have nothing in the last column,
    /// which is the whole of why the two loops below differ.
    static byte[] interleave(byte[] data, int version, Level level) {
        var blockCount = Version.blocks(version, level);
        var eccLength = Version.eccPerBlock(version, level);
        var shortLength = data.length / blockCount;
        var longBlocks = data.length % blockCount;

        var blocks = new byte[blockCount][];
        var parities = new byte[blockCount][];
        var read = 0;
        for (var i = 0; i < blockCount; i++) {
            var length = shortLength + (i >= blockCount - longBlocks ? 1 : 0);
            var block = new byte[length];
            System.arraycopy(data, read, block, 0, length);
            read += length;
            blocks[i] = block;
            parities[i] = ReedSolomon.remainder(block, eccLength);
        }

        var result = new byte[data.length + eccLength * blockCount];
        var written = 0;
        for (var i = 0; i <= shortLength; i++) {
            for (var b = 0; b < blockCount; b++) {
                if (i < blocks[b].length) {
                    result[written++] = blocks[b][i];
                }
            }
        }
        for (var i = 0; i < eccLength; i++) {
            for (var b = 0; b < blockCount; b++) {
                result[written++] = parities[b][i];
            }
        }
        return result;
    }

    /// Applies each of the eight masks, scores the result, and keeps the best.
    ///
    /// In place rather than on eight copies: a mask is its own inverse over the
    /// data modules, so trying one is apply, score, apply again. The format
    /// information has to be rewritten each time too, because it *names* the
    /// mask and a scoring pass that left the previous mask's format bits in
    /// place would be scoring a grid no decoder would ever see.
    private static QrMatrix chooseMask(Grid grid, Level level) {
        var best = 0;
        var bestScore = Integer.MAX_VALUE;
        for (var mask = 0; mask < 8; mask++) {
            grid.drawFormat(level, mask);
            grid.applyMask(mask);
            var score = grid.penalty();
            grid.applyMask(mask);
            if (score < bestScore) {
                bestScore = score;
                best = mask;
            }
        }
        grid.drawFormat(level, best);
        grid.applyMask(best);
        return grid.freeze(level, best);
    }
}
