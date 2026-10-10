package dev.goldberry.example.brand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.input.cursor.CursorImage;
import dev.goldberry.render.Cursor;
import dev.goldberry.render.cursor.CursorPicture;

/// The showcase's hands: both shapes at every size, each with its hot spot on
/// the palm, transparent around the hand.
class ShowcaseCursorsTest {

    @Test
    @DisplayName("an open and a closed hand, each at 32, 48, 64 and 96 pixels")
    void twoShapesFourSizes() {
        var pictures = CursorImage.byShape(ShowcaseCursors.all());

        assertEquals(
                List.of(Cursor.GRAB, Cursor.GRABBING),
                pictures.stream().map(p -> p.shape()).toList());
        for (var picture : pictures) {
            assertEquals(
                    List.of(32, 48, 64, 96),
                    picture.sizes().stream().map(CursorPicture::width).toList());
        }
    }

    @Test
    @DisplayName("each size puts its hot spot on the palm, in its own pixels")
    void hotSpotOnThePalm() {
        for (var size : ShowcaseCursors.SIZES) {
            var grab = ShowcaseCursors.at(Cursor.GRAB, size);
            assertEquals(size / 2, grab.hotX(), "the middle of the palm across");
            assertEquals(
                    0xFF,
                    grab.picture().argb(grab.hotX(), grab.hotY()) >>> 24,
                    "the hot spot is on the hand at " + size);
        }
    }

    @Test
    @DisplayName("the corners are clear, and the two hands differ where the fingers are")
    void clearAroundTheHand() {
        var open = ShowcaseCursors.at(Cursor.GRAB, 64).picture();
        var closed = ShowcaseCursors.at(Cursor.GRABBING, 64).picture();

        assertEquals(0, open.argb(0, 0) >>> 24);
        assertEquals(0, open.argb(63, 63) >>> 24);
        // The middle finger's tip: up on the open hand, curled away on the closed one.
        assertEquals(0xFF, open.argb(28, 16) >>> 24);
        assertEquals(0, closed.argb(28, 16) >>> 24);
        assertNotEquals(open.argb(32, 36), 0, "both palms are drawn");
    }

    @Test
    @DisplayName("a shape the showcase does not draw is refused")
    void onlyTheHands() {
        assertThrows(IllegalArgumentException.class, () -> ShowcaseCursors.at(Cursor.POINTER, 32));
    }
}
