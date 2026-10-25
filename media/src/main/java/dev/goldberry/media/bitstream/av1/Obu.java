package dev.goldberry.media.bitstream.av1;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.media.bitstream.BitReader;

/// Where one OBU lies in the buffer it was found in: a temporal unit as a
/// container hands it over, or the `configOBUs` of an `av1C` record.
///
/// What a hardware decoder is told in bytes is counted from here: Vulkan
/// Video's frame header offset is the [#offset] of the frame header or frame
/// OBU, and each tile's offset is that plus the tile's own [TileGroup.Tile#offset].
///
/// @param header      its header
/// @param offset      the byte its header starts at, in the buffer
/// @param headerSize  the bytes before its payload: the header, its extension,
///                    and `obu_size` when there is one
/// @param payloadSize `obu_size`: the bytes of its payload
public record Obu(ObuHeader header, int offset, int headerSize, int payloadSize) {

    public Obu {
        Objects.requireNonNull(header, "header");
    }

    /// The byte its payload starts at, in the buffer.
    public int payloadOffset() {
        return offset + headerSize;
    }

    /// Its whole length in bytes, header and payload.
    public int size() {
        return headerSize + payloadSize;
    }

    /// The byte after it, in the buffer.
    public int end() {
        return offset + size();
    }

    /// The OBUs of `data`, in order: a temporal unit in the low overhead
    /// bitstream format (§5.2), in which each OBU but the last states its size.
    ///
    /// @throws IllegalArgumentException when an OBU is malformed or runs past
    ///                                  the end
    public static List<Obu> split(byte[] data) {
        return split(data, 0, data.length);
    }

    /// The OBUs of the `length` bytes of `data` from `from`, in order; their
    /// offsets are in `data`.
    ///
    /// @throws IllegalArgumentException when an OBU is malformed or runs past
    ///                                  the end
    public static List<Obu> split(byte[] data, int from, int length) {
        Objects.checkFromIndexSize(from, length, data.length);
        var obus = new ArrayList<Obu>();
        var at = from;
        var end = from + length;
        while (at < end) {
            var reader = new BitReader(data, at, end - at);
            var s = new Syntax(reader, null);
            var header = ObuHeader.read(s);
            long size;
            if (header.hasSizeField()) {
                size = s.leb128("obu_size");
            } else {
                size = (long) end - at - header.length();
            }
            var headerSize = (int) (reader.position() / 8);
            if (size < 0 || size > end - at - headerSize) {
                throw new IllegalArgumentException(
                        "an OBU of " + size + " bytes at byte " + (at - from) + " of " + length);
            }
            obus.add(new Obu(header, at, headerSize, (int) size));
            at += headerSize + (int) size;
        }
        return obus;
    }

    /// A reader of the OBU in `data`, at the first bit of its payload, its bit
    /// positions counted from the first bit of its header as FFmpeg counts
    /// them. With a trace the header is read again, for the trace to have it.
    Syntax payload(byte[] data, Syntax.@Nullable Trace trace) {
        var s = new Syntax(new BitReader(data, offset, size()), trace);
        if (trace == null) {
            s.skip(8L * headerSize);
        } else {
            ObuHeader.read(s);
            if (header.hasSizeField()) {
                s.leb128("obu_size");
            }
        }
        return s;
    }
}
