package dev.goldberry.paint;

import static dev.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.assets.BundledFont;
import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.css.value.Shadow;
import dev.goldberry.layout.Length;
import dev.goldberry.paint.cull.BoxInk;
import dev.goldberry.text.Paragraph;
import dev.goldberry.text.flow.TextFlow;
import dev.goldberry.text.font.Font;

/// `text-shadow`: what the cascade reads, what is painted, and the ink it
/// adds to a box.
class TextShadowPaintTest {

    private static final int RED = 0xFFFF0000;

    @Nested
    @DisplayName("in the cascade")
    class Cascade {

        private ComputedStyle style(String css) {
            var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, "card { color: #00ff00 } " + css);
            var root = element("card");
            root.with(element("text"));
            var resolver = new StyleResolver(List.of(sheet));
            var context = new CssLength.Context(16, 16);
            var parent = ComputedStyle.of(resolver.resolve(root), context, null);
            return ComputedStyle.of(resolver.resolve(root.descend(1)), context, parent);
        }

        @Test
        @DisplayName("a list of offset, blur and colour, the element's own colour when none is written")
        void reads() {
            var shadows = style("text { text-shadow: 1px 2px 3px red, 0 0 4px }")
                    .typography()
                    .lettering()
                    .shadows();
            assertEquals(List.of(new Shadow(1, 2, 3, 0, RED), new Shadow(0, 0, 4, 0, 0xFF00FF00)), shadows);
        }

        @Test
        @DisplayName("inherits, and none clears it")
        void inherits() {
            assertEquals(
                    1,
                    style("card { text-shadow: 1px 1px red }")
                            .typography()
                            .lettering()
                            .shadows()
                            .size());
            assertEquals(
                    List.of(),
                    style("card { text-shadow: 1px 1px red } text { text-shadow: none }")
                            .typography()
                            .lettering()
                            .shadows());
        }

        @Test
        @DisplayName("refuses a spread and inset, which a text shadow does not have")
        void refuses() {
            assertEquals(
                    List.of(),
                    style("text { text-shadow: 1px 1px 2px 3px red }")
                            .typography()
                            .lettering()
                            .shadows());
            assertEquals(
                    List.of(),
                    style("text { text-shadow: inset 1px 1px red }")
                            .typography()
                            .lettering()
                            .shadows());
        }
    }

    @Nested
    @DisplayName("painted")
    class Painted {

        private Font font;

        @BeforeEach
        void setUp() {
            RendererRequirement.enforce();
            font = Font.bundled(BundledFont.UI, 24);
        }

        @AfterEach
        void tearDown() {
            if (font != null) {
                font.close();
            }
        }

        /// Invisible text "H" with `shadows`, in a 60x50 frame at (10, 10).
        private TestFrames.Target painted(Shadow... shadows) {
            var target = TestFrames.of(60, 50, 1f);
            var paragraph = Paragraph.of(font, "H");
            var text = Box.text(paragraph, 0x00000000)
                    .text(new Box.Text(paragraph, 0x00000000, TextFlow.NORMAL, List.of(), List.of(shadows)));
            BoxPainter.paint(
                    target.frame(),
                    Box.of()
                            .padding(Length.points(10))
                            .size(Length.points(60), Length.points(50))
                            .children(text));
            target.end();
            return target;
        }

        /// The number of pixels with any coverage.
        private static int inked(TestFrames.Target target) {
            var count = 0;
            for (var y = 0; y < 50; y++) {
                for (var x = 0; x < 60; x++) {
                    if (target.alphaAt(x, y) > 0) {
                        count++;
                    }
                }
            }
            return count;
        }

        @Test
        @DisplayName("a shadow with no blur is the glyphs again, at the offset, in its colour")
        void hard() {
            var plain = inked(painted(new Shadow(0, 0, 0, 0, RED)));
            assertTrue(plain > 50, "an H's worth of red: " + plain);
            var offset = painted(new Shadow(3, 0, 0, 0, RED));
            assertEquals(plain, inked(offset), 2, "the same glyph, moved");
        }

        @Test
        @DisplayName("a blurred shadow spreads past the glyphs and softens them")
        void blurred() {
            var hard = inked(painted(new Shadow(0, 0, 0, 0, RED)));
            var soft = painted(new Shadow(0, 0, 6, 0, RED));
            assertTrue(inked(soft) > hard * 1.5, "the blur covers more: " + inked(soft) + " against " + hard);
            var partial = 0;
            for (var y = 0; y < 50; y++) {
                for (var x = 0; x < 60; x++) {
                    var alpha = soft.alphaAt(x, y);
                    if (alpha > 0 && alpha < 0xFF) {
                        partial++;
                    }
                }
            }
            assertTrue(partial > hard, "most of a blurred shadow is part-covered");
        }

        @Test
        @DisplayName("a shadow adds its offset and its blur to the box's ink")
        void ink() {
            var paragraph = Paragraph.of(font, "H");
            var box = Box.of()
                    .text(new Box.Text(
                            paragraph, RED, TextFlow.NORMAL, List.of(), List.of(new Shadow(4, -2, 2, 0, RED))));
            var ink = BoxInk.of(box, 20, 30);
            assertEquals(0, ink.left(), 1e-9, "the offset carries the blur away from the left");
            assertEquals(-5, ink.top(), 1e-9);
            assertEquals(27, ink.right(), 1e-9);
            assertEquals(31, ink.bottom(), 1e-9);
        }
    }
}
