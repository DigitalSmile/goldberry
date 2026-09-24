package io.github.digitalsmile.goldberry.natives.sdl.gpu;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assumptions;

import io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement;
import io.github.digitalsmile.goldberry.natives.sdl.Sdl;
import io.github.digitalsmile.goldberry.natives.sdl.SdlException;
import io.github.digitalsmile.goldberry.natives.sdl.SdlSubsystem;

/// What a test that needs a GPU device does when there is none: skip, or fail
/// where the run said a device is required.
///
/// The rule is `NativeLibraryRequirement`'s (ADR-0016) for a device rather than
/// for the library. A contributor's machine without a usable driver skips; the
/// GPU lane passes `-Pgoldberry.gpu.required=true`, because a GPU job whose tests
/// all skipped is a green tick over nothing.
///
/// The video driver is SDL's default unless `goldberry.gpu.videoDriver` names
/// one: `offscreen` for lavapipe on a runner with no display, whose Vulkan
/// surface needs none.
sealed interface GpuDeviceRequirement {

    /// Set by the test task from `-Pgoldberry.gpu.required`.
    String REQUIRED_PROPERTY = "goldberry.gpu.required";

    /// Set by the test task from `-Pgoldberry.gpu.videoDriver`.
    String VIDEO_DRIVER_PROPERTY = "goldberry.gpu.videoDriver";

    /// A device was made. The test runs on it.
    record Run(SdlGpuDevice device) implements GpuDeviceRequirement {}

    /// None could be made, and none was promised.
    record Skip(String reason) implements GpuDeviceRequirement {}

    /// None could be made where one was required.
    record Fail(String reason) implements GpuDeviceRequirement {}

    /// Decides from what creating a device gave, and whether one was required.
    ///
    /// @param device  the device, or null when none could be made
    /// @param reason  why not, when none could be made
    /// @param required whether the run declared a device mandatory
    static GpuDeviceRequirement decide(@Nullable SdlGpuDevice device, String reason, boolean required) {
        if (device != null) {
            return new Run(device);
        }
        return required
                ? new Fail("a GPU device is required for this run but none could be made: " + reason)
                : new Skip("no GPU device on this machine: " + reason);
    }

    /// Initialises SDL's video under the configured driver and makes a device
    /// with the driver's validation on; skips or fails the calling test when
    /// that is not possible.
    static SdlGpuDevice enforce() {
        NativeLibraryRequirement.enforce();
        var sdl = Sdl.get();
        var videoDriver = System.getProperty(VIDEO_DRIVER_PROPERTY, "");
        if (!videoDriver.isBlank()) {
            sdl.setHint(Sdl.VIDEO_DRIVER_HINT, videoDriver);
        }
        SdlGpuDevice device = null;
        var reason = "";
        try {
            sdl.initialize(EnumSet.of(SdlSubsystem.VIDEO));
            device = SdlGpuDevice.create(SdlGpuDevice.Options.defaults().withDebugMode(true));
        } catch (SdlException e) {
            reason = e.getMessage();
            sdl.quit();
        }
        return switch (decide(device, reason, Boolean.getBoolean(REQUIRED_PROPERTY))) {
            case Run(var made) -> made;
            case Skip(var why) -> {
                Assumptions.abort(why);
                throw new AssertionError("unreachable");
            }
            case Fail(var why) -> fail(why);
        };
    }
}
