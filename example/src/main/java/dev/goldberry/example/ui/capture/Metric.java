package dev.goldberry.example.ui.capture;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/// One line of a capture card's metrics: what is measured, and what it reads.
///
/// @param label what is measured
/// @param value what it reads, as a reader would say it
record Metric(String label, String value) {

    Metric {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(value, "value");
    }

    /// The lines `labels` name, each reading `–`: the metrics of nothing
    /// running.
    static List<Metric> idle(String... labels) {
        return Arrays.stream(labels).map(label -> new Metric(label, "–")).toList();
    }

    /// `duration` in milliseconds, to a tenth.
    static String millis(Duration duration) {
        return String.format(Locale.ROOT, "%.1f ms", duration.toNanos() / 1e6);
    }
}
