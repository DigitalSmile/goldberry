package io.github.digitalsmile.goldberry.widgets.core.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.render.model.PhysicalRect;

/// §1's four fit modes, as arithmetic ([ADR-0358]).
///
/// A 200×100 image at natural size 200×100, placed in boxes of other shapes.
class FitTest {

    private static final PhysicalRect WHOLE = new PhysicalRect(0, 0, 200, 100);

    private static Fit.Placement place(Fit fit, double boxWidth, double boxHeight) {
        return fit.place(200, 100, 200, 100, boxWidth, boxHeight);
    }

    @Nested
    @DisplayName("contain")
    class Contain {

        @Test
        @DisplayName("letterboxes a wide picture in a square, centred, with every pixel")
        void letterbox() {
            assertEquals(new Fit.Placement(WHOLE, 0, 25, 100, 50), place(Fit.CONTAIN, 100, 100));
        }

        @Test
        @DisplayName("grows a picture into a larger box")
        void grows() {
            assertEquals(new Fit.Placement(WHOLE, 0, 0, 400, 200), place(Fit.CONTAIN, 400, 200));
        }
    }

    @Nested
    @DisplayName("cover")
    class Cover {

        @Test
        @DisplayName("fills a square by cropping the sides, not by clipping the drawing")
        void cropsTheSides() {
            assertEquals(
                    new Fit.Placement(new PhysicalRect(50, 0, 100, 100), 0, 0, 100, 100), place(Fit.COVER, 100, 100));
        }

        @Test
        @DisplayName("fills a tall strip by cropping all but a centred column")
        void tallStrip() {
            var placement = place(Fit.COVER, 50, 200);

            assertEquals(new PhysicalRect(87, 0, 25, 100), placement.source());
            assertEquals(50, placement.width());
            assertEquals(200, placement.height());
        }
    }

    @Test
    @DisplayName("fill stretches the whole picture over the box")
    void fill() {
        assertEquals(new Fit.Placement(WHOLE, 0, 0, 100, 300), place(Fit.FILL, 100, 300));
    }

    @Nested
    @DisplayName("none")
    class None {

        @Test
        @DisplayName("centres the natural size in a larger box")
        void centres() {
            assertEquals(new Fit.Placement(WHOLE, 50, 50, 200, 100), place(Fit.NONE, 300, 200));
        }

        @Test
        @DisplayName("crops a centred window from a picture larger than its box")
        void crops() {
            assertEquals(new Fit.Placement(new PhysicalRect(50, 25, 100, 50), 0, 0, 100, 50), place(Fit.NONE, 100, 50));
        }
    }

    @Test
    @DisplayName("a 2x raster drawn at its natural size takes two pixels per unit")
    void aDenseRaster() {
        var placement = Fit.NONE.place(400, 200, 200, 100, 100, 100);

        assertEquals(new PhysicalRect(100, 0, 200, 200), placement.source());
        assertEquals(100, placement.width());
        assertEquals(100, placement.height());
    }

    @Test
    @DisplayName("a box with no area draws nothing")
    void nothingToFill() {
        assertNull(place(Fit.COVER, 0, 100));
        assertNull(place(Fit.CONTAIN, 100, -1));
    }

    @Test
    @DisplayName("a crop is never less than a pixel, however thin the box")
    void atLeastAPixel() {
        var placement = place(Fit.COVER, 1, 10_000);

        assertEquals(1, placement.source().width());
    }

    @Test
    @DisplayName("fit= names the four and refuses a typo rather than meaning contain")
    void named() {
        assertEquals(Fit.CONTAIN, Fit.named(null));
        assertEquals(Fit.COVER, Fit.named(" Cover "));
        assertEquals(Fit.NONE, Fit.named("none"));
        assertThrows(IllegalArgumentException.class, () -> Fit.named("cober"));
    }
}
