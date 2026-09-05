package io.github.digitalsmile.goldberry.input.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// **Reading a button off an event that has none is reported**, which is the
/// trap `text-input` fell into and the entry that recorded it asked for
/// ([ADR-0266]).
///
/// The failure it catches is specific and it is not a null-pointer: a guard at
/// the top of `onPointer` is a guard on *every* kind, and a null button compares
/// **unequal to everything** — so `button() != PRIMARY` is true for a move and
/// the guard fires backwards, dropping every drag while the press it was written
/// for keeps working.
class ButtonReadTest {

    @BeforeEach
    void forget() {
        PointerEvent.forgetReportedButtonReads();
    }

    private static PointerEvent moved() {
        return new PointerEvent(PointerEvent.Kind.MOVED, 10, 10, null, 0, null);
    }

    private static PointerEvent pressed() {
        return new PointerEvent(PointerEvent.Kind.PRESSED, 10, 10, PointerEvent.Button.PRIMARY, 1, null);
    }

    @Test
    @DisplayName("a button read off a move is reported, and still answers null")
    void aMoveHasNoButton() {
        var event = moved();

        assertNull(event.button(), "null is still the answer — this is a diagnostic, not a refusal");
        assertEquals(1, PointerEvent.reportedButtonReadCount());
    }

    @Test
    @DisplayName("and reported once, however many events arrive")
    void reportedOnce() {
        // A pointer event is read per event per handler, so an unguarded warning
        // would be a few thousand lines a second on a trackpad.
        for (var i = 0; i < 50; i++) {
            moved().button();
        }

        assertEquals(1, PointerEvent.reportedButtonReadCount());
    }

    @Test
    @DisplayName("a button read off a press is not reported, because it is there")
    void aPressHasOne() {
        assertEquals(PointerEvent.Button.PRIMARY, pressed().button());
        assertEquals(0, PointerEvent.reportedButtonReadCount());
    }

    /// The shape of the bug, asserted so the message stays about the right thing:
    /// a null button is **unequal to everything**, so the common guard is true
    /// exactly when it should be false.
    @Test
    @DisplayName("the guard that caused this fires backwards, which is why null alone is not enough")
    void theGuardFiresBackwards() {
        var move = moved();

        assertTrue(move.button() != PointerEvent.Button.PRIMARY, "which is what silently lost every drag");
        assertTrue(move.button() != PointerEvent.Button.SECONDARY, "and it is unequal to the others too");
    }

    /// Not the same as `dragX`'s `NaN`, which the entry contrasts it with: that
    /// one is arithmetic, so the meaninglessness **propagates** and every
    /// comparison against it is false. A caller cannot act on a `NaN` by
    /// accident; it can act on a null by writing `!=`.
    @Test
    @DisplayName("and it is not dragX's NaN, which fails safe in both directions")
    void unlikeNaN() {
        var move = moved();

        assertTrue(Double.isNaN(move.dragX()));
        assertTrue(!(move.dragX() > 0) && !(move.dragX() <= 0), "every comparison against NaN is false");
    }

    @Test
    @DisplayName("two kinds are two reports, and the same kind twice is one")
    void keyedByThePair() {
        moved().button();
        new PointerEvent(PointerEvent.Kind.ENTERED, 1, 1, null, 0, null).button();
        moved().button();

        assertEquals(2, PointerEvent.reportedButtonReadCount());
    }
}
