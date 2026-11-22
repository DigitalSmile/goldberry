package dev.goldberry.widgets.data.sparkline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.TestFont;

/// A sparkline's box is drawn through a painter that is a value: the same
/// series in the same colour is an equal painter, which the render tree reads
/// as nothing to repaint.
class SparklinePainterTest {

    @Test
    @DisplayName("the same series in the same colour is an equal painter, whatever its attributes")
    void sameSeries() {
        var context = TestFont.context();
        var first = new Sparkline(List.of(1.0, 4.0, 2.0)).render(ComputedStyle.INITIAL, List.of(), context);
        var second = new Sparkline(List.of(1.0, 4.0, 2.0), false, false, Attributes.NONE.id("trend"))
                .render(ComputedStyle.INITIAL, List.of(), context);
        assertEquals(first.painting(), second.painting());
    }

    @Test
    @DisplayName("another value, another colour or a fill is another painter")
    void differences() {
        var context = TestFont.context();
        var line = new Sparkline(List.of(1.0, 4.0, 2.0));
        var painter = line.render(ComputedStyle.INITIAL, List.of(), context).painting();
        assertNotEquals(
                painter,
                new Sparkline(List.of(1.0, 4.0, 3.0))
                        .render(ComputedStyle.INITIAL, List.of(), context)
                        .painting());
        assertNotEquals(
                painter,
                line.render(ComputedStyle.INITIAL.color(0xFFFF0000), List.of(), context)
                        .painting());
        assertNotEquals(
                painter,
                line.fill(true)
                        .render(ComputedStyle.INITIAL, List.of(), context)
                        .painting());
    }
}
