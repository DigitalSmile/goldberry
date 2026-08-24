package io.github.digitalsmile.goldberry.widgets.data;

/// What a chart does where a series has no value — `charts.md` §3.1's "null
/// handling: gap / connect / zero, three-way, explicit".
///
/// **A missing value is `Double.NaN`**, and a `null` in the list is read as one
/// ([Series]). It is not zero, and the difference is the whole reason this type
/// exists: a sensor that was offline for an hour and a sensor that read zero for
/// an hour are different facts, and a chart that draws them the same way has
/// destroyed one of them.
///
/// ## Why the default is the pessimistic one
///
/// [#GAP]. A gap drawn as a line is a claim that the value moved smoothly through
/// values nobody measured, and a gap drawn as zero is a claim that it was zero.
/// Both are inventions; a hole is not. So the honest answer is the default and
/// the other two are opt-in, which is the opposite of what a chart library
/// usually does — most connect by default, because a broken line looks like a
/// rendering bug rather than like missing data.
///
/// ## It belongs to the chart, not to the series
///
/// One picture, one convention. Two series in one chart treating their holes
/// differently is a chart a reader cannot interpret without being told which line
/// is which kind — the same argument that gives a chart one x axis and refuses it
/// a second y (`charts.md` §3.4).
public enum NullPolicy {

    /// The line stops and starts again — the **default**.
    ///
    /// A polyline per run of values that are actually there, so the hole is
    /// visible as a hole. For a stacked `area-chart` the whole stack breaks
    /// where any of its series is missing, because a total with an unknown
    /// component is unknown.
    GAP,

    /// Join across the hole, by interpolating it.
    ///
    /// The missing values are filled in linearly between the nearest values that
    /// exist, which for a line is the straight segment across the gap and for a
    /// band is the same shape filled. For a series sampled evenly enough that a
    /// dropped reading means nothing — a metric scraped every 15s that missed
    /// one — this is what a reader assumes anyway.
    ///
    /// **A gap at either end is still a gap.** Connecting needs two ends, and a
    /// series that starts late did not have a value before it started.
    CONNECT,

    /// Treat it as zero.
    ///
    /// Right where a missing value genuinely *means* zero — a counter of events
    /// that reports nothing when none happened — and a lie everywhere else. It is
    /// here because that case is real and common, and because an application that
    /// substitutes zeroes itself would do it before the chart could tell the
    /// difference.
    ZERO
}
