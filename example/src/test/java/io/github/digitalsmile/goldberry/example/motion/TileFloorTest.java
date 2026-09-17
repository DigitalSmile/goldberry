package io.github.digitalsmile.goldberry.example.motion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.paint.CanvasStyle;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;

/// The floor's choreography: who waits how long, when it stops asking for
/// frames, and what a swap changes ([ADR-0354]).
class TileFloorTest {

    private static final LogicalSize SIZE = LogicalSize.of(400, 180);

    private TileFloor floor;

    @BeforeEach
    void setUp() {
        floor = new TileFloor(10, 4, Settle.TILES);
    }

    private void paintAt(double now, boolean reduced) {
        RendererRequirement.enforce();
        var target = TestFrames.of(400, 180, 1.0f);
        try {
            floor.paint(target.frame(), SIZE, new CanvasStyle(CanvasStyle.none().font(), 0xFF000000, now, reduced));
        } finally {
            target.end();
        }
    }

    @Test
    @DisplayName("the focus lands first and the farthest corner last, as a ripple")
    void ripple() {
        floor.replay(0, 0);

        assertEquals(0, floor.delayOf(0));
        assertEquals(Math.hypot(9, 3) * 45, floor.delayOf(39), 1e-9);
        assertEquals(Math.hypot(9, 3) * 45 + 850, floor.settledAfter(), 1e-9);
    }

    @Test
    @DisplayName("it asks for frames until the last tile lands, and not a frame after")
    void asksUntilLanded() {
        paintAt(1_000, false);

        assertTrue(floor.isMoving(1_000, false));
        assertTrue(floor.isMoving(1_000 + floor.settledAfter() - 1, false));
        assertFalse(floor.isMoving(1_000 + floor.settledAfter(), false));
        assertTrue(floor.hasSettled(1_000 + floor.settledAfter()));
    }

    @Test
    @DisplayName("a replay asks for the frame that will start it")
    void replayAsks() {
        paintAt(0, false);
        floor.replay(5, 2);

        assertTrue(floor.isMoving(99_999, false), "the start is the next frame's time, so it needs one");
    }

    @Test
    @DisplayName("a swap moves one tile to a neighbouring band's glaze and fades for 400 ms")
    void swap() {
        paintAt(0, false);
        var before = new int[40];
        for (var i = 0; i < 40; i++) {
            before[i] = floor.glaze(i);
        }
        var settled = floor.settledAfter() + 10;

        floor.swap();
        paintAt(settled, false);

        var changed = 0;
        for (var i = 0; i < 40; i++) {
            if (floor.glaze(i) != before[i]) {
                changed++;
            }
        }
        assertEquals(1, changed, "one tile at a time");
        assertTrue(floor.isMoving(settled + 399, false));
        assertFalse(floor.isMoving(settled + 400, false));
    }

    @Test
    @DisplayName("successive swaps visit different tiles")
    void swapsWander() {
        var visited = new HashSet<Integer>();
        var previous = new int[40];
        for (var n = 0; n < 12; n++) {
            for (var i = 0; i < 40; i++) {
                previous[i] = floor.glaze(i);
            }
            floor.swap();
            for (var i = 0; i < 40; i++) {
                if (floor.glaze(i) != previous[i]) {
                    visited.add(i);
                }
            }
        }
        assertTrue(visited.size() >= 10, "twelve swaps, " + visited.size() + " tiles");
    }

    @Test
    @DisplayName("with reduced motion nothing asks for a frame, and a swap still changes the glaze")
    void reducedMotion() {
        var before = floor.glaze(3);
        paintAt(0, true);

        assertFalse(floor.isMoving(0, true));
        for (var n = 0; n < 40 && floor.glaze(3) == before; n++) {
            floor.swap();
        }
        assertNotEquals(before, floor.glaze(3));
        assertFalse(floor.isMoving(1, true));
    }

    @Test
    @DisplayName("a press lands on the tile under it, and off the floor on nothing")
    void tileAt() {
        assertEquals(Optional.of(new TileFloor.Place(0, 0)), floor.tileAt(10, 10, SIZE));
        assertEquals(Optional.of(new TileFloor.Place(9, 3)), floor.tileAt(395, 175, SIZE));
        assertEquals(Optional.empty(), floor.tileAt(-5, 10, SIZE));
    }

    @Test
    @DisplayName("every tile's starting turn is within one either way, and neighbours differ")
    void turns() {
        for (var i = 0; i < 40; i++) {
            var turn = TileFloor.turnOf(i);
            assertTrue(turn >= -1 && turn <= 1, i + ": " + turn);
        }
        assertNotEquals(TileFloor.turnOf(0), TileFloor.turnOf(1));
    }
}
