package io.github.digitalsmile.goldberry.widgets.form.textarea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.image.Image;
import io.github.digitalsmile.goldberry.offscreen.Offscreen;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.core.Column;

/// `docs/gaps.md` G43: the gutter strip reaches the border, and the text wraps in
/// the room it actually has ([ADR-0350]).
///
/// Asserted on **pixels**, because both defects were invisible in the box tree.
/// The strip's insets were right and a clip cut it, and the wrap width was a
/// number that only looked wrong once a stylesheet made the two paddings differ.
/// Every colour here is opaque and far from every other, so a pixel is one of
/// four things and nothing in between except at an anti-aliased glyph edge.
class TextAreaGutterStripTest {

    private static final int WIDTH = 340;
    private static final int HEIGHT = 220;

    private static final int FIELD = 0xFF2040C0;
    private static final int BORDER = 0xFF20C040;
    private static final int STRIP = 0xFFC02020;

    private static final String TEXT = "one short line\n"
            + "this is a much longer line that is going to wrap more than once inside the area\n"
            + "third\n"
            + "fourth line also wraps because it keeps on going and going\n"
            + "fifth";

    @BeforeEach
    void requireRenderer() {
        RendererRequirement.enforce();
    }

    private static Image render(String declarations, TextArea area) {
        var sheet = Stylesheet.parse(
                CascadeLayer.APPLICATION,
                "text-area { background: #2040c0; border: 1px solid #20c040; color: #ffffff; " + declarations + " }"
                        + " text-area-gutter { background: #c02020 }");
        var sheets = new ArrayList<>(Controls.stylesheets(Theme.NORD_DARK));
        sheets.add(sheet);
        return Offscreen.of(WIDTH, HEIGHT).stylesheets(sheets).render(new Column(area));
    }

    private static TextArea numbered() {
        return new TextArea(TEXT, value -> {}).gutter(true).rows(8, 20);
    }

    /// How tall the control came out: the last row whose left border is drawn.
    private static int bottomOf(Image image) {
        var bottom = 0;
        for (var y = 0; y < HEIGHT; y++) {
            if (image.argb(0, y) == BORDER) {
                bottom = y;
            }
        }
        return bottom;
    }

    @Test
    @DisplayName("the strip fills the padding above and beside the numbers, up to the border")
    void theStripReachesTheBorder() {
        var image = render("padding: 12px 16px; border-radius: 0", numbered());
        var bottom = bottomOf(image);

        assertEquals(STRIP, image.argb(1, 1), "the top-left corner inside the border is the strip's");
        assertEquals(STRIP, image.argb(8, 6), "the top padding above the first number is the strip's");
        assertEquals(STRIP, image.argb(1, bottom - 1), "and so is the bottom-left corner");
        assertEquals(FIELD, image.argb(WIDTH - 4, 6), "the field's own padding is still the field's");
    }

    @Test
    @DisplayName("the strip stops at the border's inner edge rather than painting over it")
    void theBorderIsStillDrawn() {
        var image = render("padding: 12px 16px; border-radius: 0", numbered());

        assertEquals(BORDER, image.argb(0, 40), "the left edge");
        assertEquals(BORDER, image.argb(8, 0), "the top edge over the strip");
        assertEquals(BORDER, image.argb(8, bottomOf(image)), "the bottom edge under it");
    }

    @Test
    @DisplayName("a rounded field keeps its corner: the strip is rounded to fit inside it")
    void aRoundedFieldKeepsItsCorner() {
        var image = render("padding: 12px 16px; border-radius: 10px", numbered());

        assertTrue(image.argb(0, 0) >>> 24 < 0x80, "outside the curve is not painted by the strip");
        assertEquals(STRIP, image.argb(6, 20));
    }

    @ParameterizedTest(name = "left {0}px, right {1}px, gap {2}px")
    @CsvSource({"0, 16, 8", "4, 16, 14", "4, 16, 8", "0, 16, 14", "16, 4, 14"})
    @DisplayName("nothing is drawn in the right padding, whatever the two paddings and the gap are")
    void theRightPaddingIsEmpty(int left, int right, int gap) {
        var image = render(
                "padding: 12px " + right + "px 12px " + left + "px; --gb-gutter-gap: " + gap + "px; border-radius: 0",
                numbered());
        var bottom = bottomOf(image);

        var inked = new ArrayList<String>();
        for (var y = 13; y < bottom - 12; y++) {
            for (var x = WIDTH - right; x < WIDTH - 1; x++) {
                if (image.argb(x, y) != FIELD) {
                    inked.add(x + "," + y);
                }
            }
        }
        assertEquals(List.of(), inked, "a line wrapped wider than its room runs into the right padding");
    }

    @Test
    @DisplayName("text scrolled past the top is still cut at the content box, not at the border")
    void scrolledTextIsStillClipped() {
        var area = new TextArea(TEXT + "\n6\n7\n8\n9\n10\n11\n12", value -> {}).rows(2, 2);
        var image = render("padding: 12px 16px; border-radius: 0", area);

        var inked = new ArrayList<String>();
        for (var y = 1; y < 12; y++) {
            for (var x = 1; x < WIDTH - 1; x++) {
                if (image.argb(x, y) != FIELD) {
                    inked.add(x + "," + y);
                }
            }
        }
        assertEquals(List.of(), inked, "the top padding holds no glyphs");
    }
}
