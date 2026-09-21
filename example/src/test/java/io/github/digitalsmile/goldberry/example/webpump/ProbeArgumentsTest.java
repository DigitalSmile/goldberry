package io.github.digitalsmile.goldberry.example.webpump;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// That the probe reads what the page actually sends, and refuses everything
/// else.
///
/// Worth testing precisely because it is not a JSON parser: a reader that
/// handles one shape has to be exact about which shape, or a malformed reading
/// becomes a plausible-looking number and the measurement is quietly wrong.
class ProbeArgumentsTest {

    @Test
    @DisplayName("the four numbers arrive in the order the document sends them")
    void readsTheDocumentsShape() {
        var parsed = ProbeArguments.parse("[1.5,63,125,1]").orElseThrow();
        assertEquals(1.5, parsed.rafFps());
        assertEquals(63, parsed.timelineFps());
        assertEquals(125, parsed.timerFps());
        assertTrue(parsed.visible());
    }

    @Test
    @DisplayName("zero is the only falsehood, because JavaScript sends a number")
    void zeroIsHidden() {
        assertFalse(ProbeArguments.parse("[1,63,125,0]").orElseThrow().visible());
        assertTrue(ProbeArguments.parse("[1,63,125,1]").orElseThrow().visible());
    }

    @Test
    @DisplayName("whitespace around the numbers is not a different shape")
    void toleratesSpacing() {
        var parsed = ProbeArguments.parse("  [ 1 , 2 , 3 , 1 ]  ").orElseThrow();
        assertEquals(1, parsed.rafFps());
        assertEquals(3, parsed.timerFps());
    }

    @Test
    @DisplayName("an engine that sent the wrong number of values is refused, not guessed at")
    void arityIsExact() {
        assertTrue(ProbeArguments.parse("[1,2,3]").isEmpty());
        assertTrue(ProbeArguments.parse("[1,2,3,4,5]").isEmpty());
        // The `-1` limit in the split is what makes this one fail: without it
        // the trailing empty field is dropped and this reads as three values.
        assertTrue(ProbeArguments.parse("[1,2,3,]").isEmpty());
    }

    @Test
    @DisplayName("anything that is not an array of numbers is refused")
    void refusesEverythingElse() {
        assertTrue(ProbeArguments.parse(null).isEmpty());
        assertTrue(ProbeArguments.parse("").isEmpty());
        assertTrue(ProbeArguments.parse("[]").isEmpty());
        assertTrue(ProbeArguments.parse("1,2,3,4").isEmpty());
        assertTrue(ProbeArguments.parse("{\"raf\":1}").isEmpty());
        assertTrue(ProbeArguments.parse("[1,2,3,\"yes\"]").isEmpty());
    }
}
