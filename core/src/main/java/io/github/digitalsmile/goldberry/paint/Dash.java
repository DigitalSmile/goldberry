package io.github.digitalsmile.goldberry.paint;

import java.util.ArrayList;
import java.util.List;

/// The on/off pattern a stroke is cut into — SVG's `stroke-dasharray` and
/// `stroke-dashoffset`.
///
/// ## Why this is a type and not two fields on [Stroke]
///
/// Because a dash array is a list and [Stroke] is a record. A `double[]`
/// component would give a record whose `equals` compares array identity, so two
/// strokes written the same way would be unequal — and a widget caching "have I
/// already drawn this?" would cache nothing, silently. A `List<Double>` boxes
/// two to four numbers per stroke call, which is nothing next to rasterizing the
/// path they describe, and it makes the enclosing record behave like a value.
///
/// ## Units and the odd-length rule
///
/// Lengths are logical pixels, in the same space as [Stroke#width()]. An
/// odd-length pattern repeats to make an even one, exactly as SVG says: `5`
/// means `5 on, 5 off`, and `5 3 2` means `5 on, 3 off, 2 on, 5 off, 3 on, 2
/// off`. The doubling is done here rather than left to the rasterizer so that
/// [#pattern()] reads back what will actually be drawn.
///
/// @param pattern alternating on and off lengths, starting with an *on*; empty
///        for a solid stroke
/// @param offset  how far into the pattern the first segment starts, which is
///        what makes a marching-ants animation a changing number rather than a
///        rebuilt path
public record Dash(List<Double> pattern, double offset) {

    /// A solid stroke — no dashes at all.
    public static final Dash NONE = new Dash(List.of(), 0);

    public Dash {
        if (pattern == null) {
            throw new IllegalArgumentException("a dash pattern is a list, and null is not one; use Dash.NONE");
        }
        if (!Double.isFinite(offset)) {
            throw new IllegalArgumentException("a dash offset must be a finite number, not " + offset);
        }
        for (var length : pattern) {
            if (length == null || !Double.isFinite(length) || length < 0) {
                throw new IllegalArgumentException(
                        "every dash length must be a finite, non-negative number, and " + length + " is not");
            }
        }
        // SVG's rule, applied here so that pattern() reads back what is drawn.
        // An empty list is already even and stays empty, which is NONE.
        pattern = pattern.size() % 2 == 0 ? List.copyOf(pattern) : doubled(pattern);
    }

    /// The commonest case — `on` pixels drawn, `off` pixels skipped.
    public static Dash of(double on, double off) {
        return new Dash(List.of(on, off), 0);
    }

    /// An arbitrary pattern, at no offset.
    public static Dash of(double... pattern) {
        var lengths = new ArrayList<Double>(pattern.length);
        for (var length : pattern) {
            lengths.add(length);
        }
        return new Dash(lengths, 0);
    }

    /// This pattern, started `offset` pixels in.
    ///
    /// The animation knob: a ghost layer's marching ants are this number moving,
    /// and the path underneath never changes.
    public Dash startedAt(double offset) {
        return new Dash(pattern, offset);
    }

    /// Whether this describes a solid stroke, and so whether the rasterizer can
    /// be left alone.
    ///
    /// A pattern of all zeros is solid too: nothing is ever skipped. Asked before
    /// setting dash state on a context, because the overwhelming majority of
    /// strokes in a frame are solid and the state has to be put back afterwards.
    public boolean isSolid() {
        if (pattern.isEmpty()) {
            return true;
        }
        for (var length : pattern) {
            if (length > 0) {
                return false;
            }
        }
        return true;
    }

    /// The total length of one cycle of the pattern.
    ///
    /// What an animation wraps at: advancing [#offset()] by this much is the
    /// identity, so a marching-ants timer can stay inside one period forever
    /// instead of growing a number until it loses precision.
    public double period() {
        var total = 0d;
        for (var length : pattern) {
            total += length;
        }
        return total;
    }

    private static List<Double> doubled(List<Double> pattern) {
        var repeated = new ArrayList<Double>(pattern.size() * 2);
        repeated.addAll(pattern);
        repeated.addAll(pattern);
        return List.copyOf(repeated);
    }
}
