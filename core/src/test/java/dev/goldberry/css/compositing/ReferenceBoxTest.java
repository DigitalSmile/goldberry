package dev.goldberry.css.compositing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReferenceBoxTest {

    @Test
    @DisplayName("each box writes the keyword it is read from")
    void cssNameRoundTrips() {
        for (var box : ReferenceBox.values()) {
            assertEquals(box, ReferenceBox.parse(box.cssName()), box.cssName());
        }
        assertEquals("padding-box", ReferenceBox.PADDING_BOX.cssName());
    }

    @Test
    @DisplayName("the margin and SVG boxes read as the border box, and anything else as none")
    void parse() {
        assertEquals(ReferenceBox.BORDER_BOX, ReferenceBox.parse("Margin-Box"));
        assertEquals(ReferenceBox.BORDER_BOX, ReferenceBox.parse("view-box"));
        assertNull(ReferenceBox.parse("outline-box"));
    }
}
