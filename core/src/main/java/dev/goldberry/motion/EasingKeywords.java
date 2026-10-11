package dev.goldberry.motion;

import java.util.Map;

import dev.goldberry.motion.easing.CubicBezier;
import dev.goldberry.motion.easing.Steps;

/// CSS's easing keywords, as the curves CSS defines for them. Kept here, beside
/// [Easing], because an interface cannot hold a private table of its own.
final class EasingKeywords {

    /// `ease`, the four it is a family of, and the two one-step functions.
    static final Map<String, Easing> CSS = Map.of(
            "ease", new CubicBezier(0.25, 0.1, 0.25, 1),
            "ease-in", new CubicBezier(0.42, 0, 1, 1),
            "ease-out", new CubicBezier(0, 0, 0.58, 1),
            "ease-in-out", new CubicBezier(0.42, 0, 0.58, 1),
            "step-start", new Steps(1, Steps.Position.JUMP_START),
            "step-end", new Steps(1, Steps.Position.JUMP_END));

    private EasingKeywords() {}
}
