package dev.goldberry.gpu;

import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/// Which GPU the process's device is asked for on a machine with two: a laptop
/// with an integrated GPU beside a discrete one.
///
/// A preference, not a choice: the driver decides, and on a machine with one
/// GPU both values give that one. [GpuDevice#adapter] says which one ran.
///
/// The property `goldberry.gpu.power` (`low` or `high`) sets it for the whole
/// process; `OffscreenGpu.open(GpuPower)` sets it for an offscreen device made
/// before any window has one.
///
/// Read more: [The GPU device](https://goldberry.dev/docs/components/gpu.html#what-the-module-does-to-a-window).
public enum GpuPower {
    /// The integrated GPU: less heat and battery. The default.
    LOW,
    /// The discrete GPU: more speed.
    HIGH;

    /// The preference `value` names, `low` or `high` in any case and with any
    /// surrounding blanks, or empty for anything else, null included.
    public static Optional<GpuPower> parse(@Nullable String value) {
        if (value == null) {
            return Optional.empty();
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "low" -> Optional.of(LOW);
            case "high" -> Optional.of(HIGH);
            default -> Optional.empty();
        };
    }
}
