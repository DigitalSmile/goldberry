package dev.goldberry.css.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Border;
import dev.goldberry.css.Decoration;
import dev.goldberry.css.background.Background;
import dev.goldberry.css.background.BackgroundBox;
import dev.goldberry.css.background.BackgroundPosition;
import dev.goldberry.css.background.BackgroundRepeat;
import dev.goldberry.css.background.BackgroundRepeat.Tiling;
import dev.goldberry.css.background.BackgroundSize;
import dev.goldberry.image.Image;
import dev.goldberry.layout.Insets;
import dev.goldberry.layout.Length;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.TestFrames;
import dev.goldberry.paint.tree.RenderTree;

/// A background picture in pixels: placed by a percentage, in its origin's
/// box, cut to its clip's, and spread or stretched by `space` and `round`.
class BackgroundPlacementPaintTest {

    private static final int WHITE = 0xFFFFFFFF;

    private static final int RED = 0xFFFF0000;

    private static final int BLUE = 0xFF0000FF;

    private static final CssImage.Url SQUARE = new CssImage.Url("test/square.png");

    private static final CssImage.Url HALVES = new CssImage.Url("test/halves.png");

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        // A red 10x10 square, and a 10x10 picture red on the left, blue on the right.
        var square = Image.ofArgb(10, 10, filled(10, 10, (x, y) -> RED));
        var halves = Image.ofArgb(10, 10, filled(10, 10, (x, y) -> x < 5 ? RED : BLUE));
        Registry.use(address ->
                CompletableFuture.completedFuture(address.toString().equals(HALVES.href()) ? halves : square));
    }

    @AfterEach
    void tearDown() {
        Registry.use(null);
    }

    private interface Pixel {
        int at(int x, int y);
    }

    private static int[] filled(int width, int height, Pixel pixel) {
        var pixels = new int[width * height];
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                pixels[y * width + x] = pixel.at(x, y);
            }
        }
        return pixels;
    }

    /// A white page with one 100x40 box at (10, 10).
    private static TestFrames.Target painted(Box box) {
        var target = TestFrames.of(120, 60, 1f);
        try (var render = RenderTree.create()) {
            var page = Box.filled(WHITE).padding(Insets.all(Length.points(10))).children(box);
            // Twice: the first frame asks for the picture, the second draws it.
            render.update(target.frame(), page);
            render.paint(target.frame());
            render.update(target.frame(), page);
            render.paint(target.frame());
        }
        target.end();
        return target;
    }

    private static Box box(Background fill) {
        return Box.of().fill(fill).size(Length.points(100), Length.points(40));
    }

    private static Background picture(
            CssImage.Url url, BackgroundPosition position, BackgroundRepeat repeat, BackgroundBox origin) {
        return Background.of(
                0,
                List.of(url),
                List.of(position),
                Background.AUTO_SIZE,
                List.of(repeat),
                List.of(origin),
                Background.BORDER_BOX);
    }

    @Test
    @DisplayName("`right bottom` puts the picture in the box's corner")
    void corner() {
        var target = painted(box(picture(
                SQUARE,
                new BackgroundPosition(0, 0, 100, 100),
                BackgroundRepeat.NO_REPEAT,
                BackgroundBox.PADDING_BOX)));

        assertEquals(RED, target.pixel(105, 45));
        assertEquals(WHITE, target.pixel(15, 15));
    }

    @Test
    @DisplayName("`content-box` places it inside the padding")
    void contentBox() {
        var fill = picture(SQUARE, BackgroundPosition.ZERO, BackgroundRepeat.NO_REPEAT, BackgroundBox.CONTENT_BOX);
        var target = painted(box(fill).padding(Insets.all(Length.points(10))));

        assertEquals(WHITE, target.pixel(15, 15), "not at the box's corner");
        assertEquals(RED, target.pixel(25, 25), "but ten in");
    }

    @Test
    @DisplayName("`space` spreads whole tiles edge to edge")
    void space() {
        var fill = Background.of(
                0,
                List.of(SQUARE),
                Background.ZERO,
                List.of(new BackgroundSize(BackgroundSize.Kind.LENGTHS, Length.points(30), Length.points(10))),
                List.of(new BackgroundRepeat(Tiling.SPACE, Tiling.NO_REPEAT)),
                Background.PADDING_BOX,
                Background.BORDER_BOX);
        var target = painted(box(fill));

        assertEquals(RED, target.pixel(12, 15), "the first tile at the left edge");
        assertEquals(WHITE, target.pixel(42, 15), "a gap of five after it");
        assertEquals(RED, target.pixel(46, 15), "the second tile");
        assertEquals(RED, target.pixel(108, 15), "the last at the right edge");
    }

    @Test
    @DisplayName("`round` stretches the tile until three fit in a hundred")
    void round() {
        var fill = Background.of(
                0,
                List.of(HALVES),
                Background.ZERO,
                List.of(new BackgroundSize(BackgroundSize.Kind.LENGTHS, Length.points(30), Length.points(10))),
                List.of(new BackgroundRepeat(Tiling.ROUND, Tiling.NO_REPEAT)),
                Background.PADDING_BOX,
                Background.BORDER_BOX);
        var target = painted(box(fill));

        // A tile is 33.3 wide, red to 16.7 and blue after; a 30 tile would be
        // red again from 30.
        assertEquals(BLUE, target.pixel(10 + 31, 15));
        assertEquals(RED, target.pixel(10 + 35, 15));
    }

    @Test
    @DisplayName("`background-clip: padding-box` keeps the colour out from under the border")
    void clip() {
        var fill = Background.of(BLUE).clips(List.of(BackgroundBox.PADDING_BOX));
        var target = painted(box(fill).decoration(Decoration.NONE.border(Border.all(5, 0x00000000))));

        assertEquals(WHITE, target.pixel(12, 12), "the border's ring is the page");
        assertEquals(BLUE, target.pixel(20, 20));
        assertNotEquals(BLUE, target.pixel(108, 48));
    }
}
