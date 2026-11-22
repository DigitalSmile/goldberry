package dev.goldberry.gpu.render.staging;

import java.util.Arrays;
import java.util.Locale;

/// Timings of one measured row, in nanoseconds, sorted.
///
/// @param nanos the samples, sorted ascending
record Samples(long[] nanos) {

    /// Copies and sorts `nanos`.
    Samples {
        if (nanos.length == 0) {
            throw new IllegalArgumentException("no samples");
        }
        nanos = nanos.clone();
        Arrays.sort(nanos);
    }

    /// The median, in milliseconds.
    double median() {
        return nanos[nanos.length / 2] / 1e6;
    }

    /// The 95th percentile, in milliseconds.
    double p95() {
        return nanos[Math.min(nanos.length - 1, (int) (nanos.length * 0.95))] / 1e6;
    }

    /// `median / p95` in milliseconds, for a table cell.
    String cell() {
        return String.format(Locale.ROOT, "%.3f / %.3f", median(), p95());
    }
}
