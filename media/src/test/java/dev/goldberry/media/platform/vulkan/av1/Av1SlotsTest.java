package dev.goldberry.media.platform.vulkan.av1;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// AV1's reference slots on the session's picture buffer.
@DisplayName("AV1's slots on the picture buffer")
class Av1SlotsTest {

    @Test
    @DisplayName("a key frame refreshing every slot holds all eight, and the ninth is free")
    void keyFrame() {
        var slots = new Av1Slots();
        var first = slots.free();
        slots.refresh(0xFF, first);
        for (var i = 0; i < Av1Slots.REFERENCES; i++) {
            assertEquals(first, slots.slotOf(i));
        }
        assertNotEquals(first, slots.free());
    }

    @Test
    @DisplayName("the seven references name their buffer slots, an intra frame none")
    void named() {
        var slots = new Av1Slots();
        slots.refresh(0xFF, 0);
        slots.refresh(0b0000_0010, 1);
        var named = slots.named(List.of(0, 1, 0, 0, 0, 0, 0));
        assertArrayEquals(new int[] {0, 1, 0, 0, 0, 0, 0}, named);
        assertEquals(List.of(0, 1), Av1Slots.distinct(named));
        var intra = slots.named(List.of());
        assertArrayEquals(new int[] {-1, -1, -1, -1, -1, -1, -1}, intra);
        assertEquals(List.of(), Av1Slots.distinct(intra));
    }

    @Test
    @DisplayName("nine slots always leave one free, however the eight are refreshed")
    void alwaysOneFree() {
        var slots = new Av1Slots();
        for (var frame = 0; frame < 40; frame++) {
            var free = slots.free();
            slots.refresh(1 << (frame % 8), free);
        }
        var held = new HashSet<Integer>();
        for (var i = 0; i < Av1Slots.REFERENCES; i++) {
            held.add(slots.slotOf(i));
        }
        assertEquals(8, held.size(), "eight frames in eight slots");
    }

    @Test
    @DisplayName("a key frame shown again is loaded into every slot")
    void loadEverywhere() {
        var slots = new Av1Slots();
        slots.refresh(0xFF, 0);
        slots.refresh(0b100, 3);
        slots.loadEverywhere(2);
        for (var i = 0; i < Av1Slots.REFERENCES; i++) {
            assertEquals(3, slots.slotOf(i));
        }
        slots.clear();
        assertEquals(Av1Slots.NONE, slots.slotOf(0));
    }
}
