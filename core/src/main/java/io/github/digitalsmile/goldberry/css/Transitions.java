package io.github.digitalsmile.goldberry.css;

import io.github.digitalsmile.goldberry.motion.Easing;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/// Which of a node's properties move rather than snap, and how —
/// `docs/design-system.md` §1.7.
///
/// Part of [ComputedStyle] and therefore resolved by the cascade like everything
/// else, which is what lets `button:hover` and `button` declare different
/// transitions and lets an application turn one off by overriding a rule.
///
/// ## The whitelist is the design, not a limitation
///
/// §1.7: "whitelist = compositor-cheap properties only … **layout properties
/// never transition**". Animating a width or a padding would run Yoga on every
/// frame of every transition, which on a CPU renderer is the difference between
/// a transition and a stutter. The sanctioned movement effects — a tab indicator,
/// a toast reflowing — are done with transforms instead.
///
/// So [Animatable] is a closed enum rather than a property-name string. A
/// stylesheet writing `transition: width 200ms` is a **dropped declaration with
/// a warning naming it**, not a rule that silently never fires: the author asked
/// for something the system deliberately refuses, and needs to be told.
public record Transitions(Map<Animatable, Timing> byProperty) {

    /// Nothing moves. What every node starts as, because §1.7's rule 5 is that
    /// motion is meaning — a toolkit where everything animates by default has
    /// decided that nothing means anything.
    public static final Transitions NONE = new Transitions(Map.of());

    public Transitions {
        byProperty = byProperty == null || byProperty.isEmpty()
                ? Map.of()
                : Map.copyOf(byProperty);
    }

    /// The properties a transition may name.
    ///
    /// `transform` is **absent and would be in this list**: §1.7 whitelists it,
    /// and `checkbox`'s specified check animation is "scale 0.6→1 + opacity".
    /// `Box` carries no transform, and adding one means the painter *and* hit
    /// testing — which would need the inverse to map a pointer back through it,
    /// and silently mis-routes clicks if it does not. That is a correctness trap
    /// worth arriving on its own rather than inside this
    /// ([ADR-0067](../../../../../../book/src/adr/0067-motion-is-an-overlay-on-a-frame-clock.md)).
    public enum Animatable {

        /// Fades. The one every control uses for `:disabled`.
        OPACITY("opacity"),

        /// A control's surface — §3.1's "hover: `background-color` fast".
        BACKGROUND_COLOR("background-color"),

        /// The border, so a checkbox's glyph outline can follow its hover.
        BORDER_COLOR("border-color"),

        /// The foreground: text, icons, and a checkbox's mark.
        COLOR("color");

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
        public static Animatable parse(String name) {
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
    /// @param easing         the curve — one of §1.7's three keywords
    /// @param delayMillis    how long to wait before starting
    public record Timing(double durationMillis, Easing easing, double delayMillis) {

        /// A transition that does not move — what `prefers-reduced-motion`
        /// collapses every one of them to (§1.7's rule 6).
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
    public Timing get(Animatable property) {
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
        next.put(Objects.requireNonNull(property, "property"),
                Objects.requireNonNull(timing, "timing"));
        return new Transitions(next);
    }

    /// Every transition collapsed to instant — §1.7's `prefers-reduced-motion`.
    ///
    /// The declarations are **kept** rather than dropped, at zero duration. That
    /// is deliberate: the transition machinery still runs, still ends, and still
    /// fires whatever depends on it ending, so a reduced-motion user reaches the
    /// same states by the same route and does not take a different code path
    /// through the toolkit. §4 calls that out for the high-contrast theme in the
    /// same words — an alias swap, not a special case.
    public Transitions reduced() {
        if (isEmpty()) {
            return this;
        }
        var next = new EnumMap<Animatable, Timing>(Animatable.class);
        byProperty.keySet().forEach(property -> next.put(property, Timing.INSTANT));
        return new Transitions(next);
    }
}
