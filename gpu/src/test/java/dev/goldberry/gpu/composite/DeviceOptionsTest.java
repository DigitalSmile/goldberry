package dev.goldberry.gpu.composite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.gpu.GpuPower;
import dev.goldberry.natives.sdl.gpu.SdlGpuDevice;

/// How the compositor asks for its device: D2's defaults, and the three
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

    @Test
    @DisplayName("prefer the discrete GPU for high, and the integrated one for low or anything else")
    void power() {
        assertFalse(DeviceOptions.of(null, null, "high").preferLowPower());
        assertFalse(DeviceOptions.of(null, null, " HIGH ").preferLowPower(), "in any case");
        assertTrue(DeviceOptions.of(null, null, "low").preferLowPower());
        assertTrue(DeviceOptions.of(null, null, "fast").preferLowPower(), "a typo is the default");
        assertTrue(DeviceOptions.of(null, null, null).preferLowPower());
        var both = DeviceOptions.of("vulkan", "true", "high");
        assertEquals(Optional.of("vulkan"), both.driver(), "power keeps the driver");
        assertTrue(both.debugMode(), "and the validation");
    }

    @Test
    @DisplayName("let a caller's preference win over the property")
    void override() {
        var previous = System.getProperty(DeviceOptions.POWER_PROPERTY);
        System.setProperty(DeviceOptions.POWER_PROPERTY, "low");
        try {
            assertFalse(DeviceOptions.fromProperties(GpuPower.HIGH).preferLowPower());
            assertTrue(DeviceOptions.fromProperties(null).preferLowPower(), "null follows the property");
            System.setProperty(DeviceOptions.POWER_PROPERTY, "high");
            assertFalse(DeviceOptions.fromProperties(null).preferLowPower());
            assertTrue(DeviceOptions.fromProperties(GpuPower.LOW).preferLowPower());
        } finally {
            if (previous == null) {
                System.clearProperty(DeviceOptions.POWER_PROPERTY);
            } else {
                System.setProperty(DeviceOptions.POWER_PROPERTY, previous);
            }
        }
    }
}
