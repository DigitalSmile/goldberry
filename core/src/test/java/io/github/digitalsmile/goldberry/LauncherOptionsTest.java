package io.github.digitalsmile.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

    @Test
    @DisplayName("a frame count that is not a number is refused by the flag's name")
    void framesRefusedByName() {
        var refused =
                assertThrows(IllegalArgumentException.class, () -> Launcher.Options.of(new String[] {"--frames=ten"}));

        assertTrue(refused.getMessage().contains("--frames=ten"), refused.getMessage());
        assertInstanceOf(NumberFormatException.class, refused.getCause());
    }

    @Test
    @DisplayName("a size that is not two numbers is refused by the flag's name")
    void sizeRefusedByName() {
        var refused = assertThrows(
                IllegalArgumentException.class, () -> Launcher.Options.of(new String[] {"--size=widexhigh"}));

        assertTrue(refused.getMessage().contains("--size=widexhigh"), refused.getMessage());
        assertInstanceOf(NumberFormatException.class, refused.getCause());
    }
}
