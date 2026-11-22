package dev.goldberry.gpu.render.mapped;

import java.util.Arrays;
import java.util.Locale;

/// Whether a device's upload memory is fit to paint in, from timings of the
/// same paint in heap memory and in it, taken in pairs.
///
/// Write-combined or uncached memory reads several times slower than cached
/// memory, and blending reads what it blends onto; so the path is switched off
/// when the mapped median is more than [#limit] times the heap median. The
/// limit is generous on purpose: a check at start-up has a millisecond, and a
/// shared machine moves the same paint by 5 to 13 percent from one moment to
/// the next, which a tighter limit would read as a verdict.
///
/// @param limit how many times slower than heap mapped memory may paint
/// @param pairs how many interleaved pairs are timed, after one discarded
public record InPlaceGate(double limit, int pairs) {

    /// One and a half times, over nine pairs.
    public static final InPlaceGate DEFAULT = new InPlaceGate(1.5, 9);

    /// Checks the limit is above 1 and there is at least one pair.
    public InPlaceGate {
        if (!(limit > 1) || Double.isInfinite(limit)) {
            throw new IllegalArgumentException("a limit of " + limit);
        }
        if (pairs < 1) {
            throw new IllegalArgumentException(pairs + " pairs");
        }
    }

    /// What the timings say.
    ///
    /// @param ratio the mapped median over the heap median
    /// @param on    whether the in-place path is switched on
    public record Verdict(double ratio, boolean on) {

        /// For the log: "1.02× heap".
        public String describe() {
            return String.format(Locale.ROOT, "%.2f× heap", ratio);
        }
    }

    /// Decides from `heap` and `mapped`, the same number of samples each.
    ///
    /// @throws IllegalArgumentException when they differ in length or are empty
    public Verdict decide(long[] heap, long[] mapped) {
        if (heap.length == 0 || heap.length != mapped.length) {
            throw new IllegalArgumentException(heap.length + " heap and " + mapped.length + " mapped samples");
        }
        var ratio = median(mapped) / Math.max(1.0, median(heap));
        return new Verdict(ratio, ratio <= limit);
    }

    private static double median(long[] samples) {
        var sorted = samples.clone();
        Arrays.sort(sorted);
        var middle = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2.0;
    }
}
