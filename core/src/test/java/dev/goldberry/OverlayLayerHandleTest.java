package dev.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Corner;

/// [OverlayLayer]: the half of a window's overlays that writes the list a
/// [dev.goldberry.widget.root.WindowRoot] draws.
///
/// `OverlayLayerTest` is about the drawing half. This is about the handles:
/// that an overlay put on a layer comes back attached, so that its own
/// `remove()` takes it off again, which a detached one cannot do.
///
/// Read more: [Overlays and popups](https://goldberry.dev/docs/guide/windows.html#overlays-and-popups).
@DisplayName("an overlay layer")
class OverlayLayerHandleTest {

    private record Hud() implements Widget.Leaf {

        @Override
        public List<Widget> children() {
            return List.of();
        }
    }

    @Test
    @DisplayName("hands back an attached overlay whose remove() takes it off")
    void handleRemoves() {
        var layer = new OverlayLayer();
        var veil = layer.fill(new Hud());
        assertTrue(veil.isAttached());
        assertTrue(veil.isFilling());
        assertEquals(List.of(veil), layer.overlays().get());

        veil.remove();
        assertFalse(veil.isAttached());
        assertEquals(List.of(), layer.current());
    }

    @Test
    @DisplayName("tells two identical overlays apart by which one was handed back")
    void byIdentity() {
        var layer = new OverlayLayer();
        var first = layer.overlay(new Hud(), Corner.TOP_END, 8);
        var second = layer.overlay(new Hud(), Corner.TOP_END, 8);
        first.remove();
        assertEquals(1, layer.current().size());
        assertSame(second, layer.current().getFirst());
    }

    @Test
    @DisplayName("sets a fresh list on every change, so the root watching it rebuilds")
    void freshLists() {
        var layer = new OverlayLayer();
        var seen = new AtomicInteger();
        try (var _ = layer.overlays().subscribe(_ -> seen.incrementAndGet())) {
            var hud = layer.overlay(new Hud(), Corner.BOTTOM_END, 0);
            hud.remove();
            hud.remove();
        }
        assertEquals(2, seen.get(), "one add and one removal; a second remove() is a no-op");
    }

    @Test
    @DisplayName("says when it changed, for the window that has to paint it")
    void tellsTheWindow() {
        var changes = new AtomicInteger();
        var layer = new OverlayLayer(changes::incrementAndGet);
        layer.fill(new Hud()).remove();
        assertEquals(2, changes.get());
    }

    @Test
    @DisplayName("refuses an overlay that is already on a layer")
    void oneLayerAtATime() {
        var layer = new OverlayLayer();
        var veil = layer.fill(new Hud());
        assertThrows(IllegalStateException.class, () -> new OverlayLayer().add(veil));
    }
}
