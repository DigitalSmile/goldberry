package io.github.digitalsmile.goldberry.example.brand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The showcase's window icon ([ADR-0351]): drawn at each size, transparent
/// outside its rounded plate, and four different tiles on it.
class ShowcaseIconTest {

    @Test
    @DisplayName("four sizes, each drawn at its own size rather than scaled")
    void fourSizes() {

        assertEquals(
                List.of(16, 32, 48, 256),
                ShowcaseIcon.sizes().stream().map(i -> i.width()).toList());
        ShowcaseIcon.sizes().forEach(image -> assertEquals(image.width(), image.height()));
    }

    @Test
    @DisplayName("the corner outside the plate's curve is transparent, and the middle of a tile is opaque")
    void roundedAndOpaque() {
        var icon = ShowcaseIcon.at(256);

        assertEquals(0, icon.argb(0, 0) >>> 24, "outside the rounded corner");
        assertEquals(0xFF, icon.argb(80, 80) >>> 24, "inside the first tile");
    }

    @Test
    @DisplayName("the four tiles are four colours")
    void fourTiles() {
        var icon = ShowcaseIcon.at(256);

        var topLeft = icon.argb(80, 80);
        var topRight = icon.argb(176, 80);
        var bottomRight = icon.argb(176, 176);
        var bottomLeft = icon.argb(80, 176);
        assertEquals(
                4,
                List.of(topLeft, topRight, bottomRight, bottomLeft).stream()
                        .distinct()
                        .count());
        assertNotEquals(icon.argb(128, 128), topLeft, "the gap between the tiles shows the plate");
    }
}
