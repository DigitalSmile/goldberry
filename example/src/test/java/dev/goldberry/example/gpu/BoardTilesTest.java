package dev.goldberry.example.gpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The walk the GPU screen's board has its piece take.
@DisplayName("the board of raster tiles")
class BoardTilesTest {

    @Test
    @DisplayName("walks every square of the board's edge once, clockwise, and back to the start")
    void walksTheEdge() {
        var route = BoardTiles.route();
        assertEquals(16, route);
        var seen = new HashSet<Integer>();
        for (var step = 0; step < route; step++) {
            var square = BoardTiles.squareAt(step);
            var column = square % BoardTiles.COLUMNS;
            var row = square / BoardTiles.COLUMNS;
            assertTrue(
                    column == 0 || row == 0 || column == BoardTiles.COLUMNS - 1 || row == BoardTiles.ROWS - 1,
                    "step " + step + " is on square " + square + ", inside the edge");
            assertTrue(seen.add(square), "step " + step + " visits " + square + " again");
            var next = BoardTiles.squareAt(step + 1);
            var apart = Math.abs(next % BoardTiles.COLUMNS - column) + Math.abs(next / BoardTiles.COLUMNS - row);
            assertEquals(1, apart, "step " + step + " moves one square");
        }
        assertEquals(0, BoardTiles.squareAt(route));
        assertEquals(
                List.of(0, 1, 5, 17, 22, 18, 12, 6),
                List.of(0, 1, 5, 7, 9, 13, 14, 15).stream()
                        .map(BoardTiles::squareAt)
                        .toList());
    }
}
