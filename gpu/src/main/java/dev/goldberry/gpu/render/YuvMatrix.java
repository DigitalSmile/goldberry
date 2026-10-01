package dev.goldberry.gpu.render;

/// The Y'CbCr matrices a picture can be tagged with, by their luma weights:
/// `Y' = Kr R' + Kg G' + Kb B'`, with `Kg = 1 - Kr - Kb`.
///
/// BT.2020 is its non-constant-luminance form, which is what every 10-bit
/// stream the toolkit decodes uses.
public enum YuvMatrix {
    /// SD video, and what an untagged picture under 720 rows is taken to be.
    BT601(0.299, 0.114),
    /// HD video, and what an untagged picture of 720 rows or more is taken to be.
    BT709(0.2126, 0.0722),
    /// UHD and HDR video, non-constant luminance.
    BT2020(0.2627, 0.0593);

    private final double kr;
    private final double kb;

    YuvMatrix(double kr, double kb) {
        this.kr = kr;
        this.kb = kb;
    }

    /// The red weight, `Kr`.
    public double kr() {
        return kr;
    }

    /// The blue weight, `Kb`.
    public double kb() {
        return kb;
    }

    /// The green weight, `1 - Kr - Kb`.
    public double kg() {
        return 1 - kr - kb;
    }

    /// `R = Y + crToRed Cr`.
    double crToRed() {
        return 2 * (1 - kr);
    }

    /// The Cb part of `G = Y + cbToGreen Cb + crToGreen Cr`.
    double cbToGreen() {
        return -2 * kb * (1 - kb) / kg();
    }

    /// The Cr part of `G`.
    double crToGreen() {
        return -2 * kr * (1 - kr) / kg();
    }

    /// `B = Y + cbToBlue Cb`.
    double cbToBlue() {
        return 2 * (1 - kb);
    }
}
