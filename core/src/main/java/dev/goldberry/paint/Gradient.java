package dev.goldberry.paint;

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
/// call.
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
/// ## Sealed, with two shapes
///
/// [Linear] along a line and [Radial] out from a centre, which are the two a
/// CSS `background` names. Sealing means a third is a new record and an
/// exhaustive `switch` that stops compiling until every consumer handles it,
/// rather than a silent default branch that draws the wrong thing.
///
/// ## Beyond the ends
///
/// [Extend] says what is drawn past the first and last stop: the end colours
/// held ([Extend#PAD], CSS's plain gradients), the ramp again
/// ([Extend#REPEAT], CSS's `repeating-` ones), or the ramp back and forth
/// ([Extend#REFLECT]).
///
/// Read more: [Canvas, images and QR codes](https://goldberry.dev/docs/components/drawing.html#the-painter).
public sealed interface Gradient {

    /// The stops, in ascending offset order and never empty.
    List<Stop> stops();

    /// What is drawn beyond the first and the last stop.
    Extend extend();

    /// What a ramp does past its two ends.
    enum Extend {

        /// The end colours hold.
        PAD,

        /// The ramp starts again, which is what a `repeating-` gradient is.
        REPEAT,

        /// The ramp runs backwards, then forwards again.
        REFLECT
    }

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
    /// Beyond either end, what [#extend] says: by default the nearest stop's
    /// colour continues — the ramp covers the shape it was placed over and
    /// anything past it holds, rather than repeating.
    record Linear(double x1, double y1, double x2, double y2, List<Stop> stops, Extend extend) implements Gradient {

        public Linear {
            if (!Double.isFinite(x1) || !Double.isFinite(y1) || !Double.isFinite(x2) || !Double.isFinite(y2)) {
                // Blend2D accepts a NaN here and fills the shape with nothing,
                // which is indistinguishable from arithmetic that went wrong
                // upstream -- so it is refused where the number is written.
                throw new IllegalArgumentException("a gradient runs between two finite points, and (" + x1 + "," + y1
                        + ") to (" + x2 + "," + y2 + ") is not a pair of them");
            }
            Objects.requireNonNull(extend, "extend");
            stops = sorted(stops);
        }

        /// A ramp whose ends hold.
        public Linear(double x1, double y1, double x2, double y2, List<Stop> stops) {
            this(x1, y1, x2, y2, stops, Extend.PAD);
        }
    }

    /// A ramp out from `(cx, cy)`, round an ellipse with radii `radiusX` and
    /// `radiusY`.
    ///
    /// Offset 0 sits `start` of the way out and offset 1 on the ellipse, so a
    /// ramp whose first colour is some way from the centre needs no extra stop
    /// inside it — which is what lets a repeating one repeat inwards as well as
    /// out. Inside `start` and past the ellipse, what [#extend] says.
    ///
    /// @param start where offset 0 is, as a fraction of the way from the centre
    ///              to the ellipse, from 0 up to but not including 1
    record Radial(double cx, double cy, double radiusX, double radiusY, double start, List<Stop> stops, Extend extend)
            implements Gradient {

        public Radial {
            if (!Double.isFinite(cx) || !Double.isFinite(cy)) {
                throw new IllegalArgumentException(
                        "a gradient's centre is a finite point, and (" + cx + "," + cy + ") is not one");
            }
            if (!(radiusX > 0) || !(radiusY > 0) || !Double.isFinite(radiusX) || !Double.isFinite(radiusY)) {
                throw new IllegalArgumentException("a radial gradient's radii are finite and positive, and " + radiusX
                        + " and " + radiusY + " are not both");
            }
            if (!(start >= 0) || !(start < 1)) {
                throw new IllegalArgumentException(
                        "a radial gradient starts from 0 up to but not including 1, and " + start + " does not");
            }
            Objects.requireNonNull(extend, "extend");
            stops = sorted(stops);
        }
    }

    /// A circular ramp of `radius` round `(cx, cy)` through `stops`, its ends
    /// held.
    static Radial radial(double cx, double cy, double radius, Stop... stops) {
        return new Radial(cx, cy, radius, radius, 0, List.of(stops), Extend.PAD);
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
