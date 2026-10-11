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

/// `+`, `~` and `:has()` through the element tree's style cache: a change to
/// one element restyles the siblings after it and the elements above it that
/// read it, and nothing waits a frame for it.
class SiblingAndHasRestyleTest {

    private record Node(String name, boolean checked, List<Widget> children) implements Widget.Leaf, Styled, Paints {

        Node(String name, Widget... children) {
            this(name, false, List.of(children));
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
        public boolean isChecked() {
            return checked;
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

    private WidgetRenderer renderer(String css) {
        return new WidgetRenderer(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, css)), font);
    }

    @Test
    @DisplayName("hovering an element restyles the sibling placed after it")
    void nextSibling() {
        var tree = new ElementTree(new Node("root", new Node("a"), new Node("b")));
        var renderer = renderer(".a:hover + .b { opacity: 0.5 }");
        assertEquals(1.0, renderer.render(tree).children().get(1).opacity());

        var a = tree.root().children().getFirst();
        a.setPseudoClass(PseudoClass.HOVER, true);
        assertEquals(0.5, renderer.render(tree).children().get(1).opacity());
        a.setPseudoClass(PseudoClass.HOVER, false);
        assertEquals(1.0, renderer.render(tree).children().get(1).opacity());
    }

    @Test
    @DisplayName("a sibling's subtree follows when the rule reads it in an ancestor position")
    void siblingSubtree() {
        var tree = new ElementTree(new Node("root", new Node("a"), new Node("b", new Node("c"))));
        var renderer = renderer(".a:hover ~ .b .c { opacity: 0.5 }");
        assertEquals(
                1.0,
                renderer.render(tree).children().get(1).children().getFirst().opacity());

        tree.root().children().getFirst().setPseudoClass(PseudoClass.HOVER, true);
        assertEquals(
                0.5,
                renderer.render(tree).children().get(1).children().getFirst().opacity());
    }

    @Test
    @DisplayName("hovering a descendant restyles the ancestor whose :has() reads it")
    void hasAbove() {
        var tree = new ElementTree(new Node("root", new Node("mid", new Node("leaf"))));
        var renderer = renderer(".root:has(.leaf:hover) { opacity: 0.5 }");
        assertEquals(1.0, renderer.render(tree).opacity());

        var leaf = tree.root().children().getFirst().children().getFirst();
        leaf.setPseudoClass(PseudoClass.HOVER, true);
        assertEquals(0.5, renderer.render(tree).opacity());
        leaf.setPseudoClass(PseudoClass.HOVER, false);
        assertEquals(1.0, renderer.render(tree).opacity());
    }

    @Test
    @DisplayName("a state the widget owns is seen by :has() in the frame it changes")
    void mirroredStateSameFrame() {
        var tree = new ElementTree(new Node("root", false, List.of(new Node("box", true, List.of()))));
        var renderer = renderer(".root:has(:checked) { opacity: 0.5 }");
        assertEquals(0.5, renderer.render(tree).opacity(), "checked on the first frame");

        tree.update(new Node("root", false, List.of(new Node("box", false, List.of()))));
        tree.flush();
        assertEquals(1.0, renderer.render(tree).opacity(), "unchecked on the frame that unchecks it");
    }

    @Test
    @DisplayName("a child added below restyles the ancestor whose :has() reads it")
    void childAdded() {
        var tree = new ElementTree(new Node("root", new Node("a")));
        var renderer = renderer(".root:has(> .extra) { opacity: 0.5 }");
        assertEquals(1.0, renderer.render(tree).opacity());

        tree.update(new Node("root", new Node("a"), new Node("extra")));
        tree.flush();
        assertEquals(0.5, renderer.render(tree).opacity());

        tree.update(new Node("root", new Node("a")));
        tree.flush();
        assertEquals(1.0, renderer.render(tree).opacity());
    }

    @Test
    @DisplayName("what is below the :has() element follows it when the rule reads it in an ancestor position")
    void hasSubtree() {
        var tree = new ElementTree(new Node("root", new Node("other"), new Node("mid", new Node("leaf"))));
        var renderer = renderer(".root:has(.leaf:hover) .other { opacity: 0.5 }");
        assertEquals(1.0, renderer.render(tree).children().getFirst().opacity());

        tree.root().children().get(1).children().getFirst().setPseudoClass(PseudoClass.HOVER, true);
        assertEquals(0.5, renderer.render(tree).children().getFirst().opacity());
    }

    @Test
    @DisplayName("hovering an element restyles the sibling before it whose :has(+ …) reads it")
    void hasNextSibling() {
        var tree = new ElementTree(new Node("root", new Node("a"), new Node("b")));
        var renderer = renderer(".a:has(+ .b:hover) { opacity: 0.5 }");
        assertEquals(1.0, renderer.render(tree).children().getFirst().opacity());

        tree.root().children().get(1).setPseudoClass(PseudoClass.HOVER, true);
        assertEquals(0.5, renderer.render(tree).children().getFirst().opacity());
    }
}
