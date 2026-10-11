package dev.goldberry.motion;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import dev.goldberry.motion.easing.CubicBezier;
import dev.goldberry.motion.easing.PiecewiseLinear;
import dev.goldberry.motion.easing.Steps;

/// How a value moves between two others.
///
/// **The design system has three curves**, and they are the constants here:
/// [#EASE_ENTER] decelerates, [#EASE_EXIT] accelerates, [#LINEAR] is for
/// continuous indicators. There is deliberately no bounce or overshoot in system
/// components, and the toolkit's own stylesheets name these three and nothing
/// else.
///
/// The type has room for CSS's other curves as well, one record per kind: a
/// [CubicBezier], a [Steps] function and a [PiecewiseLinear] function. They
/// exist for an application's own sheets; a component of the toolkit never
/// reaches for one. A reader that switches over the kinds handles all three or
/// does not compile.
///
/// CSS's own keywords, `ease`, `ease-in`, `ease-out`, `ease-in-out`,
/// `step-start` and `step-end`, are the curves CSS defines for them. The
/// toolkit's own sheets do not write them.
///
/// Read more: [Motion](https://goldberry.dev/docs/guide/design-system.html#motion).
public sealed interface Easing permits CubicBezier, Steps, PiecewiseLinear {

    /// `cubic-bezier(0.2, 0, 0, 1)` — decelerate. Enters, and anything arriving.
    Easing EASE_ENTER = new CubicBezier(0.2, 0, 0, 1);

    /// `cubic-bezier(0.4, 0, 1, 1)` — accelerate. Exits, and anything leaving.
    ///
    /// Every exit is shorter than its enter *and* uses this curve,
    /// which together are what make a dismissal feel decisive rather than
    /// reluctant.
    Easing EASE_EXIT = new CubicBezier(0.4, 0, 1, 1);

    /// No easing at all. **Continuous indicators only** — a spinner that eased
    /// would appear to stutter once per revolution.
    Easing LINEAR = PiecewiseLinear.IDENTITY;

    /// The design system's three curves, in the order above.
    List<Easing> BUILT_IN = List.of(EASE_ENTER, EASE_EXIT, LINEAR);

    /// The eased progress for a linear progress `t`.
    ///
    /// `t` is clamped to `0..1` first: a transition asked about a time before it
    /// started or after it ended has a defined answer, which is what lets the
    /// caller not special-case the boundaries. Every curve answers 1 at the end.
    /// The three built-in curves stay within `0..1` throughout; a curve an
    /// application wrote may overshoot, as CSS allows.
    double at(double t);

    /// The curve as CSS writes it: `ease-enter` for a built-in curve, its
    /// function otherwise, such as `cubic-bezier(0.1, 0.7, 1, 0.1)`.
    String cssName();

    /// The curve a keyword names, or null if it names none.
    ///
    /// The three system names, and CSS's keywords as the curves CSS defines.
    /// Anything else, a function or a misspelling, is null rather than a
    /// default, so the caller can name the text it refused rather than run a
    /// curve nobody chose. The functions are a stylesheet's to read.
    static @Nullable Easing parse(String name) {
        for (var candidate : BUILT_IN) {
            if (candidate.cssName().equalsIgnoreCase(name)) {
                return candidate;
            }
        }
        return EasingKeywords.CSS.get(name.toLowerCase(Locale.ROOT));
    }

    /// Whether `name` is one of CSS's own easing keywords rather than one of the
    /// three system names.
    static boolean isCssKeyword(String name) {
        return EasingKeywords.CSS.containsKey(name.toLowerCase(Locale.ROOT));
    }
}
