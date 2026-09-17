package io.github.digitalsmile.goldberry.stats;

import java.util.Locale;

/// What a whole run cost, in one line — the number a 60 fps claim is.
///
/// [FrameStats] is a window over the last sixty frames, because that is what a
/// HUD wants to watch. A run that exits after three hundred frames wants the
/// opposite: every frame it painted, and every refresh it missed, since the
/// window opened. This is that, and it exists so the line the launcher writes
/// at exit — "N of M frames were late while resizing" — is a value a test can
/// assert on rather than a string it has to parse ([ADR-0342]).
///
/// @param frames           every frame painted since the window opened
/// @param late             every refresh that went by with a frame wanted and
///                         undelivered, summed over the run — the
///                         [FrameStats#lateFrames] of every frame, not of the
///                         last sixty
/// @param meanPaintMillis  the mean of every frame's paint time
/// @param worstPaintMillis the dearest single frame
/// @param displayHertz     what the display was doing, or 0 if it would not say
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
    /// A budget under zero is no budget, which is how a run that was not asked
    /// to judge itself reads: nothing is over a ceiling that was never set.
    public boolean exceeds(long budget) {
        return budget >= 0 && late > budget;
    }

    /// The line the launcher logs at exit.
    ///
    /// `Locale.ROOT` because this line is grepped by a workflow, and a decimal
    /// comma on a German runner would be a green build turned red by a locale.
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
