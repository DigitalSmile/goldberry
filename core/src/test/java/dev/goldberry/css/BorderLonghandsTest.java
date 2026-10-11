package dev.goldberry.css;

import static dev.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.css.value.CssLength;

/// `border-style`, the four `border-<side>-style`s, and the four corners of
/// `border-radius`.
class BorderLonghandsTest {

    private static Decoration decoration(String css) {
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, css);
        var declarations = new StyleResolver(List.of(sheet)).resolve(element("button"));
        return ComputedStyle.of(declarations, CssLength.Context.DEFAULT).decoration();
    }

    @Test
    @DisplayName("border-style sets each side's style, by CSS's one-to-four rule")
    void styles() {
        var border = decoration("button { border: 2px solid red; border-style: dashed double }")
                .border();
        assertEquals(Border.Style.DASHED, border.top().style());
        assertEquals(Border.Style.DOUBLE, border.right().style());
        assertEquals(Border.Style.DASHED, border.bottom().style());
        assertEquals(2, border.top().width(), "the width is kept");
    }

    @Test
    @DisplayName("a side's style sets that side alone")
    void oneSide() {
        var border = decoration("button { border: 2px solid red; border-left-style: dotted }")
                .border();
        assertEquals(Border.Style.DOTTED, border.left().style());
        assertEquals(Border.Style.SOLID, border.top().style());
    }

    @Test
    @DisplayName("none and hidden take the side's width away")
    void none() {
        var border = decoration(
                        "button { border: 2px solid red; border-top-style: none;" + " border-bottom-style: hidden }")
                .border();
        assertEquals(0, border.top().width());
        assertEquals(0, border.bottom().width());
        assertEquals(2, border.left().width());
    }

    @Test
    @DisplayName("a corner's radius sets that corner alone, after the shorthand or before it")
    void corners() {
        assertEquals(
                new Corners(4, 4, 12, 4),
                decoration("button { border-radius: 4px; border-bottom-right-radius: 12px }")
                        .corners());
        assertEquals(
                Corners.all(4),
                decoration("button { border-bottom-right-radius: 12px; border-radius: 4px }")
                        .corners());
    }
}
