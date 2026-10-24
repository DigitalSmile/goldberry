package dev.goldberry.media.container.ogg;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import dev.goldberry.media.bitstream.OpusPackets;

/// Writes Ogg pages, for tests that need a page layout no fixture has: many
/// small pages to bisect over, and a packet cut across two of them.
///
/// Every page gets a real CRC (RFC 3533 §6: the 32-bit CRC with the
/// polynomial 0x04c11db7, direct, over the page with the field zeroed), so
/// that a checking reader would take these too.
final class OggWriter {

    private static final int[] CRC_TABLE = new int[256];

    static {
        for (var i = 0; i < 256; i++) {
            var r = i << 24;
            for (var j = 0; j < 8; j++) {
                r = (r & 0x8000_0000) != 0 ? (r << 1) ^ 0x04c1_1db7 : r << 1;
            }
            CRC_TABLE[i] = r;
        }
    }

    /// One page to write.
    ///
    /// @param flags     the header type
    /// @param granule   the granule position
    /// @param fragments the packets, or the parts of them, back to back
    /// @param openEnded whether the last fragment goes on in the next page: its
    ///                  length is then a multiple of 255 and gets no closing
    ///                  segment
    record Page(int flags, long granule, List<byte[]> fragments, boolean openEnded) {
        Page(int flags, long granule, List<byte[]> packets) {
            this(flags, granule, packets, false);
        }
    }

    private OggWriter() {}

    /// `pages` as one stream of `serial`, numbered from 0.
    static byte[] write(long serial, List<Page> pages) {
        var out = new ByteArrayOutputStream();
        for (var sequence = 0; sequence < pages.size(); sequence++) {
            var page = pages.get(sequence);
            var segments = new ByteArrayOutputStream();
            var body = new ByteArrayOutputStream();
            for (var i = 0; i < page.fragments().size(); i++) {
                var fragment = page.fragments().get(i);
                var length = fragment.length;
                while (length >= 255) {
                    segments.write(255);
                    length -= 255;
                }
                var last = i == page.fragments().size() - 1;
                if (!(last && page.openEnded())) {
                    segments.write(length);
                } else if (length != 0) {
                    throw new IllegalArgumentException("an open-ended fragment of " + fragment.length + " bytes");
                }
                body.writeBytes(fragment);
            }
            var header = new ByteArrayOutputStream();
            header.writeBytes(OggPage.CAPTURE);
            header.write(0);
            header.write(page.flags());
            littleEndian(header, page.granule(), 8);
            littleEndian(header, serial, 4);
            littleEndian(header, sequence, 4);
            littleEndian(header, 0, 4);
            header.write(segments.size());
            var bytes = new ByteArrayOutputStream();
            bytes.writeBytes(header.toByteArray());
            bytes.writeBytes(segments.toByteArray());
            bytes.writeBytes(body.toByteArray());
            var written = bytes.toByteArray();
            var crc = crc(written);
            for (var i = 0; i < 4; i++) {
                written[22 + i] = (byte) (crc >>> (8 * i));
            }
            out.writeBytes(written);
        }
        return out.toByteArray();
    }

    /// The packets of an Ogg file's one stream, in order, headers first.
    static List<byte[]> packets(byte[] file) {
        var packets = new ArrayList<byte[]>();
        var at = 0;
        var fragment = new ByteArrayOutputStream();
        while (at < file.length) {
            var count = file[at + 26] & 0xFF;
            var body = at + OggPage.HEADER_SIZE + count;
            for (var i = 0; i < count; i++) {
                var length = file[at + OggPage.HEADER_SIZE + i] & 0xFF;
                fragment.write(file, body, length);
                body += length;
                if (length < 255) {
                    packets.add(fragment.toByteArray());
                    fragment.reset();
                }
            }
            at = body;
        }
        return packets;
    }

    /// An Ogg Opus stream of `file`'s packets with `perPage` audio packets a
    /// page: the two headers on a page each, then the audio, every page's
    /// granule the samples played by its end plus the pre-skip, and the last
    /// page's `granule`, which ends the stream where the file did.
    static byte[] repagedOpus(byte[] file, int perPage, long lastGranule) {
        var packets = packets(file);
        var pages = new ArrayList<Page>();
        pages.add(new Page(OggPage.BOS, 0, List.of(packets.get(0))));
        pages.add(new Page(0, 0, List.of(packets.get(1))));
        var position = 0L;
        var audio = packets.subList(2, packets.size());
        for (var from = 0; from < audio.size(); from += perPage) {
            var group = audio.subList(from, Math.min(from + perPage, audio.size()));
            for (var packet : group) {
                position += OpusPackets.samples(packet);
            }
            var last = from + perPage >= audio.size();
            pages.add(new Page(last ? OggPage.EOS : 0, last ? lastGranule : position, group));
        }
        return write(1234, pages);
    }

    private static void littleEndian(ByteArrayOutputStream out, long value, int count) {
        for (var i = 0; i < count; i++) {
            out.write((int) (value >>> (8 * i)) & 0xFF);
        }
    }

    static int crc(byte[] page) {
        var crc = 0;
        for (var b : page) {
            crc = (crc << 8) ^ CRC_TABLE[((crc >>> 24) ^ b) & 0xFF];
        }
        return crc;
    }
}
