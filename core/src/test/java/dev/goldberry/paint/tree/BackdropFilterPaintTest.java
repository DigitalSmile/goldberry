package dev.goldberry.paint.tree;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.parse.CssParser;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.layout.Length;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.Frame;
import dev.goldberry.render.DamageRect;
import dev.goldberry.render.GpuContent;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.model.DisplayScale;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.render.model.PixelFormat;
import dev.goldberry.render.window.GpuSurface;

/// `backdrop-filter`, drawn: what is under the box is filtered inside the box's
/// rounded border and nowhere else, at the box's opacity, from what the tree
/// draws under it rather than from what the frame happens to hold.
@DisplayName("backdrop-filter")
class BackdropFilterPaintTest {

    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int SIZE = 100;
    private static final GpuContent LAYER = new GpuContent() {};

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static ComputedStyle style(String css) {
        var map = new LinkedHashMap<String, List<Token>>();
        for (var declaration : CssParser.parse("x { " + css + " }").getFirst().declarations()) {
            map.put(declaration.property(), declaration.value());
        }
        return ComputedStyle.of(Map.copyOf(map), CssLength.Context.DEFAULT);
    }

    /// Black stripes two pixels wide every four, on white, filling the box.
    private static Box stripes(String css) {
        return Box.of().style(style(css)).painting((frame, size) -> {
            frame.fillRect(0, 0, size.width(), size.height(), WHITE);
            for (var x = 0; x < size.width(); x += 4) {
                frame.fillRect(x, 0, 2, size.height(), BLACK);
            }
        });
    }

    /// A 40×40 box at (30, 30) styled `css`.
    private static Box frosted(String css) {
        return Box.of().style(style("position: absolute; left: 30px; top: 30px; width: 40px; height: 40px; " + css));
    }

    /// The page: stripes all over it and `over` on top.
    private static Box page(Box... over) {
        var children = new Box[over.length + 1];
        children[0] = stripes("position: absolute; left: 0; top: 0; width: 100px; height: 100px");
        System.arraycopy(over, 0, children, 1, over.length);
        return Box.filled(WHITE).size(Length.points(SIZE), Length.points(SIZE)).children(children);
    }

    private static PixelBuffer buffer(int size) {
        return PixelBuffer.allocate(new PhysicalSize(size, size), PixelFormat.BGRA32_PREMULTIPLIED);
    }

    private static int pixel(PixelBuffer buffer, int x, int y) {
        return buffer.pixels().duplicate().order(ByteOrder.LITTLE_ENDIAN).getInt(y * buffer.stride() + x * 4);
    }

    private static int[] pixels(PixelBuffer buffer) {
        var size = buffer.size();
        var all = new int[size.width() * size.height()];
        for (var y = 0; y < size.height(); y++) {
            for (var x = 0; x < size.width(); x++) {
                all[y * size.width() + x] = pixel(buffer, x, y);
            }
        }
        return all;
    }

    /// `root` painted whole into a new buffer at `scale` with `threads` workers.
    private static PixelBuffer paint(Box root, float scale, int threads) {
        var buffer = buffer((int) (SIZE * scale));
        var frame = Frame.over(buffer, new DisplayScale(scale), threads);
        try (var tree = RenderTree.create()) {
            tree.update(frame, root);
            tree.paint(frame);
        } finally {
            frame.end();
        }
        return buffer;
    }

    private static PixelBuffer paint(Box root) {
        return paint(root, 1f, 0);
    }

    private static int green(int argb) {
        return (argb >>> 8) & 0xFF;
    }

    private static boolean sharp(int argb) {
        return argb == BLACK || argb == WHITE;
    }

    @Nested
    @DisplayName("over what is drawn under the box")
    class Over {

        @Test
        @DisplayName("blurs the stripes to grey inside the box and leaves them sharp outside it")
        void blursInside() {
            var buffer = paint(page(frosted("backdrop-filter: blur(4px)")));
            var middle = pixel(buffer, 50, 50);
            assertTrue(Math.abs(green(middle) - 0x80) < 0x20, Integer.toHexString(middle));
            assertTrue(sharp(pixel(buffer, 20, 50)), "left of the box");
            assertTrue(sharp(pixel(buffer, 50, 80)), "below it");
        }

        @Test
        @DisplayName("is the stripes as they are without one")
        void withoutOne() {
            var buffer = paint(page(frosted("")));
            assertTrue(sharp(pixel(buffer, 50, 50)));
        }

        @Test
        @DisplayName("draws the box's own background over the frost")
        void backgroundOver() {
            var buffer = paint(page(frosted("backdrop-filter: blur(4px); background: rgb(255 0 0 / 50%)")));
            var middle = pixel(buffer, 50, 50);
            assertTrue(((middle >>> 16) & 0xFF) > 0xB0, "red over grey: " + Integer.toHexString(middle));
            assertTrue(Math.abs(green(middle) - 0x40) < 0x20, Integer.toHexString(middle));
        }

        @Test
        @DisplayName("is faded with the box's opacity")
        void opacity() {
            var whole = pixel(paint(page(frosted("backdrop-filter: blur(4px)"))), 50, 50);
            var half = pixel(paint(page(frosted("backdrop-filter: blur(4px); opacity: 0.5"))), 50, 50);
            // (50, 50) is on a white stripe: half of the grey over it.
            assertEquals((0xFF + green(whole)) / 2.0, green(half), 3, Integer.toHexString(half));
        }

        @Test
        @DisplayName("keeps to the box's rounded corners")
        void rounded() {
            var buffer = paint(page(frosted("backdrop-filter: blur(4px); border-radius: 20px")));
            assertTrue(sharp(pixel(buffer, 31, 31)), "the corner the curve cuts off");
            assertTrue(!sharp(pixel(buffer, 50, 31)), "the top edge between the corners");
        }

        @Test
        @DisplayName("follows the box's transform")
        void rotated() {
            var buffer = paint(page(frosted("backdrop-filter: blur(4px); transform: rotate(45deg)")));
            assertTrue(!sharp(pixel(buffer, 50, 50)), "the middle of the diamond");
            assertTrue(sharp(pixel(buffer, 32, 32)), "a corner of the box the turn moved away");
        }

        @Test
        @DisplayName("applies the colour functions too")
        void colour() {
            var buffer = paint(page(frosted("backdrop-filter: invert(1)")));
            assertEquals(BLACK, pixel(buffer, 50, 50), "a white stripe, inverted");
            assertTrue(sharp(pixel(buffer, 20, 50)), "outside, as it was");
            assertEquals(WHITE, pixel(buffer, 48, 50), "a black stripe, inverted");
        }

        @Test
        @DisplayName("is the same at twice the scale")
        void doubled() {
            var buffer = paint(page(frosted("backdrop-filter: blur(4px)")), 2f, 0);
            assertTrue(Math.abs(green(pixel(buffer, 100, 100)) - 0x80) < 0x20);
            assertTrue(sharp(pixel(buffer, 40, 100)));
        }

        @Test
        @DisplayName("is the same with the rasterizer's workers")
        void threaded() {
            var tree = page(frosted("backdrop-filter: blur(4px); border-radius: 8px"));
            assertArrayEquals(pixels(paint(tree, 1f, 0)), pixels(paint(tree, 1f, 4)));
        }
    }

    @Nested
    @DisplayName("in a tree with rasters of its own")
    class Rasters {

        @Test
        @DisplayName("reads what a group under it drew, and leaves the group's raster alone")
        void groupUnder() {
            var group = Box.of()
                    .style(style(
                            "position: absolute; left: 0; top: 0; width: 100px; height: 100px; isolation: isolate"))
                    .children(stripes("width: 100px; height: 100px"));
            var root = Box.filled(WHITE)
                    .size(Length.points(SIZE), Length.points(SIZE))
                    .children(group, frosted("backdrop-filter: blur(4px)"));
            var buffer = buffer(SIZE);
            try (var tree = RenderTree.create()) {
                for (var pass = 0; pass < 2; pass++) {
                    var frame = Frame.over(buffer, DisplayScale.ONE, 0);
                    try {
                        tree.update(frame, root);
                        tree.paint(frame);
                    } finally {
                        frame.end();
                    }
                    assertTrue(!sharp(pixel(buffer, 50, 50)), "pass " + pass);
                    assertTrue(sharp(pixel(buffer, 20, 50)), "pass " + pass);
                }
                assertEquals(0, tree.layersRepainted(), "the group's raster is still the one it drew");
            }
        }

        @Test
        @DisplayName("inside a group, reads what the group drew under it")
        void insideAGroup() {
            var group = Box.of()
                    .style(style(
                            "position: absolute; left: 0; top: 0; width: 100px; height: 100px; isolation: isolate"))
                    .children(
                            stripes("position: absolute; left: 0; top: 0; width: 100px; height: 100px"),
                            frosted("backdrop-filter: blur(4px)"));
            var buffer = paint(Box.filled(WHITE)
                    .size(Length.points(SIZE), Length.points(SIZE))
                    .children(group));
            assertTrue(Math.abs(green(pixel(buffer, 50, 50)) - 0x80) < 0x20);
            assertTrue(sharp(pixel(buffer, 20, 50)));
        }

        @Test
        @DisplayName("inside a rounded clip, reads what the clip's children drew under it")
        void insideARoundedClip() {
            var clip = Box.of()
                    .style(style("position: absolute; left: 0; top: 0; width: 100px; height: 100px;"
                            + " overflow: hidden; border-radius: 12px"))
                    .children(
                            stripes("position: absolute; left: 0; top: 0; width: 100px; height: 100px"),
                            frosted("backdrop-filter: blur(4px)"));
            var buffer = paint(Box.filled(WHITE)
                    .size(Length.points(SIZE), Length.points(SIZE))
                    .children(clip));
            assertTrue(Math.abs(green(pixel(buffer, 50, 50)) - 0x80) < 0x20);
            assertTrue(sharp(pixel(buffer, 20, 50)));
        }
    }

    @Nested
    @DisplayName("in a partial repaint")
    class Partial {

        /// The page with a 4px bar of `colour` just left of the frosted box,
        /// outside it but inside the blur's reach.
        private static Box scene(String colour) {
            var bar = Box.of()
                    .style(style("position: absolute; left: 24px; top: 40px; width: 4px; height: 20px; background: "
                            + colour));
            return page(bar, frosted("backdrop-filter: blur(4px)"));
        }

        @Test
        @DisplayName("repaints the whole box when what changed is within the blur's reach of it")
        void widened() {
            var buffer = buffer(SIZE);
            try (var tree = RenderTree.create()) {
                var first = Frame.over(buffer, DisplayScale.ONE, 0);
                tree.update(first, scene("red"));
                tree.damage(first);
                tree.paint(first);
                first.end();

                var second = Frame.over(buffer, DisplayScale.ONE, 0);
                tree.update(second, scene("blue"));
                var damage = tree.damage(second);
                assertTrue(covers(damage, 31, 31) && covers(damage, 69, 69), "the frosted box: " + damage);
                tree.paint(second, damage);
                second.end();
            }
            assertArrayEquals(
                    pixels(paint(scene("blue"))),
                    pixels(buffer),
                    "the same as a whole repaint, not frost over last frame's frost");
        }

        @Test
        @DisplayName("and frosts what is under it, not what the frame held, when the damage cuts across it")
        void notFrostedTwice() {
            var buffer = buffer(SIZE);
            try (var tree = RenderTree.create()) {
                var first = Frame.over(buffer, DisplayScale.ONE, 0);
                tree.update(first, scene("red"));
                tree.paint(first);
                first.end();

                var second = Frame.over(buffer, DisplayScale.ONE, 0);
                tree.update(second, scene("red"));
                tree.paint(second, List.of(new DamageRect(40, 40, 10, 10)));
                second.end();
            }
            var whole = pixels(paint(scene("red")));
            var repainted = pixels(buffer);
            assertEquals(whole[45 * SIZE + 45], repainted[45 * SIZE + 45], "inside the damage");
        }

        @Test
        @DisplayName("damages nothing when nothing near it changed")
        void farAway() {
            try (var tree = RenderTree.create()) {
                var buffer = buffer(SIZE);
                var first = Frame.over(buffer, DisplayScale.ONE, 0);
                tree.update(first, page(frosted("backdrop-filter: blur(1px)"), corner("red")));
                tree.damage(first);
                tree.paint(first);
                first.end();

                var second = Frame.over(buffer, DisplayScale.ONE, 0);
                tree.update(second, page(frosted("backdrop-filter: blur(1px)"), corner("blue")));
                var damage = tree.damage(second);
                second.end();
                assertTrue(!covers(damage, 50, 50), "the box is not repainted for a corner: " + damage);
            }
        }

        private static Box corner(String colour) {
            return Box.of()
                    .style(style(
                            "position: absolute; left: 0; top: 0; width: 4px; height: 4px; background: " + colour));
        }

        private static boolean covers(List<DamageRect> damage, int x, int y) {
            return damage.stream()
                    .anyMatch(r -> x >= r.x() && x < r.x() + r.width() && y >= r.y() && y < r.y() + r.height());
        }
    }

    @Nested
    @DisplayName("over a GPU layer")
    class OverGpu {

        private static final int FALLBACK = 0xFF00FF00;

        private static Box scene() {
            var video = Box.of()
                    .style(style("position: absolute; left: 0; top: 0; width: 100px; height: 100px"))
                    .painting((frame, size) -> {
                        if (!frame.gpuLayer(LAYER, 0, 0, size.width(), size.height())) {
                            frame.fillRect(0, 0, size.width(), size.height(), FALLBACK);
                        }
                    });
            return Box.of()
                    .size(Length.points(SIZE), Length.points(SIZE))
                    .children(video, frosted("backdrop-filter: blur(4px); background: rgb(0 0 255 / 40%)"));
        }

        @Test
        @DisplayName("draws the box's background at full strength where the frame shows GPU layers")
        void opaque() {
            var buffer = buffer(SIZE);
            var frame = Frame.over(buffer, DisplayScale.ONE, (GpuSurface.Composited) layers -> {});
            try (var tree = RenderTree.create()) {
                tree.update(frame, scene());
                tree.paint(frame);
            } finally {
                frame.end();
            }
            assertEquals(0xFF0000FF, pixel(buffer, 50, 50), "the opaque fallback");
            assertEquals(0, pixel(buffer, 10, 10), "the layer's hole beside it");
        }

        @Test
        @DisplayName("frosts the painter's fallback where the frame shows none")
        void fallback() {
            var middle = pixel(paint(scene()), 50, 50);
            assertTrue(green(middle) > 0x80, "green under translucent blue: " + Integer.toHexString(middle));
        }
    }
}
