package io.github.digitalsmile.goldberry.widgets.core.affix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.Widgets;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// An affix pinned to a vertical and a horizontal edge at once ([ADR-0371]).
class AffixTwoAxesTest {

    private static final LogicalRect VIEWPORT = LogicalRect.of(0, 0, 200, 100);
    private static final LogicalRect DOCUMENT = LogicalRect.of(-300, -400, 1000, 2000);

    @Test
    @DisplayName("each axis is its own subtraction: scrolled down and right, it is pulled back on both")
    void independentAxes() {
        var self = LogicalRect.of(-40, -60, 120, 24);

        assertEquals(60, AffixState.shiftFor(Edge.TOP, 0, self, VIEWPORT, DOCUMENT), 1e-6);
        assertEquals(40, AffixState.shiftFor(Edge.LEFT, 0, self, VIEWPORT, DOCUMENT), 1e-6);
    }

    @Test
    @DisplayName("in view on one axis, it moves only on the other")
    void onlyTheAxisThatScrolled() {
        var self = LogicalRect.of(10, -60, 120, 24);

        assertEquals(60, AffixState.shiftFor(Edge.TOP, 0, self, VIEWPORT, DOCUMENT), 1e-6);
        assertEquals(0, AffixState.shiftFor(Edge.LEFT, 0, self, VIEWPORT, DOCUMENT), 1e-6);
    }

    @Test
    @DisplayName("markup writes both edges, and a second edge on the same axis is not one")
    void markup() {
        var affix = (Affix) Widgets.inflater()
                .inflate(KdlParser.parse("affix edge=\"top left\" { text \"x\" }")
                        .getFirst());

        assertEquals(Edge.TOP, affix.edge());
        assertEquals(Edge.LEFT, affix.cross());
        assertNull(Edge.parseCross("top bottom"));
        assertNull(Edge.parseCross("right"));
    }

    @Test
    @DisplayName("two edges on one axis is refused in Java")
    void sameAxisRefused() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Affix(List.of(new Text("x")), Edge.TOP, 0, Attributes.NONE).alsoPinnedTo(Edge.BOTTOM));
    }
}
