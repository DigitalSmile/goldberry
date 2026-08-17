package io.github.digitalsmile.goldberry.motion;

import java.util.Locale;

/// How a value moves between two others — `docs/design-system.md` §1.7.
///
/// **Three keywords, and no raw beziers in the stylesheet.** §1.7 says the CSS
/// subset accepts these names rather than `cubic-bezier(…)`, and that is the
/// whole point: a design system where every screen can invent its own curve has
/// no motion language, only motion. `ease-enter` decelerates, `ease-exit`
/// accelerates, `linear` is for continuous indicators. There is deliberately no
/// bounce or overshoot in system components.
///
/// ## Why the solver is here and not in the parser
///
/// A cubic Bézier easing is not a function of `t` directly. CSS defines it as a
/// parametric curve through `(0,0)`, `(x1,y1)`, `(x2,y2)`, `(1,1)`, where the
/// *input* progress is the x coordinate and the eased output is y. Getting from
/// x to y means solving `bezierX(s) = x` for the parameter `s` first, which has
/// no closed form.
public enum Easing {

    /// `cubic-bezier(0.2, 0, 0, 1)` — decelerate. Enters, and anything arriving.
    EASE_ENTER(0.2, 0, 0, 1),

    /// `cubic-bezier(0.4, 0, 1, 1)` — accelerate. Exits, and anything leaving.
    ///
    /// §1.7's rule 2 says every exit is shorter than its enter *and* uses this,
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

    /// The keyword, or null if the name is not one of the three.
    ///
    /// Null rather than a default, so a stylesheet writing `ease-in-out` — a
    /// keyword CSS has and this system does not — is a dropped declaration with
    /// the text quoted rather than a curve nobody chose.
    public static Easing parse(String name) {
        for (var candidate : values()) {
            if (candidate.cssName().equalsIgnoreCase(name)) {
                return candidate;
            }
        }
        return null;
    }

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
        return 3 * inverse * inverse * s * c1
                + 3 * inverse * s * s * c2
                + s * s * s;
    }

    /// The derivative of [#bezier], for Newton's method.
    private static double slope(double s, double c1, double c2) {
        var inverse = 1 - s;
        return 3 * inverse * inverse * c1
                + 6 * inverse * s * (c2 - c1)
                + 3 * s * s * (1 - c2);
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
