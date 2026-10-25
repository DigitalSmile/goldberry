package dev.goldberry.media.bitstream.h264;

import java.util.Optional;

import dev.goldberry.media.bitstream.BitReader;
import dev.goldberry.media.codec.ColorDescription;

/// `vui_parameters()` (H.264 §E.1.1), as Vulkan Video's
/// `StdVideoH264SequenceParameterSetVui` takes it, and the colour and reorder
/// depth a player wants from it.
public record Vui(
        boolean aspectRatioInfoPresent,
        int aspectRatioIdc,
        int sarWidth,
        int sarHeight,
        boolean overscanInfoPresent,
        boolean overscanAppropriate,
        boolean videoSignalTypePresent,
        int videoFormat,
        boolean fullRange,
        boolean colourDescriptionPresent,
        int colourPrimaries,
        int transferCharacteristics,
        int matrixCoefficients,
        boolean chromaLocInfoPresent,
        int chromaSampleLocTypeTop,
        int chromaSampleLocTypeBottom,
        boolean timingInfoPresent,
        long numUnitsInTick,
        long timeScale,
        boolean fixedFrameRate,
        Optional<Hrd> nalHrd,
        Optional<Hrd> vclHrd,
        boolean lowDelayHrd,
        boolean picStructPresent,
        boolean bitstreamRestriction,
        int maxNumReorderFrames,
        int maxDecFrameBuffering) {

    /// The primaries and transfer function, unspecified unless signalled.
    public ColorDescription colour() {
        return colourDescriptionPresent
                ? ColorDescription.fromH273(colourPrimaries, transferCharacteristics)
                : ColorDescription.UNSPECIFIED;
    }

    static Vui read(BitReader r) {
        var aspectPresent = r.flag();
        var aspect = 0;
        var sarWidth = 0;
        var sarHeight = 0;
        if (aspectPresent) {
            aspect = r.bits(8);
            if (aspect == 255) {
                sarWidth = r.bits(16);
                sarHeight = r.bits(16);
            }
        }
        var overscanPresent = r.flag();
        var overscan = overscanPresent && r.flag();
        var signal = r.flag();
        var format = 5;
        var full = false;
        var colour = false;
        var primaries = 2;
        var transfer = 2;
        var matrix = 2;
        if (signal) {
            format = r.bits(3);
            full = r.flag();
            colour = r.flag();
            if (colour) {
                primaries = r.bits(8);
                transfer = r.bits(8);
                matrix = r.bits(8);
            }
        }
        var chromaLoc = r.flag();
        var top = 0;
        var bottom = 0;
        if (chromaLoc) {
            top = r.ue();
            bottom = r.ue();
        }
        var timing = r.flag();
        var units = 0L;
        var scale = 0L;
        var fixed = false;
        if (timing) {
            units = r.bitsLong(32);
            scale = r.bitsLong(32);
            fixed = r.flag();
        }
        Optional<Hrd> nal = r.flag() ? Optional.of(Hrd.read(r)) : Optional.empty();
        Optional<Hrd> vcl = r.flag() ? Optional.of(Hrd.read(r)) : Optional.empty();
        var lowDelay = (nal.isPresent() || vcl.isPresent()) && r.flag();
        var picStruct = r.flag();
        var restriction = r.flag();
        var reorder = -1;
        var buffering = -1;
        if (restriction) {
            r.skip(1); // motion_vectors_over_pic_boundaries_flag
            r.ue(); // max_bytes_per_pic_denom
            r.ue(); // max_bits_per_mb_denom
            r.ue(); // log2_max_mv_length_horizontal
            r.ue(); // log2_max_mv_length_vertical
            reorder = r.ue();
            buffering = r.ue();
        }
        return new Vui(
                aspectPresent,
                aspect,
                sarWidth,
                sarHeight,
                overscanPresent,
                overscan,
                signal,
                format,
                full,
                colour,
                primaries,
                transfer,
                matrix,
                chromaLoc,
                top,
                bottom,
                timing,
                units,
                scale,
                fixed,
                nal,
                vcl,
                lowDelay,
                picStruct,
                restriction,
                reorder,
                buffering);
    }
}
