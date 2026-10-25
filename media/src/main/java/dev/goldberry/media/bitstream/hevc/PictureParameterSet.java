package dev.goldberry.media.bitstream.hevc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.goldberry.media.bitstream.BitReader;

/// A picture parameter set (H.265 §7.3.2.3), every field Vulkan Video's
/// `StdVideoH265PictureParameterSet` reads, with the range extension's. The
/// multilayer, 3D and screen content extensions are refused.
///
/// The flags are kept as the specification names them; the numbers too, with
/// the lists of tile columns and rows and of chroma QP offsets as lists.
public record PictureParameterSet(
        int id,
        int spsId,
        boolean dependentSliceSegmentsEnabled,
        boolean outputFlagPresent,
        int numExtraSliceHeaderBits,
        boolean signDataHidingEnabled,
        boolean cabacInitPresent,
        int numRefIdxL0DefaultActiveMinus1,
        int numRefIdxL1DefaultActiveMinus1,
        int initQpMinus26,
        boolean constrainedIntraPred,
        boolean transformSkipEnabled,
        boolean cuQpDeltaEnabled,
        int diffCuQpDeltaDepth,
        int cbQpOffset,
        int crQpOffset,
        boolean sliceChromaQpOffsetsPresent,
        boolean weightedPred,
        boolean weightedBipred,
        boolean transquantBypassEnabled,
        boolean tilesEnabled,
        boolean entropyCodingSyncEnabled,
        int numTileColumnsMinus1,
        int numTileRowsMinus1,
        boolean uniformSpacing,
        List<Integer> columnWidthMinus1,
        List<Integer> rowHeightMinus1,
        boolean loopFilterAcrossTilesEnabled,
        boolean loopFilterAcrossSlicesEnabled,
        boolean deblockingFilterControlPresent,
        boolean deblockingFilterOverrideEnabled,
        boolean deblockingFilterDisabled,
        int betaOffsetDiv2,
        int tcOffsetDiv2,
        boolean scalingListDataPresent,
        Optional<ScalingLists> scalingLists,
        boolean listsModificationPresent,
        int log2ParallelMergeLevelMinus2,
        boolean sliceSegmentHeaderExtensionPresent,
        boolean extensionPresent,
        boolean rangeExtension,
        int log2MaxTransformSkipBlockSizeMinus2,
        boolean crossComponentPredictionEnabled,
        boolean chromaQpOffsetListEnabled,
        int diffCuChromaQpOffsetDepth,
        List<Integer> cbQpOffsetList,
        List<Integer> crQpOffsetList,
        int log2SaoOffsetScaleLuma,
        int log2SaoOffsetScaleChroma) {

    public PictureParameterSet {
        columnWidthMinus1 = List.copyOf(columnWidthMinus1);
        rowHeightMinus1 = List.copyOf(rowHeightMinus1);
        cbQpOffsetList = List.copyOf(cbQpOffsetList);
        crQpOffsetList = List.copyOf(crQpOffsetList);
    }

    /// The PPS whose RBSP, after the two-byte NAL header, is `rbsp`.
    ///
    /// @throws IllegalArgumentException when it is malformed or uses an
    ///                                  extension this does not read
    public static PictureParameterSet parse(byte[] rbsp) {
        var r = new BitReader(rbsp);
        var id = r.ue();
        var sps = r.ue();
        if (id > 63 || sps > 15) {
            throw new IllegalArgumentException("PPS " + id + " of SPS " + sps);
        }
        var dependent = r.flag();
        var output = r.flag();
        var extraBits = r.bits(3);
        var signHiding = r.flag();
        var cabacInit = r.flag();
        var l0 = r.ue();
        var l1 = r.ue();
        if (l0 > 14 || l1 > 14) {
            throw new IllegalArgumentException("default active references " + (l0 + 1) + " and " + (l1 + 1));
        }
        var initQp = r.se();
        var constrained = r.flag();
        var skip = r.flag();
        var cuQp = r.flag();
        var cuQpDepth = cuQp ? r.ue() : 0;
        var cb = r.se();
        var cr = r.se();
        var sliceOffsets = r.flag();
        var weighted = r.flag();
        var bipred = r.flag();
        var bypass = r.flag();
        var tiles = r.flag();
        var sync = r.flag();
        var columns = 0;
        var rows = 0;
        var uniform = true;
        var widths = new ArrayList<Integer>();
        var heights = new ArrayList<Integer>();
        var acrossTiles = true;
        if (tiles) {
            columns = r.ue();
            rows = r.ue();
            if (columns > 19 || rows > 21) {
                throw new IllegalArgumentException((columns + 1) + " by " + (rows + 1) + " tiles");
            }
            uniform = r.flag();
            if (!uniform) {
                for (var i = 0; i < columns; i++) {
                    widths.add(r.ue());
                }
                for (var i = 0; i < rows; i++) {
                    heights.add(r.ue());
                }
            }
            acrossTiles = r.flag();
        }
        var acrossSlices = r.flag();
        var deblockingControl = r.flag();
        var override = false;
        var disabled = false;
        var beta = 0;
        var tc = 0;
        if (deblockingControl) {
            override = r.flag();
            disabled = r.flag();
            if (!disabled) {
                beta = r.se();
                tc = r.se();
            }
        }
        var scalingPresent = r.flag();
        Optional<ScalingLists> lists = scalingPresent ? Optional.of(ScalingLists.read(r)) : Optional.empty();
        var modification = r.flag();
        var merge = r.ue();
        var headerExtension = r.flag();
        var extension = r.flag();
        var range = false;
        var skipSize = 0;
        var crossComponent = false;
        var chromaList = false;
        var chromaDepth = 0;
        var cbList = new ArrayList<Integer>();
        var crList = new ArrayList<Integer>();
        var saoLuma = 0;
        var saoChroma = 0;
        if (extension) {
            range = r.flag();
            var multilayer = r.flag();
            var threeD = r.flag();
            var scc = r.flag();
            r.skip(4); // pps_extension_4bits
            if (range) {
                if (skip) {
                    skipSize = r.ue();
                }
                crossComponent = r.flag();
                chromaList = r.flag();
                if (chromaList) {
                    chromaDepth = r.ue();
                    var length = r.ue() + 1;
                    if (length > 6) {
                        throw new IllegalArgumentException("a chroma QP offset list of " + length);
                    }
                    for (var i = 0; i < length; i++) {
                        cbList.add(r.se());
                        crList.add(r.se());
                    }
                }
                saoLuma = r.ue();
                saoChroma = r.ue();
            }
            if (multilayer || threeD || scc) {
                throw new IllegalArgumentException("a PPS with the "
                        + (multilayer ? "multilayer" : threeD ? "3D" : "screen content") + " extension");
            }
        }
        return new PictureParameterSet(
                id,
                sps,
                dependent,
                output,
                extraBits,
                signHiding,
                cabacInit,
                l0,
                l1,
                initQp,
                constrained,
                skip,
                cuQp,
                cuQpDepth,
                cb,
                cr,
                sliceOffsets,
                weighted,
                bipred,
                bypass,
                tiles,
                sync,
                columns,
                rows,
                uniform,
                widths,
                heights,
                acrossTiles,
                acrossSlices,
                deblockingControl,
                override,
                disabled,
                beta,
                tc,
                scalingPresent,
                lists,
                modification,
                merge,
                headerExtension,
                extension,
                range,
                skipSize,
                crossComponent,
                chromaList,
                chromaDepth,
                cbList,
                crList,
                saoLuma,
                saoChroma);
    }
}
