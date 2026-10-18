package dev.goldberry.widgets.panel.list;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Which rows a virtual list builds: its window, and each pinned row on its own.
class BuiltRunsTest {

    @Test
    @DisplayName("with nothing pinned, the window is the only run")
    void theWindowAlone() {
        assertEquals(List.of(new BuiltRuns(10, 30)), BuiltRuns.of(10, 30, 100));
    }

    @Test
    @DisplayName("a pin far below the window is one row of its own, not the rows between")
    void aPinBelow() {
        assertEquals(List.of(new BuiltRuns(0, 16), new BuiltRuns(9000, 9001)), BuiltRuns.of(0, 16, 10_000, 9000));
    }

    @Test
    @DisplayName("a pin above the window comes first, so the runs stay in the model's order")
    void aPinAbove() {
        assertEquals(List.of(new BuiltRuns(2, 3), new BuiltRuns(400, 420)), BuiltRuns.of(400, 420, 1000, 2));
    }

    @Test
    @DisplayName("a pin inside the window, or touching it, joins it")
    void aPinThatMeetsTheWindow() {
        assertEquals(List.of(new BuiltRuns(10, 30)), BuiltRuns.of(10, 30, 100, 15));
        assertEquals(List.of(new BuiltRuns(9, 30)), BuiltRuns.of(10, 30, 100, 9));
        assertEquals(List.of(new BuiltRuns(10, 31)), BuiltRuns.of(10, 30, 100, 30));
    }

    @Test
    @DisplayName("two pins on the same row are one, and pins on either side are two")
    void twoPins() {
        assertEquals(
                List.of(new BuiltRuns(0, 1), new BuiltRuns(50, 60), new BuiltRuns(99, 100)),
                BuiltRuns.of(50, 60, 100, 99, 0, 99));
    }

    @Test
    @DisplayName("a pin that has left the model is dropped, and -1 means none")
    void aPinOutsideTheModel() {
        assertEquals(List.of(new BuiltRuns(0, 8)), BuiltRuns.of(0, 8, 20, -1, 20, 500));
    }

    @Test
    @DisplayName("a window past the end of a model that shrank is clamped to it")
    void aWindowPastTheEnd() {
        assertEquals(List.of(new BuiltRuns(3, 5)), BuiltRuns.of(3, 40, 5));
        assertEquals(List.of(new BuiltRuns(1, 2)), BuiltRuns.of(40, 80, 5, 1));
    }

    @Test
    @DisplayName("an empty model builds nothing")
    void empty() {
        assertEquals(List.of(), BuiltRuns.of(0, 40, 0, 0));
    }
}
