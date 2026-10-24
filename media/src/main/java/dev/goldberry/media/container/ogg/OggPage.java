package dev.goldberry.media.container.ogg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.container.ByteReader;

/// One page of an Ogg stream (RFC 3533 §6): the header, and the packet
/// fragments the segment table cuts the body into.
///
/// A packet is a run of segments ending in one shorter than 255 bytes, so a
/// page's body holds whole packets, the tail of one begun on an earlier page
/// when the `continued` flag is set, and the head of one that runs on when
/// the last segment is 255 bytes long. The granule position is the stream's
/// sample count at the end of the last packet this page completes, or -1 on
/// a page that completes none.
///
/// The CRC is read and kept, not verified: a page that does not parse fails
/// as invalid data either way, and the Engine reads files, not radio.
///
/// @param position  where the page starts in the stream, in bytes
/// @param flags     the header type: [#CONTINUED], [#BOS], [#EOS]
/// @param granule   the granule position, or -1
/// @param serial    the logical stream's serial number
/// @param sequence  the page's number within its stream
/// @param crc       the checksum as written
/// @param segments  the segment table, lengths 0–255
/// @param body      the page's payload, the segments back to back
@SuppressWarnings("ArrayRecordComponent")
record OggPage(
        long position, int flags, long granule, long serial, long sequence, long crc, byte[] segments, byte[] body) {

    /// The capture pattern every page starts with.
    static final byte[] CAPTURE = {'O', 'g', 'g', 'S'};

    /// The header's first packet continues one begun on the page before.
    static final int CONTINUED = 0x01;

    /// The first page of a logical stream.
    static final int BOS = 0x02;

    /// The last page of a logical stream.
    static final int EOS = 0x04;

    /// The fixed part of the header: capture pattern through the segment count.
    static final int HEADER_SIZE = 27;

    /// What a page's segment table says of its payload: the packets it
    /// completes, in order, and the head of a packet that runs on to the next
    /// page, empty when none does. Whether the first completed packet (or the
    /// tail, when nothing completes) continues one from an earlier page is the
    /// header's [#CONTINUED] flag.
    @SuppressWarnings("ArrayRecordComponent")
    record Split(List<byte[]> completed, byte[] tail) {}

    boolean isContinued() {
        return (flags & CONTINUED) != 0;
    }

    boolean isFirst() {
        return (flags & BOS) != 0;
    }

    boolean isLast() {
        return (flags & EOS) != 0;
    }

    /// Where the page ends: the next page's position in a well-formed stream.
    long end() {
        return position + HEADER_SIZE + segments.length + body.length;
    }

    /// The packets this page completes, and the fragment it leaves open.
    Split split() {
        var completed = new ArrayList<byte[]>();
        var at = 0;
        var start = 0;
        for (var segment : segments) {
            var length = segment & 0xFF;
            at += length;
            if (length < 255) {
                completed.add(Arrays.copyOfRange(body, start, at));
                start = at;
            }
        }
        return new Split(completed, Arrays.copyOfRange(body, start, body.length));
    }

    /// The next page at or after `in`'s position, skipping anything that is
    /// not one — junk between pages, or a capture pattern inside a packet
    /// followed by a version that is not 0 — or null when the stream ends
    /// first.
    ///
    /// @throws MediaException [MediaError.InvalidData] for a page cut short by
    ///                        the end of the stream
    static @Nullable OggPage read(ByteReader in) {
        var position = -1L;
        while (position < 0) {
            if (!sync(in)) {
                return null;
            }
            if (in.atEnd()) {
                throw new MediaException(new MediaError.InvalidData("an Ogg page cut short at byte " + in.position()));
            }
            if (in.u8() == 0) {
                position = in.position() - CAPTURE.length - 1;
            }
        }
        var flags = in.u8();
        var granule = littleEndian(in.bytes(8));
        var serial = in.u32LittleEndian();
        var sequence = in.u32LittleEndian();
        var crc = in.u32LittleEndian();
        var count = in.u8();
        var segments = in.bytes(count);
        var size = 0;
        for (var segment : segments) {
            size += segment & 0xFF;
        }
        var body = in.bytes(size);
        return new OggPage(position, flags, granule, serial, sequence, crc, segments, body);
    }

    /// Moves `in` past the next capture pattern, skipping whatever is before
    /// it.
    ///
    /// @return false when the stream ends without one
    static boolean sync(ByteReader in) {
        var matched = 0;
        while (matched < CAPTURE.length) {
            if (in.atEnd()) {
                return false;
            }
            var b = in.u8();
            if (b == (CAPTURE[matched] & 0xFF)) {
                matched++;
            } else {
                matched = b == CAPTURE[0] ? 1 : 0;
            }
        }
        return true;
    }

    /// `bytes` as a little-endian signed 64-bit integer.
    static long littleEndian(byte[] bytes) {
        var value = 0L;
        for (var i = bytes.length - 1; i >= 0; i--) {
            value = (value << 8) | (bytes[i] & 0xFF);
        }
        return value;
    }
}
