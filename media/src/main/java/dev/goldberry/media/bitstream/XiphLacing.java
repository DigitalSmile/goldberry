package dev.goldberry.media.bitstream;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Several packets in one record, the way Matroska's `CodecPrivate` carries a
/// Vorbis stream's three headers: a count byte
/// of `packets - 1`, the length of every packet but the last as a run of
/// 255s plus a remainder, then the packets back to back. The last packet's
/// length is whatever is left, which is why it is not written.
///
/// A container that reads the headers as separate packets, as Ogg does, laces
/// them for the decoder; the decoder splits either container's record the
/// same way.
public final class XiphLacing {

    private XiphLacing() {}

    /// `packets` as one record.
    ///
    /// @throws IllegalArgumentException for no packets, or more than 256
    public static byte[] lace(List<byte[]> packets) {
        Objects.requireNonNull(packets, "packets");
        if (packets.isEmpty() || packets.size() > 256) {
            throw new IllegalArgumentException(packets.size() + " packets cannot be laced");
        }
        var out = new ByteArrayOutputStream();
        out.write(packets.size() - 1);
        for (var i = 0; i < packets.size() - 1; i++) {
            var length = packets.get(i).length;
            while (length >= 255) {
                out.write(255);
                length -= 255;
            }
            out.write(length);
        }
        for (var packet : packets) {
            out.writeBytes(packet);
        }
        return out.toByteArray();
    }

    /// The packets of `record`.
    ///
    /// @throws IllegalArgumentException when the record is cut short, or its
    ///                                  lengths do not fit it
    public static List<byte[]> split(byte[] record) {
        Objects.requireNonNull(record, "record");
        if (record.length == 0) {
            throw new IllegalArgumentException("an empty record");
        }
        var count = (record[0] & 0xFF) + 1;
        var lengths = new int[count];
        var at = 1;
        var total = 0;
        for (var i = 0; i < count - 1; i++) {
            var length = 0;
            while (true) {
                if (at >= record.length) {
                    throw new IllegalArgumentException("a laced record whose lengths run past its end");
                }
                var b = record[at++] & 0xFF;
                length += b;
                if (b < 255) {
                    break;
                }
            }
            lengths[i] = length;
            total += length;
        }
        if (at + total > record.length) {
            throw new IllegalArgumentException(
                    "a laced record of " + record.length + " bytes whose packets need " + (at + total));
        }
        lengths[count - 1] = record.length - at - total;
        var packets = new ArrayList<byte[]>(count);
        for (var length : lengths) {
            var packet = new byte[length];
            System.arraycopy(record, at, packet, 0, length);
            at += length;
            packets.add(packet);
        }
        return packets;
    }
}
