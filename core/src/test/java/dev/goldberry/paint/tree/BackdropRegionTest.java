package dev.goldberry.paint.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.paint.Clip;
import dev.goldberry.paint.filter.EdgeExtension;
import dev.goldberry.render.model.LogicalSize;

/// The part of a raster a backdrop is drawn again in: the box out by what its
/// filters read, cut to the raster and to what can be seen.
@DisplayName("a backdrop's region")
class BackdropRegionTest {

    private static final LogicalSize RASTER = new LogicalSize(200, 100);
    private static final double[] TWELVE = {12, 12, 12, 12};

    @Test
    @DisplayName("is the box out by the reach, on whole pixels, inside the raster")
    void inside() {
        var region = BackdropRegion.of(Clip.of(50.5, 30, 40, 20), TWELVE, RASTER, Clip.NONE, 1);
        assertNotNull(region);
        assertEquals(new BackdropRegion(38, 18, 103, 62, EdgeExtension.NONE), region);
    }

    @Test
    @DisplayName("is cut at the raster's edge, which it repeats instead, in physical pixels")
    void atTheEdge() {
        var region = BackdropRegion.of(Clip.of(0, 90, 40, 10), TWELVE, RASTER, Clip.NONE, 2);
        assertNotNull(region);
        assertEquals(0, region.left());
        assertEquals(100, region.bottom());
        assertEquals(new EdgeExtension(24, 0, 0, 24), region.edges());
    }

    @Test
    @DisplayName("is cut to what can be seen, out by the reach")
    void visible() {
        var region = BackdropRegion.of(Clip.of(50, 30, 100, 20), TWELVE, RASTER, Clip.of(0, 0, 80, 100), 1);
        assertNotNull(region);
        assertEquals(92, region.right());
        assertEquals(EdgeExtension.NONE, region.edges(), "what is cut there exists, it is only not needed");
    }

    @Test
    @DisplayName("is nothing for a box out of sight")
    void outOfSight() {
        assertNull(BackdropRegion.of(Clip.of(50, 30, 40, 20), TWELVE, RASTER, Clip.of(150, 0, 50, 100), 1));
        assertNull(BackdropRegion.of(Clip.of(300, 30, 40, 20), TWELVE, RASTER, Clip.NONE, 1));
        assertNull(BackdropRegion.of(Clip.of(50, 30, 0, 20), TWELVE, RASTER, Clip.NONE, 1));
    }
}
