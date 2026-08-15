package io.github.digitalsmile.goldberry.natives.sdl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class SdlSubsystemTest {

    /// The literals are SDL's, from the `SDL_INIT_*` defines in
    /// `SDL3/SDL_init.h`. Asserting them here means a mistyped bit shows up as a
    /// failure rather than as a subsystem that silently never initializes.
    @ParameterizedTest
    @CsvSource({
        "AUDIO,    0x00000010",
        "VIDEO,    0x00000020",
        "JOYSTICK, 0x00000200",
        "HAPTIC,   0x00001000",
        "GAMEPAD,  0x00002000",
        "EVENTS,   0x00004000",
        "SENSOR,   0x00008000",
        "CAMERA,   0x00010000",
    })
    @DisplayName("bits match SDL_INIT_*")
    void bitsMatchSdl(SdlSubsystem subsystem, int expected) {
        assertEquals(expected, subsystem.bit());
    }

    @ParameterizedTest
    @EnumSource(SdlSubsystem.class)
    @DisplayName("every subsystem survives a mask round trip")
    void singleRoundTrips(SdlSubsystem subsystem) {
        assertEquals(Set.of(subsystem), SdlSubsystem.decode(SdlSubsystem.mask(Set.of(subsystem))));
    }

    @Test
    @DisplayName("a set of subsystems round-trips as one mask")
    void setRoundTrips() {
        var requested = EnumSet.of(SdlSubsystem.VIDEO, SdlSubsystem.EVENTS, SdlSubsystem.GAMEPAD);

        assertEquals(requested, SdlSubsystem.decode(SdlSubsystem.mask(requested)));
    }

    @Test
    @DisplayName("no subsystems is a zero mask")
    void emptyIsZero() {
        assertEquals(0, SdlSubsystem.mask(Set.of()));
        assertEquals(Set.of(), SdlSubsystem.decode(0));
    }

    @Test
    @DisplayName("the mask is the bitwise or, not a sum")
    void maskIsBitwise() {
        // VIDEO|EVENTS happens to be a sum too; overlapping flags would not be.
        // Asserting the literal keeps the implementation honest either way.
        assertEquals(
                0x00000020 | 0x00004000,
                SdlSubsystem.mask(EnumSet.of(SdlSubsystem.VIDEO, SdlSubsystem.EVENTS)));
    }

    @Test
    @DisplayName("a bit this enum does not know is ignored, not rejected")
    void unknownBitsIgnored() {
        // SDL_WasInit reports what SDL initialized. A future SDL may report a
        // subsystem this enum predates, and a dependency bump must not become a
        // crash. Contrast MeasureMode.of(), where an unknown value means the
        // binding is wrong.
        var flags = SdlSubsystem.EVENTS.bit() | 0x40000000;

        assertEquals(Set.of(SdlSubsystem.EVENTS), SdlSubsystem.decode(flags));
    }

    @Test
    @DisplayName("all bits set decodes to every known subsystem")
    void allBitsDecodeToEverything() {
        var all = SdlSubsystem.decode(-1);

        assertEquals(EnumSet.allOf(SdlSubsystem.class), all);
    }

    @Test
    @DisplayName("no two subsystems share a bit")
    void bitsAreDistinct() {
        var seen = 0;
        for (var subsystem : SdlSubsystem.values()) {
            assertTrue((seen & subsystem.bit()) == 0, () -> subsystem + " overlaps another subsystem");
            seen |= subsystem.bit();
        }
    }
}
