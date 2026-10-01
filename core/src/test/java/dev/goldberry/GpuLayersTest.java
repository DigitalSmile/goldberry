package dev.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.render.DamageRect;
import dev.goldberry.render.GpuContent;
import dev.goldberry.render.GpuPlacement;
import dev.goldberry.render.model.PhysicalRect;

/// Which GPU layers a window shows after a frame that repainted only part of
/// itself (ADR-0481).
@DisplayName("the GPU layers on screen after a frame")
class GpuLayersTest {

    private static final GpuContent A = new GpuContent() {};
    private static final GpuContent B = new GpuContent() {};
    private static final GpuContent C = new GpuContent() {};

    private static GpuPlacement at(GpuContent content, int x, int y) {
        var box = PhysicalRect.of(x, y, 10, 10);
        return new GpuPlacement(content, box, box);
    }

    @Test
    @DisplayName("are what a whole frame placed, and nothing else")
    void wholeFrame() {
        var painted = List.of(at(B, 0, 0));
        assertSame(painted, GpuLayers.merge(List.of(at(A, 50, 50)), painted, null));
    }

    @Test
    @DisplayName("keep a layer the damage did not touch, which a partial repaint never reached")
    void keepsWhatWasNotRepainted() {
        var video = at(A, 50, 50);
        var shown = GpuLayers.merge(List.of(video), List.of(), List.of(new DamageRect(0, 0, 5, 5)));
        assertEquals(List.of(video), shown, "a caret blinked elsewhere; the video is still there");
        assertEquals(List.of(video), GpuLayers.merge(List.of(video), List.of(), List.of()), "and with no damage");
    }

    @Test
    @DisplayName("drop a layer the damage touched and the frame did not place again: it is gone")
    void dropsWhatWentAway() {
        var shown = GpuLayers.merge(List.of(at(A, 50, 50)), List.of(), List.of(new DamageRect(55, 55, 1, 1)));
        assertEquals(List.of(), shown);
    }

    @Test
    @DisplayName("take a layer placed again from the frame, where it is now")
    void placedAgainWins() {
        var moved = at(A, 20, 20);
        var shown = GpuLayers.merge(List.of(at(A, 50, 50)), List.of(moved), List.of(new DamageRect(20, 20, 40, 40)));
        assertEquals(List.of(moved), shown);
    }

    @Test
    @DisplayName("keep paint order: a kept layer stays between the layers placed again around it")
    void keepsPaintOrder() {
        var a = at(A, 0, 0);
        var b = at(B, 100, 100);
        var c = at(C, 5, 5);
        var aAgain = at(A, 0, 0);
        var cAgain = at(C, 5, 5);
        var shown = GpuLayers.merge(List.of(a, b, c), List.of(aAgain, cAgain), List.of(new DamageRect(0, 0, 20, 20)));
        assertEquals(List.of(aAgain, b, cAgain), shown);
    }

    @Test
    @DisplayName("put a new layer after the ones it was painted after")
    void newLayers() {
        var a = at(A, 0, 0);
        var b = at(B, 100, 100);
        var aAgain = at(A, 0, 0);
        var c = at(C, 5, 5);
        var shown = GpuLayers.merge(List.of(a, b), List.of(aAgain, c), List.of(new DamageRect(0, 0, 20, 20)));
        assertEquals(List.of(aAgain, b, c), shown);
    }
}
