package dev.goldberry.natives.desktop;

/// What the desktop says about animation — the reduce-motion accessibility
/// switch, read rather than set.
///
/// Three answers and not two, for [dev.goldberry.natives.sdl.desktop.SdlSystemTheme]'s
/// reason: "the desktop asks for less movement" and "the desktop does not say"
/// are different facts, and an application needs to tell them apart — the first
/// is an instruction and the second is a default.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public enum MotionPreference {

    /// The desktop has no such setting, has not been asked, or could not be.
    UNKNOWN,

    /// Animate normally.
    FULL,

    /// Reduce it: the desktop's reduce-motion, CSS's `prefers-reduced-motion: reduce`.
    REDUCED;

    /// Whether this asks for less movement. `UNKNOWN` does not — a default is
    /// not an instruction.
    public boolean isReduced() {
        return this == REDUCED;
    }
}
