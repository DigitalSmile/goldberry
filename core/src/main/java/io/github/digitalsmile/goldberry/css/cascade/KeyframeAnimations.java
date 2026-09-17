package io.github.digitalsmile.goldberry.css.cascade;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.motion.Easing;

/// Which `@keyframes` a node runs, and how: CSS's `animation` and its seven
/// longhands, resolved by the cascade (ADR-0353).
///
/// ```css
/// .tile { animation: tile-drop 850ms ease-enter both }
/// .tile:nth-of-kind(2) { animation-delay: 60ms }
/// ```
///
/// ## Lists, the way CSS keeps them
///
/// Every longhand is a comma-separated list, and `animation-name`'s length
/// decides how many animations run. A shorter list repeats from its start. So
/// `animation-name: a, b, c; animation-duration: 1s, 2s` runs `c` for one second.
/// Keeping the seven lists apart until [#entries()] is what lets a later rule
/// change only the delay of an animation an earlier rule named, which is what a
/// stagger is.
///
/// ## The whitelist still holds
///
/// A keyframe may declare anything, and only [Transitions.Animatable]'s six
/// properties move. The rest are dropped when a keyframe is resolved, with a
/// warning that names the property, for §1.7's reason: a keyframed `width` is a
/// layout pass on every frame.
///
/// @param names       what `animation-name` said; empty is `none`
/// @param durations   milliseconds, never empty
/// @param easings     §1.7's three curves, never empty
/// @param delays      milliseconds, possibly negative, never empty
/// @param iterations  how many times; [Double#POSITIVE_INFINITY] is `infinite`
/// @param directions  never empty
/// @param fillModes   never empty
public record KeyframeAnimations(
        List<String> names,
        List<Double> durations,
        List<Easing> easings,
        List<Double> delays,
        List<Double> iterations,
        List<Direction> directions,
        List<FillMode> fillModes) {

    /// Nothing runs — CSS's initial value for every longhand.
    public static final KeyframeAnimations NONE = new KeyframeAnimations(
            List.of(),
            List.of(0.0),
            List.of(Easing.EASE_ENTER),
            List.of(0.0),
            List.of(1.0),
            List.of(Direction.NORMAL),
            List.of(FillMode.NONE));

    public KeyframeAnimations {
        names = List.copyOf(names);
        durations = nonEmpty(durations, "animation-duration");
        easings = nonEmpty(easings, "animation-timing-function");
        delays = nonEmpty(delays, "animation-delay");
        iterations = nonEmpty(iterations, "animation-iteration-count");
        directions = nonEmpty(directions, "animation-direction");
        fillModes = nonEmpty(fillModes, "animation-fill-mode");
    }

    private static <T> List<T> nonEmpty(List<T> values, String property) {
        var copy = List.copyOf(Objects.requireNonNull(values, property));
        if (copy.isEmpty()) {
            throw new IllegalArgumentException(property + " needs at least one value");
        }
        return copy;
    }

    /// Which way each iteration plays — `animation-direction`.
    public enum Direction {
        NORMAL,
        REVERSE,
        ALTERNATE,
        ALTERNATE_REVERSE;

        /// The value for a CSS keyword, or null.
        public static @Nullable Direction parse(String keyword) {
            return switch (keyword.toLowerCase(Locale.ROOT)) {
                case "normal" -> NORMAL;
                case "reverse" -> REVERSE;
                case "alternate" -> ALTERNATE;
                case "alternate-reverse" -> ALTERNATE_REVERSE;
                default -> null;
            };
        }

        /// Whether iteration `index` (from 0) plays backwards.
        public boolean backwards(long index) {
            return switch (this) {
                case NORMAL -> false;
                case REVERSE -> true;
                case ALTERNATE -> index % 2 == 1;
                case ALTERNATE_REVERSE -> index % 2 == 0;
            };
        }
    }

    /// What an animation holds outside its active time — `animation-fill-mode`.
    public enum FillMode {
        NONE,
        FORWARDS,
        BACKWARDS,
        BOTH;

        /// The value for a CSS keyword, or null.
        public static @Nullable FillMode parse(String keyword) {
            return switch (keyword.toLowerCase(Locale.ROOT)) {
                case "none" -> NONE;
                case "forwards" -> FORWARDS;
                case "backwards" -> BACKWARDS;
                case "both" -> BOTH;
                default -> null;
            };
        }

        /// Whether the last frame is held after the animation ends.
        public boolean holdsEnd() {
            return this == FORWARDS || this == BOTH;
        }

        /// Whether the first frame is shown during the delay.
        public boolean holdsStart() {
            return this == BACKWARDS || this == BOTH;
        }
    }

    /// One animation, with every longhand's value for it.
    ///
    /// @param name           the `@keyframes` block it runs
    /// @param durationMillis one iteration
    /// @param easing         the curve applied between each pair of keyframes
    /// @param delayMillis    before the first iteration; negative starts part way in
    /// @param iterations     how many; infinite for a loop
    /// @param direction      which way each iteration plays
    /// @param fillMode       what is held before and after
    public record Entry(
            String name,
            double durationMillis,
            Easing easing,
            double delayMillis,
            double iterations,
            Direction direction,
            FillMode fillMode) {

        /// The whole active span, delay excluded — infinite for a loop.
        public double activeMillis() {
            return durationMillis * iterations;
        }
    }

    /// Whether nothing runs.
    public boolean isEmpty() {
        return names.isEmpty();
    }

    /// One [Entry] per name, each longhand's list repeated to cover them.
    public List<Entry> entries() {
        var entries = new ArrayList<Entry>(names.size());
        for (var i = 0; i < names.size(); i++) {
            entries.add(new Entry(
                    names.get(i),
                    durations.get(i % durations.size()),
                    easings.get(i % easings.size()),
                    delays.get(i % delays.size()),
                    iterations.get(i % iterations.size()),
                    directions.get(i % directions.size()),
                    fillModes.get(i % fillModes.size())));
        }
        return entries;
    }

    public KeyframeAnimations names(List<String> value) {
        return new KeyframeAnimations(value, durations, easings, delays, iterations, directions, fillModes);
    }

    public KeyframeAnimations durations(List<Double> value) {
        return new KeyframeAnimations(names, value, easings, delays, iterations, directions, fillModes);
    }

    public KeyframeAnimations easings(List<Easing> value) {
        return new KeyframeAnimations(names, durations, value, delays, iterations, directions, fillModes);
    }

    public KeyframeAnimations delays(List<Double> value) {
        return new KeyframeAnimations(names, durations, easings, value, iterations, directions, fillModes);
    }

    public KeyframeAnimations iterations(List<Double> value) {
        return new KeyframeAnimations(names, durations, easings, delays, value, directions, fillModes);
    }

    public KeyframeAnimations directions(List<Direction> value) {
        return new KeyframeAnimations(names, durations, easings, delays, iterations, value, fillModes);
    }

    public KeyframeAnimations fillModes(List<FillMode> value) {
        return new KeyframeAnimations(names, durations, easings, delays, iterations, directions, value);
    }

    /// Every animation turned off — what reduced motion does to them (§1.7).
    ///
    /// Unlike a transition, a keyframe animation has no end state the cascade
    /// already resolved to collapse onto: a loop never arrives, and a settle's
    /// last keyframe need not be the element's style. So they are dropped, and
    /// the element shows the style its rules give it.
    public KeyframeAnimations reduced() {
        return isEmpty() ? this : names(List.of());
    }
}
