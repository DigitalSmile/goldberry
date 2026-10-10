package dev.goldberry.input.hit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.render.Cursor;

/// `HitTest.cursorOf`: what one box says about the pointer, rather than what
/// the stack of boxes under a point adds up to.
class CursorOfTest {

    @Test
    @DisplayName("an owner's topmost box answers, its default included")
    void topmostBoxOfTheOwner() {
        var regions = List.of(
                new HitTest.Region("card", Cursor.GRAB, 0, 0, 50, 50),
                new HitTest.Region("label", Cursor.POINTER, 0, 0, 20, 20),
                new HitTest.Region("card", Cursor.DEFAULT, 10, 10, 10, 10));

        assertEquals(Cursor.DEFAULT, HitTest.cursorOf(regions, "card"), "the later box is the one on top");
        assertEquals(Cursor.POINTER, HitTest.cursorOf(regions, "label"));
    }

    @Test
    @DisplayName("an owner with no box painted has nothing to say")
    void notPainted() {
        var regions = List.of(new HitTest.Region("card", Cursor.GRAB, 0, 0, 50, 50));

        assertNull(HitTest.cursorOf(regions, "gone"));
    }
}
