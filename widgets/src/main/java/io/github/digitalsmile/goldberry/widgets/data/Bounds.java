package io.github.digitalsmile.goldberry.widgets.data;

/// What the value axis has to reach — `charts.md` §3.1's "axis min/max, soft
/// min/max".
///
/// ## Why soft bounds exist
///
/// A chart scales to its data, which is right until the data does not move. An
/// uptime that reads `99.94, 99.97, 99.91, 99.99` auto-scaled fills the whole
/// plot with the difference between 99.91 and 99.99 — a mountain range made of
/// eight hundredths of a percent. The picture says "look at this" about noise,
/// and it says it most loudly exactly when the news is good.
///
/// A **soft** bound is the fix: "the axis reaches at least here, and further if
/// the data does". `soft(99, 100)` draws that uptime as the flat line near the
/// top that it is, and still shows an outage if one happens, because a reading of
/// 40 pushes the axis down to meet it. A **hard** bound does not move.
///
/// ## Which one an application wants
///
/// Soft, nearly always. A hard bound is a promise that the data cannot leave the
/// range, and data that leaves it is drawn outside the plot and clipped — which
/// is the correct rendering of "you told me this could not happen" and a bad
/// surprise if you were wrong. Hard is for an axis whose range is a definition
/// rather than an observation: a percentage of a whole, a fraction, a gauge with
/// a physical stop.
///
/// @param min  the smallest value the axis must reach, or `NaN` for none
/// @param max  the largest, or `NaN`
/// @param hard whether the axis is exactly this rather than at least this
public record Bounds(double min, double max, boolean hard) {

    /// A chart that scales to its data, which is what one does unasked.
    public static final Bounds NONE = new Bounds(Double.NaN, Double.NaN, false);

    public Bounds {
        if (!Double.isNaN(min) && !Double.isNaN(max) && min > max) {
            var swap = min;
            min = max;
            max = swap;
        }
    }

    /// An axis that reaches **at least** `min…max`, and further if the data does.
    public static Bounds soft(double min, double max) {
        return new Bounds(min, max, false);
    }

    /// An axis that is **exactly** `min…max`. See the class note before choosing
    /// this.
    public static Bounds hard(double min, double max) {
        return new Bounds(min, max, true);
    }

    /// Whether either end has been set.
    public boolean isSet() {
        return !Double.isNaN(min) || !Double.isNaN(max);
    }

    /// The axis' bottom, given what the data reached.
    public double applyMin(double dataMin) {
        if (Double.isNaN(min)) {
            return dataMin;
        }
        return hard ? min : Math.min(min, dataMin);
    }

    /// The axis' top, given what the data reached.
    public double applyMax(double dataMax) {
        if (Double.isNaN(max)) {
            return dataMax;
        }
        return hard ? max : Math.max(max, dataMax);
    }
}
