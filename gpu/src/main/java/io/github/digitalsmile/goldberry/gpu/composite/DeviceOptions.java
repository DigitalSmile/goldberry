package io.github.digitalsmile.goldberry.gpu.composite;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuDevice;

/// How the process's GPU device is asked for (`docs/gpu-plan.md`, D2): every
/// shader format the toolkit ships, the low-power GPU, and SDL's choice of
/// driver, unless two system properties say otherwise.
///
/// - `goldberry.gpu.driver`: `metal`, `vulkan` or `direct3d12`;
/// - `goldberry.gpu.debug`: `true` for the driver's validation, which is slower
///   and says what it objects to.
final class DeviceOptions {

    /// Names the driver.
    static final String DRIVER_PROPERTY = "goldberry.gpu.driver";

    /// Turns the driver's validation on.
    static final String DEBUG_PROPERTY = "goldberry.gpu.debug";

    private DeviceOptions() {}

    /// The options the system properties give.
    static SdlGpuDevice.Options fromProperties() {
        return of(System.getProperty(DRIVER_PROPERTY), System.getProperty(DEBUG_PROPERTY));
    }

    /// The options `driver` and `debug` give; either may be null for unset, and a
    /// blank driver is SDL's choice.
    static SdlGpuDevice.Options of(@Nullable String driver, @Nullable String debug) {
        var options = SdlGpuDevice.Options.defaults().withDebugMode(Boolean.parseBoolean(debug));
        return driver == null || driver.isBlank() ? options : options.withDriver(driver.trim());
    }
}
