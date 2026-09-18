package io.github.digitalsmile.goldberry.natives.sdl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/// `SDL_INIT_*`, as a mask and back again.
///
/// Unlike every other SDL enum the bindings model, `SdlSubsystem` is **not** in
/// `NativeConstants.registry()` — it has no `nativeName()`, so the layout probe
/// never compares its bits with the compiled SDL. The literals below are
/// therefore the only thing holding them, and they stay.
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

    /// Every word the decoder has to get right, and the subsystems it names.
    private static Stream<Arguments> words() {
        return Stream.concat(
                Stream.of(
                        arguments("no bits at all", 0, Set.of()),
                        // SDL_WasInit reports what SDL initialized. A future SDL may
                        // report a subsystem this enum predates, and a dependency bump
                        // must not become a crash. Contrast MeasureMode.of(), where an
                        // unknown value means the binding is wrong.
                        arguments(
                                "a bit this enum predates, beside one it knows",
                                SdlSubsystem.EVENTS.bit() | 0x4000_0000,
                                Set.of(SdlSubsystem.EVENTS)),
                        arguments("every bit set", -1, EnumSet.allOf(SdlSubsystem.class))),
                // One row per subsystem, so a bit shared between two shows up as the
                // row that decoded to a pair.
                Arrays.stream(SdlSubsystem.values())
                        .map(subsystem -> arguments(subsystem + " alone", subsystem.bit(), Set.of(subsystem))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("words")
    @DisplayName("a word decodes to exactly the subsystems its bits name")
    void decodes(String label, int word, Set<SdlSubsystem> expected) {
        assertEquals(expected, SdlSubsystem.decode(word), label);
    }

    /// Every set the encoder has to get right, and the word it becomes.
    private static Stream<Arguments> requests() {
        return Stream.of(
                arguments("nothing requested", Set.of(), 0),
                // VIDEO|EVENTS happens to be a sum too; overlapping flags would not
                // be. Asserting the literal keeps the implementation honest either way.
                arguments("video and events", EnumSet.of(SdlSubsystem.VIDEO, SdlSubsystem.EVENTS), 0x20 | 0x4000),
                arguments(
                        "video, events and gamepad",
                        EnumSet.of(SdlSubsystem.VIDEO, SdlSubsystem.EVENTS, SdlSubsystem.GAMEPAD),
                        0x20 | 0x4000 | 0x2000));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("requests")
    @DisplayName("a set of subsystems masks to the bitwise or of their bits, and comes back")
    void masks(String label, Set<SdlSubsystem> requested, int expected) {
        assertEquals(expected, SdlSubsystem.mask(requested), label);
        assertEquals(requested, SdlSubsystem.decode(SdlSubsystem.mask(requested)), label);
    }
}
