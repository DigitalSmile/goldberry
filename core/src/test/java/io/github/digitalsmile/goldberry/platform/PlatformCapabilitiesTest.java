package io.github.digitalsmile.goldberry.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

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

    /// The capabilities that are bits in `libgoldberry`, which since ADR-0441 is
    /// no longer all of them.
    ///
    /// [Capability#WEB_VIEW] is the presence of a **second** library,
    /// `libgoldberry-webview`, which exists so that GTK and WebKit are not
    /// load-time dependencies of the toolkit. So it has no `NativeCapability`
    /// twin and must not grow one: there is no flag compiled into `libgoldberry`
    /// that could answer it.
    private static final Set<Capability> NOT_A_NATIVE_BIT = EnumSet.of(Capability.WEB_VIEW);

    @Test
    @DisplayName("every capability is a native bit, or is one of the few that deliberately is not")
    void theTwoEnumsAgree() {
        // A `Capability` nothing can report is a promise to an application that
        // nothing keeps: `Goldberry.capabilities()` would simply never contain it,
        // which reads exactly like a build that lacks it. So every constant is
        // either translated from the native enum or named above as something
        // answered another way -- and a constant added to neither fails here.
        var fromNatives =
                List.of(NativeCapability.values()).stream().map(Enum::name).toList();
        var answeredOtherwise = NOT_A_NATIVE_BIT.stream().map(Enum::name).toList();
        var expected =
                Stream.concat(fromNatives.stream(), answeredOtherwise.stream()).toList();

        assertEquals(
                expected, List.of(Capability.values()).stream().map(Enum::name).toList());
    }

    @Test
    @DisplayName("a set translates whole, in declaration order")
    void translatesASet() {
        var all = EnumSet.allOf(NativeCapability.class);

        var translated = PlatformCapabilities.translate(all);

        // Everything but the ones that are not native bits, which `translate` has
        // no way to be told about and no business inventing.
        var expected = List.of(Capability.values()).stream()
                .filter(c -> !NOT_A_NATIVE_BIT.contains(c))
                .toList();
        assertEquals(expected, List.copyOf(translated));
    }

    @Test
    @DisplayName("a web view is reported from the second library, not from libgoldberry's bits")
    void theWebViewIsNotANativeBit() {
        // The two halves are independent, which is the whole of ADR-0441's build
        // argument: a process may have `libgoldberry-webview` and no
        // `libgoldberry`, or the other way round, and each answer stands alone.
        var neither = PlatformCapabilities.read(EnumSet.noneOf(NativeCapability.class), false);
        var webViewOnly = PlatformCapabilities.read(EnumSet.noneOf(NativeCapability.class), true);
        var themeOnly = PlatformCapabilities.read(EnumSet.of(NativeCapability.SYSTEM_THEME), false);
        var both = PlatformCapabilities.read(EnumSet.of(NativeCapability.SYSTEM_THEME), true);

        assertEquals(Set.of(), neither);
        assertEquals(Set.of(Capability.WEB_VIEW), webViewOnly);
        assertEquals(Set.of(Capability.SYSTEM_THEME), themeOnly);
        assertEquals(Set.of(Capability.SYSTEM_THEME, Capability.WEB_VIEW), both);
    }

    @Test
    @DisplayName("a build with no libgoldberry can still report a web view")
    void theWebViewSurvivesAMissingLibrary() {
        // The regression this guards: `read()` used to answer `Set.of()` for a
        // LinkageError, which would have thrown away an answer the other library
        // could give. That is the "asked and was told nothing" mistake this class
        // exists to avoid, aimed at itself.
        assertEquals(
                Set.of(Capability.WEB_VIEW), PlatformCapabilities.read(EnumSet.noneOf(NativeCapability.class), true));
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
