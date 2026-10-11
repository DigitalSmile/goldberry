package dev.goldberry.text;

import java.util.ArrayList;
import java.util.List;

/// A stretch of a line from `left` to `right`, in logical units from the line's
/// left edge: one piece of a selection's highlight.
///
/// [Paragraph#stretchesOf] answers with these. A range of text that runs one way
/// is one stretch; a range across two directions is one per run it touches,
/// since what is contiguous in the text is not contiguous on screen.
///
/// @param left  the left edge
/// @param right the right edge, not left of `left`
public record Stretch(double left, double right) {

    public Stretch {
        if (right < left) {
            throw new IllegalArgumentException("a stretch cannot end left of where it starts: " + left + ".." + right);
        }
    }

    /// How wide it is.
    public double width() {
        return right - left;
    }

    /// `stretches` sorted from left to right, with those that touch or overlap
    /// joined into one and empty ones dropped.
    public static List<Stretch> joined(List<Stretch> stretches) {
        var sorted = new ArrayList<Stretch>();
        for (var stretch : stretches) {
            if (stretch.right > stretch.left) {
                sorted.add(stretch);
            }
        }
        sorted.sort((a, b) -> Double.compare(a.left, b.left));
        var joined = new ArrayList<Stretch>();
        for (var stretch : sorted) {
            if (!joined.isEmpty() && stretch.left <= joined.getLast().right + TOUCHING) {
                var last = joined.removeLast();
                joined.add(new Stretch(last.left, Math.max(last.right, stretch.right)));
            } else {
                joined.add(stretch);
            }
        }
        return List.copyOf(joined);
    }

    /// How close two edges are to count as one: the rounding of two prefix
    /// sums of the same advances, never a visible gap.
    private static final double TOUCHING = 1e-6;
}
