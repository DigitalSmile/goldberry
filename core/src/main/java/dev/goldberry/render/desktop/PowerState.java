package dev.goldberry.render.desktop;

import java.util.Objects;
import java.util.OptionalInt;

/// Where the machine's power comes from, how much battery is left, and whether
/// the user asked the desktop to save power.
///
/// ```java
/// window.onPowerChanged(power -> spinner.setVisible(!power.saving()));
/// ```
///
/// Read, not set, and read every so often: no desktop raises an event for a
/// battery draining, so a window asks again on a timer and reports a change.
/// **`saving` is the switch the user turned on** — macOS's Low Power Mode,
/// Windows' battery saver, a Linux desktop's power-saver profile — and it is
/// what the toolkit acts on itself, by holding animation frames to
/// [#SAVING_FRAME_RATE] a second. Running on battery alone changes nothing the
/// toolkit does; it is there for an application that wants to do less.
///
/// @param source  where the power comes from
/// @param percent how much battery is left, 0 to 100, or -1 where there is no
///                battery or the platform does not say
/// @param saving  whether the desktop's power-saving mode is on; false where
///                it has none or does not say
public record PowerState(Source source, int percent, boolean saving) {

    /// The animation frame rate a window holds itself to while [#saving] is on,
    /// in frames a second: what a browser holds a page to in its energy saver.
    public static final double SAVING_FRAME_RATE = 30;

    /// What a machine that says nothing reads as: power from nowhere in
    /// particular, no battery reading, no saving mode.
    public static final PowerState UNKNOWN = new PowerState(Source.UNKNOWN, -1, false);

    /// Where the power comes from.
    public enum Source {

        /// On battery, not being charged.
        BATTERY,

        /// Plugged in and charging the battery.
        CHARGING,

        /// Plugged in with the battery full.
        CHARGED,

        /// Plugged in, with no battery at all: a desktop machine.
        MAINS,

        /// The platform would not say.
        UNKNOWN
    }

    public PowerState {
        Objects.requireNonNull(source, "source");
        if (percent < -1 || percent > 100) {
            throw new IllegalArgumentException("a battery is 0 to 100 percent full, or -1 for unknown: " + percent);
        }
    }

    /// Whether the machine is running on its battery.
    public boolean onBattery() {
        return source == Source.BATTERY;
    }

    /// How much battery is left, where the platform says.
    public OptionalInt batteryPercent() {
        return percent < 0 ? OptionalInt.empty() : OptionalInt.of(percent);
    }
}
