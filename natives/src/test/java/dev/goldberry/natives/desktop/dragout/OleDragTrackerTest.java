package dev.goldberry.natives.desktop.dragout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.natives.desktop.dragout.ole.OleStructs;

/// [OleDragTracker]: what `DoDragDrop`'s questions become, with the pointer
/// and the loop played by the test.
@DisplayName("a Windows drag's tracking")
class OleDragTrackerTest {

    private static final int LEFT_HELD = 0x1;
    private static final long OWN = 0x5000;

    /// Every report, in words.
    private static final class Heard implements DragSourceListener {

        final List<String> reports = new ArrayList<>();

        @Override
        public void over(long window, int x, int y) {
            reports.add("over " + Long.toHexString(window) + " " + x + "," + y);
        }

        @Override
        public void outside() {
            reports.add("outside");
        }

        @Override
        public void delivered(@Nullable DragAction action) {
            reports.add("delivered " + action);
        }

        @Override
        public void releasedOver(long window, int x, int y) {
            reports.add("released over " + Long.toHexString(window) + " " + x + "," + y);
        }

        @Override
        public void cancelled() {
            reports.add("cancelled");
        }
    }

    private final Heard heard = new Heard();
    private OleDragTracker.@Nullable Over where = new OleDragTracker.Over(OWN, 10, 20);
    private int turns;

    private final OleDragTracker tracker = new OleDragTracker(() -> where, heard, () -> turns++);

    @Test
    @DisplayName("reports the pointer over the application's windows when it moves, and outside once")
    void follows() {
        tracker.giveFeedback(OleStructs.DROPEFFECT_COPY);
        tracker.queryContinueDrag(false, LEFT_HELD);
        where = new OleDragTracker.Over(OWN, 11, 20);
        tracker.giveFeedback(OleStructs.DROPEFFECT_COPY);
        where = null;
        tracker.giveFeedback(0);
        tracker.queryContinueDrag(false, LEFT_HELD);

        assertEquals(List.of("over 5000 10,20", "over 5000 11,20", "outside"), heard.reports);
    }

    @Test
    @DisplayName("runs a turn of the loop on each question while the button is held")
    void turns() {
        tracker.giveFeedback(OleStructs.DROPEFFECT_COPY);
        assertEquals(OleStructs.DRAGDROP_S_USEDEFAULTCURSORS, tracker.giveFeedback(OleStructs.DROPEFFECT_COPY));
        assertEquals(0, tracker.queryContinueDrag(false, LEFT_HELD));

        assertEquals(3, turns);
    }

    @Test
    @DisplayName("never runs a turn inside a turn")
    void noNestedTurn() {
        var nested = new int[1];
        var inner = new OleDragTracker[1];
        inner[0] = new OleDragTracker(() -> where, heard, () -> {
            nested[0]++;
            inner[0].giveFeedback(OleStructs.DROPEFFECT_COPY);
        });

        inner[0].giveFeedback(OleStructs.DROPEFFECT_COPY);

        assertEquals(1, nested[0]);
    }

    @Test
    @DisplayName("drops when the button comes up, and lands over the window it came up over")
    void releasedOverOwn() {
        tracker.giveFeedback(OleStructs.DROPEFFECT_COPY);

        assertEquals(OleStructs.DRAGDROP_S_DROP, tracker.queryContinueDrag(false, 0));
        // SDL's target declined it, and OLE says cancelled: still an in-app drop.
        tracker.finish(OleStructs.DRAGDROP_S_CANCEL, 0);

        assertEquals("released over 5000 10,20", heard.reports.getLast());
    }

    @Test
    @DisplayName("is delivered when the button comes up over another application that takes it")
    void delivered() {
        where = null;
        tracker.giveFeedback(OleStructs.DROPEFFECT_MOVE);
        tracker.queryContinueDrag(false, 0);
        tracker.finish(OleStructs.DRAGDROP_S_DROP, OleStructs.DROPEFFECT_MOVE);

        assertEquals(List.of("delivered MOVE"), heard.reports);
    }

    @Test
    @DisplayName("is cancelled when let go where nothing takes it")
    void declined() {
        where = null;
        tracker.queryContinueDrag(false, 0);
        tracker.finish(OleStructs.DRAGDROP_S_CANCEL, 0);

        assertEquals(List.of("cancelled"), heard.reports);
    }

    @Test
    @DisplayName("is cancelled by Escape, even over one of the application's windows")
    void escape() {
        tracker.giveFeedback(OleStructs.DROPEFFECT_COPY);

        assertEquals(OleStructs.DRAGDROP_S_CANCEL, tracker.queryContinueDrag(true, LEFT_HELD));
        tracker.finish(OleStructs.DRAGDROP_S_CANCEL, 0);

        assertEquals("cancelled", heard.reports.getLast());
    }

    @Test
    @DisplayName("is cancelled at the next question once asked to, from inside a turn too")
    void cancel() {
        var asking = new OleDragTracker(() -> where, heard, () -> {});
        var cancelling = new OleDragTracker[1];
        cancelling[0] = new OleDragTracker(() -> where, heard, () -> cancelling[0].cancel());

        asking.cancel();

        assertEquals(OleStructs.DRAGDROP_S_CANCEL, asking.queryContinueDrag(false, LEFT_HELD));
        assertEquals(OleStructs.DRAGDROP_S_CANCEL, cancelling[0].queryContinueDrag(false, LEFT_HELD));
    }

    @Test
    @DisplayName("knows whether OLE asked anything, which a refused drag never does")
    void heard() {
        assertFalse(tracker.heard());
        tracker.giveFeedback(0);
        assertTrue(tracker.heard());
    }

    @Test
    @DisplayName("reads the effect a target performed, move first")
    void effects() {
        assertEquals(DragAction.COPY, OleDragTracker.action(OleStructs.DROPEFFECT_COPY));
        assertEquals(DragAction.MOVE, OleDragTracker.action(OleStructs.DROPEFFECT_COPY | OleStructs.DROPEFFECT_MOVE));
        assertEquals(DragAction.LINK, OleDragTracker.action(OleStructs.DROPEFFECT_LINK));
        assertNull(OleDragTracker.action(0), "a move the target finished itself says nothing");
    }

    @Test
    @DisplayName("keeps going when a turn throws")
    void throwingTurn() {
        var throwing = new OleDragTracker(() -> where, heard, () -> {
            throw new IllegalStateException("the application's own failure");
        });

        assertEquals(0, throwing.queryContinueDrag(false, LEFT_HELD));
    }
}
