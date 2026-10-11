package dev.goldberry.paint;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.Decoration;
import dev.goldberry.layout.Length;

class BoxEdgesTest {

    private static final Box BOXED =
            Box.of().decoration(Decoration.NONE.border(3, 0xFF000000)).padding(Length.points(5));

    @Test
    @DisplayName("the padding box starts inside the border, and the content box inside the padding too")
    void paddingAndContent() {
        var padding = BoxEdges.padding(BOXED);
        var content = BoxEdges.content(BOXED, 100, 50);

        assertEquals(new BoxEdges(3, 3, 3, 3), padding);
        assertEquals(new BoxEdges(8, 8, 8, 8), content);
        assertEquals(6, padding.horizontal());
        assertEquals(16, content.vertical());
    }

    @Test
    @DisplayName("a box with no border has its padding box at its edge")
    void noBorder() {
        assertEquals(new BoxEdges(0, 0, 0, 0), BoxEdges.padding(Box.of()));
    }
}
