package dev.goldberry.widgets.controls.select;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.paint.TestFrames;
import dev.goldberry.paint.tree.RenderTree;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.Density;
import dev.goldberry.widgets.controls.TestFont;
import dev.goldberry.widgets.controls.option.Option;
import dev.goldberry.widgets.core.Row;

/// A `select` does not move when its value does: the closed control is as wide
/// as its widest option, so choosing a shorter one leaves the field where it was.
///
/// Read more: [Select](https://goldberry.dev/docs/components/choices.html#select).
class SelectWidthTest {

    private static final String LONGEST = "Nord Dark, high contrast";

    @BeforeEach
    void requireRenderer() {
        RendererRequirement.enforce();
    }

    private static Select select(String value, String placeholder) {
        return new Select(
                value,
                List.of(new Option("light", "Light"), new Option("contrast", LONGEST), new Option("dim", "Dim")),
                null,
                null,
                placeholder,
                false,
                false,
                false,
                null,
                List.of(),
                false,
                Attributes.NONE);
    }

    /// The laid-out widths of the field and of its value cell, in that order,
    /// with the select in a row so nothing stretches it.
    private static float[] widths(Widget select, int available) {
        return widths(select, available, "");
    }

    private static float[] widths(Widget select, int available, String css) {
        var target = TestFrames.of(available, 80, 1.0f, 0);
        var tree = new ElementTree(new Row(List.of(select), Attributes.NONE));
        var sheets = new ArrayList<>(Controls.stylesheets(Theme.NORD_DARK, Density.REGULAR));
        sheets.add(Stylesheet.parse(CascadeLayer.APPLICATION, css));
        var renderer = new WidgetRenderer(sheets, TestFont.get());
        var out = new float[2];
        try (var render = RenderTree.create()) {
            render.measure(renderer.render(tree), target.frame().scale(), available, Float.NaN);
            render.update(target.frame(), renderer.render(tree));
            render.forEachPlacedBox(placed -> {
                if (placed.box().text() != null && out[1] == 0) {
                    out[1] = placed.layout().width();
                }
            });
            render.forEachPlacedBox(placed -> {
                if (out[0] == 0
                        && placed.box().children().size() >= 2
                        && placed.layout().width() > out[1]) {
                    out[0] = placed.layout().width();
                }
            });
        }
        return out;
    }

    @Test
    @DisplayName("choosing a short value after a long one changes the word and not the control")
    void widthDoesNotFollowTheValue() {
        var shortValue = widths(select("light", ""), 600);
        var longValue = widths(select("contrast", ""), 600);

        assertEquals(longValue[0], shortValue[0], 0.01f);
        assertEquals(longValue[1], shortValue[1], 0.01f);
    }

    @Test
    @DisplayName("the value cell is as wide as the widest label, the placeholder included")
    void widestLabel() {
        var cell = widths(select("light", ""), 600)[1];

        assertTrue(cell >= 1, "the cell has a width");
        var withPlaceholder = widths(select("", "Choose a colour scheme for every window you open"), 900)[1];
        assertTrue(withPlaceholder > cell, "a longer placeholder widens it: " + withPlaceholder + " vs " + cell);
    }

    @Test
    @DisplayName("a stylesheet's width still wins, so a narrow select ellipsizes rather than overflowing")
    void stillShrinks() {
        var natural = widths(select("contrast", ""), 600);
        var narrow = widths(select("contrast", ""), 600, "select { width: 90px }");

        assertEquals(90, narrow[0], 0.01f);
        assertTrue(narrow[1] < natural[1], "the value cell gave way: " + narrow[1] + " vs " + natural[1]);
    }
}
