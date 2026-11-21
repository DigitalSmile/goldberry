package dev.goldberry.mic.view.draw;

/// A range of decibels laid along a length: `floor` at the start, `ceiling` at
/// the end, linear in decibels between, as a meter's scale is.
///
/// @param floorDecibels   what reads as nothing at all
/// @param ceilingDecibels what reads as full
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public record DecibelScale(double floorDecibels, double ceilingDecibels) {

    public DecibelScale {
        if (!(ceilingDecibels > floorDecibels)
                || !Double.isFinite(floorDecibels)
                || !Double.isFinite(ceilingDecibels)) {
            throw new IllegalArgumentException("a scale from " + floorDecibels + " to " + ceilingDecibels + " dB");
        }
    }

    /// How far along the scale `decibels` lies, 0 to 1: below the floor is
    /// 0 and above the ceiling 1.
    public double fraction(double decibels) {
        if (Double.isNaN(decibels)) {
            return 0;
        }
        var fraction = (decibels - floorDecibels) / (ceilingDecibels - floorDecibels);
        return Math.clamp(fraction, 0.0, 1.0);
    }
}
