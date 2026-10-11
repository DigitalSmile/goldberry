package dev.goldberry.motion.easing;

import java.util.Locale;
import java.util.Objects;

import dev.goldberry.motion.Easing;

/// `steps(count, position)`: progress that jumps rather than moves.
///
/// `step-start` is `steps(1, jump-start)` and `step-end` is `steps(1, jump-end)`.
///
/// @param count    how many intervals the progress is divided into; at least 1,
///                 and at least 2 for [Position#JUMP_NONE], which would
///                 otherwise never move
/// @param position where the jumps fall
public record Steps(int count, Position position) implements Easing {

    public Steps {
        Objects.requireNonNull(position, "position");
        var least = position == Position.JUMP_NONE ? 2 : 1;
        if (count < least) {
            throw new IllegalArgumentException("steps(" + count + ", " + position.cssName() + ") needs at least "
                    + least + " step" + (least == 1 ? "" : "s"));
        }
    }

    /// Where a step function's jumps fall, as CSS names it.
    public enum Position {

        /// The first jump happens at the very start: `jump-start`, or `start`.
        JUMP_START,

        /// The last jump happens at the very end: `jump-end`, or `end`, which is
        /// the default.
        JUMP_END,

        /// No jump at either end: the progress holds at 0 for the first interval
        /// and at 1 for the last.
        JUMP_NONE,

        /// A jump at both ends.
        JUMP_BOTH;

        /// The name as CSS writes it — `jump-start`.
        public String cssName() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    /// The output for `t`, by CSS's step easing function: the step `t` falls
    /// in, moved up one for a jump at the start, capped at the number of jumps,
    /// as a fraction of that number.
    @Override
    public double at(double t) {
        var clamped = Math.max(0, Math.min(1, t));
        var step = (int) Math.floor(clamped * count);
        if (position == Position.JUMP_START || position == Position.JUMP_BOTH) {
            step++;
        }
        var jumps = switch (position) {
            case JUMP_START, JUMP_END -> count;
            case JUMP_NONE -> count - 1;
            case JUMP_BOTH -> count + 1;
        };
        return (double) Math.min(step, jumps) / jumps;
    }

    @Override
    public String cssName() {
        return "steps(" + count + ", " + position.cssName() + ")";
    }

    @Override
    public String toString() {
        return cssName();
    }
}
