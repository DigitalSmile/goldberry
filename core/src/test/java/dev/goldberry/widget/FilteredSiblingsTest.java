package dev.goldberry.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import dev.goldberry.css.select.Selector.PseudoClass;
import dev.goldberry.paint.Box;
import dev.goldberry.text.font.Font;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// `:nth-child(An+B of S)` through the element tree's style cache: a sibling's
/// state decides an element's position, so changing it restyles the element.
class FilteredSiblingsTest {

    private record Node(String name, List<Widget> children) implements Widget.Leaf, Styled, Paints {

        Node(String name, Widget... children) {
            this(name, List.of(children));
        }

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
            return Box.of().style(style).children(children.toArray(Box[]::new));
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
    @DisplayName("hovering one sibling moves another to second among those that match")
    void siblingStateRestyles() {
        var tree = new ElementTree(new Node("root", new Node("b"), new Node("a")));
        var renderer = new WidgetRenderer(
                List.of(Stylesheet.parse(CascadeLayer.APPLICATION, ".a:nth-child(2 of .a, :hover) { opacity: 0.5 }")),
                font);
        var before = renderer.render(tree);
        assertEquals(1.0, before.children().get(1).opacity(), "alone among the matching siblings, a is first");

        tree.root().children().getFirst().setPseudoClass(PseudoClass.HOVER, true);
        var after = renderer.render(tree);
        assertEquals(0.5, after.children().get(1).opacity(), "with b hovered, a is the second that matches");
    }
}
