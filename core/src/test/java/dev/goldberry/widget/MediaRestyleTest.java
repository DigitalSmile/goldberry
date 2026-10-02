package dev.goldberry.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.assets.BundledFont;
import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.paint.Box;
import dev.goldberry.render.desktop.SystemTheme;
import dev.goldberry.text.font.Font;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// `@media` through the renderer: the window's size, the desktop's theme and
/// reduced motion move the media context, and a move that flips a condition
/// restyles the next frame while one that flips nothing keeps every cache.
class MediaRestyleTest {

    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;
    private static final int GREEN = 0xFF008000; // CSS green is #008000

    private Font font;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        font = Font.bundled(BundledFont.UI, 13);
    }

    @AfterEach
    void tearDown() {
        if (font != null) {
            font.close();
        }
    }

    private WidgetRenderer renderer() {
        return new WidgetRenderer(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, """
                        group { background: red }
                        @media (max-width: 600px) { group { background: blue } }
                        @media (prefers-color-scheme: dark) and (min-width: 601px) { group { background: green } }
                        """)), font);
    }

    @Test
    @DisplayName("crossing a breakpoint restyles the next frame, and staying inside one keeps the resolver")
    void breakpoint() {
        var renderer = renderer();
        var tree = new ElementTree(new Group());

        renderer.viewport(800, 600);
        assertEquals(RED, renderer.render(tree).background());
        var wide = renderer.resolver();

        renderer.viewport(1000, 700);
        assertSame(wide, renderer.resolver(), "no breakpoint crossed, so every cached style stays good");

        renderer.viewport(500, 700);
        assertNotSame(wide, renderer.resolver());
        assertEquals(BLUE, renderer.render(tree).background());
    }

    @Test
    @DisplayName("the desktop's theme reaches prefers-color-scheme")
    void colorScheme() {
        var renderer = renderer().viewport(800, 600);
        var tree = new ElementTree(new Group());
        assertEquals(RED, renderer.render(tree).background());

        renderer.colorScheme(SystemTheme.DARK);
        assertEquals(GREEN, renderer.render(tree).background());
        assertEquals(SystemTheme.DARK, renderer.media().colorScheme());
    }

    /// A styled leaf whose CSS type is `group`.
    private record Group() implements Widget.Leaf, Styled, Paints {

        @Override
        public List<Widget> children() {
            return List.of();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            return Box.of().style(style);
        }
    }
}
