package io.github.digitalsmile.goldberry.widgets.data;

import io.github.digitalsmile.goldberry.widget.style.Paints;
import java.util.Objects;

/// A limit drawn across a chart — `charts.md` §3.1's "thresholds: lines and
/// shaded regions, drawn in the *semantic* hues, never a series slot".
///
/// ```java
/// chart.threshold(Threshold.above(90, Threshold.Level.DANGER).labelled("SLO"));
/// ```
///
/// ## Why it must not take a series colour
///
/// A series slot means "this is one of the things being compared"; the palette's
/// whole job is to keep those apart (ADR-0194). A limit is not one of them — it is
/// a statement *about* them — and a threshold drawn in slot 4 would both look like
/// a fourth series and steal the hue of the real one. So a threshold takes a
/// **semantic** hue, which is the vocabulary the rest of the toolkit already uses
/// for "this is fine" and "this is not".
///
/// Specifically `--gb-<level>-line`, which is the rank that exists for a stroke
/// drawn on the page rather than for a label or a fill — the same token
/// `message` draws its border in, measured against §1.2's 3:1 floor for non-text
/// (ADR-0088).
///
/// ## A line or a band
///
/// [#at] is a line: *this* is the limit. [#above], [#below] and [#band] are
/// shaded regions: *this range* is the bad one. A band is drawn at low alpha
/// under the data, so the gridlines and the series still read through it — a
/// threshold that hid the data would be a warning that costs you the thing you
/// were warned about.
///
/// ## It is part of the domain
///
/// A chart with a threshold at 90 and data that reaches 50 shows the threshold,
/// with the axis stretched to reach it. A threshold you have not crossed yet is
/// the one that matters most: "we are a long way from the limit" is a reading,
/// and a threshold that only appeared once it had been breached would be a
/// warning light that comes on after the fire.
///
/// @param from  the lower edge of the region, or the value itself for a line;
///              may be [Double#NEGATIVE_INFINITY]
/// @param to    the upper edge, equal to `from` for a line; may be
///              [Double#POSITIVE_INFINITY]
/// @param level which semantic hue it is drawn in
/// @param label a word for it — `SLO`, `p99 budget` — or null for a bare line
public record Threshold(double from, double to, Level level, String label) {

    /// Which semantic hue a threshold is drawn in.
    ///
    /// The four the design system has. There is no "pick a colour": a threshold
    /// that could be any colour is a threshold that can collide with a series,
    /// which is the one thing this type exists to prevent.
    public enum Level {

        /// `--gb-info`. A reference line rather than a limit — last quarter's
        /// median, the target.
        INFO,

        /// `--gb-success`. The band you want to be in.
        SUCCESS,

        /// `--gb-warning`. Getting close.
        WARNING,

        /// `--gb-danger`. Over the line.
        DANGER;

        /// The custom property this level reads.
        String token() {
            return "--gb-" + name().toLowerCase(java.util.Locale.ROOT) + "-line";
        }

        /// What it is drawn in when there is no stylesheet at all — the dark
        /// theme's values, for [SeriesPalette]'s reason: a chart rendered against
        /// no sheet should still be four distinguishable hues rather than four
        /// black lines.
        int fallback() {
            return switch (this) {
                case INFO -> 0xFF81A1C1;
                case SUCCESS -> 0xFFA3BE8C;
                case WARNING -> 0xFFEBCB8B;
                case DANGER -> 0xFFCE858C;
            };
        }
    }

    public Threshold {
        Objects.requireNonNull(level, "level");
        if (Double.isNaN(from) || Double.isNaN(to)) {
            throw new IllegalArgumentException(
                    "a threshold needs a position, and NaN is where a *missing* value goes"
                            + " (NullPolicy) rather than a limit");
        }
        if (to < from) {
            var swap = from;
            from = to;
            to = swap;
        }
        if (label != null && label.isBlank()) {
            label = null;
        }
    }

    /// A line at `value`.
    public static Threshold at(double value, Level level) {
        return new Threshold(value, value, level, null);
    }

    /// Everything above `value` — a band with no top.
    public static Threshold above(double value, Level level) {
        return new Threshold(value, Double.POSITIVE_INFINITY, level, null);
    }

    /// Everything below `value` — a band with no bottom.
    public static Threshold below(double value, Level level) {
        return new Threshold(Double.NEGATIVE_INFINITY, value, level, null);
    }

    /// The band between two values.
    public static Threshold band(double from, double to, Level level) {
        return new Threshold(from, to, level, null);
    }

    /// This threshold, with a word for it.
    public Threshold labelled(String text) {
        return new Threshold(from, to, level, text);
    }

    /// Whether this is a line rather than a region.
    public boolean isLine() {
        return from == to;
    }

    /// What this threshold contributes to the axis' domain — its finite edges.
    ///
    /// An unbounded side contributes nothing: `above(90)` says the axis must
    /// reach 90, and says nothing at all about infinity.
    public double domainMin() {
        return Double.isFinite(from) ? from : Double.POSITIVE_INFINITY;
    }

    /// See [#domainMin].
    public double domainMax() {
        return Double.isFinite(to) ? to : Double.NEGATIVE_INFINITY;
    }

    /// The colour this is drawn in, resolved against the node being painted.
    ///
    /// Through the cascade rather than from a table, for the reason a series
    /// colour is: a theme owns its hues, and `#slo { --gb-danger-line: … }` is an
    /// ordinary rule (ADR-0195).
    public int colour(Paints.Context context) {
        return context.color(level.token(), level.fallback());
    }
}
