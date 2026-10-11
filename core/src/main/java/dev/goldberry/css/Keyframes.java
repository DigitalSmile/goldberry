package dev.goldberry.css;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import dev.goldberry.css.media.MediaCondition;

/// A named `@keyframes` block: where each moment of an animation is, and what
/// it declares there.
///
/// ```css
/// @keyframes tile-drop {
///   from { opacity: 0; transform: translateY(-20px) rotate(-4deg) }
///   60%  { opacity: 1 }
///   to   { transform: none }
/// }
/// ```
///
/// Declarations are kept as tokens, like a [StyleRule]'s. What `var(--gb-accent)`
/// means depends on the element the animation runs on, so a keyframe is resolved
/// per element rather than once per stylesheet.
///
/// A block inside `@media` is declared only while its condition holds: the
/// last block of a name whose condition holds is the one an animation runs.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#transition-and-animation).
///
/// @param name   the name `animation-name` refers to, as written; matched with
///               case preserved, as CSS matches it
/// @param frames the keyframes, sorted by their first offset
/// @param media  the condition it is declared under, [MediaCondition#ALWAYS]
///               outside any `@media`
public record Keyframes(String name, List<Frame> frames, MediaCondition media) {

    /// A block declared outside any `@media`.
    public Keyframes(String name, List<Frame> frames) {
        this(name, frames, MediaCondition.ALWAYS);
    }

    /// This block, declared under `condition` as well as its own.
    public Keyframes under(MediaCondition condition) {
        Objects.requireNonNull(condition, "condition");
        if (condition == MediaCondition.ALWAYS) {
            return this;
        }
        var joined = media == MediaCondition.ALWAYS ? condition : new MediaCondition.And(List.of(condition, media));
        return new Keyframes(name, frames, joined);
    }

    /// Whether it is declared only under a condition.
    public boolean isConditional() {
        return media != MediaCondition.ALWAYS;
    }

    public Keyframes {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(media, "media");
        frames = Objects.requireNonNull(frames, "frames").stream()
                .sorted(Comparator.comparingDouble(Frame::offset))
                .toList();
    }

    /// One moment of the animation.
    ///
    /// `from, 50%` is two moments sharing a block, and the parser splits it into
    /// two frames here, so a frame has one offset and a lookup never has to
    /// search a list inside a list.
    ///
    /// @param offset       where in one iteration, from 0 to 1
    /// @param declarations what is declared there, in source order
    public record Frame(double offset, List<Declaration> declarations) {

        public Frame {
            if (!Double.isFinite(offset) || offset < 0 || offset > 1) {
                throw new IllegalArgumentException("a keyframe is between 0% and 100%, not " + offset * 100 + "%");
            }
            declarations = List.copyOf(Objects.requireNonNull(declarations, "declarations"));
        }
    }
}
