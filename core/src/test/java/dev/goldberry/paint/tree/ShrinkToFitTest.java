package dev.goldberry.paint.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.assets.BundledFont;
import dev.goldberry.layout.FlexDirection;
import dev.goldberry.layout.Insets;
import dev.goldberry.layout.Length;
import dev.goldberry.layout.Limits;
import dev.goldberry.layout.Position;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.TestFrames;
import dev.goldberry.render.model.DisplayScale;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.text.Paragraph;
import dev.goldberry.text.font.Font;

/// An absolutely positioned box capped by `max-width` in a row is laid out at
/// the width it ends up with, so a paragraph that wraps inside it is as tall as
/// its lines and the next child starts below them.
///
/// The scene is a game's log panel, over its board: a column at
/// `top: 20px; left: 16px; max-width: 220px` in a 300 by 160 window, holding a
/// line that wraps and one that does not.
///
/// Read more: [How layout works](https://goldberry.dev/docs/layout/index.html#flexbox-from-yoga).
@DisplayName("shrink-to-fit")
class ShrinkToFitTest {

    private static final String LONG = "The opponent plays Slave Hunter on their melee row.";
    private static final String SHORT = "Lyrian Scytheman 6 to 3 (damage).";

    /// Yoga rounds every edge to the pixel grid on its own, so two stacked
    /// boxes can meet a pixel apart from where their heights add up to.
    private static final double EDGE = 1.0;
    private static final Insets AT =
            new Insets(Length.points(20), Length.UNDEFINED, Length.UNDEFINED, Length.points(16));

    private TestFrames.Target target;
    private Font font;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        target = TestFrames.of(300, 160, 1.0f);
        font = Font.bundled(BundledFont.UI, 14);
    }

    @AfterEach
    void tearDown() {
        if (target != null) {
            target.end();
        }
        if (font != null) {
            font.close();
        }
    }

    private Box text(String value) {
        return Box.text(Paragraph.of(font, value), 0xFFFFFFFF);
    }

    /// The log: an absolute column of `lines` at [#AT], capped at 220.
    private Box log(String... lines) {
        return Box.of()
                .direction(FlexDirection.COLUMN)
                .position(Position.ABSOLUTE)
                .inset(AT)
                .limits(Limits.NONE.maxWidth(Length.points(220)))
                .children(Arrays.stream(lines).map(this::text).toArray(Box[]::new));
    }

    /// `child` in a 300 by 160 window whose direction is `direction`.
    private static Box window(FlexDirection direction, Box child) {
        return Box.of()
                .direction(direction)
                .size(Length.points(300), Length.points(160))
                .children(child);
    }

    /// Every box's absolute rectangle, root first.
    private static List<LogicalRect> layouts(RenderTree tree) {
        var out = new ArrayList<LogicalRect>();
        tree.forEachPlacedBox(placed -> out.add(placed.layout()));
        return out;
    }

    private List<LogicalRect> laidOut(Box root) {
        try (var tree = RenderTree.create()) {
            tree.update(target.frame(), root);
            return layouts(tree);
        }
    }

    @Test
    @DisplayName("wraps the long line and starts the next one below it, as a box of width 220 does")
    void theLogPanel() {
        var capped = laidOut(window(FlexDirection.ROW, log(LONG, SHORT)));
        var sized = laidOut(
                window(FlexDirection.ROW, log(LONG, SHORT).limits(Limits.NONE).size(Length.points(220), Length.AUTO)));

        var column = capped.get(1);
        var first = capped.get(2);
        var second = capped.get(3);
        assertEquals(220, column.width(), 0.5, "the column is at its cap");
        assertTrue(first.height() > font.lineHeight() * 1.5, "the long line wrapped: " + first);
        assertEquals(first.top() + first.height(), second.top(), EDGE, "the next line starts below the wrapped one");
        assertEquals(first.height() + second.height(), column.height(), EDGE, "and the column holds both");
        assertEquals(sized, capped, "the same layout as an explicit width of 220");
    }

    @Test
    @DisplayName("leaves a box narrower than its cap at its content's width")
    void narrowerThanTheCap() {
        var laid = laidOut(window(FlexDirection.ROW, log("Pass.", "Round 2.")));
        var column = laid.get(1);
        assertTrue(column.width() < 200, "as wide as its widest line: " + column);
        assertEquals(laid.get(2).height() + laid.get(3).height(), column.height(), EDGE);
    }

    @Test
    @DisplayName("keeps its layout on a frame where nothing changed, and measures again when a line does")
    void acrossFrames() {
        try (var tree = RenderTree.create()) {
            tree.update(target.frame(), window(FlexDirection.ROW, log(LONG, SHORT)));
            var wrapped = layouts(tree);
            tree.update(target.frame(), window(FlexDirection.ROW, log(LONG, SHORT)));
            assertEquals(wrapped, layouts(tree), "a static frame");

            tree.update(target.frame(), window(FlexDirection.ROW, log("Pass.", "Round 2.")));
            var shorter = layouts(tree);
            assertTrue(shorter.get(1).width() < 200, "no line reaches the cap any more: " + shorter.get(1));
            assertEquals(
                    shorter.get(2).height() + shorter.get(3).height(),
                    shorter.get(1).height(),
                    EDGE);

            tree.update(target.frame(), window(FlexDirection.ROW, log(LONG, SHORT)));
            assertEquals(wrapped, layouts(tree), "and back");
        }
    }

    @Test
    @DisplayName("gives the lines the heights Yoga already gives them inside a column")
    void columnParent() {
        var inColumn = laidOut(window(FlexDirection.COLUMN, log(LONG, SHORT)));
        var inRow = laidOut(window(FlexDirection.ROW, log(LONG, SHORT)));
        // The widths differ, and the row's is the one CSS gives: a column
        // shrinks the box to its widest wrapped line, where CSS keeps the cap.
        for (var i = 1; i < 4; i++) {
            assertEquals(inColumn.get(i).top(), inRow.get(i).top(), EDGE, "box " + i + " top");
            assertEquals(inColumn.get(i).height(), inRow.get(i).height(), EDGE, "box " + i + " height");
        }
    }

    @Test
    @DisplayName("sizes a popup measured with no width the same way")
    void measured() {
        try (var tree = RenderTree.create()) {
            var size =
                    tree.measure(window(FlexDirection.ROW, log(LONG, SHORT)), DisplayScale.ONE, Float.NaN, Float.NaN);
            assertEquals(new LogicalSize(300, 160), size);
            var laid = layouts(tree);
            assertEquals(laid.get(2).top() + laid.get(2).height(), laid.get(3).top(), EDGE);
            assertTrue(laid.get(2).height() > font.lineHeight() * 1.5, "the long line wrapped: " + laid.get(2));
        }
    }

    @Nested
    @DisplayName("applies to")
    class Applies {

        private final Box row = Box.of().direction(FlexDirection.ROW);

        @Test
        @DisplayName("an absolute box in a row with an auto width and a cap in points")
        void theCase() {
            assertEquals(220, ShrinkToFit.cap(log(LONG), row));
            assertEquals(
                    240,
                    ShrinkToFit.cap(
                            log(LONG)
                                    .limits(Limits.NONE
                                            .maxWidth(Length.points(220))
                                            .minWidth(Length.points(240))),
                            row),
                    "min-width wins");
            assertEquals(220, ShrinkToFit.cap(log(LONG), Box.of().direction(FlexDirection.ROW_REVERSE)));
        }

        @Test
        @DisplayName("and to nothing Yoga already lays out right")
        void notTheCase() {
            assertTrue(Float.isNaN(ShrinkToFit.cap(log(LONG), null)), "a root");
            assertTrue(Float.isNaN(ShrinkToFit.cap(log(LONG), Box.of().direction(FlexDirection.COLUMN))), "a column");
            assertTrue(Float.isNaN(ShrinkToFit.cap(log(LONG).position(Position.RELATIVE), row)), "in flow");
            assertTrue(Float.isNaN(ShrinkToFit.cap(log(LONG).size(Length.points(200), Length.AUTO), row)), "a width");
            assertTrue(Float.isNaN(ShrinkToFit.cap(log(LONG).limits(Limits.NONE), row)), "no cap");
            assertTrue(
                    Float.isNaN(ShrinkToFit.cap(log(LONG).limits(Limits.NONE.maxWidth(Length.percent(50))), row)),
                    "a percentage cap");
            assertTrue(
                    Float.isNaN(ShrinkToFit.cap(
                            log(LONG)
                                    .inset(new Insets(
                                            Length.points(0), Length.points(10), Length.UNDEFINED, Length.points(10))),
                            row)),
                    "left and right");
        }
    }
}
