package dev.goldberry.natives.desktop.dragout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.natives.desktop.macos.ObjC;

/// [CocoaDragTracker]: what AppKit's reports become, with AppKit played by the
/// test.
@DisplayName("a macOS drag's tracking")
class CocoaDragTrackerTest {

    private static final long OWN = 0x7000;

    private final List<String> heard = new ArrayList<>();

    private final CocoaDragTracker tracker = new CocoaDragTracker(new CocoaDragListener() {
        @Override
        public void over(long window, double x, double yFromBottom) {
            heard.add("over " + Long.toHexString(window) + " " + x + "," + yFromBottom);
        }

        @Override
        public void outside() {
            heard.add("outside");
        }

        @Override
        public void delivered(@Nullable DragAction action) {
            heard.add("delivered " + action);
        }

        @Override
        public void releasedOver(long window, double x, double yFromBottom) {
            heard.add("released over " + Long.toHexString(window) + " " + x + "," + yFromBottom);
        }

        @Override
        public void cancelled() {
            heard.add("cancelled");
        }
    });

    @Test
    @DisplayName("reports the pointer over the application's windows when it moves, and outside once")
    void follows() {
        tracker.moved(new CocoaDragTracker.Over(OWN, 10, 20));
        tracker.moved(new CocoaDragTracker.Over(OWN, 10, 20));
        tracker.moved(new CocoaDragTracker.Over(OWN, 11, 20));
        tracker.moved(null);
        tracker.moved(null);

        assertEquals(List.of("over 7000 10.0,20.0", "over 7000 11.0,20.0", "outside"), heard);
    }

    @Test
    @DisplayName("lands over the application's window it was let go over, whatever SDL's target did")
    void releasedOverOwn() {
        tracker.ended(new CocoaDragTracker.Over(OWN, 5, 6), 0, false);

        assertEquals(List.of("released over 7000 5.0,6.0"), heard);
        assertTrue(tracker.isEnded());
    }

    @Test
    @DisplayName("is delivered when another application performed an operation, and cancelled when none did")
    void elsewhere() {
        tracker.ended(null, CocoaDragTracker.MOVE, false);
        var second = new CocoaDragTracker(new CocoaDragListener() {
            @Override
            public void over(long window, double x, double yFromBottom) {}

            @Override
            public void outside() {}

            @Override
            public void delivered(@Nullable DragAction action) {
                heard.add("second delivered");
            }

            @Override
            public void releasedOver(long window, double x, double yFromBottom) {}

            @Override
            public void cancelled() {
                heard.add("second cancelled");
            }
        });
        second.ended(null, 0, false);

        assertEquals(List.of("delivered MOVE", "second cancelled"), heard);
    }

    @Test
    @DisplayName("is cancelled by Escape, even over the application's own window, and ends once")
    void escape() {
        tracker.ended(new CocoaDragTracker.Over(OWN, 5, 6), 0, true);
        tracker.ended(null, CocoaDragTracker.COPY, false);
        tracker.moved(new CocoaDragTracker.Over(OWN, 1, 1));

        assertEquals(List.of("cancelled"), heard);
    }

    @Test
    @DisplayName("offers copy with Generic beside it, and reads Generic alone as no operation named")
    void operations() {
        assertEquals(CocoaDragTracker.COPY | CocoaDragTracker.GENERIC, CocoaDragTracker.mask(Set.of(DragAction.COPY)));
        assertEquals(
                CocoaDragTracker.COPY | CocoaDragTracker.GENERIC | CocoaDragTracker.MOVE | CocoaDragTracker.LINK,
                CocoaDragTracker.mask(EnumSet.allOf(DragAction.class)));
        assertEquals(DragAction.MOVE, CocoaDragTracker.action(CocoaDragTracker.MOVE | CocoaDragTracker.COPY));
        assertEquals(DragAction.COPY, CocoaDragTracker.action(CocoaDragTracker.COPY));
        assertEquals(DragAction.LINK, CocoaDragTracker.action(CocoaDragTracker.LINK));
        assertNull(CocoaDragTracker.action(CocoaDragTracker.GENERIC));
    }

    @Test
    @DisplayName("frames the picture with its hotspot under the pointer, in a flipped view and an unflipped one")
    void frame() {
        // A 40x20 picture held at 10,5, the pointer at 100,30 in a 300-tall view.
        assertEquals(new ObjC.Rect(90, 25, 40, 20), CocoaDragTracker.frame(100, 30, 300, true, 40, 20, 10, 5));
        assertEquals(
                new ObjC.Rect(90, 300 - 30 - 15, 40, 20), CocoaDragTracker.frame(100, 30, 300, false, 40, 20, 10, 5));
    }
}
