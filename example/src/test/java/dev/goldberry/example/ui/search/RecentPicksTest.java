package dev.goldberry.example.ui.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("the palette's recent picks")
class RecentPicksTest {

    @Test
    @DisplayName("put the latest first and list each once")
    void order() {
        var recent = new RecentPicks();
        recent.remember("a");
        recent.remember("b");
        recent.remember("a");

        assertEquals(List.of("a", "b"), recent.keys());
    }

    @Test
    @DisplayName("drop the oldest past the limit")
    void capped() {
        var recent = new RecentPicks();
        for (var i = 0; i < RecentPicks.LIMIT + 2; i++) {
            recent.remember("k" + i);
        }

        assertEquals(RecentPicks.LIMIT, recent.keys().size());
        assertEquals("k" + (RecentPicks.LIMIT + 1), recent.keys().getFirst());
        assertEquals("k2", recent.keys().getLast());
    }
}
