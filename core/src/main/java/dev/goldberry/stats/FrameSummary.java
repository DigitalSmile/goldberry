package dev.goldberry.stats;

import java.util.Locale;

/// What a whole run cost, in one line: every frame painted and every refresh
/// missed since the window opened.
///
/// ```java
/// FrameSummary run = host.frames().summary();
/// if (run.exceeds(30)) {
///     System.err.println(run.describe());
/// }
/// ```
///
/// [FrameStats] is a window over the last sixty frames, which is what a HUD
/// wants to watch. A run that paints three hundred frames and exits wants the
/// opposite: the totals. This record is those totals as a value, so a test can
/// assert on the number of late frames rather than parse the line the launcher
/// logs. [FrameStats#summary] gives it.
///
/// Read more: [Measuring](https://goldberry.dev/docs/performance/measuring.html#the-showcase-under-load).
///
/// @param frames           every frame painted since the window opened
/// @param late             every refresh that went by with a frame wanted and
///                         undelivered, summed over the run rather than over
///                         the last sixty frames
/// @param meanPaintMillis  the mean of every frame's paint time
/// @param worstPaintMillis the most expensive single frame, in milliseconds
/// @param displayHertz     the display's refresh rate, or 0 if the platform
///                         would not say
public record FrameSummary(
        long frames, long late, double meanPaintMillis, double worstPaintMillis, double displayHertz) {

    /// A run that painted nothing.
    public static final FrameSummary NONE = new FrameSummary(0, 0, 0, 0, 0);

    public FrameSummary {
        if (frames < 0 || late < 0) {
            throw new IllegalArgumentException("a count cannot be negative: " + frames + " frames, " + late + " late");
        }
    }

    /// Whether more refreshes were missed than `budget` allows.
    ///
    /// A budget under zero is no budget, so a run that was not asked to judge
    /// itself never exceeds one.
    public boolean exceeds(long budget) {
        return budget >= 0 && late > budget;
    }

    /// The line the launcher logs at exit.
    ///
    /// Formatted in `Locale.ROOT`, because a workflow greps this line and a
    /// decimal comma on a German runner would turn a green build red.
    public String describe() {
        return String.format(
                Locale.ROOT,
                "%d frame(s) painted, %d late; paint mean %.2f ms, worst %.2f ms; display %.1f Hz",
                frames,
                late,
                meanPaintMillis,
                worstPaintMillis,
                displayHertz);
    }
}
