package dev.goldberry.media.codec;

import java.util.Objects;
import java.util.OptionalInt;

/// What a picture's colours mean beyond its matrix and range: the primaries its
/// RGB is in, the transfer function that encodes light, and, for HDR, how
/// bright it was mastered.
///
/// Signalled as ITU-T H.273 numbers it: by the codec (AV1's sequence header,
/// H.264's and HEVC's VUI) or by the container (Matroska's `Colour`, MP4's
/// `colr`, `mdcv` and `clli`). A part nobody signalled is
/// [Primaries#UNSPECIFIED] or [Transfer#UNSPECIFIED], and [#orElse] fills it
/// from another source; the present path draws what is still unspecified as
/// SDR in BT.709 primaries, which is what an untagged picture has always been
/// drawn as.
///
/// The present path draws in SDR, BT.709 primaries: an HDR picture
/// ([Transfer#PQ], [Transfer#HLG]) is tone-mapped down to it, and a picture in
/// wider primaries is converted into it, on the GPU and on the CPU alike.
///
/// @param primaries     the RGB primaries
/// @param transfer      the transfer function
/// @param masteringPeak the brightest the mastering display showed, in
///                      candelas a square metre (nits), when stated
/// @param contentPeak   the brightest pixel of the content (`MaxCLL`), in nits,
///                      when stated
///
/// Read more: [Bringing a codec](https://goldberry.dev/docs/components/media.html#bringing-a-codec).
public record ColorDescription(
        Primaries primaries, Transfer transfer, OptionalInt masteringPeak, OptionalInt contentPeak) {

    /// Nothing signalled.
    public static final ColorDescription UNSPECIFIED =
            new ColorDescription(Primaries.UNSPECIFIED, Transfer.UNSPECIFIED, OptionalInt.empty(), OptionalInt.empty());

    /// SDR in BT.709 primaries: what the present path draws in, and what an
    /// unspecified description is drawn as.
    public static final ColorDescription SDR =
            new ColorDescription(Primaries.BT709, Transfer.SDR, OptionalInt.empty(), OptionalInt.empty());

    /// How bright SDR's reference white is, in nits: ITU-R BT.2408's 203, which
    /// HDR's diffuse white is graded to and which a tone-mapped picture's
    /// brightest white becomes.
    public static final int REFERENCE_WHITE = 203;

    /// What a PQ picture that states no peak was mastered to, in nits, and what
    /// an HLG picture's nominal peak is (ITU-R BT.2100).
    public static final int DEFAULT_HDR_PEAK = 1000;

    /// The RGB primaries, by the gamut the present path tells apart.
    public enum Primaries {
        /// Not signalled.
        UNSPECIFIED,
        /// BT.709's, which sRGB shares; BT.601's (525 and 625 lines) and
        /// SMPTE 240M's are drawn as these, as every player draws them.
        BT709,
        /// BT.2020's, UHD and HDR video's.
        BT2020,
        /// DCI-P3 with the D65 white point (SMPTE EG 432-1, "Display P3"); the
        /// cinema white point of SMPTE RP 431-2 is drawn as D65.
        DISPLAY_P3;

        /// The primaries H.273's `ColourPrimaries` code `code` names.
        public static Primaries fromH273(int code) {
            return switch (code) {
                case 1, 5, 6, 7 -> BT709;
                case 9 -> BT2020;
                case 11, 12 -> DISPLAY_P3;
                default -> UNSPECIFIED;
            };
        }
    }

    /// The transfer function, by what the present path does with it.
    public enum Transfer {
        /// Not signalled.
        UNSPECIFIED,
        /// An SDR camera curve: BT.709's, BT.601's, BT.2020's, sRGB's and the
        /// plain gammas, all displayed through BT.1886's gamma of 2.4.
        SDR,
        /// SMPTE ST 2084, perceptual quantisation: absolute light up to 10 000
        /// nits. HDR10's.
        PQ,
        /// ARIB STD-B67, hybrid log-gamma: relative scene light, BT.2100's
        /// broadcast HDR.
        HLG;

        /// The transfer function H.273's `TransferCharacteristics` code `code`
        /// names.
        public static Transfer fromH273(int code) {
            return switch (code) {
                case 1, 4, 5, 6, 7, 8, 13, 14, 15 -> SDR;
                case 16 -> PQ;
                case 18 -> HLG;
                default -> UNSPECIFIED;
            };
        }
    }

    public ColorDescription {
        Objects.requireNonNull(primaries, "primaries");
        Objects.requireNonNull(transfer, "transfer");
        Objects.requireNonNull(masteringPeak, "masteringPeak");
        Objects.requireNonNull(contentPeak, "contentPeak");
        if (masteringPeak.isPresent() && masteringPeak.getAsInt() <= 0) {
            throw new IllegalArgumentException("a mastering peak of " + masteringPeak.getAsInt() + " nits");
        }
        if (contentPeak.isPresent() && contentPeak.getAsInt() <= 0) {
            throw new IllegalArgumentException("a content peak of " + contentPeak.getAsInt() + " nits");
        }
    }

    /// The primaries and transfer function H.273's codes name, with no light
    /// levels.
    public static ColorDescription fromH273(int primaries, int transfer) {
        return new ColorDescription(
                Primaries.fromH273(primaries), Transfer.fromH273(transfer), OptionalInt.empty(), OptionalInt.empty());
    }

    /// This description with its light levels: a peak that is not positive is
    /// read as not stated, as a stream that writes zero means.
    public ColorDescription withLightLevels(int masteringPeakNits, int contentPeakNits) {
        return new ColorDescription(
                primaries,
                transfer,
                masteringPeakNits > 0 ? OptionalInt.of(masteringPeakNits) : masteringPeak,
                contentPeakNits > 0 ? OptionalInt.of(contentPeakNits) : contentPeak);
    }

    /// This description, with what it leaves unspecified taken from `fallback`:
    /// a frame's own word first, then its track's.
    public ColorDescription orElse(ColorDescription fallback) {
        Objects.requireNonNull(fallback, "fallback");
        return new ColorDescription(
                primaries == Primaries.UNSPECIFIED ? fallback.primaries : primaries,
                transfer == Transfer.UNSPECIFIED ? fallback.transfer : transfer,
                masteringPeak.isPresent() ? masteringPeak : fallback.masteringPeak,
                contentPeak.isPresent() ? contentPeak : fallback.contentPeak);
    }

    /// Whether the transfer function is an HDR one, which the present path
    /// tone-maps.
    public boolean isHdr() {
        return transfer == Transfer.PQ || transfer == Transfer.HLG;
    }

    /// Whether the present path draws this as it is: SDR in BT.709 primaries,
    /// with what is unspecified read as those.
    public boolean isPlainSdr() {
        return (primaries == Primaries.UNSPECIFIED || primaries == Primaries.BT709)
                && (transfer == Transfer.UNSPECIFIED || transfer == Transfer.SDR);
    }

    /// The brightest the picture is taken to reach, in nits: for PQ the
    /// mastering display's peak, else the content's, else
    /// [#DEFAULT_HDR_PEAK]; for HLG [#DEFAULT_HDR_PEAK]; for SDR
    /// [#REFERENCE_WHITE].
    public int peakNits() {
        return switch (transfer) {
            case PQ -> masteringPeak.orElse(contentPeak.orElse(DEFAULT_HDR_PEAK));
            case HLG -> DEFAULT_HDR_PEAK;
            case SDR, UNSPECIFIED -> REFERENCE_WHITE;
        };
    }
}
