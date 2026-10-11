package dev.goldberry.natives.desktop;

/// One of the desktop's accessibility switches, read rather than set.
///
/// Three answers and not two, for [MotionPreference]'s reason: "the desktop
/// says off" and "the desktop does not say" are different facts, and the
/// second is a default rather than an instruction.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public enum Setting {
    /// The desktop has no such switch, has not been asked, or could not be.
    UNKNOWN,
    /// The switch is off.
    OFF,
    /// The switch is on.
    ON;

    /// Whether the switch is on. `UNKNOWN` is not.
    public boolean isOn() {
        return this == ON;
    }
}
