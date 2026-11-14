package dev.goldberry.motion.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.value.ViewTransition;
import dev.goldberry.motion.Easing;

/// Where a named box is drawn, pass by pass, while layout moves it: the
/// bookkeeping alone, on numbers, with a linear curve so a halfway point is a
/// number to compare.
@DisplayName("layout tracks")
class LayoutTracksTest {

    private static final ViewTransition TILE = new ViewTransition("tile", 100, Easing.LINEAR);
    private static final Place TOP = new Place(0, 0, 50, 50);
    private static final Place BELOW = new Place(0, 100, 50, 50);

    private final LayoutTracks tracks = new LayoutTracks();

    private Place pass(ViewTransition timing, Place laidOut, double now) {
        tracks.begin();
        var drawn = tracks.place(timing, laidOut, now);
        tracks.end();
        return drawn;
    }

    @Test
    @DisplayName("draw a box where it is laid out the first time they see it")
    void firstSighting() {
        assertEquals(TOP, pass(TILE, TOP, 0));
        assertFalse(tracks.isMoving());
    }

    @Test
    @DisplayName("draw a box that layout moved where it was, then carry it to where it is")
    void moves() {
        pass(TILE, TOP, 0);

        assertEquals(TOP, pass(TILE, BELOW, 1000), "drawn where it was");
        assertTrue(tracks.isMoving());
        assertEquals(new Place(0, 50, 50, 50), pass(TILE, BELOW, 1050), "halfway");
        assertEquals(BELOW, pass(TILE, BELOW, 1100), "arrived");
        assertFalse(tracks.isMoving(), "and done");
    }

    @Test
    @DisplayName("follow the curve, not the clock")
    void easing() {
        var eased = new ViewTransition("tile", 100, Easing.EASE_ENTER);
        pass(eased, TOP, 0);
        pass(eased, BELOW, 1000);

        var halfway = pass(eased, BELOW, 1050);

        assertEquals(100 * Easing.EASE_ENTER.at(0.5), halfway.y(), 1e-9);
    }

    @Test
    @DisplayName("set off again from where the box is drawn when layout moves it mid-move")
    void interrupted() {
        pass(TILE, TOP, 0);
        pass(TILE, BELOW, 1000);
        pass(TILE, BELOW, 1050);

        assertEquals(new Place(0, 50, 50, 50), pass(TILE, TOP, 1050), "no jump");
        assertEquals(new Place(0, 25, 50, 50), pass(TILE, TOP, 1100), "halfway back from where it turned");
    }

    @Test
    @DisplayName("stretch a box that changed size, as well as move it")
    void stretches() {
        var wide = new Place(0, 0, 150, 50);
        pass(TILE, TOP, 0);
        pass(TILE, wide, 1000);

        assertEquals(new Place(0, 0, 100, 50), pass(TILE, wide, 1050));
    }

    @Test
    @DisplayName("move at once with an instant timing, or with no clock")
    void instant() {
        var snaps = new ViewTransition("tile", 0, Easing.LINEAR);
        pass(snaps, TOP, 0);
        assertEquals(BELOW, pass(snaps, BELOW, 1000));
        assertFalse(tracks.isMoving());

        pass(TILE, TOP, 2000);
        assertEquals(BELOW, pass(TILE, BELOW, LayoutTracks.NO_CLOCK));
        assertFalse(tracks.isMoving());
    }

    @Test
    @DisplayName("ignore a change smaller than a quarter pixel, which is rounding")
    void rounding() {
        pass(TILE, TOP, 0);

        assertEquals(new Place(0.1, 0, 50, 50), pass(TILE, new Place(0.1, 0, 50, 50), 1000));
        assertFalse(tracks.isMoving());
    }

    @Test
    @DisplayName("forget a name a pass did not see, so a box that comes back arrives in place")
    void forget() {
        pass(TILE, TOP, 0);
        tracks.begin();
        tracks.end();
        assertEquals(0, tracks.size());

        assertEquals(BELOW, pass(TILE, BELOW, 1000));
    }

    @Test
    @DisplayName("give a name used twice in one pass to the first box, and leave the second in place")
    void duplicate() {
        pass(TILE, TOP, 0);
        tracks.begin();
        var first = tracks.place(TILE, BELOW, 1000);
        var second = tracks.place(TILE, new Place(200, 0, 50, 50), 1000);
        tracks.end();

        assertEquals(TOP, first, "the first moves");
        assertEquals(new Place(200, 0, 50, 50), second, "the second is drawn where it is");
    }
}
