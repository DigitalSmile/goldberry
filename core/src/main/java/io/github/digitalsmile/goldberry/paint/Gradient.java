package io.github.digitalsmile.goldberry.paint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/// A fill that is not a colour — a ramp between stops, placed on the frame.
///
/// ## Why it is a value and the rasterizer's is a resource
///
/// Blend2D's gradient is an object with a lifetime: it is allocated, filled with
/// stops, handed to a context and closed. That is the right shape for a binding
/// and the wrong one for an application, which wants to say *what the ramp is*
/// and have the toolkit worry about when the native object exists. So this is an
/// immutable value, and [Frame] is what turns one into a fill for the length of a
/// call (ADR-0207, ADR-0277).
///
/// ## Coordinates
///
/// The geometry is in the **frame's own space** — logical pixels — and it is not
/// moved by where a path is drawn. A gradient placed from the top of a plot to
/// its baseline stays there whichever path is filled through it, which is what
/// makes one ramp usable for a run of bands.
///
/// ## A fade to transparent must repeat the colour
///
/// `0x00000000` is transparent *black*, so a ramp from a green to it passes
/// through grey — the classic wrong gradient. The transparent end of a fade is
/// the same RGB with a zero alpha, which is what [#fade] builds and why it
/// exists rather than being left to each caller to remember.
///
/// ## Sealed, with one implementation
///
/// Only [Linear] today, because linear is what the rasterizer's wrapper binds.
/// Radial is the obvious second and is deliberately not guessed at here: sealing
/// means adding it later is a new record and an exhaustive `switch` that stops
/// compiling until every consumer handles it, rather than a silent default branch
/// that draws the wrong thing.
public sealed interface Gradient {

    /// The stops, in ascending offset order and never empty.
    List<Stop> stops();

    /// One colour, at one position along the ramp.
    ///
    /// @param offset where along the ramp this colour sits, from 0 at the start
    ///        to 1 at the end
    /// @param argb  the colour as `0xAARRGGBB`, **not** premultiplied
    record Stop(double offset, int argb) {

        public Stop {
            if (!Double.isFinite(offset) || offset < 0 || offset > 1) {
                throw new IllegalArgumentException("a gradient stop sits between 0 and 1, and " + offset + " does not");
            }
        }
    }

    /// A ramp along the line from `(x1, y1)` to `(x2, y2)`.
    ///
    /// Beyond either end the nearest stop's colour continues — the ramp covers
    /// the shape it was placed over and anything past it holds, rather than
    /// repeating.
    record Linear(double x1, double y1, double x2, double y2, List<Stop> stops) implements Gradient {

        public Linear {
            if (!Double.isFinite(x1) || !Double.isFinite(y1) || !Double.isFinite(x2) || !Double.isFinite(y2)) {
                // Blend2D accepts a NaN here and fills the shape with nothing,
                // which is indistinguishable from arithmetic that went wrong
                // upstream -- so it is refused where the number is written.
                throw new IllegalArgumentException("a gradient runs between two finite points, and (" + x1 + "," + y1
                        + ") to (" + x2 + "," + y2 + ") is not a pair of them");
            }
            stops = sorted(stops);
        }
    }

    /// A linear ramp through `stops`.
    static Linear linear(double x1, double y1, double x2, double y2, Stop... stops) {
        return new Linear(x1, y1, x2, y2, List.of(stops));
    }

    /// A linear ramp from `argb` to the same colour at zero alpha.
    ///
    /// The gradient a chart's area fill wants, and the one that is wrong when it
    /// is written by hand — see the class note on transparent black.
    static Linear fade(double x1, double y1, double x2, double y2, int argb) {
        return new Linear(x1, y1, x2, y2, List.of(new Stop(0, argb), new Stop(1, argb & 0x00FFFFFF)));
    }

    /// The stops in ascending order, copied, with at least one of them.
    ///
    /// Sorted so that two gradients written in a different order are equal, and
    /// **stably** so that a pair of stops at the same offset keeps the order it
    /// was written in — which is how a hard edge between two colours is
    /// expressed, and reordering it would turn the edge around.
    private static List<Stop> sorted(List<Stop> stops) {
        Objects.requireNonNull(stops, "stops");
        if (stops.isEmpty()) {
            throw new IllegalArgumentException("a gradient with no stops fills with nothing; give it at least one");
        }
        var copy = new ArrayList<>(stops);
        copy.sort(Comparator.comparingDouble(Stop::offset));
        return List.copyOf(copy);
    }
}
