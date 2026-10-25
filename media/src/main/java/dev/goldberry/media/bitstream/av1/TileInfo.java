package dev.goldberry.media.bitstream.av1;

import java.util.List;

/// How a frame is cut into tiles (§5.9.15), as Vulkan Video's
/// `StdVideoAV1TileInfo` wants it.
///
/// @param uniformTileSpacing `uniform_tile_spacing_flag`
/// @param tileColsLog2       `TileColsLog2`
/// @param tileRowsLog2       `TileRowsLog2`
/// @param miColStarts        `MiColStarts`: each tile column's first 4×4
///                           column, then `MiCols`; one more than the columns
/// @param miRowStarts        `MiRowStarts`, then `MiRows`
/// @param widthInSbsMinus1   each tile column's width in superblocks, less
///                           one: `width_in_sbs_minus_1` as coded, or as
///                           uniform spacing makes it
/// @param heightInSbsMinus1  each tile row's height in superblocks, less one
/// @param contextUpdateTileId `context_update_tile_id`
/// @param tileSizeBytesMinus1 `tile_size_bytes_minus_1`, 0 for a single tile
public record TileInfo(
        boolean uniformTileSpacing,
        int tileColsLog2,
        int tileRowsLog2,
        List<Integer> miColStarts,
        List<Integer> miRowStarts,
        List<Integer> widthInSbsMinus1,
        List<Integer> heightInSbsMinus1,
        int contextUpdateTileId,
        int tileSizeBytesMinus1) {

    public TileInfo {
        miColStarts = List.copyOf(miColStarts);
        miRowStarts = List.copyOf(miRowStarts);
        widthInSbsMinus1 = List.copyOf(widthInSbsMinus1);
        heightInSbsMinus1 = List.copyOf(heightInSbsMinus1);
    }

    /// `TileCols`.
    public int tileCols() {
        return widthInSbsMinus1.size();
    }

    /// `TileRows`.
    public int tileRows() {
        return heightInSbsMinus1.size();
    }

    /// `NumTiles`.
    public int tiles() {
        return tileCols() * tileRows();
    }

    /// `TileSizeBytes`: the bytes of each `tile_size_minus_1`.
    public int tileSizeBytes() {
        return tileSizeBytesMinus1 + 1;
    }
}
