package dev.goldberry.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import dev.goldberry.paint.Box;
import dev.goldberry.text.font.Font;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// `text-transform` and the spacing through the renderer's paragraph seam:
/// what a widget asking for its text to be shaped is handed.
class TextTransformRenderTest {

    private final List<String> shaped = new ArrayList<>();
    private final Map<String, Double> widths = new HashMap<>();

    /// A leaf that shapes "Hello there" and records what it was given.
    private final class Label implements Widget.Leaf, Styled, Paints {

        private final String name;
        private final List<Widget> children;

        Label(String name, Widget... children) {
            this.name = name;
            this.children = List.of(children);
        }

        @Override
        public List<Widget> children() {
            return children;
        }

        @Override
        public String cssType() {
            return "label";
        }

        @Override
        public Set<String> classes() {
            return Set.of(name);
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            var paragraph = context.paragraph(style, "Hello there");
            shaped.add(name + ":" + paragraph.text());
            widths.put(name, paragraph.layout(Double.POSITIVE_INFINITY).width());
            return Box.of().style(style).children(boxes.toArray(Box[]::new));
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
    @DisplayName("a label is shaped in the case its style draws, and a child inherits it")
    void casesTheText() {
        var renderer = new WidgetRenderer(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, """
                        .outer { text-transform: uppercase }
                        .inner { text-transform: capitalize }
                        """)), font);
        renderer.render(new ElementTree(new Label("outer", new Label("child"), new Label("inner"))));
        // Children render before their parent.
        assertEquals(Set.of("outer:HELLO THERE", "child:HELLO THERE", "inner:Hello There"), Set.copyOf(shaped));
    }

    @Test
    @DisplayName("a label is shaped with its letter spacing, which makes it wider")
    void spacesTheText() {
        var renderer = new WidgetRenderer(
                List.of(Stylesheet.parse(CascadeLayer.APPLICATION, ".wide { letter-spacing: 2px }")), font);
        renderer.render(new ElementTree(new Label("plain", new Label("wide"))));
        assertEquals(widths.get("plain") + 2 * "Hello there".length(), widths.get("wide"), 0.05);
    }
}
