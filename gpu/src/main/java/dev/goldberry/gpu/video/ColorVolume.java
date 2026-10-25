package dev.goldberry.gpu.video;

import java.util.Objects;

import dev.goldberry.gpu.render.ToneMapping;

/// The colour a picture's R'G'B' is in, past its matrix and range: its
/// primaries, its transfer function and, for HDR, the brightest it reaches.
///
/// The layer draws in SDR with BT.709 primaries. A picture in [#SDR] is drawn
/// as it is converted; any other is converted to linear light, into BT.709's
/// primaries, tone-mapped from its peak down to SDR's reference white of 203
/// nits when it is HDR, and encoded again ([ToneMapping]).
///
/// @param primaries the RGB primaries
/// @param transfer  the transfer function
/// @param peakNits  the brightest the picture reaches, in nits: the mastering
///                  display's or the content's for PQ, 1000 for HLG; unused for
///                  SDR
public record ColorVolume(Primaries primaries, Transfer transfer, double peakNits) {

    /// SDR in BT.709 primaries, drawn as it is converted.
    public static final ColorVolume SDR = new ColorVolume(Primaries.BT709, Transfer.SDR, ToneMapping.REFERENCE_WHITE);

    /// The RGB primaries.
    public enum Primaries {
        /// BT.709's, sRGB's: the layer's own.
        BT709,
        /// BT.2020's.
        BT2020,
        /// DCI-P3's with the D65 white point.
        DISPLAY_P3
    }

    /// The transfer function.
    public enum Transfer {
        /// A camera curve displayed through BT.1886's gamma of 2.4.
        SDR,
        /// SMPTE ST 2084.
        PQ,
        /// ARIB STD-B67, with BT.2100's display OOTF at a 1000-nit peak.
        HLG
    }

    public ColorVolume {
        Objects.requireNonNull(primaries, "primaries");
        Objects.requireNonNull(transfer, "transfer");
        if (!(peakNits > 0) || !Double.isFinite(peakNits)) {
            throw new IllegalArgumentException("a peak of " + peakNits + " nits");
        }
    }

    /// Whether the layer draws this as it is converted: SDR in BT.709.
    public boolean isSdr() {
        return primaries == Primaries.BT709 && transfer == Transfer.SDR;
    }

    /// The shaders' arithmetic for this volume.
    ToneMapping toneMapping() {
        var gamut = switch (primaries) {
            case BT709 -> ToneMapping.Gamut.BT709;
            case BT2020 -> ToneMapping.Gamut.BT2020;
            case DISPLAY_P3 -> ToneMapping.Gamut.DISPLAY_P3;
        };
        var curve = switch (transfer) {
            case SDR -> ToneMapping.Curve.SDR;
            case PQ -> ToneMapping.Curve.PQ;
            case HLG -> ToneMapping.Curve.HLG;
        };
        return new ToneMapping(gamut, curve, peakNits);
    }
}
