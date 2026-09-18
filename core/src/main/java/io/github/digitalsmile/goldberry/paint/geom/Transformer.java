package io.github.digitalsmile.goldberry.paint.geom;

import java.util.Objects;

import io.github.digitalsmile.goldberry.css.value.Affine;
import io.github.digitalsmile.goldberry.paint.Path;

/// Maps a [Path] through an affine transform, segment by segment.
///
/// ## Why a turned shape is geometry and not a matrix
///
/// `Frame.transform` states the **whole** matrix, and a painter cannot read back
/// the one it is running under. Inside a `canvas` that matrix already holds the
/// translation that puts the canvas on screen, so a painter that sets a rotation
/// draws at the window's corner instead of at its own. The two ways out are to
/// compose with what is already there — which `Frame.concat` now does — or to
/// hand the rasterizer a path that is already turned, which is this.
///
/// Both exist because they answer different questions. A path is transformed
/// once and drawn many times, it can be measured and hit-tested afterwards in
/// the coordinates it will be drawn in, and it needs no state on the frame to be
/// balanced. The showcase's settling tiles were forty lines of exactly this
/// arithmetic before it moved here (ADR-0354, ADR-0390, `docs/gaps.md` G46).
///
/// ## The arc is the reason this belongs to the toolkit
///
/// Moves, lines and curves are their control points mapped: an affine transform
/// of a Bézier is the Bézier of the transformed controls, so the curve needs no
/// subdivision and comes back as the same kind of curve. An SVG elliptic arc
/// does not work that way. It carries the **shape of its ellipse** — two radii
/// and a rotation — and a transform turns, stretches and shears that ellipse
/// into a different one whose radii and rotation have to be recovered. Under a
/// mirroring transform it also runs the other way round, so the sweep flag
/// flips. Getting either wrong leaves a shape that still draws, which is why a
/// second copy of this in every application is a bad trade.
public final class Transformer {

    private Transformer() {}

    /// `path` with every coordinate mapped through `affine`.
    ///
    /// The identity hands `path` straight back — the same value, not a copy — so
    /// an animation may transform a shape every frame and pay nothing on the
    /// frames where it is at rest.
    public static Path transform(Path path, Affine affine) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(affine, "affine");
        if (path.isEmpty() || affine.isIdentity()) {
            return path;
        }

        var builder = Path.builder();
        for (var segment : path.segments()) {
            switch (segment) {
                case Path.Segment.MoveTo(var x, var y) -> builder.moveTo(affine.mapX(x, y), affine.mapY(x, y));
                case Path.Segment.LineTo(var x, var y) -> builder.lineTo(affine.mapX(x, y), affine.mapY(x, y));
                case Path.Segment.QuadTo(var cx, var cy, var x, var y) ->
                    builder.quadTo(affine.mapX(cx, cy), affine.mapY(cx, cy), affine.mapX(x, y), affine.mapY(x, y));
                case Path.Segment.CubicTo(var c1x, var c1y, var c2x, var c2y, var x, var y) ->
                    builder.cubicTo(
                            affine.mapX(c1x, c1y),
                            affine.mapY(c1x, c1y),
                            affine.mapX(c2x, c2y),
                            affine.mapY(c2x, c2y),
                            affine.mapX(x, y),
                            affine.mapY(x, y));
                case Path.Segment.ArcTo arc -> arcTo(builder, arc, affine);
                // A close is the same instruction wherever the sub-path went.
                case Path.Segment.Close _ -> builder.close();
            }
        }
        return builder.build();
    }

    /// An elliptic arc through `affine`, as the ellipse it becomes.
    ///
    /// An arc's ellipse is the unit circle under `R(rotation) · diag(rx, ry)`, so
    /// the transformed ellipse is the unit circle under the caller's linear part
    /// times that. Recovering radii and a rotation from the product is a singular
    /// value decomposition of a 2x2 matrix, which has a closed form: the two
    /// singular values are the semi-axes and the left rotation is the angle they
    /// sit at. Whatever rotation is left on the right does not matter — it spins
    /// the unit circle onto itself, and the arc is stated by its endpoints rather
    /// than by where its parameter starts.
    private static void arcTo(Path.Builder builder, Path.Segment.ArcTo arc, Affine affine) {

        var cos = Math.cos(arc.rotation());
        var sin = Math.sin(arc.rotation());

        // The linear part of `affine` times the arc's own ellipse basis.
        var m00 = arc.rx() * (affine.a() * cos + affine.c() * sin);
        var m01 = arc.ry() * (affine.c() * cos - affine.a() * sin);
        var m10 = arc.rx() * (affine.b() * cos + affine.d() * sin);
        var m11 = arc.ry() * (affine.d() * cos - affine.b() * sin);

        // The decomposition, in the symmetric/antisymmetric form: two rotations
        // whose half-sum is the angle of the axes and whose singular values are
        // their lengths. A degenerate ellipse -- a zero radius, or a transform
        // that collapses the plane -- falls out as a zero radius here, and a
        // zero-radius arc is a straight line, which is what SVG says it is.
        var sum = (m00 + m11) / 2;
        var difference = (m00 - m11) / 2;
        var across = (m10 + m01) / 2;
        var twist = (m10 - m01) / 2;
        var half = Math.hypot(sum, twist);
        var rest = Math.hypot(difference, across);
        var rx = half + rest;
        var ry = Math.abs(half - rest);
        var rotation = (Math.atan2(twist, sum) + Math.atan2(across, difference)) / 2;

        // A mirroring transform reverses the direction the arc is travelled in.
        // The endpoints are where they are either way, so the flag is the only
        // thing that says which side of the chord the ink is on -- and a shape
        // that keeps it comes back bulging the wrong way.
        var mirrored = affine.determinant() < 0;

        builder.arcTo(
                rx,
                ry,
                halfTurn(rotation),
                arc.largeArc(),
                mirrored != arc.sweep(),
                affine.mapX(arc.x(), arc.y()),
                affine.mapY(arc.x(), arc.y()));
    }

    /// `radians` brought into `[0, pi)`.
    ///
    /// An ellipse is unchanged by a half turn, so this is the same ellipse
    /// written the one way. It matters because a path turned a degree at a time
    /// for an hour would otherwise carry a rotation that grows without bound,
    /// and because two paths that are the same shape should say so.
    private static double halfTurn(double radians) {
        var wrapped = radians % Math.PI;
        // Adding zero rather than branching to a return: it also turns a negative
        // zero into a positive one, and a path's coordinates are compared bit for
        // bit.
        return wrapped + (wrapped < 0 ? Math.PI : 0);
    }
}
