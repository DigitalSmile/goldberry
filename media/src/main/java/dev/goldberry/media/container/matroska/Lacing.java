package dev.goldberry.media.container.matroska;

import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.container.ByteReader;
import dev.goldberry.media.container.ebml.EbmlReader;

/// How several frames share one block: the three lacings of a Matroska
/// block's flags, and the sizes they encode.
///
/// A laced block's header, after the track number, timestamp and flags, is a
/// frame count less one and then the sizes of every frame but the last, which
/// takes what is left. Xiph lacing writes a size as bytes of 255 and a
/// remainder; EBML lacing writes the first size as a variable-length integer
/// and each next one as a signed difference from the one before; fixed lacing
/// writes nothing, the frames being equal.
public final class Lacing {

    /// The lacing bits of a block's flags, shifted down: none, Xiph, fixed,
    /// EBML.
    public static final int NONE = 0;

    public static final int XIPH = 1;
    public static final int FIXED = 2;
    public static final int EBML = 3;

    private Lacing() {}

    /// The lacing of a block's `flags` byte.
    public static int of(int flags) {
        return (flags >>> 1) & 0x03;
    }

    /// Reads the lace header from `in`, positioned right after the flags, and
    /// answers every frame's size. `payloadEnd` is where the block's payload
    /// ends; the last frame runs to it.
    ///
    /// @throws MediaException when the sizes do not fit the payload
    public static long[] read(ByteReader in, int lacing, long payloadEnd) {
        if (lacing == NONE) {
            return new long[] {remaining(in, payloadEnd, 0, "a block")};
        }
        var frames = in.u8() + 1;
        var sizes = new long[frames];
        var known = 0L;
        switch (lacing) {
            case XIPH -> {
                for (var i = 0; i < frames - 1; i++) {
                    var size = 0L;
                    int piece;
                    do {
                        piece = in.u8();
                        size += piece;
                    } while (piece == 255);
                    sizes[i] = size;
                    known += size;
                }
            }
            case EBML -> {
                var previous = 0L;
                for (var i = 0; i < frames - 1; i++) {
                    long size;
                    if (i == 0) {
                        size = vint(in);
                    } else {
                        var first = in.peek();
                        var length = EbmlReader.lengthOf(first);
                        var raw = vint(in);
                        // The signed range of a vint of `length` bytes is centred: a
                        // raw value of 2^(7n-1) - 1 is a difference of zero.
                        var bias = (1L << (7 * length - 1)) - 1;
                        size = previous + (raw - bias);
                    }
                    if (size < 0) {
                        throw new MediaException(
                                new MediaError.InvalidData("an EBML-laced frame of " + size + " bytes"));
                    }
                    sizes[i] = size;
                    known += size;
                    previous = size;
                }
            }
            case FIXED -> {
                var total = remaining(in, payloadEnd, 0, "a fixed-laced block");
                if (total % frames != 0) {
                    throw new MediaException(new MediaError.InvalidData(
                            "a fixed-laced block of " + total + " bytes does not split into " + frames + " frames"));
                }
                java.util.Arrays.fill(sizes, total / frames);
                return sizes;
            }
            default -> throw new IllegalArgumentException("lacing " + lacing);
        }
        sizes[frames - 1] = remaining(in, payloadEnd, known, "a laced block");
        return sizes;
    }

    /// A variable-length integer with its marker removed, as EBML lacing writes
    /// sizes; unlike an element size, all ones is a value here.
    private static long vint(ByteReader in) {
        var first = in.u8();
        var length = EbmlReader.lengthOf(first);
        if (length == 0) {
            throw new MediaException(new MediaError.InvalidData("not a lace size: 0x00"));
        }
        var value = (long) (first & (0xFF >>> length));
        for (var i = 1; i < length; i++) {
            value = (value << 8) | in.u8();
        }
        return value;
    }

    private static long remaining(ByteReader in, long payloadEnd, long known, String what) {
        var left = payloadEnd - in.position() - known;
        if (left < 0) {
            throw new MediaException(new MediaError.InvalidData(what + " names more bytes than it holds"));
        }
        return left;
    }
}
