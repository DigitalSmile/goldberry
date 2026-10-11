package dev.goldberry.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.assets.BundledFont;
import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.layout.Length;
import dev.goldberry.paint.Box;
import dev.goldberry.text.font.Font;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// A viewport unit as the renderer resolves it: against the window it was
/// told about, and again when the window changes.
class ViewportUnitsTest {

    private record Node(String name) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "node";
        }

        @Override
        public Set<String> classes() {
            return Set.of(name);
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style);
        }
    }

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

    @Test
    @DisplayName("a vw box is sized from the window, and resized with it")
    void resizes() {
        var renderer = new WidgetRenderer(
                List.of(Stylesheet.parse(CascadeLayer.APPLICATION, ".a { width: 50vw; height: calc(10vh + 1px) }")),
                font);
        var tree = new ElementTree(new Node("a"));

        renderer.viewport(800, 600);
        var first = renderer.render(tree);
        assertEquals(Length.points(400), first.width());
        assertEquals(Length.points(61), first.height());

        var before = renderer.resolver();
        renderer.viewport(1000, 600);
        assertNotSame(before, renderer.resolver());
        assertEquals(Length.points(500), renderer.render(tree).width());
    }

    @Test
    @DisplayName("a sheet without viewport units keeps its styles through a resize")
    void keepsOthers() {
        var renderer =
                new WidgetRenderer(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, ".a { width: 50% }")), font);
        var tree = new ElementTree(new Node("a"));
        renderer.viewport(800, 600);
        renderer.render(tree);
        var before = renderer.resolver();
        renderer.viewport(1000, 700);
        assertSame(before, renderer.resolver());
    }

    @Test
    @DisplayName("ch is measured in the renderer's font")
    void chInTheFont() {
        var renderer =
                new WidgetRenderer(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, ".a { width: 10ch }")), font);
        var box = renderer.render(new ElementTree(new Node("a")));
        var expected = (float) (10
                * font.widthOf("0")
                / font.size()
                * ComputedStyle.INITIAL.typography().size());
        assertEquals(expected, ((Length.Points) box.width()).value(), 1e-3);
    }
}
