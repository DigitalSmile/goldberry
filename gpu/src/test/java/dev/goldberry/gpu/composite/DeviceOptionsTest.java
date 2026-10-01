package dev.goldberry.gpu.composite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.natives.sdl.gpu.SdlGpuDevice;

/// How the compositor asks for its device: D2's defaults, and the two
/// properties that change them.
@DisplayName("the compositor's device options")
class DeviceOptionsTest {

    @Test
    @DisplayName("are the toolkit's shader formats, the low-power GPU and SDL's driver, unvalidated, by default")
    void defaults() {
        var options = DeviceOptions.of(null, null);
        assertEquals(SdlGpuDevice.Options.defaults().shaderFormats(), options.shaderFormats());
        assertTrue(options.preferLowPower());
        assertFalse(options.debugMode());
        assertEquals(Optional.empty(), options.driver());
        assertEquals(Optional.empty(), DeviceOptions.of("  ", null).driver(), "a blank driver is SDL's choice");
    }

    @Test
    @DisplayName("name a driver and turn validation on when the properties say so")
    void properties() {
        var options = DeviceOptions.of(" vulkan ", "true");
        assertEquals(Optional.of("vulkan"), options.driver());
        assertTrue(options.debugMode());
        assertFalse(DeviceOptions.of(null, "yes").debugMode(), "only true is true");
    }
}
