package io.github.digitalsmile.goldberry.render.backend.sdl3;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The one arithmetic in the preedit path: SDL reports the clause inside a
/// composition in **UTF-8 bytes**, and everything above the backend counts in
/// Java chars (ADR-0289).
///
/// It is done once, here, because a composition is by definition not ASCII — a
/// Latin keyboard never produces one — so a layer that passed the bytes through
/// would be wrong for every user this feature exists for.
class PreeditOffsetTest {

    @Test
    @DisplayName("ASCII is one byte a char, which is the case that hides the bug")
    void asciiIsUnchanged() {
        assertEquals(0, Sdl3Backend.charOffset("abc", 0));
        assertEquals(2, Sdl3Backend.charOffset("abc", 2));
        assertEquals(3, Sdl3Backend.charOffset("abc", 3));
    }

    @Test
    @DisplayName("Japanese is three bytes a char")
    void threeByteCharacters() {
        // にほんご: four characters, twelve bytes.
        assertEquals(0, Sdl3Backend.charOffset("にほんご", 0));
        assertEquals(1, Sdl3Backend.charOffset("にほんご", 3));
        assertEquals(2, Sdl3Backend.charOffset("にほんご", 6));
        assertEquals(4, Sdl3Backend.charOffset("にほんご", 12));
    }

    @Test
    @DisplayName("an emoji is four bytes and two chars, which is the case that breaks a naive division")
    void surrogatePairs() {
        // U+1F600, one code point, two chars, four bytes.
        assertEquals(0, Sdl3Backend.charOffset("😀a", 0));
        assertEquals(2, Sdl3Backend.charOffset("😀a", 4));
        assertEquals(3, Sdl3Backend.charOffset("😀a", 5));
    }

    @Test
    @DisplayName("past the end is the end, rather than an exception in an event handler")
    void clampsPastTheEnd() {
        assertEquals(4, Sdl3Backend.charOffset("にほんご", 999));
        assertEquals(0, Sdl3Backend.charOffset("", 999));
    }

    @Test
    @DisplayName("-1 stays -1, because it is the platform saying it does not know")
    void unknownStaysUnknown() {
        assertEquals(-1, Sdl3Backend.charOffset("にほんご", -1));
        assertEquals(-1, Sdl3Backend.charSpan("にほんご", -1, 3));
        assertEquals(-1, Sdl3Backend.charSpan("にほんご", 3, -1));
    }

    @Test
    @DisplayName("a span is a difference of two offsets, not a length converted on its own")
    void spanDependsOnWhereItStarts() {
        // Six bytes is two characters here...
        assertEquals(2, Sdl3Backend.charSpan("にほんご", 0, 6));
        // ...and starting inside an ASCII run, six bytes is six characters.
        assertEquals(6, Sdl3Backend.charSpan("abcdefgh", 0, 6));
        // Mixed: two bytes of "ab" then one Japanese character.
        assertEquals(1, Sdl3Backend.charSpan("abに", 2, 3));
    }
}
