package io.github.digitalsmile.goldberry.natives.desktop;

/// What the desktop says about animation — §13's fourth accessibility switch,
/// read rather than set.
///
/// Three answers and not two, for [io.github.digitalsmile.goldberry.natives.sdl.desktop.SdlSystemTheme]'s
/// reason: "the desktop asks for less movement" and "the desktop does not say"
/// are different facts, and an application needs to tell them apart — the first
/// is an instruction and the second is a default.
public enum MotionPreference {

    /// The desktop has no such setting, has not been asked, or could not be.
    UNKNOWN,

    /// Animate normally.
    FULL,

    /// Reduce it: §13's reduce-motion, CSS's `prefers-reduced-motion: reduce`.
    REDUCED;

    /// Whether this asks for less movement. `UNKNOWN` does not — a default is
    /// not an instruction.
    public boolean isReduced() {
        return this == REDUCED;
    }
}
