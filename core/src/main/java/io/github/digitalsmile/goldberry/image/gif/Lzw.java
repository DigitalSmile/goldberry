package io.github.digitalsmile.goldberry.image.gif;

import java.nio.ByteBuffer;

/// GIF's variable-width LZW, which is the whole of what makes the format worth a
/// decoder rather than a loop — [ADR-0329].
///
/// ## The variant
///
/// It is LZW with three GIF-specific rules, and every one of them is a way a
/// naive implementation goes wrong:
///
/// - **Codes are least-significant-bit first**, packed across byte boundaries and
///   across sub-block boundaries — a code may begin in one sub-block and end in
///   the next, so the bit reader has to be fed the concatenation rather than each
///   block in turn.
/// - **The code width grows**, from the minimum the file states plus one, up to
///   twelve bits, and steps up the moment the next free entry would not fit.
/// - **A clear code resets the dictionary** and may appear at any point, which is
///   what an encoder does when the table fills. A decoder that treated the table
///   as write-once would produce a picture that is correct for a while and then
///   noise.
///
/// The "code not yet in the table" case is real and not a corruption: an encoder
/// emits a code for a string it has just defined, and the decoder resolves it as
/// the previous string plus that string's own first character. Every LZW
/// implementation has this branch; one without it fails on perfectly ordinary
/// files.
final class Lzw {

    /// The widest code the format allows. The table therefore has at most 4096
    /// entries, which is what every array here is sized for.
    private static final int MAX_CODE_WIDTH = 12;

    private static final int MAX_CODES = 1 << MAX_CODE_WIDTH;

    private Lzw() {}

    /// Decodes the image data that follows an image descriptor.
    ///
    /// @param in     positioned at the minimum-code-size byte; left after the
    ///               sub-block chain's terminating zero
    /// @param pixels how many indices the frame has — `width * height`
    /// @return exactly `pixels` colour-table indices, zero-filled if the stream
    ///         ended early
    static byte[] decode(ByteBuffer in, int pixels) {
        var minimumCodeSize = Byte.toUnsignedInt(in.get());
        if (minimumCodeSize < 2 || minimumCodeSize > 11) {
            throw new GifFormatException(
                    "a GIF's minimum code size is between 2 and 11, and this one says " + minimumCodeSize);
        }
        var data = subBlocks(in);

        var clearCode = 1 << minimumCodeSize;
        var endCode = clearCode + 1;

        var prefix = new int[MAX_CODES];
        var suffix = new byte[MAX_CODES];
        var first = new byte[MAX_CODES];
        for (var code = 0; code < clearCode; code++) {
            prefix[code] = -1;
            suffix[code] = (byte) code;
            first[code] = (byte) code;
        }

        var out = new byte[pixels];
        var written = 0;
        // Scratch for walking a code's chain, which comes out backwards. One slot
        // per table entry, plus one: the longest legal chain is the whole table,
        // and the "code not yet defined" branch pushes a character before walking
        // it. A malformed stream with a cycle in its prefix chain is what the
        // bound below catches -- without it, that is an infinite loop.
        var stack = new byte[MAX_CODES + 1];

        var codeWidth = minimumCodeSize + 1;
        var next = clearCode + 2;
        var previous = -1;

        var bitPosition = 0L;
        var totalBits = (long) data.length * 8;

        while (written < pixels) {
            if (bitPosition + codeWidth > totalBits) {
                // The stream ended before the frame did. A truncated GIF is a
                // real thing to be handed; what is left stays zero, which is the
                // background index and is what every viewer shows.
                break;
            }
            var code = read(data, bitPosition, codeWidth);
            bitPosition += codeWidth;

            if (code == clearCode) {
                codeWidth = minimumCodeSize + 1;
                next = clearCode + 2;
                previous = -1;
                continue;
            }
            if (code == endCode) {
                break;
            }

            int current;
            var top = 0;
            if (code < next) {
                // A root, or an entry the table already has.
                current = code;
            } else if (previous >= 0) {
                // The code the encoder has just defined and we have not: it is
                // the previous string followed by that string's own first
                // character. See the note on this class.
                stack[top++] = first[previous];
                current = previous;
            } else {
                throw new GifFormatException("this GIF's LZW stream opens with a code that is not in the table");
            }

            while (current >= clearCode) {
                if (top >= MAX_CODES) {
                    throw new GifFormatException("this GIF's LZW table has a cycle in it");
                }
                stack[top++] = suffix[current];
                current = prefix[current];
            }
            stack[top++] = suffix[current];
            var head = suffix[current];

            // The chain came out backwards.
            for (var i = top - 1; i >= 0 && written < pixels; i--) {
                out[written++] = stack[i];
            }

            if (previous >= 0 && next < MAX_CODES) {
                prefix[next] = previous;
                suffix[next] = head;
                first[next] = first[previous];
                next++;
                if (next == (1 << codeWidth) && codeWidth < MAX_CODE_WIDTH) {
                    codeWidth++;
                }
            }
            previous = code;
        }

        // Consume whatever is left of the chain so the caller's buffer is
        // positioned after the frame, however early the stream stopped being
        // useful.
        return out;
    }

    /// `width` bits at `bitPosition`, least-significant first.
    private static int read(byte[] data, long bitPosition, int width) {
        var value = 0;
        for (var i = 0; i < width; i++) {
            var at = bitPosition + i;
            var bit = (data[(int) (at >>> 3)] >>> (int) (at & 7)) & 1;
            value |= bit << i;
        }
        return value;
    }

    /// The sub-block chain, concatenated — which is what a code spanning two
    /// blocks needs.
    ///
    /// The buffer is left after the terminating zero, so the caller can carry on
    /// reading blocks.
    private static byte[] subBlocks(ByteBuffer in) {
        var out = new byte[Math.max(256, in.remaining())];
        var length = 0;
        var size = Byte.toUnsignedInt(in.get());
        while (size != 0) {
            if (length + size > out.length) {
                var bigger = new byte[Math.max(out.length * 2, length + size)];
                System.arraycopy(out, 0, bigger, 0, length);
                out = bigger;
            }
            in.get(out, length, size);
            length += size;
            size = Byte.toUnsignedInt(in.get());
        }
        var exact = new byte[length];
        System.arraycopy(out, 0, exact, 0, length);
        return exact;
    }
}
