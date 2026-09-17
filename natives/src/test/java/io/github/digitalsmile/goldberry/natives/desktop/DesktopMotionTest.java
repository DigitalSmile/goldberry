package io.github.digitalsmile.goldberry.natives.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.natives.desktop.calls.PortalSettings;

/// Asking the desktop whether to move less — [ADR-0383].
///
/// What can be asserted anywhere is the shape: the question is answered, the
/// answer is one of three, the override decides it, and nothing here throws on a
/// machine with no desktop at all. What the *answer* is on this machine is not
/// an assertion, because a test that demanded a particular setting would fail on
/// the developer who turned it on.
class DesktopMotionTest {

    @AfterEach
    void clearOverride() {
        System.clearProperty(DesktopMotion.PROPERTY);
    }

    @Test
    @DisplayName("the desktop is asked, and answers one of the three")
    void answers() {
        var preference = DesktopMotion.preference();

        assertNotNull(preference);
        assertTrue(preference == MotionPreference.UNKNOWN
                || preference == MotionPreference.FULL
                || preference == MotionPreference.REDUCED);
    }

    @Test
    @DisplayName("asking twice costs nothing and says the same thing")
    void cached() {
        assertEquals(DesktopMotion.preference(), DesktopMotion.preference());
    }

    @Test
    @DisplayName("the property decides it, for a test and for a screenshot")
    void override() {
        System.setProperty(DesktopMotion.PROPERTY, "reduce");
        assertEquals(MotionPreference.REDUCED, DesktopMotion.ask());

        System.setProperty(DesktopMotion.PROPERTY, "full");
        assertEquals(MotionPreference.FULL, DesktopMotion.ask());

        // Anything else is "do not ask", which is what a value nobody recognises
        // has to mean: the alternative is guessing which of the two a typo was.
        System.setProperty(DesktopMotion.PROPERTY, "yes please");
        assertEquals(MotionPreference.UNKNOWN, DesktopMotion.ask());
    }

    @Test
    @DisplayName("`unknown` is not an instruction")
    void unknownIsNotReduced() {
        assertFalse(MotionPreference.UNKNOWN.isReduced(), "a default is not a preference");
        assertFalse(MotionPreference.FULL.isReduced());
        assertTrue(MotionPreference.REDUCED.isReduced());
    }

    @Test
    @DisplayName("asking a desktop that is not there answers rather than throwing")
    void noDesktop() {
        // Every failure path — no library, no bus, no portal, no key, a type
        // nobody expected — lands on the same answer, which is what makes this
        // safe to call on the way to the first frame. A namespace no portal
        // serves is the one of those a test can produce anywhere.
        assertEquals(MotionPreference.UNKNOWN, PortalSettings.read("org.example.nothing", "no-such-key", true));
    }
}
