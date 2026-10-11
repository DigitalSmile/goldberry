package dev.goldberry.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
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
import dev.goldberry.input.PointerRouter;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.paint.Box;
import dev.goldberry.text.font.Font;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// `display: none` and `display: contents`, as the renderer and the router see
/// them.
class DisplayTest {

    /// A painted, focusable node with an id, whose box carries its id as owner
    /// tag so the output can be read back by name.
    private record Node(String name, List<Widget> children) implements Widget.Leaf, Styled, Paints, Handles {

        Node(String name, Widget... children) {
            this(name, List.of(children));
        }

        @Override
        public String cssType() {
            return "node";
        }

        @Override
        public String id() {
            return name;
        }

        @Override
        public Set<String> classes() {
            return Set.of(name);
        }

        @Override
        public boolean isFocusable() {
            return true;
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

    /// `root( a( a1 a2 ) b )`, rendered once under `css`.
    private ElementTree tree(String css) {
        var tree = new ElementTree(new Node("root", new Node("a", new Node("a1"), new Node("a2")), new Node("b")));
        new WidgetRenderer(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, css)), font).render(tree);
        return tree;
    }

    private Box rendered(String css) {
        var tree = new ElementTree(new Node("root", new Node("a", new Node("a1"), new Node("a2")), new Node("b")));
        return new WidgetRenderer(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, css)), font).render(tree);
    }

    private static List<String> names(List<Box> boxes) {
        var names = new ArrayList<String>();
        for (var box : boxes) {
            names.add(((Element) box.owner()).id());
        }
        return names;
    }

    private static List<String> tabbing(ElementTree tree, int steps) {
        var router = new PointerRouter();
        router.focusRoot(tree.root());
        var visited = new ArrayList<String>();
        for (var i = 0; i < steps; i++) {
            router.moveFocus(1);
            visited.add(router.focused() == null ? "(none)" : router.focused().id());
        }
        return visited;
    }

    @Test
    @DisplayName("display: none takes the node and its subtree out of the boxes")
    void noneHasNoBox() {
        var root = rendered(".a { display: none }");
        assertEquals(List.of("b"), names(root.children()));
    }

    @Test
    @DisplayName("display: none keeps the elements mounted, and out of the focus order")
    void noneIsNotFocusable() {
        var tree = tree(".a { display: none }");
        assertTrue(tree.root().children().getFirst().isMounted(), "the element is kept");
        assertTrue(tree.root().children().getFirst().isDisplayNone());
        var visited = tabbing(tree, 4);
        assertTrue(visited.stream().noneMatch(id -> id.startsWith("a")), visited::toString);
    }

    @Test
    @DisplayName("display: contents hands its children to its parent in its place")
    void contents() {
        var root = rendered(".a { display: contents }");
        assertEquals(List.of("a1", "a2", "b"), names(root.children()));
    }

    @Test
    @DisplayName("display: flex is every box, as before")
    void flex() {
        var root = rendered(".a { display: flex }");
        assertEquals(List.of("a", "b"), names(root.children()));
    }
}
