package dev.goldberry.natives.sdl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Skip where nobody promised a tray, fail where somebody did.
@DisplayName("a test that needs a tray")
class TrayRequirementTest {

    @Test
    @DisplayName("runs when a tray opened, whatever was required")
    void runs() {
        assertInstanceOf(TrayRequirement.Run.class, TrayRequirement.decide(true, false, null));
        assertInstanceOf(TrayRequirement.Run.class, TrayRequirement.decide(true, true, null));
    }

    @Test
    @DisplayName("skips with SDL's reason when none opened and none was promised")
    void skips() {
        var decision = TrayRequirement.decide(false, false, "Could not load AppIndicator libraries");

        assertEquals(new TrayRequirement.Skip("Could not load AppIndicator libraries"), decision);
    }

    @Test
    @DisplayName("fails when one was promised, and says what would ship unexercised")
    void fails() {
        var decision = TrayRequirement.decide(false, true, null);

        var fail = assertInstanceOf(TrayRequirement.Fail.class, decision);
        assertTrue(fail.reason().contains("no system tray"), fail.reason());
        assertTrue(fail.reason().contains("unexercised"), fail.reason());
    }
}
