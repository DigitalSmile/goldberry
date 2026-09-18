package io.github.digitalsmile.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.digitalsmile.goldberry.render.model.LogicalSize;

/// The two flags the launcher reads for itself, and what it says when one of
/// them is not a number.
///
/// An argument error is read by whoever typed the command line, so the refusal
/// has to name the flag: `For input string: "ten"` does not say which of two
/// flags held it.
class LauncherOptionsTest {

    @Test
    @DisplayName("frames and size are read, and everything else is left alone")
    void reads() {
        var options = Launcher.Options.of(new String[] {"--frames=3", "--verbose", "--size=800x600"});

        assertEquals(3, options.frames());
        assertEquals(new LogicalSize(800, 600), options.size());
    }

    @Test
    @DisplayName("no arguments at all is no frames, no size, no walk and no budget")
    void nothing() {
        assertEquals(0, Launcher.Options.of(null).frames());
        assertNull(Launcher.Options.of(new String[0]).size());
        assertNull(Launcher.Options.of(new String[0]).resize());
        assertEquals(-1, Launcher.Options.of(new String[0]).lateBudget());
    }

    @Test
    @DisplayName("a walk and a budget are read like the other two")
    void walkAndBudget() {
        var options = Launcher.Options.of(new String[] {"--resize=1580x1100", "--late-budget=12"});

        assertEquals(new LogicalSize(1580, 1100), options.resize());
        assertEquals(12, options.lateBudget());
    }

    @Test
    @DisplayName("a walk with one number is no walk, and a budget that is not a number names its flag")
    void walkAndBudgetRefusals() {
        assertNull(Launcher.Options.of(new String[] {"--resize=1580x"}).resize());

        var refused = assertThrows(
                IllegalArgumentException.class, () -> Launcher.Options.of(new String[] {"--late-budget=few"}));
        assertTrue(refused.getMessage().contains("--late-budget=few"), refused.getMessage());
        assertTrue(refused.getMessage().contains("late frames"), refused.getMessage());
    }

    @Test
    @DisplayName("a size with one number is not a size")
    void halfASize() {
        assertNull(Launcher.Options.of(new String[] {"--size=800x"}).size());
    }

    /// The flag as it was typed, which is what a reader has to go and correct —
    /// not the sentence around it, and not `NumberFormatException`'s own message,
    /// which names the fragment and not the flag it came from.
    @ParameterizedTest
    @ValueSource(strings = {"--frames=ten", "--size=widexhigh"})
    @DisplayName("a flag whose value is not a number is refused by the flag's own name")
    void refusedByName(String flag) {
        var refused = assertThrows(IllegalArgumentException.class, () -> Launcher.Options.of(new String[] {flag}));

        assertTrue(refused.getMessage().contains(flag), refused.getMessage());
        assertInstanceOf(NumberFormatException.class, refused.getCause());
    }
}
