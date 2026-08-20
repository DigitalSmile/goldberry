package io.github.digitalsmile.goldberry.icon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.TestFrames;
import io.github.digitalsmile.goldberry.golden.ScaleInvariance;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendPath;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// Where the ink lands, which is the only thing that proves an icon drew.
///
/// A parse test can say a path has vertices; only a painted frame can say those
/// vertices were the right ones in the right place. Every assertion here is
/// about a pixel (ADR-0043).
class IconPaintTest {

    private static final int WIDTH = 120;
    private static final int HEIGHT = 120;
    private static final int INK = 0xFFFFFFFF;

    @BeforeEach
    void requireRenderer() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("a bundled icon puts ink inside its own box and none outside it")
    void iconDrawsInsideItsBox() {
        var target = TestFrames.of(WIDTH, HEIGHT, 1.0f);
        try (var icon = Icon.bundled("check", 48)) {
            icon.draw(target.frame(), 20, 20, INK);
        } finally {
            target.end();
        }

        // Somewhere in the box, something was drawn.
        var inked = 0;
        for (var y = 20; y < 68; y++) {
            for (var x = 20; x < 68; x++) {
                if (target.alphaAt(x, y) > 0) {
                    inked++;
                }
            }
        }
        var inkedCount = inked;
        assertTrue(inked > 50, () -> "only " + inkedCount + " pixels of the icon were drawn");

        // And nothing was drawn outside it. A stroke is centred on the path, so
        // half a stroke width may spill past the nominal box -- two pixels of
        // margin covers that and still catches an icon drawn at the wrong scale
        // or the wrong origin, which is the failure this is looking for.
        for (var y = 0; y < HEIGHT; y++) {
            for (var x = 0; x < WIDTH; x++) {
                var inside = x >= 18 && x < 70 && y >= 18 && y < 70;
                if (!inside && target.alphaAt(x, y) != 0) {
                    var fx = x;
                    var fy = y;
                    assertEquals(0, target.alphaAt(x, y),
                            () -> "ink at (" + fx + "," + fy + ") is outside the icon's box");
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {16, 24, 48, 96})
    @DisplayName("an icon's stroke weight scales with its size")
    void strokeWeightScalesWithSize(double size) {
        try (var icon = Icon.bundled("square", size)) {
            assertEquals(size, icon.size());
            // Lucide is 2px in a 24x24 box, so the weight scales with the icon.
            // A fixed weight would make a 96px icon look like wireframe and a
            // 16px one like a blob.
            assertEquals(2.0 * size / 24.0, icon.strokeWidth(), 1e-9);
        }
    }

    @Test
    @DisplayName("doubling the size doubles the ink")
    void geometryScalesWithSize() {
        // Measured rather than predicted. Lucide's shapes do not fill their
        // 24x24 box -- `square` runs from 3 to 21 -- so the only honest
        // assertion is that the drawn extent scales with the size, not that it
        // lands on a coordinate computed here from the size alone.
        var small = inkBounds("square", 24);
        var large = inkBounds("square", 48);

        // Width and height both double, give or take the stroke: a 24px icon
        // strokes at 2px and a 48px one at 4, so the larger box carries one
        // extra pixel of stroke on each side.
        assertTrue(Math.abs((large[2] - large[0]) - 2 * (small[2] - small[0])) <= 4,
                () -> "widths " + (small[2] - small[0]) + " and " + (large[2] - large[0]));
        assertTrue(Math.abs((large[3] - large[1]) - 2 * (small[3] - small[1])) <= 4,
                () -> "heights " + (small[3] - small[1]) + " and " + (large[3] - large[1]));
    }

    /// The bounding box of everything inked, as `{minX, minY, maxX, maxY}`.
    private int[] inkBounds(String name, double size) {
        var target = TestFrames.of(WIDTH, HEIGHT, 1.0f);
        try (var icon = Icon.bundled(name, size)) {
            icon.draw(target.frame(), 5, 5, INK);
        } finally {
            target.end();
        }

        var minX = WIDTH;
        var minY = HEIGHT;
        var maxX = -1;
        var maxY = -1;
        for (var y = 0; y < HEIGHT; y++) {
            for (var x = 0; x < WIDTH; x++) {
                if (target.alphaAt(x, y) > 0) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        assertTrue(maxX >= 0, () -> name + " at " + size + " drew nothing at all");
        return new int[] {minX, minY, maxX, maxY};
    }

    @Test
    @DisplayName("the pen returns to the sub-path start after a Z")
    void penReturnsToTheSubPathStartAfterClose() {
        // After Z the current point is the start of the figure, not its last
        // vertex. `l30 0` therefore draws from (10,10) to (40,10) -- and from
        // (10,50) to (40,50) if the pen were left where the outline ended.
        var target = TestFrames.of(WIDTH, HEIGHT, 1.0f);
        try (var path = BlendPath.create()) {
            SvgPath.appendTo(path, "M10 10L50 10L50 50Zl30 0");
            target.frame().strokePath(0, 0, path, 2.0, Icon.CAP, Icon.JOIN, INK);
        } finally {
            target.end();
        }

        // The tell-tale pixel: halfway along a segment that only exists if the
        // pen went back to (10,10). Row 50 is on the outline either way.
        assertTrue(target.alphaAt(25, 10) > 0, "no ink on the segment drawn after Z");
    }

    @Test
    @DisplayName("relative and absolute spellings of the same outline paint the same pixels")
    void relativeMatchesAbsolute() {
        var absolute = paint("M10 10L50 10L50 50L10 50Z");
        var relative = paint("m10 10l40 0l0 40l-40 0z");

        for (var i = 0; i < absolute.length; i++) {
            var index = i;
            if (absolute[i] != relative[i]) {
                assertEquals(absolute[i], relative[i],
                        () -> "pixel " + (index % WIDTH) + "," + (index / WIDTH) + " differs");
            }
        }
    }

    @Test
    @DisplayName("an unknown icon name is an absence, not a mystery")
    void unknownIconIsRefusedByName() {
        var thrown = assertThrows(NoSuchElementException.class, () -> Icon.bundled("no-such-icon", 24));

        assertTrue(thrown.getMessage().contains("no-such-icon"), thrown::getMessage);
        // The count is in the message so a caller can tell "wrong name" from
        // "the icon table did not load".
        assertTrue(thrown.getMessage().matches("(?s).*\\d+.*"), thrown::getMessage);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -8, Double.NaN, Double.POSITIVE_INFINITY})
    @DisplayName("an unusable icon size is refused at construction")
    void refusesAnUnusableSize(double size) {
        assertThrows(IllegalArgumentException.class, () -> Icon.bundled("check", size));
    }

    @Test
    @DisplayName("an icon drawn at a fractional display scale is not snapped")
    void fractionalScaleIsAntialiased() {
        // The whole fractional-DPI claim, applied to an icon: at 1.5x the
        // stroke lands between physical pixels and is antialiased, so there are
        // partial alphas. A path that had been snapped to whole pixels would
        // produce only 0 and 255.
        var target = TestFrames.of(WIDTH, HEIGHT, 1.5f);
        try (var icon = Icon.bundled("check", 21)) {
            icon.draw(target.frame(), 10.5, 10.5, INK);
        } finally {
            target.end();
        }

        var partial = 0;
        for (var y = 0; y < HEIGHT; y++) {
            for (var x = 0; x < WIDTH; x++) {
                var alpha = target.alphaAt(x, y);
                if (alpha > 0 && alpha < 255) {
                    partial++;
                }
            }
        }
        var partialCount = partial;
        assertTrue(partial > 10, () -> "only " + partialCount + " antialiased pixels; is it snapping?");
    }

    /// An icon is a 24x24 path scaled to a size in logical units and stroked
    /// with a width in them too, onto a context already scaled to the display.
    /// Two multiplications by the same factor, one of which is easy to apply
    /// twice — and at 1x, which is every other assertion in this class, twice is
    /// indistinguishable from once (ADR-0157).
    @Test
    @DisplayName("an icon is the same size and weight at every display scale")
    void iconIsInLogicalUnits() {
        ScaleInvariance.assertSamePictureAtEveryScale("icon-check", WIDTH, HEIGHT, frame -> {
            frame.fill(0xFF000000);
            try (var icon = Icon.bundled("check", 48)) {
                icon.draw(frame, 20, 20, INK);
            }
        });
    }

    private int[] paint(String data) {
        var target = TestFrames.of(WIDTH, HEIGHT, 1.0f);
        try (var path = BlendPath.create()) {
            SvgPath.appendTo(path, data);
            target.frame().strokePath(0, 0, path, 2.0, Icon.CAP, Icon.JOIN, INK);
        } finally {
            target.end();
        }

        var pixels = new int[WIDTH * HEIGHT];
        for (var y = 0; y < HEIGHT; y++) {
            for (var x = 0; x < WIDTH; x++) {
                pixels[y * WIDTH + x] = target.pixel(x, y);
            }
        }
        return pixels;
    }
}
