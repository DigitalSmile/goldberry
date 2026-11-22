package dev.goldberry.natives.desktop.calls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.natives.desktop.dbus.Dbus;

/// power-profiles-daemon's answer, read out of the variants a property reply
/// wraps it in.
class PowerProfilesTest {

    @Test
    @DisplayName("the profile is the string inside however many variants there are")
    void unwraps() {
        assertEquals("power-saver", PowerProfiles.profile(List.of(new Dbus.Variant("power-saver"))));
        assertEquals("balanced", PowerProfiles.profile(List.of(new Dbus.Variant(new Dbus.Variant("balanced")))));
    }

    @Test
    @DisplayName("an empty reply, or one that is not a string, is no answer")
    void nothing() {
        assertNull(PowerProfiles.profile(List.of()));
        assertNull(PowerProfiles.profile(List.of(new Dbus.Variant(7))));
    }
}
