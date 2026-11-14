package dev.goldberry.gpu.composite;

import org.jspecify.annotations.Nullable;

import dev.goldberry.gpu.GpuPower;
import dev.goldberry.natives.sdl.gpu.SdlGpuDevice;

/// How the process's GPU device is asked for: every
/// shader format the toolkit ships, the low-power GPU, and SDL's choice of
/// driver, unless three system properties say otherwise.
///
/// - `goldberry.gpu.driver`: `metal`, `vulkan` or `direct3d12`;
/// - `goldberry.gpu.debug`: `true` for the driver's validation, which is slower
///   and says what it objects to;
/// - `goldberry.gpu.power`: `low` (the default) or `high`, the GPU preferred on
///   a machine with an integrated and a discrete one ([GpuPower]).
final class DeviceOptions {

    /// Names the driver.
    static final String DRIVER_PROPERTY = "goldberry.gpu.driver";

    /// Turns the driver's validation on.
    static final String DEBUG_PROPERTY = "goldberry.gpu.debug";

    /// Prefers the integrated or the discrete GPU.
    static final String POWER_PROPERTY = "goldberry.gpu.power";

    private DeviceOptions() {}

    /// The options the system properties give, with `power` in place of the
    /// power property when it is not null.
    static SdlGpuDevice.Options fromProperties(@Nullable GpuPower power) {
        var options = of(
                System.getProperty(DRIVER_PROPERTY),
                System.getProperty(DEBUG_PROPERTY),
                System.getProperty(POWER_PROPERTY));
        return power == null ? options : options.withPreferLowPower(power == GpuPower.LOW);
    }

    /// The options `driver` and `debug` give; either may be null for unset, and a
    /// blank driver is SDL's choice.
    static SdlGpuDevice.Options of(@Nullable String driver, @Nullable String debug) {
        return of(driver, debug, null);
    }

    /// The options `driver`, `debug` and `power` give; any may be null for unset.
    /// A power that is neither `low` nor `high` is the default, as every tuning
    /// flag here is: a typo should not stop a window opening.
    static SdlGpuDevice.Options of(@Nullable String driver, @Nullable String debug, @Nullable String power) {
        var options = SdlGpuDevice.Options.defaults()
                .withDebugMode(Boolean.parseBoolean(debug))
                .withPreferLowPower(GpuPower.parse(power).orElse(GpuPower.LOW) == GpuPower.LOW);
        return driver == null || driver.isBlank() ? options : options.withDriver(driver.trim());
    }
}
