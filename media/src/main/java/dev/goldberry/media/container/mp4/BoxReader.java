package dev.goldberry.media.container.mp4;

import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.container.ByteReader;

/// Box headers over a [ByteReader] (ISO/IEC 14496-12 §4.2): the size, the
/// type, and where the payload starts and ends.
///
/// A box's size is 32 bits, or 64 in a `largesize` that follows the type when
/// the 32-bit field is 1; a size of 0 says the box runs to the end of its
/// parent, which at the top level is the end of the file. A `uuid` box carries
/// its real type in sixteen more bytes, which this reader counts as header so
/// that the payload offsets stay right, and which the demuxer skips. A full
/// box starts its payload with a version byte and 24 bits of flags, which
/// [#fullBox()] reads.
final class BoxReader {

    /// One box: its type and where it lies in the stream.
    ///
    /// @param type        the four-character code
    /// @param headerStart the box's first byte
    /// @param dataStart   the payload's first byte
    /// @param end         the byte after the box, or [Long#MAX_VALUE] when the
    ///                    box runs to the end of a stream whose size is not
    ///                    known
    record Box(int type, long headerStart, long dataStart, long end) {

        /// The payload's length.
        long size() {
            return end - dataStart;
        }

        /// The type, for a message.
        String name() {
            return BoxTypes.name(type);
        }
    }

    /// A full box's first four bytes.
    record FullBox(int version, int flags) {}

    private final ByteReader in;

    BoxReader(ByteReader in) {
        this.in = in;
    }

    /// The reader the boxes are read from.
    ByteReader in() {
        return in;
    }

    /// Reads the header at the current position, inside a parent that ends at
    /// `parentEnd` ([Long#MAX_VALUE] at the top level of a stream whose size
    /// is not known).
    ///
    /// @throws MediaException [MediaError.InvalidData] when the size is smaller
    ///                        than the header, or the box runs past its parent
    Box next(long parentEnd) {
        var headerStart = in.position();
        var size = in.u32();
        var type = (int) in.u32();
        var headerLength = 8L;
        if (size == 1) {
            size = in.unsignedBigEndian(8);
            headerLength = 16;
        } else if (size == 0) {
            size = parentEnd == Long.MAX_VALUE ? in.size().orElse(Long.MAX_VALUE) : parentEnd;
            size = size == Long.MAX_VALUE ? Long.MAX_VALUE : size - headerStart;
        }
        if (type == BoxTypes.UUID) {
            in.skip(16);
            headerLength += 16;
        }
        if (size != Long.MAX_VALUE && size < headerLength) {
            throw new MediaException(new MediaError.InvalidData(
                    "a '" + BoxTypes.name(type) + "' box of " + size + " bytes at byte " + headerStart));
        }
        var end = size == Long.MAX_VALUE ? Long.MAX_VALUE : headerStart + size;
        if (end > parentEnd) {
            throw new MediaException(new MediaError.InvalidData("a '" + BoxTypes.name(type) + "' box at byte "
                    + headerStart + " runs past its parent, which ends at byte " + parentEnd));
        }
        return new Box(type, headerStart, headerStart + headerLength, end);
    }

    /// Whether another box can start before `parentEnd`: eight bytes fit, and
    /// the stream has not ended.
    boolean hasNext(long parentEnd) {
        if (parentEnd == Long.MAX_VALUE) {
            return !in.atEnd();
        }
        return in.position() + 8 <= parentEnd;
    }

    /// Reads a full box's version and flags.
    FullBox fullBox() {
        var value = in.u32();
        return new FullBox((int) (value >>> 24), (int) (value & 0xFFFFFF));
    }

    /// Moves to `position`: forward by skipping, which a stream that cannot
    /// seek still does, backward by seeking.
    void seekTo(long position) {
        var distance = position - in.position();
        if (distance > 0) {
            in.skip(distance);
        } else if (distance < 0) {
            in.seek(position);
        }
    }

    /// Passes over the rest of `box`.
    void skip(Box box) {
        if (box.end() == Long.MAX_VALUE) {
            // To the end of a stream of unknown size: there is nothing after.
            while (!in.atEnd()) {
                in.u8();
            }
            return;
        }
        seekTo(box.end());
    }

    /// The rest of `box`'s payload, on the heap. For the small boxes: a
    /// configuration record, a sample table.
    byte[] payload(Box box) {
        var left = box.end() - in.position();
        if (left < 0 || left > Integer.MAX_VALUE - 16) {
            throw new MediaException(new MediaError.InvalidData(
                    "a '" + box.name() + "' box of " + box.size() + " bytes at byte " + box.headerStart()));
        }
        return in.bytes((int) left);
    }
}
