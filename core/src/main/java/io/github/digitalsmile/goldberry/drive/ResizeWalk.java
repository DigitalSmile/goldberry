package io.github.digitalsmile.goldberry.drive;

import java.util.Objects;

import io.github.digitalsmile.goldberry.render.model.LogicalSize;

/// A window's size, walked a pixel a frame between two corners.
///
/// What a drag actually produces is a resize event per pointer motion, each a
/// pixel or two from the last — and that is the load a frame loop has to be
/// measured under, because it is what found the damage-clamp bug
/// ([ADR-0072]) and what a 60 fps claim is about. A jump from one size to
/// another is one reallocation and says nothing.
///
/// So this walks: from `origin` towards `target`, one pixel on each axis per
/// step, and back again once it arrives, for as long as it is asked. Both
/// axes move together, which is a diagonal drag; an axis that has arrived
/// waits for the other.
///
/// The walk is told the window's **current** size on every step rather than
/// remembering where it thinks the window is, because a window manager may
/// clamp, round or lag the size it was asked for — and a walk that stepped
/// from where it wanted to be rather than from where it is would drift away
/// from the window it was meant to be driving ([ADR-0342]).
public final class ResizeWalk {

    private final LogicalSize origin;
    private final LogicalSize target;

    /// Whether the walk is heading for [#target] or back to [#origin].
    private boolean outward = true;

    /// A walk between two sizes.
    ///
    /// @param origin where the window opened
    /// @param target where the walk turns round
    public ResizeWalk(LogicalSize origin, LogicalSize target) {
        this.origin = Objects.requireNonNull(origin, "origin");
        this.target = Objects.requireNonNull(target, "target");
    }

    /// The size to ask for next, given where the window is now.
    ///
    /// Arriving at the goal turns the walk round on the same step, so a walk
    /// never asks for the size the window already has — except when the two
    /// corners are the same size, in which case there is nowhere to go and it
    /// says so by returning `current`.
    public LogicalSize next(LogicalSize current) {
        Objects.requireNonNull(current, "current");
        var step = toward(current, outward ? target : origin);
        if (step.equals(current)) {
            outward = !outward;
            step = toward(current, outward ? target : origin);
        }
        return step;
    }

    /// Whether the walk is currently heading for its target rather than home.
    public boolean isOutward() {
        return outward;
    }

    /// One pixel from `from` towards `goal` on each axis, never overshooting.
    static LogicalSize toward(LogicalSize from, LogicalSize goal) {
        return new LogicalSize(toward(from.width(), goal.width()), toward(from.height(), goal.height()));
    }

    private static float toward(float from, float goal) {
        if (from < goal) {
            return Math.min(from + 1f, goal);
        }
        if (from > goal) {
            return Math.max(from - 1f, goal);
        }
        return from;
    }

    @Override
    public String toString() {
        return "ResizeWalk[" + origin + " <-> " + target + (outward ? ", outward" : ", homeward") + "]";
    }
}
