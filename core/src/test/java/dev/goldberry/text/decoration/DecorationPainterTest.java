package dev.goldberry.text.decoration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import dev.goldberry.RendererRequirement;
import dev.goldberry.paint.Frame;
import dev.goldberry.paint.TestFrames;
import dev.goldberry.text.flow.DecorationPaint;
import dev.goldberry.text.flow.DecorationStyle;
import dev.goldberry.text.flow.TextDecoration;
import dev.goldberry.text.font.Font;

/// The five styles, measured in ink on a frame with no text on it: what each
/// one draws, where, and that a line drawn in two pieces is the line drawn in
/// one.
class DecorationPainterTest {

    private static final int WIDTH = 120;
    private static final int HEIGHT = 48;
    private static final int BACKGROUND = 0xFF000000;
    private static final int INK = 0xFFFFFFFF;
    private static final double BASELINE = 20;

    /// A face whose underline's top is 3 below the baseline and whose
    /// strikethrough's top is 6 above it, both 2 thick.
    private static final Font.Decorations RULES = new Font.Decorations(3, 2, -6, 2);

    private static final Set<TextDecoration> UNDER = Set.of(TextDecoration.UNDERLINE);
    private static final Set<TextDecoration> THROUGH = Set.of(TextDecoration.LINE_THROUGH);

    @BeforeEach
    void renderer() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("solid is a rectangle at the face's own place and thickness")
    void solid() {
        var target = draw(frame -> paint(frame, 0, WIDTH, UNDER, DecorationPaint.SOLID));

        assertEquals(List.of(23, 24), solidRows(target), "baseline 20 + position 3, two thick");
    }

    @Test
    @DisplayName("double is two lines a thickness apart, growing down from the underline")
    void doubled() {
        var target = draw(frame -> paint(frame, 0, WIDTH, UNDER, style(DecorationStyle.DOUBLE)));

        assertEquals(List.of(23, 24, 27, 28), solidRows(target));
    }

    @Test
    @DisplayName("a line-through stays centred on the face's line, whatever its style")
    void lineThroughIsCentred() {
        var solid = draw(frame -> paint(frame, 0, WIDTH, THROUGH, DecorationPaint.SOLID));
        var doubled = draw(frame -> paint(frame, 0, WIDTH, THROUGH, style(DecorationStyle.DOUBLE)));

        assertEquals(List.of(14, 15), solidRows(solid));
        assertEquals(List.of(12, 13, 16, 17), solidRows(doubled), "the band of three thicknesses is centred on 14..16");
    }

    @Test
    @DisplayName("dotted is dots a thickness apart, and dashed is dashes three thicknesses long")
    void dotsAndDashes() {
        var dotted = draw(frame -> paint(frame, 0, WIDTH, UNDER, style(DecorationStyle.DOTTED)));
        var dashed = draw(frame -> paint(frame, 0, WIDTH, UNDER, style(DecorationStyle.DASHED)));

        assertEquals(List.of(), solidRows(dotted), "no row of a dotted line is inked all the way");
        assertEquals(WIDTH / 4, inkRuns(dotted, 24).size(), "a 2-thick dot every 4 pixels");
        var dashes = inkRuns(dashed, 24);
        assertEquals(WIDTH / 10, dashes.size(), "a 6-pixel dash every 10 pixels");
        assertTrue(dashes.stream().allMatch(run -> run == 6), () -> "dash lengths " + dashes);
    }

    @Test
    @DisplayName("wavy swings above and below its centre and inks no row all the way")
    void wavy() {
        var target = draw(frame -> paint(frame, 0, WIDTH, UNDER, style(DecorationStyle.WAVY)));

        assertEquals(List.of(), solidRows(target));
        var rows = inkedRows(target);
        // Amplitude 2 either side of the centre, plus the stroke's own 2.
        assertEquals(23, rows.getFirst(), "the wave's top is the underline's top");
        assertTrue(rows.getLast() >= 28 && rows.getLast() <= 29, () -> "the wave reaches row " + rows.getLast());
    }

    @Test
    @DisplayName("a colour of its own is used over the text's")
    void ownColour() {
        var target = draw(frame -> DecorationPainter.paint(
                frame, 0, BASELINE, WIDTH, INK, UNDER, DecorationPaint.SOLID.colour(0xFFFF0000), RULES));

        assertEquals(0xFFFF0000, target.pixel(10, 23));
    }

    @Test
    @DisplayName("a thickness and an offset of its own move the underline")
    void thicknessAndOffset() {
        var target = draw(frame ->
                paint(frame, 0, WIDTH, UNDER, DecorationPaint.SOLID.thickness(3).underlineOffset(5)));

        assertEquals(List.of(25, 26, 27), solidRows(target));
    }

    @Test
    @DisplayName("zero thickness and zero width draw nothing")
    void nothing() {
        var thin = draw(frame -> paint(frame, 0, WIDTH, UNDER, DecorationPaint.SOLID.thickness(0)));
        var narrow = draw(frame -> paint(frame, 10, 0, UNDER, DecorationPaint.SOLID));
        var none = draw(frame -> paint(frame, 0, WIDTH, TextDecoration.NONE, style(DecorationStyle.WAVY)));

        assertEquals(List.of(), inkedRows(thin));
        assertEquals(List.of(), inkedRows(narrow));
        assertEquals(List.of(), inkedRows(none));
    }

    /// A word in a span of its own is a piece of its own. The pattern is
    /// anchored to the frame, so the two pieces draw what one would.
    @Nested
    @DisplayName("a line drawn in two pieces")
    class Pieces {

        @ParameterizedTest
        @EnumSource(
                value = DecorationStyle.class,
                names = {"SOLID", "DOUBLE", "DOTTED", "DASHED"})
        @DisplayName("is the line drawn in one")
        void straight(DecorationStyle style) {
            var whole = draw(frame -> paint(frame, 0, WIDTH, UNDER, style(style)));
            var split = draw(frame -> {
                paint(frame, 0, 47, UNDER, style(style));
                paint(frame, 47, WIDTH - 47, UNDER, style(style));
            });

            assertEquals(pixels(whole), pixels(split));
        }

        /// A stroke's coverage where two strokes meet is not bit for bit the
        /// coverage of one stroke through the point. What matters is that the
        /// wave does not jump or restart, so each column's ink is compared.
        @Test
        @DisplayName("keeps one wave")
        void wave() {
            var whole = draw(frame -> paint(frame, 0, WIDTH, UNDER, style(DecorationStyle.WAVY)));
            var split = draw(frame -> {
                paint(frame, 0, 47, UNDER, style(DecorationStyle.WAVY));
                paint(frame, 47, WIDTH - 47, UNDER, style(DecorationStyle.WAVY));
            });

            for (var x = 0; x < WIDTH; x++) {
                var expected = inkCentre(whole, x);
                var actual = inkCentre(split, x);
                assertEquals(expected, actual, 0.5, "column " + x);
            }
        }
    }

    // --- helpers --------------------------------------------------------------

    private static DecorationPaint style(DecorationStyle style) {
        return DecorationPaint.SOLID.style(style);
    }

    private static void paint(Frame frame, double x, double width, Set<TextDecoration> lines, DecorationPaint paint) {
        DecorationPainter.paint(frame, x, BASELINE, width, INK, lines, paint, RULES);
    }

    private static TestFrames.Target draw(Consumer<Frame> painting) {
        var target = TestFrames.of(WIDTH, HEIGHT, 1.0f);
        target.frame().fill(BACKGROUND);
        painting.accept(target.frame());
        target.end();
        return target;
    }

    private static boolean isInk(TestFrames.Target target, int x, int y) {
        return target.pixel(x, y) != BACKGROUND;
    }

    /// The rows inked in every column.
    private static List<Integer> solidRows(TestFrames.Target target) {
        var rows = new ArrayList<Integer>();
        for (var y = 0; y < HEIGHT; y++) {
            var solid = true;
            for (var x = 0; x < WIDTH && solid; x++) {
                solid = target.pixel(x, y) == INK;
            }
            if (solid) {
                rows.add(y);
            }
        }
        return rows;
    }

    /// The rows with any ink at all.
    private static List<Integer> inkedRows(TestFrames.Target target) {
        var rows = new ArrayList<Integer>();
        for (var y = 0; y < HEIGHT; y++) {
            for (var x = 0; x < WIDTH; x++) {
                if (isInk(target, x, y)) {
                    rows.add(y);
                    break;
                }
            }
        }
        return rows;
    }

    /// The lengths of the runs of ink along row `y`.
    private static List<Integer> inkRuns(TestFrames.Target target, int y) {
        var runs = new ArrayList<Integer>();
        var length = 0;
        for (var x = 0; x < WIDTH; x++) {
            if (isInk(target, x, y)) {
                length++;
            } else if (length > 0) {
                runs.add(length);
                length = 0;
            }
        }
        if (length > 0) {
            runs.add(length);
        }
        return runs;
    }

    /// Where column `x`'s ink is centred, weighted by coverage.
    private static double inkCentre(TestFrames.Target target, int x) {
        var weight = 0.0;
        var sum = 0.0;
        for (var y = 0; y < HEIGHT; y++) {
            var coverage = target.pixel(x, y) & 0xFF;
            weight += coverage;
            sum += coverage * y;
        }
        return weight == 0 ? -1 : sum / weight;
    }

    private static List<Integer> pixels(TestFrames.Target target) {
        var all = new ArrayList<Integer>(WIDTH * HEIGHT);
        for (var y = 0; y < HEIGHT; y++) {
            for (var x = 0; x < WIDTH; x++) {
                all.add(target.pixel(x, y));
            }
        }
        return all;
    }
}
