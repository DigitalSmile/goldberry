package io.github.digitalsmile.goldberry.css;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/// A named `@keyframes` block: where each moment of an animation is, and what
/// it declares there (ADR-0353).
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
/// @param name   the name `animation-name` refers to, as written; matched with
///               case preserved, as CSS matches it
/// @param frames the keyframes, sorted by their first offset
public record Keyframes(String name, List<Frame> frames) {

    public Keyframes {
        Objects.requireNonNull(name, "name");
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
