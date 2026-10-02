package dev.goldberry.motion;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;

/// How a value moves between two others: the design system's three easing curves.
///
/// **Three curves, and no raw beziers in the stylesheet.** The CSS subset
/// accepts these names rather than `cubic-bezier(…)`, and that is the
/// whole point: a design system where every screen can invent its own curve has
/// no motion language, only motion. `ease-enter` decelerates, `ease-exit`
/// accelerates, `linear` is for continuous indicators. There is deliberately no
/// bounce or overshoot in system components.
///
/// CSS's own four keywords are read **onto** the three, so a stylesheet written
/// for a browser keeps its motion rather than losing the declaration:
///
/// | written | read as | because |
/// |---|---|---|
/// | `ease` | `ease-enter` | CSS's default; it decelerates for most of its length |
/// | `ease-out` | `ease-enter` | the same shape: fast, then settling |
/// | `ease-in` | `ease-exit` | the same shape: slow, then leaving |
/// | `ease-in-out` | `ease-enter` | the system has no symmetric curve, and the ending is what is seen |
///
/// Each mapping is logged once, at info, so an author can see what their
/// keyword became.
///
/// ## Why the solver is here and not in the parser
///
/// A cubic Bézier easing is not a function of `t` directly. CSS defines it as a
/// parametric curve through `(0,0)`, `(x1,y1)`, `(x2,y2)`, `(1,1)`, where the
/// *input* progress is the x coordinate and the eased output is y. Getting from
/// x to y means solving `bezierX(s) = x` for the parameter `s` first, which has
/// no closed form.
///
/// Read more: [Motion](https://goldberry.dev/docs/guide/design-system.html#motion).
public enum Easing {

    /// `cubic-bezier(0.2, 0, 0, 1)` — decelerate. Enters, and anything arriving.
    EASE_ENTER(0.2, 0, 0, 1),

    /// `cubic-bezier(0.4, 0, 1, 1)` — accelerate. Exits, and anything leaving.
    ///
    /// Every exit is shorter than its enter *and* uses this curve,
    /// which together are what make a dismissal feel decisive rather than
    /// reluctant.
    EASE_EXIT(0.4, 0, 1, 1),

    /// No easing at all. **Continuous indicators only** — a spinner that eased
    /// would appear to stutter once per revolution.
    LINEAR(0, 0, 1, 1);

    private final double x1;
    private final double y1;
    private final double x2;
    private final double y2;

    Easing(double x1, double y1, double x2, double y2) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
    }

    /// The name as CSS writes it — `ease-enter`.
    public String cssName() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /// The curve a keyword names, or null if it names none.
    ///
    /// The three system names, and CSS's `ease`, `ease-in`, `ease-out` and
    /// `ease-in-out`, read onto them as the type's table says. Anything else,
    /// `step-start` or a misspelling, is null rather than a default, so the
    /// caller can name the text it refused rather than run a curve nobody chose.
    public static @Nullable Easing parse(String name) {
        for (var candidate : values()) {
            if (candidate.cssName().equalsIgnoreCase(name)) {
                return candidate;
            }
        }
        var lower = name.toLowerCase(Locale.ROOT);
        var mapped = CSS_KEYWORDS.get(lower);
        if (mapped != null && MAPPED.add(lower)) {
            LOG.info(
                    "easing \"{}\" is read as {}: the design system has three curves, ease-enter, ease-exit and linear",
                    lower,
                    mapped.cssName());
        }
        return mapped;
    }

    /// Whether `name` is one of CSS's own easing keywords rather than one of the
    /// three system names.
    public static boolean isCssKeyword(String name) {
        return CSS_KEYWORDS.containsKey(name.toLowerCase(Locale.ROOT));
    }

    /// CSS's four keywords and the system curve each is read as. See the type's
    /// table for the reasons.
    private static final Map<String, Easing> CSS_KEYWORDS = Map.of(
            "ease", EASE_ENTER,
            "ease-out", EASE_ENTER,
            "ease-in", EASE_EXIT,
            "ease-in-out", EASE_ENTER);

    /// The CSS keywords already reported, so a mapping is one line in a log
    /// rather than one per element per restyle.
    private static final Set<String> MAPPED = ConcurrentHashMap.newKeySet();

    private static final Logger LOG = Logs.of(Easing.class);

    /// The eased progress for a linear progress `t`, both in `0..1`.
    ///
    /// Clamped at both ends: a transition asked about a time before it started
    /// or after it ended has a defined answer, which is what lets the caller not
    /// special-case the boundaries.
    public double at(double t) {
        if (t <= 0) {
            return 0;
        }
        if (t >= 1) {
            return 1;
        }
        if (this == LINEAR) {
            return t;
        }
        return bezier(solveForX(t), y1, y2);
    }

    /// One coordinate of a cubic Bézier from 0 to 1 through two controls.
    ///
    /// The standard basis, with the first and last points fixed at 0 and 1 so
    /// only the two control coordinates vary.
    private static double bezier(double s, double c1, double c2) {
        var inverse = 1 - s;
        return 3 * inverse * inverse * s * c1 + 3 * inverse * s * s * c2 + s * s * s;
    }

    /// The derivative of [#bezier], for Newton's method.
    private static double slope(double s, double c1, double c2) {
        var inverse = 1 - s;
        return 3 * inverse * inverse * c1 + 6 * inverse * s * (c2 - c1) + 3 * s * s * (1 - c2);
    }

    /// The curve parameter whose x coordinate is `x`.
    ///
    /// Newton–Raphson, falling back to bisection where the slope is too flat for
    /// it to converge — which is exactly what happens at the ends of
    /// `ease-enter`, whose second control x is 0 and whose tangent there is
    /// therefore horizontal. Newton alone would step off the interval and return
    /// a progress outside `0..1`, which reads as a value that jumps.
    ///
    /// Eight Newton iterations then eight bisections is far more than either
    /// needs for a curve this shallow; it costs about 40 ns and runs once per
    /// animating property per frame.
    private double solveForX(double x) {
        var s = x;
        for (var i = 0; i < 8; i++) {
            var error = bezier(s, x1, x2) - x;
            if (Math.abs(error) < 1e-7) {
                return s;
            }
            var derivative = slope(s, x1, x2);
            if (Math.abs(derivative) < 1e-6) {
                break;
            }
            s -= error / derivative;
        }

        var low = 0.0;
        var high = 1.0;
        s = x;
        for (var i = 0; i < 32 && high - low > 1e-7; i++) {
            if (bezier(s, x1, x2) < x) {
                low = s;
            } else {
                high = s;
            }
            s = (low + high) / 2;
        }
        return s;
    }
}
