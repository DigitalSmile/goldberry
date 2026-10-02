package dev.goldberry.css.cascade;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.motion.Easing;

/// Which of a node's properties move rather than snap when their value changes,
/// and how.
///
/// ```css
/// button        { transition: background-color var(--gb-motion-fast) ease-enter }
/// button:active { transition: background-color 0ms }
/// ```
///
/// Part of [ComputedStyle], so it is resolved by the cascade like every other
/// property: `button:hover` and `button` can declare different transitions, and
/// an application turns one off by overriding the rule. The timing that applies
/// is the one on the style being moved to, so the two rules above make a press
/// snap and a release fade.
///
/// The properties that can transition are a closed whitelist, [Animatable], and
/// a layout property is never among them. Animating a width or a padding would
/// run layout on every frame of every transition, which on a CPU renderer is the
/// difference between a transition and a stutter; a tab indicator or a toast
/// moves with a transform instead. So a stylesheet writing
/// `transition: width 200ms` gets a dropped declaration with a warning naming
/// it, not a rule that silently never fires.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#transition-and-animation).
public record Transitions(Map<Animatable, Timing> byProperty) {

    /// Nothing moves, which is what every node starts as: motion carries meaning,
    /// and a toolkit where everything animates by default has decided that nothing
    /// means anything.
    public static final Transitions NONE = new Transitions(Map.of());

    /// Written out so the parameter can say that null, like empty, means no transitions.
    public Transitions(@Nullable Map<Animatable, Timing> byProperty) {
        byProperty = byProperty == null || byProperty.isEmpty() ? Map.of() : Map.copyOf(byProperty);
        this.byProperty = byProperty;
    }

    /// The properties a transition may name.
    ///
    /// Six, and none of them changes a layout. `transform` is among them because
    /// hit testing inverts the same matrix the painter applies, so a moving
    /// control responds where it is drawn; a transform the painter applied and hit
    /// testing did not would be a control that looks right and does not respond
    /// where it looks like it should, a failure with no error and no wrong pixel.
    /// `box-shadow` is among them because an elevation change is how an `affix`
    /// detaches and how a `card.interactive` lifts under the pointer, and a
    /// `transition` naming a property the engine resolves but cannot animate would
    /// be exactly the silent nothing this type refuses.
    public enum Animatable {

        /// Fades. The one every control uses for `:disabled`.
        OPACITY("opacity"),

        /// A control's surface, which is what a hover changes, fast.
        BACKGROUND_COLOR("background-color"),

        /// The border, so a checkbox's glyph outline can follow its hover.
        BORDER_COLOR("border-color"),

        /// The drop shadow, which is elevation moving. Every component of it
        /// interpolates, so a card lifting from `--gb-elevation-1` to `-2` grows
        /// its blur and its offset as well as its alpha, which is what an object
        /// rising off a page does.
        BOX_SHADOW("box-shadow"),

        /// The foreground: text, icons, and a checkbox's mark.
        COLOR("color"),

        /// Position, scale, rotation and skew: the compositor-cheap way to move
        /// something, and the reason a width can be refused without forbidding
        /// movement. A checkbox tick's `scale 0.6→1` is this one.
        TRANSFORM("transform");

        private final String cssName;

        Animatable(String cssName) {
            this.cssName = cssName;
        }

        public String cssName() {
            return cssName;
        }

        /// The property this name refers to, or null.
        ///
        /// `background` is accepted as a synonym for `background-color`, because
        /// the toolkit's own rules are written with the shorthand and an author
        /// who wrote `transition: background` meant the colour — it is the only
        /// part of `background` that exists here.
        public static @Nullable Animatable parse(String name) {
            var lower = name.toLowerCase(Locale.ROOT);
            if (lower.equals("background")) {
                return BACKGROUND_COLOR;
            }
            for (var candidate : values()) {
                if (candidate.cssName.equals(lower)) {
                    return candidate;
                }
            }
            return null;
        }
    }

    /// How long one property takes, on what curve, after what wait.
    ///
    /// @param durationMillis how long the move takes; zero means it snaps
    /// @param easing         the curve: `ease-enter`, `ease-exit` or `linear`
    /// @param delayMillis    how long to wait before starting
    public record Timing(double durationMillis, Easing easing, double delayMillis) {

        /// A transition that does not move, which is what reduced motion collapses
        /// every one of them to.
        public static final Timing INSTANT = new Timing(0, Easing.LINEAR, 0);

        public Timing {
            Objects.requireNonNull(easing, "easing");
            if (!Double.isFinite(durationMillis) || durationMillis < 0) {
                throw new IllegalArgumentException(
                        "a duration is a non-negative number of milliseconds, not " + durationMillis);
            }
            if (!Double.isFinite(delayMillis)) {
                throw new IllegalArgumentException("a delay must be finite, not " + delayMillis);
            }
        }

        /// Whether this actually moves anything.
        public boolean isInstant() {
            return durationMillis <= 0;
        }

        /// The whole span, delay included.
        public double totalMillis() {
            return Math.max(0, delayMillis) + durationMillis;
        }
    }

    /// The timing for one property, or null if it does not transition.
    public @Nullable Timing get(Animatable property) {
        return byProperty.get(property);
    }

    /// Whether anything here moves.
    public boolean isEmpty() {
        return byProperty.isEmpty();
    }

    /// This set with one property's timing replaced.
    public Transitions with(Animatable property, Timing timing) {
        var next = new EnumMap<Animatable, Timing>(Animatable.class);
        next.putAll(byProperty);
        next.put(Objects.requireNonNull(property, "property"), Objects.requireNonNull(timing, "timing"));
        return new Transitions(next);
    }

    /// Every transition collapsed to instant, which is what reduced motion does.
    ///
    /// The declarations are kept at zero duration rather than dropped: the
    /// transition machinery still runs, still ends, and still fires whatever
    /// depends on it ending, so a reduced-motion user reaches the same states by
    /// the same route and does not take a different code path through the toolkit.
    public Transitions reduced() {
        if (isEmpty()) {
            return this;
        }
        var next = new EnumMap<Animatable, Timing>(Animatable.class);
        byProperty.keySet().forEach(property -> next.put(property, Timing.INSTANT));
        return new Transitions(next);
    }
}
