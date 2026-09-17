package io.github.digitalsmile.goldberry.example.motion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// One tile's settle, as arithmetic over a time ([ADR-0354]).
class SettleTest {

    private final Settle settle = Settle.TILES;

    @Test
    @DisplayName("before its delay a tile is above its place, turned, and not yet visible")
    void waiting() {
        var pose = settle.at(100, 200, 1);

        assertEquals(-20, pose.offsetY());
        assertEquals(Math.toRadians(4), pose.radians(), 1e-12);
        assertEquals(0, pose.opacity());
    }

    @Test
    @DisplayName("once its duration has passed a tile is exactly at rest")
    void landed() {
        assertSame(Settle.Pose.REST, settle.at(200 + 850, 200, -1));
        assertTrue(settle.at(10_000, 0, 0.3).atRest());
    }

    @Test
    @DisplayName("on the way it falls and straightens together, and is opaque well before it lands")
    void onTheWay() {
        var early = settle.at(100, 0, -1);
        var late = settle.at(700, 0, -1);

        assertTrue(early.offsetY() < late.offsetY() && late.offsetY() < 0, "falling: " + early + " then " + late);
        assertTrue(Math.abs(early.radians()) > Math.abs(late.radians()), "straightening");
        assertEquals(1, settle.at(0.35 * 850, 0, 0).opacity(), 1e-9);
    }

    @Test
    @DisplayName("the stagger is per place of distance, and a turn beyond one is clamped")
    void staggerAndClamp() {
        assertEquals(90, settle.delayFor(2));
        assertEquals(0, settle.delayFor(-3));
        assertEquals(Math.toRadians(4), settle.at(0, 10, 7).radians(), 1e-12);
    }

    @Test
    @DisplayName("a settle that takes no time is refused")
    void refusesNoDuration() {
        assertThrows(IllegalArgumentException.class, () -> new Settle(0, 20, 4, 45));
    }
}
