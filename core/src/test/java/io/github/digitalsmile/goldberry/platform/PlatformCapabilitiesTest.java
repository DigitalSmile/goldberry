package io.github.digitalsmile.goldberry.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.Goldberry;
import io.github.digitalsmile.goldberry.natives.platform.NativeCapability;

/// What this build can do, in the toolkit's own words — `docs/gaps.md` G32,
/// ADR-0325.
///
/// The interesting half is the translation. `Capability` and `NativeCapability`
/// are two enums on purpose — `natives.*` does not leave `:natives` (ADR-0174) —
/// and two enums that must agree are exactly the pair that quietly stops
/// agreeing. The `switch` in [PlatformCapabilities] makes the compiler notice a
/// constant added to the native side; what it cannot notice is one added to the
/// public side and never reported, which is what the count below is for.
@DisplayName("the platform capabilities")
class PlatformCapabilitiesTest {

    @Test
    @DisplayName("neither enum has a constant the other does not")
    void theTwoEnumsAgree() {
        // A `Capability` nothing can report is a promise to an application that
        // nothing keeps: `Goldberry.capabilities()` would simply never contain it,
        // which reads exactly like a build that lacks it.
        assertEquals(
                List.of(NativeCapability.values()).stream().map(Enum::name).toList(),
                List.of(Capability.values()).stream().map(Enum::name).toList());
    }

    @Test
    @DisplayName("a set translates whole, in declaration order")
    void translatesASet() {
        var all = EnumSet.allOf(NativeCapability.class);

        var translated = PlatformCapabilities.translate(all);

        assertEquals(List.of(Capability.values()), List.copyOf(translated));
    }

    @Test
    @DisplayName("a build with nothing translates to nothing, not to a failure")
    void translatesTheEmptySet() {
        // What a Linux library built without libdbus-1-dev reports. It has to be an
        // ordinary answer: such a library runs, paints and takes input, and the
        // whole point is that it can say so.
        assertEquals(Set.of(), PlatformCapabilities.translate(EnumSet.noneOf(NativeCapability.class)));
    }

    @Test
    @DisplayName("what it reports cannot be modified by whoever asked")
    void theAnswerIsUnmodifiable() {
        var translated = PlatformCapabilities.translate(EnumSet.of(NativeCapability.SYSTEM_THEME));

        assertThrows(UnsupportedOperationException.class, () -> translated.add(Capability.FILE_DIALOG));
    }

    @Test
    @DisplayName("Goldberry.capabilities() answers without a window, a backend or a library")
    void theFrontDoorAnswers() {
        // Deliberately asserts nothing about the CONTENT. This suite runs both with
        // a compiled libgoldberry and without one, and on Linux with or without the
        // D-Bus headers; all of those are legitimate and all of them must answer.
        var capabilities = Goldberry.capabilities();

        assertNotNull(capabilities);
        assertSame(capabilities, PlatformCapabilities.get());
        assertEquals(capabilities, Goldberry.capabilities());
        assertThrows(UnsupportedOperationException.class, () -> capabilities.add(Capability.SYSTEM_THEME));
    }

    @Test
    @DisplayName("has() agrees with the set it is a shorthand for")
    void hasAgreesWithTheSet() {
        for (var capability : Capability.values()) {
            assertEquals(
                    Goldberry.capabilities().contains(capability),
                    PlatformCapabilities.has(capability),
                    capability + " is reported differently by the two ways of asking");
        }
    }
}
