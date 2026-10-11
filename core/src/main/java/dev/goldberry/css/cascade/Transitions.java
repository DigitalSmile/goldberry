package dev.goldberry.css.cascade;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
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
public record Transitions(Map<Animatable, Timing> byProperty, Lists lists) {

    /// Nothing moves, which is what every node starts as: motion carries meaning,
    /// and a toolkit where everything animates by default has decided that nothing
    /// means anything.
    public static final Transitions NONE = new Transitions(Map.of());

    public Transitions {
        byProperty = byProperty.isEmpty() ? Map.of() : Map.copyOf(byProperty);
        Objects.requireNonNull(lists, "lists");
    }

    /// The transitions in `byProperty`, with the longhands' lists written as the
    /// `transition` shorthand would have written them. Null, like empty, means
    /// no transitions.
    public Transitions(@Nullable Map<Animatable, Timing> byProperty) {
        this(byProperty == null ? Map.of() : byProperty, Lists.of(byProperty == null ? Map.of() : byProperty));
    }

    /// The transitions the four longhands' `lists` describe.
    public static Transitions of(Lists lists) {
        return new Transitions(lists.timings(), lists);
    }

    /// `transition-property`, `-duration`, `-timing-function` and `-delay` as
    /// written: four lists, paired up by position, the shorter three repeated
    /// to the length of the first, as CSS pairs them.
    ///
    /// Kept beside the timings because the longhands arrive one at a time: a
    /// rule that sets only `transition-duration` changes the durations of the
    /// properties another rule named, and only the lists can say which.
    ///
    /// The initial property list is empty, where CSS's is `all`: a duration on
    /// its own animates nothing here until a property is named, for [#NONE]'s
    /// reason.
    ///
    /// @param properties the names, each an [Animatable]'s or `all`; empty for
    ///                   `none`
    /// @param durations  in milliseconds, at least one
    /// @param easings    at least one
    /// @param delays     in milliseconds, at least one
    public record Lists(List<String> properties, List<Double> durations, List<Easing> easings, List<Double> delays) {

        /// No property, and CSS's initial duration and delay, on the default
        /// curve.
        public static final Lists NONE = new Lists(List.of(), List.of(0.0), List.of(Easing.EASE_ENTER), List.of(0.0));

        public Lists {
            properties = List.copyOf(properties);
            durations = List.copyOf(durations);
            easings = List.copyOf(easings);
            delays = List.copyOf(delays);
            if (durations.isEmpty() || easings.isEmpty() || delays.isEmpty()) {
                throw new IllegalArgumentException("a transition longhand's list has at least one entry");
            }
        }

        /// The lists the timings in `byProperty` came from, one entry each.
        static Lists of(Map<Animatable, Timing> byProperty) {
            if (byProperty.isEmpty()) {
                return NONE;
            }
            var names = new ArrayList<String>();
            var durations = new ArrayList<Double>();
            var easings = new ArrayList<Easing>();
            var delays = new ArrayList<Double>();
            for (var property : Animatable.values()) {
                var timing = byProperty.get(property);
                if (timing != null) {
                    names.add(property.cssName());
                    durations.add(timing.durationMillis());
                    easings.add(timing.easing());
                    delays.add(timing.delayMillis());
                }
            }
            return new Lists(names, durations, easings, delays);
        }

        public Lists properties(List<String> value) {
            return new Lists(value, durations, easings, delays);
        }

        public Lists durations(List<Double> value) {
            return new Lists(properties, value, easings, delays);
        }

        public Lists easings(List<Easing> value) {
            return new Lists(properties, durations, value, delays);
        }

        public Lists delays(List<Double> value) {
            return new Lists(properties, durations, easings, value);
        }

        /// The timing each property ends up with. A later entry for the same
        /// property, or an `all` after it, wins.
        Map<Animatable, Timing> timings() {
            var timings = new EnumMap<Animatable, Timing>(Animatable.class);
            for (var i = 0; i < properties.size(); i++) {
                var timing = new Timing(
                        durations.get(i % durations.size()),
                        easings.get(i % easings.size()),
                        delays.get(i % delays.size()));
                var name = properties.get(i);
                if (name.equalsIgnoreCase("all")) {
                    for (var property : Animatable.values()) {
                        timings.put(property, timing);
                    }
                } else {
                    var property = Animatable.parse(name);
                    if (property != null) {
                        timings.put(property, timing);
                    }
                }
            }
            return timings;
        }
    }

    /// The properties a transition may name.
    ///
    /// Seven, and none of them changes a layout. `transform` is among them because
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

        /// Where a box's gradient layers sit. Moving a repeating stripe by one
        /// period, forever, is how a "running" bar marches.
        BACKGROUND_POSITION("background-position"),

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
        TRANSFORM("transform"),

        /// The `translate` property, apart from `transform`: a hover's lift.
        TRANSLATE("translate"),

        /// The `rotate` property: a disclosure chevron turning.
        ROTATE("rotate"),

        /// The `scale` property: a press shrinking a button a little.
        SCALE("scale"),

        /// The corners, each radius and percentage by itself: a square button
        /// rounding into a pill. Paint only, as every one here is.
        BORDER_RADIUS("border-radius"),

        /// The focus ring's colour.
        OUTLINE_COLOR("outline-color"),

        /// How far out the focus ring sits: a ring that settles onto its box.
        OUTLINE_OFFSET("outline-offset");

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
    /// @param easing         the curve: one of the design system's three in the
    ///                       toolkit's own sheets, any CSS curve in an application's
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
        return new Transitions(next, lists);
    }
}
