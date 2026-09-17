package io.github.digitalsmile.goldberry.example.motion;

import io.github.digitalsmile.goldberry.motion.Easing;

/// How one tile settles: it drops in from above, turned a few degrees, and lands
/// flat (ADR-0354).
///
/// Pure arithmetic over a time, with no clock and no state, which is ADR-0081's
/// rule for anything that moves on a canvas: a pose is a **function** of how long
/// ago the floor started and how long this tile waits. Two frames asking about the
/// same moment get the same answer, and a test can ask about any moment.
///
/// @param durationMillis how long one tile takes from its first movement to rest
/// @param dropPixels     how far above its place a tile starts
/// @param turnDegrees    the most a tile is turned when it starts; each tile turns
///                       some fraction of this, left or right
/// @param staggerMillis  how much later a tile one place further from the focus
///                       starts
public record Settle(double durationMillis, double dropPixels, double turnDegrees, double staggerMillis) {

    /// The floor's settle: 850 ms, 20 pixels, 4 degrees, 45 ms a place.
    public static final Settle TILES = new Settle(850, 20, 4, 45);

    /// How far through its fade a tile is fully opaque — the first third of the
    /// drop. A tile that faded the whole way would still be translucent as it
    /// lands, and landing is the moment the eye is on it.
    private static final double OPAQUE_AT = 0.35;

    public Settle {
        if (!(durationMillis > 0) || dropPixels < 0 || turnDegrees < 0 || staggerMillis < 0) {
            throw new IllegalArgumentException("a settle needs a positive duration and no negative distances");
        }
    }

    /// Where a tile is, how it is turned and how visible it is.
    ///
    /// @param offsetY how far above its place, as a negative number of pixels
    /// @param radians its turn, clockwise
    /// @param opacity from 0 to 1
    public record Pose(double offsetY, double radians, double opacity) {

        /// On its place, flat, fully there.
        public static final Pose REST = new Pose(0, 0, 1);

        /// Whether this is [#REST].
        public boolean atRest() {
            return equals(REST);
        }
    }

    /// A tile's pose `sinceStart` milliseconds after the floor started.
    ///
    /// @param delayMillis how long this tile waits — [#staggerMillis] per place
    ///                    of distance from the focus
    /// @param turn        which way and how far this tile is turned, from -1 to 1
    public Pose at(double sinceStart, double delayMillis, double turn) {
        var local = sinceStart - delayMillis;
        var start = Math.toRadians(turnDegrees * Math.clamp(turn, -1, 1));
        if (local <= 0) {
            return new Pose(-dropPixels, start, 0);
        }
        if (local >= durationMillis) {
            return Pose.REST;
        }
        var t = local / durationMillis;
        // §1.7's enter curve: fast out of the top, slow onto the floor. The
        // opacity is linear and short, so it does not ease twice.
        var eased = Easing.EASE_ENTER.at(t);
        return new Pose(-dropPixels * (1 - eased), start * (1 - eased), Math.min(1, t / OPAQUE_AT));
    }

    /// How long a tile `distance` places from the focus waits.
    public double delayFor(double distance) {
        return Math.max(0, distance) * staggerMillis;
    }
}
