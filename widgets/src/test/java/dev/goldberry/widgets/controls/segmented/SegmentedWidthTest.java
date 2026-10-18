package dev.goldberry.widgets.controls.segmented;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.layout.Insets;
import dev.goldberry.layout.Length;
import dev.goldberry.layout.Position;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.TestFrames;
import dev.goldberry.paint.tree.RenderTree;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.Density;
import dev.goldberry.widgets.controls.TestFont;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.controls.option.Option;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.Row;

/// A segmented bar is as wide as its segments need where nothing sizes it, and
/// its cells stay equal however it is sized, so the pill still travels by its
/// own width.
class SegmentedWidthTest {

    @BeforeEach
    void requireRenderer() {
        RendererRequirement.enforce();
    }

    private static Segmented bar() {
        return new Segmented(
                "grid",
                new Option("list", "List"),
                new Option("grid", "Grid"),
                new Option("calendar", "Calendar view"));
    }

    /// What was laid out: the bar's width and each segment's, in order.
    private record Laid(float bar, List<Float> cells) {}

    private static Laid lay(Widget root, int available, String css) {
        var target = TestFrames.of(available, 80, 1.0f, 0);
        var tree = new ElementTree(root);
        var sheets = new ArrayList<>(Controls.stylesheets(Theme.NORD_DARK, Density.REGULAR));
        sheets.add(Stylesheet.parse(CascadeLayer.APPLICATION, css));
        var renderer = new WidgetRenderer(sheets, TestFont.get());
        var bar = new float[1];
        var cells = new ArrayList<Float>();
        try (var render = RenderTree.create()) {
            render.measure(renderer.render(tree), target.frame().scale(), available, Float.NaN);
            render.update(target.frame(), renderer.render(tree));
            render.forEachPlacedBox(placed -> {
                if (placed.box().owner() instanceof Element element) {
                    switch (element.type()) {
                        case "segmented" -> bar[0] = placed.layout().width();
                        case "option" -> cells.add(placed.layout().width());
                        default -> {}
                    }
                }
            });
        } finally {
            target.end();
        }
        return new Laid(bar[0], List.copyOf(cells));
    }

    private static void assertEqualCells(Laid laid) {
        assertEquals(3, laid.cells().size());
        for (var cell : laid.cells()) {
            // A pixel, which is where layout rounds a third of the row to.
            assertEquals(laid.cells().getFirst(), cell, 1.0f, () -> "the cells differ: " + laid.cells());
        }
    }

    @Test
    @DisplayName("in a row beside a button, the bar is three widest segments wide and leaves the rest")
    void inARow() {
        var laid = lay(new Row(List.of(bar(), new Button("Apply")), Attributes.NONE), 900, "");

        assertEqualCells(laid);
        assertTrue(laid.bar() < 500, () -> "the bar took the row: " + laid.bar());
        // Every cell is the widest, so the longest label is not cut.
        var alone =
                lay(new Row(List.of(new Segmented("x", new Option("x", "Calendar view"))), Attributes.NONE), 900, "");
        assertEquals(alone.cells().getFirst(), laid.cells().getFirst(), 0.5f);
    }

    @Test
    @DisplayName("stretched by a column it fills the row, and the cells still divide it equally")
    void stretched() {
        var laid = lay(new Column(List.of(bar()), Attributes.NONE), 600, "");

        assertEqualCells(laid);
        assertEquals(600, laid.bar(), 0.5f);
    }

    @Test
    @DisplayName("a stylesheet's width still wins, and the cells shrink equally rather than overflow")
    void narrowed() {
        var laid = lay(new Row(List.of(bar()), Attributes.NONE), 900, "segmented { width: 150px }");

        assertEqualCells(laid);
        assertEquals(150, laid.bar(), 0.5f);
        assertTrue(laid.cells().getFirst() < 50, () -> "a cell did not give way: " + laid.cells());
    }

    @Nested
    @DisplayName("a segment's natural width")
    class Natural {

        private static Box sized(float width) {
            return Box.of().size(Length.points(width), Length.points(16));
        }

        @Test
        @DisplayName("is its content side by side, its gaps between and its padding around")
        void contentGapsAndPadding() {
            var segment = Box.of()
                    .padding(new Insets(Length.points(0), Length.points(12), Length.points(0), Length.points(12)))
                    .gap(Length.points(6))
                    .children(sized(16), sized(40));

            assertEquals(12 + 16 + 6 + 40 + 12, NaturalWidth.of(segment), 0.001);
        }

        @Test
        @DisplayName("counts a child's margins and skips an absolutely placed child")
        void marginsAndAbsolutes() {
            var segment = Box.of()
                    .children(
                            sized(20)
                                    .margin(new Insets(
                                            Length.points(0), Length.points(3), Length.points(0), Length.points(2))),
                            sized(100).position(Position.ABSOLUTE));

            assertEquals(25, NaturalWidth.of(segment), 0.001);
        }

        @Test
        @DisplayName("is a stated width when the box has one, and ignores a percentage it cannot resolve")
        void statedAndPercent() {
            assertEquals(30, NaturalWidth.of(sized(30)), 0.001);
            var percent = Box.of().padding(Length.percent(10)).children(sized(8));
            assertEquals(8, NaturalWidth.of(percent), 0.001);
        }
    }
}
