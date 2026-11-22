package dev.goldberry.image.scalable;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.image.StyleImages;
import dev.goldberry.image.Image;
import dev.goldberry.image.ScalableImage;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.render.model.PhysicalRect;
import dev.goldberry.render.model.PhysicalSize;

@DisplayName("ScalableRasters")
class ScalableRastersTest {

    /// A document that draws an opaque grey raster of any size, and counts.
    private static final class Grey implements ScalableImage {

        final AtomicInteger renders = new AtomicInteger();

        @Override
        public LogicalSize naturalSize() {
            return new LogicalSize(10, 10);
        }

        @Override
        public Image render(PhysicalSize size) {
            renders.incrementAndGet();
            var argb = new int[size.width() * size.height()];
            Arrays.fill(argb, 0xFF808080);
            return Image.ofArgb(size.width(), size.height(), argb);
        }
    }

    @Test
    @DisplayName("scales a crop outward to whole pixels, inside the raster")
    void scaled() {
        var from = new PhysicalSize(10, 10);
        assertAll(
                () -> assertEquals(
                        PhysicalRect.of(4, 0, 4, 6),
                        ScalableRasters.scaled(PhysicalRect.of(2, 0, 2, 3), from, new PhysicalSize(20, 20))),
                () -> assertEquals(
                        PhysicalRect.of(1, 1, 2, 2),
                        ScalableRasters.scaled(PhysicalRect.of(3, 3, 3, 3), from, new PhysicalSize(5, 5))),
                () -> assertEquals(
                        PhysicalRect.of(4, 4, 1, 1),
                        ScalableRasters.scaled(PhysicalRect.of(9, 9, 1, 1), from, new PhysicalSize(5, 5))));
    }

    @Test
    @DisplayName("keeps no more bytes than it is given, dropping the least recently drawn")
    void bounded() {
        var rasters = new ScalableRasters(3 * 4 * 20 * 20);
        var document = new Grey();
        var natural = document.render(new PhysicalSize(10, 10));
        for (var side = 11; side <= 20; side++) {
            rasters.draw(natural, document, natural.bounds(), side, side);
        }
        assertTrue(rasters.held() <= 3 * 4 * 20 * 20, "held " + rasters.held());
        var before = document.renders.get();
        rasters.draw(natural, document, natural.bounds(), 20, 20);
        assertEquals(before, document.renders.get(), "the most recent size is still kept");
        rasters.draw(natural, document, natural.bounds(), 11, 11);
        assertEquals(before + 1, document.renders.get(), "the oldest was dropped");
    }

    @Test
    @DisplayName("answers the image itself for its own size, or a rectangle with no area")
    void itself() {
        var rasters = new ScalableRasters(ScalableRasters.DEFAULT_CAPACITY);
        var document = new Grey();
        var natural = document.render(new PhysicalSize(10, 10));
        var renders = document.renders.get();
        assertAll(
                () -> assertSame(
                        natural,
                        rasters.draw(natural, document, natural.bounds(), 10.2, 9.8)
                                .image()),
                () -> assertSame(
                        natural,
                        rasters.draw(natural, document, natural.bounds(), 0, 5).image()),
                () -> assertEquals(renders, document.renders.get()));
    }

    @Test
    @DisplayName("shares its arrivals with the stylesheet's pictures, and tells the listeners")
    void arrivals() {
        var heard = new AtomicInteger();
        try (var _ = PictureArrivals.onArrival(heard::incrementAndGet)) {
            var before = PictureArrivals.generation();
            PictureArrivals.arrived();
            assertAll(
                    () -> assertEquals(before + 1, PictureArrivals.generation()),
                    () -> assertEquals(PictureArrivals.generation(), StyleImages.generation()),
                    () -> assertEquals(1, heard.get()));
        }
    }
}
