package io.github.digitalsmile.goldberry.paint.geom;

import java.util.Objects;

import io.github.digitalsmile.goldberry.paint.Dash;
import io.github.digitalsmile.goldberry.paint.Path;

/// Cuts a [Path] into the on runs of a [Dash] pattern.
///
/// ## Why this is Goldberry's arithmetic and not the rasterizer's
///
/// Blend2D has `bl_context_set_stroke_dash_array` and `bl_context_set_stroke_dash_offset`.
/// It stores what they are given, retains it, copies it, and saves it across
/// `save`/`restore` — and never strokes with it. `core/pathstroke.cpp` is 988
/// lines and the word "dash" does not appear in it. Both calls return
/// `BL_SUCCESS` and the line comes out solid, which is the worst way for a
/// missing feature to present itself.
///
/// So dashing happens here, before the path reaches the rasterizer: a dashed
/// stroke is a **solid stroke of a different path**. Which means it costs
/// nothing on the native surface — ADR-0278 went from six new exported symbols
/// to one — and behaves identically on every target, because it is the same Java
/// on all four.
///
/// ## What comes back
///
/// A path of open sub-paths, one per on run, holding only moves and lines. Two
/// consequences worth knowing:
///
/// - **The curves are gone.** A dash is a statement about arc length, and the arc
///   length of a cubic has no closed form, so the input is flattened first
///   ([Flattener]). At a tenth of a pixel the substitution is below the
///   rasterizer's own antialiasing.
/// - **The walk stops at the path.** A run that would begin exactly where the
///   path ends is not drawn, and a run still open when the path ends is cut
///   there. So a dashed path never reaches past the thing it was made from, and
///   a bare `moveTo` with no line after it never appears in the output.
/// - **Closed sub-paths come back open.** A dashed ring is a sequence of arcs
///   with gaps between them; there is nothing left to close. The join at the
///   start point is lost with it, which is why a dashed rectangle's corner is
///   drawn by the cap and not by the join — the same thing SVG does.
public final class Dasher {

    /// Lengths below this are treated as zero, in logical pixels.
    ///
    /// A flattened path has a great many very short segments, and a walk that
    /// compared against exact zero would either loop forever on one of them or
    /// emit a zero-length dash per segment — which a round cap turns into a row
    /// of dots nobody asked for.
    private static final double EPSILON = 1e-9;

    private Dasher() {}

    /// `path` cut into `dash`'s on runs.
    ///
    /// A solid pattern returns `path` unchanged — the same value, not a copy —
    /// which is what keeps a dash-capable drawing call free for the overwhelming
    /// majority of strokes that are not dashed.
    public static Path dash(Path path, Dash dash) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(dash, "dash");
        if (dash.isSolid() || path.isEmpty()) {
            return path;
        }
        return new Walk(dash).over(Flattener.flatten(path));
    }

    /// One traversal of one path, carrying the pattern's position with it.
    ///
    /// The position is **not** reset per sub-path, which is deliberate and is
    /// what SVG specifies: a rectangle drawn as four sides has its dashes run
    /// continuously around the corner rather than restarting at each one.
    private static final class Walk {

        private final double[] pattern;
        private final Path.Builder out = Path.builder();

        private int index;
        private double remaining;
        private boolean on;
        private boolean penDown;

        private double x;
        private double y;
        private double startX;
        private double startY;

        private Walk(Dash dash) {
            this.pattern = new double[dash.pattern().size()];
            for (var i = 0; i < pattern.length; i++) {
                pattern[i] = dash.pattern().get(i);
            }
            // The offset is taken modulo the period so that an animation may
            // advance it forever without the loop below counting to it.
            var period = dash.period();
            var offset = period > 0 ? dash.offset() % period : 0;
            if (offset < 0) {
                offset += period;
            }
            this.on = true;
            this.remaining = pattern[0];
            while (offset > 0) {
                if (offset < remaining) {
                    remaining -= offset;
                    break;
                }
                offset -= remaining;
                advance();
            }
        }

        private Path over(Path flattened) {
            for (var segment : flattened.segments()) {
                switch (segment) {
                    case Path.Segment.MoveTo move -> {
                        // A new sub-path lifts the pen but keeps the pattern's
                        // position: the gap between two sub-paths is not part of
                        // either one's length.
                        penDown = false;
                        x = move.x();
                        y = move.y();
                        startX = x;
                        startY = y;
                    }
                    case Path.Segment.LineTo line -> lineTo(line.x(), line.y());
                    case Path.Segment.Close _ -> {
                        lineTo(startX, startY);
                        penDown = false;
                    }
                    // Flattener leaves none of these, and a default branch would
                    // be the place a future verb went missing in silence.
                    case Path.Segment.QuadTo quad -> throw unflattened(quad);
                    case Path.Segment.CubicTo cubic -> throw unflattened(cubic);
                    case Path.Segment.ArcTo arc -> throw unflattened(arc);
                }
            }
            return out.build();
        }

        /// Walks one straight segment, emitting whatever part of it is inked.
        private void lineTo(double toX, double toY) {
            var dx = toX - x;
            var dy = toY - y;
            var length = Math.hypot(dx, dy);
            if (length <= EPSILON) {
                x = toX;
                y = toY;
                return;
            }

            var walked = 0d;
            while (length - walked > EPSILON) {
                var step = Math.min(remaining, length - walked);
                var from = walked / length;
                var to = (walked + step) / length;
                if (on) {
                    if (!penDown) {
                        out.moveTo(x + dx * from, y + dy * from);
                        penDown = true;
                    }
                    out.lineTo(x + dx * to, y + dy * to);
                }
                walked += step;
                remaining -= step;
                if (remaining <= EPSILON) {
                    advance();
                }
            }
            x = toX;
            y = toY;
        }

        /// Moves to the next entry of the pattern.
        ///
        /// ## The two zeros are not the same zero
        ///
        /// A zero-length **gap** does not break the run: `4 0 4` is a solid
        /// eight, and stopping there would put two caps in the middle of a dash
        /// where a round one shows the seam. So a zero gap is stepped straight
        /// over, and the pen stays down.
        ///
        /// A zero-length **dash** is a dot, and is kept. `stroke-dasharray="0 4"`
        /// with a round cap is how a dotted line is written — it is an idiom
        /// rather than a degenerate case, and skipping it the way the gap is
        /// skipped would lose it.
        ///
        /// **It will not be drawn, though.** Blend2D discards a zero-length
        /// sub-path, so a round cap on nothing is nothing and a `0 n` pattern
        /// inks no pixels at all — measured in `DashRenderingTest`. The geometry
        /// here is SVG's; the ink is the backend's, and for a dotted line the
        /// answer is a short dash such as `Dash.of(1, 7)` rather than a zero one
        /// (ADR-0278).
        ///
        /// Progress is guaranteed without a loop: the index moves on every call,
        /// and [Dash#isSolid()] has already ruled out a pattern that is zeros all
        /// the way through — so the walk reaches a positive entry within one
        /// period and its length starts being consumed.
        private void advance() {
            index = (index + 1) % pattern.length;
            on = !on;
            remaining = pattern[index];

            if (!on && remaining <= EPSILON) {
                index = (index + 1) % pattern.length;
                on = true;
                remaining = pattern[index];
                return;
            }
            if (!on) {
                penDown = false;
            }
        }

        private static IllegalStateException unflattened(Path.Segment segment) {
            return new IllegalStateException("a flattened path holds only moves, lines and closes, and this is a "
                    + segment.getClass().getSimpleName());
        }
    }
}
