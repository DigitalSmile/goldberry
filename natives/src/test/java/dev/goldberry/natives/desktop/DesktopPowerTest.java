package dev.goldberry.natives.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.natives.sdl.desktop.SdlPowerState;

/// The desktop's power-saving switch, read rather than set.
class DesktopPowerTest {

    @AfterEach
    void clear() {
        System.clearProperty(DesktopPower.PROPERTY);
    }

    @Test
    @DisplayName("the property overrides the desktop, and a word it does not know means ask")
    void override() {
        System.setProperty(DesktopPower.PROPERTY, "on");
        assertEquals(Optional.of(true), DesktopPower.savingMode());
        System.setProperty(DesktopPower.PROPERTY, "OFF");
        assertEquals(Optional.of(false), DesktopPower.savingMode());
        System.setProperty(DesktopPower.PROPERTY, "maybe");
        assertEquals(Optional.empty(), DesktopPower.savingMode());
    }

    @Test
    @DisplayName("asking this machine answers something, and never throws")
    void asksThisMachine() {
        assertNotNull(DesktopPower.savingMode());
    }

    @Test
    @DisplayName("SDL's power states read back from their values, and an unknown one is unknown")
    void sdlStates() {
        for (var state : SdlPowerState.values()) {
            assertEquals(state, SdlPowerState.of(state.value()));
        }
        assertEquals(SdlPowerState.UNKNOWN, SdlPowerState.of(42));
    }
}
