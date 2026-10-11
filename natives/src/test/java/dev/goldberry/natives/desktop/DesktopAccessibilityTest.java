package dev.goldberry.natives.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The desktop's display switches: asked without throwing on any machine, and
/// overridden by their properties.
class DesktopAccessibilityTest {

    @AfterEach
    void clear() {
        System.clearProperty(DesktopAccessibility.CONTRAST);
        System.clearProperty(DesktopAccessibility.FORCED_COLORS);
        System.clearProperty(DesktopAccessibility.TRANSPARENCY);
    }

    @Test
    @DisplayName("answers on this machine, whatever it has")
    void asks() {
        var settings = DesktopAccessibility.ask();
        assertNotNull(settings.increasedContrast());
        assertNotNull(settings.forcedColors());
        assertNotNull(settings.reducedTransparency());
    }

    @Test
    @DisplayName("each property overrides what the desktop says")
    void overrides() {
        System.setProperty(DesktopAccessibility.CONTRAST, "true");
        System.setProperty(DesktopAccessibility.FORCED_COLORS, "false");
        System.setProperty(DesktopAccessibility.TRANSPARENCY, "on");
        assertEquals(new DisplayAccessibility(Setting.ON, Setting.OFF, Setting.ON), DesktopAccessibility.ask());
    }

    @Test
    @DisplayName("only on is on")
    void isOn() {
        assertEquals(true, Setting.ON.isOn());
        assertEquals(false, Setting.UNKNOWN.isOn());
        assertEquals(false, Setting.OFF.isOn());
    }
}
