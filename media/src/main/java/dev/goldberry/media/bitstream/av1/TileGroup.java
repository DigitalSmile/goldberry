package dev.goldberry.media.bitstream.av1;

import java.util.ArrayList;
import java.util.List;

/// A tile group (§5.11.1): a run of a frame's tiles, in raster order, and
/// where each one's data lies, as Vulkan Video's tile offsets and sizes want
/// it.
///
/// @param tileStartAndEndPresent `tile_start_and_end_present_flag`
/// @param tgStart                `tg_start`: its first tile
/// @param tgEnd                  `tg_end`: its last tile
/// @param tiles                  each of its tiles, `tg_start` to `tg_end`
/// @param last                   whether its last tile is the frame's last,
///                               so the frame is complete
public record TileGroup(boolean tileStartAndEndPresent, int tgStart, int tgEnd, List<Tile> tiles, boolean last)
        implements ObuContent {

    /// One tile's data.
    ///
    /// @param number its `TileNum`, in raster order over the frame
    /// @param row    its tile row
    /// @param column its tile column
    /// @param offset the byte its data starts at, from the first byte of the
    ///               OBU it is in: past `tile_size_minus_1`, when there is one
    /// @param size   the bytes of its data
    public record Tile(int number, int row, int column, int offset, int size) {}

    public TileGroup {
        tiles = List.copyOf(tiles);
    }

    /// The tile group `s` is at, byte aligned, in the OBU `obu` of `data`.
    static TileGroup read(Syntax s, byte[] data, Obu obu, FrameHeader header) {
        s.heading("Tile Group");
        var info = header.tileInfo();
        var count = info.tiles();
        var present = count > 1 && s.flag("tile_start_and_end_present_flag");
        var start = 0;
        var end = count - 1;
        if (present) {
            var bits = info.tileColsLog2() + info.tileRowsLog2();
            start = s.f(bits, "tg_start");
            end = s.f(bits, "tg_end");
            if (end < start || end >= count) {
                throw new IllegalArgumentException("tiles " + start + " to " + end + " of " + count);
            }
        }
        s.byteAlignment();
        var at = (int) (s.position() / 8);
        var stop = obu.size();
        var sizeBytes = info.tileSizeBytes();
        var tiles = new ArrayList<Tile>(end - start + 1);
        for (var number = start; number <= end; number++) {
            int size;
            if (number == end) {
                size = stop - at;
            } else {
                if (sizeBytes > stop - at) {
                    throw new IllegalArgumentException("tile " + number + "'s size runs past the tile group");
                }
                long minus1 = 0;
                for (var i = 0; i < sizeBytes; i++) {
                    minus1 |= (long) (data[obu.offset() + at + i] & 0xFF) << (8 * i);
                }
                at += sizeBytes;
                if (minus1 + 1 >= stop - at) {
                    throw new IllegalArgumentException(
                            "tile " + number + " of " + (minus1 + 1) + " bytes runs past the tile group");
                }
                size = (int) minus1 + 1;
            }
            if (size <= 0) {
                throw new IllegalArgumentException("tile " + number + " has no data");
            }
            tiles.add(new Tile(number, number / info.tileCols(), number % info.tileCols(), at, size));
            at += size;
        }
        return new TileGroup(present, start, end, tiles, end == count - 1);
    }
}
