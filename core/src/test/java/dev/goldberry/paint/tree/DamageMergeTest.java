package dev.goldberry.paint.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.render.DamageRect;

/// Merging the nearest damage rectangles until few are left: what the damage
/// list does past its limit instead of repainting the whole frame.
class DamageMergeTest {

    @Test
    @DisplayName("a list within the limit comes back as it was")
    void withinTheLimit() {
        var rects = List.of(new DamageRect(0, 0, 4, 4), new DamageRect(100, 100, 4, 4));
        assertEquals(rects, DamageMerge.reduce(rects, 8).orElseThrow());
    }

    @Test
    @DisplayName("the two nearest are joined first, and the far one is left alone")
    void nearestFirst() {
        var near = new DamageRect(0, 0, 10, 10);
        var beside = new DamageRect(12, 0, 10, 10);
        var far = new DamageRect(500, 500, 10, 10);
        var merged = DamageMerge.reduce(List.of(near, far, beside), 2).orElseThrow();
        assertEquals(2, merged.size());
        assertTrue(merged.contains(new DamageRect(0, 0, 22, 10)), merged.toString());
        assertTrue(merged.contains(far), merged.toString());
    }

    @Test
    @DisplayName("every pixel of the input is inside the output")
    void coversEverything() {
        var rects = new ArrayList<DamageRect>();
        for (var i = 0; i < 40; i++) {
            rects.add(new DamageRect((i * 37) % 900, (i * 53) % 700, 16, 16));
        }
        var merged = DamageMerge.reduce(rects, 8).orElseThrow();
        assertTrue(merged.size() <= 8, merged.toString());
        for (var rect : rects) {
            assertTrue(merged.stream().anyMatch(m -> contains(m, rect)), rect + " is inside one of " + merged);
        }
    }

    @Test
    @DisplayName("a join that grows over a third rectangle takes it in, so no pixel is uploaded twice")
    void absorbsWhatItOverlaps() {
        var left = new DamageRect(0, 0, 10, 10);
        var right = new DamageRect(40, 0, 10, 10);
        var between = new DamageRect(20, 2, 4, 4);
        var corner = new DamageRect(900, 900, 4, 4);
        var merged =
                DamageMerge.reduce(List.of(left, right, between, corner), 3).orElseThrow();
        for (var a = 0; a < merged.size(); a++) {
            for (var b = a + 1; b < merged.size(); b++) {
                assertTrue(!DamageMerge.intersects(merged.get(a), merged.get(b)), merged.toString());
            }
        }
    }

    @Test
    @DisplayName("past the search limit the caller is told to damage everything")
    void tooManyToSearch() {
        var rects = new ArrayList<DamageRect>();
        for (var i = 0; i <= DamageMerge.SEARCH_LIMIT; i++) {
            rects.add(new DamageRect(i * 3, 0, 2, 2));
        }
        assertTrue(DamageMerge.reduce(rects, 8).isEmpty());
    }

    @Test
    @DisplayName("a join costs what it adds: nothing for one inside another, two corners for a diagonal overlap")
    void costIsWhatTheJoinAdds() {
        assertEquals(0, DamageMerge.cost(new DamageRect(0, 0, 10, 10), new DamageRect(2, 2, 4, 4)));
        assertEquals(0, DamageMerge.cost(new DamageRect(0, 0, 10, 10), new DamageRect(10, 0, 10, 10)));
        assertEquals(50, DamageMerge.cost(new DamageRect(0, 0, 10, 10), new DamageRect(5, 5, 10, 10)));
    }

    @Test
    @DisplayName("a limit below one is refused")
    void limitBelowOne() {
        assertThrows(IllegalArgumentException.class, () -> DamageMerge.reduce(List.of(), 0));
    }

    private static boolean contains(DamageRect outer, DamageRect inner) {
        return inner.x() >= outer.x()
                && inner.y() >= outer.y()
                && inner.x() + inner.width() <= outer.x() + outer.width()
                && inner.y() + inner.height() <= outer.y() + outer.height();
    }
}
