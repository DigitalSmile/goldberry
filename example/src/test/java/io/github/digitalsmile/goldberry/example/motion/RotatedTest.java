package io.github.digitalsmile.goldberry.example.motion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.paint.Path;
import io.github.digitalsmile.goldberry.paint.Path.Segment;

/// A path turned about its centre by rewriting its points ([ADR-0354]).
class RotatedTest {

    @Test
    @DisplayName("a quarter turn about the centre moves each corner of a square to the next")
    void quarterTurn() {
        var square = Path.rect(0, 0, 10, 10);

        var turned = Rotated.of(square, 5, 5, Math.PI / 2, 0, 0).segments();

        var first = (Segment.MoveTo) turned.getFirst();
        assertEquals(10, first.x(), 1e-9);
        assertEquals(0, first.y(), 1e-9);
    }

    @Test
    @DisplayName("a move is added after the turn")
    void moved() {
        var line = Path.line(0, 0, 4, 0);

        var segments = Rotated.of(line, 0, 0, 0, 0, -20).segments();

        assertEquals(List.of(new Segment.MoveTo(0, -20), new Segment.LineTo(4, -20)), segments);
    }

    @Test
    @DisplayName("no turn and no move is the same path, not a copy")
    void identity() {
        var path = Path.roundRect(0, 0, 10, 10, 3);

        assertSame(path, Rotated.of(path, 5, 5, 0, 0, 0));
    }

    @Test
    @DisplayName("an arc's own rotation turns with it")
    void arcs() {
        var arc =
                Path.builder().moveTo(0, 0).arcTo(3, 2, 0.1, false, true, 5, 0).build();

        var turned = (Segment.ArcTo) Rotated.of(arc, 0, 0, 0.5, 0, 0).segments().get(1);

        assertEquals(0.6, turned.rotation(), 1e-12);
    }
}
