package dev.goldberry;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.value.Shadow;
import dev.goldberry.render.model.LogicalPoint;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.render.model.LogicalSize;

@DisplayName("a popup's shadow margin")
class PopupMarginTest {

    /// The themes' `--gb-elevation-2`: `0 8px 32px`.
    private static final Shadow LEVEL_2 = new Shadow(0, 8, 32, 0, 0x8F000000);

    @Test
    @DisplayName("reaches as far as the shadow does on each side, half the blur past the offset")
    void fromAShadow() {
        assertEquals(new PopupMargin(16, 8, 16, 24), PopupMargin.of(List.of(LEVEL_2)));
    }

    @Test
    @DisplayName("takes the farthest reach of several shadows, rounded up")
    void fromSeveral() {
        var wide = new Shadow(-3.2, 0, 4, 0, 0x40000000);
        assertEquals(new PopupMargin(16, 8, 16, 24), PopupMargin.of(List.of(LEVEL_2, wide)));
        assertEquals(new PopupMargin(6, 2, 0, 2), PopupMargin.of(List.of(wide)));
    }

    @Test
    @DisplayName("an inset shadow, or one with no ink, needs no room")
    void insetAndInkless() {
        assertAll(
                () -> assertTrue(PopupMargin.of(List.of(new Shadow(0, 8, 32, 0, 0x80000000, true)))
                        .isNone()),
                () -> assertTrue(PopupMargin.of(List.of(Shadow.NONE)).isNone()),
                () -> assertTrue(PopupMargin.of(List.of()).isNone()));
    }

    @Test
    @DisplayName("converts between the panel and the window around it")
    void geometry() {
        var margin = new PopupMargin(16, 8, 16, 24);
        var panel = LogicalSize.of(100, 40);
        var window = margin.around(panel);

        assertAll(
                () -> assertEquals(LogicalSize.of(132, 72), window),
                () -> assertEquals(panel, margin.within(window)),
                () -> assertEquals(new LogicalPoint(24, 52), margin.windowAt(new LogicalPoint(40, 60))),
                () -> assertEquals(new LogicalPoint(40, 60), margin.panelAt(new LogicalPoint(24, 52))),
                () -> assertEquals(LogicalRect.of(16, 8, 100, 40), margin.panelIn(window)),
                () -> assertFalse(margin.isNone()));
    }

    @Test
    @DisplayName("is never negative")
    void notNegative() {
        assertThrows(IllegalArgumentException.class, () -> new PopupMargin(-1, 0, 0, 0));
    }
}
