package dev.goldberry.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import dev.goldberry.css.cascade.ScopedRules;
import dev.goldberry.css.parse.CssParser;
import dev.goldberry.paint.Box;
import dev.goldberry.text.font.Font;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.StyleScope;
import dev.goldberry.widget.style.Styled;

/// `StyleScope` in the element tree: what reaches the nodes under it, what does
/// not reach anything else, and what a rebuild with other rules restyles.
@DisplayName("StyleScope")
class StyleScopeTest {

    private record Group(List<Widget> children, Attributes attributes) implements Widget.Leaf, Styled, Paints {

        @Override
        public List<Widget> children() {
            return children;
        }

        @Override
        public Set<String> classes() {
            return attributes.classes();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            return Box.of().children(boxes.toArray(Box[]::new)).style(style);
        }
    }

    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;
    private static final int GREEN = 0xFF008000;

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

    private static Group group(String name, Widget... children) {
        return new Group(List.of(children), new Attributes(null, Set.of(name), null));
    }

    private static ScopedRules rules(String css) {
        return ScopedRules.of(CssParser.parse(css));
    }

    /// Two groups classed `x` side by side, one of them under a scope.
    private static Widget page(ScopedRules scoped) {
        return group("page", new StyleScope(scoped, group("x")), group("x"));
    }

    @Test
    @DisplayName("styles the nodes under it, over the window's own rule, and nothing beside it")
    void reachesItsSubtreeOnly() {
        var renderer = renderer(".page .x { background: red }");
        var tree = new ElementTree(page(rules(".x { background: blue }")));

        var root = renderer.render(tree);

        assertEquals(BLUE, root.children().get(0).background(), "the scoped rule loses to a window rule");
        assertEquals(RED, root.children().get(1).background(), "the scoped rule escaped its subtree");
    }

    @Test
    @DisplayName("is a composition node: no box of its own, and a descendant selector sees through it")
    void addsNoBox() {
        var renderer = renderer(".page .x { background: red } .page > .x { background: blue }");
        var tree = new ElementTree(page(ScopedRules.NONE));

        var root = renderer.render(tree);

        assertEquals(2, root.children().size());
        assertEquals(RED, root.children().get(0).background());
        assertEquals(BLUE, root.children().get(1).background(), "the unscoped one is the page's child");
    }

    @Test
    @DisplayName("restyles its subtree when it is rebuilt with other rules, though no class changed")
    void rebuiltWithOtherRules() {
        var renderer = renderer("");
        var tree = new ElementTree(page(rules(".x { background: blue }")));
        assertEquals(BLUE, renderer.render(tree).children().getFirst().background());

        tree.update(page(rules(".x { background: green }")));

        assertEquals(GREEN, renderer.render(tree).children().getFirst().background());
    }

    @Test
    @DisplayName("answers no scopes in a tree that mounts none, and its own rules nearest first under two")
    void scopesNearestFirst() {
        var outer = rules(".x { color: red }");
        var inner = rules(".x { color: blue }");
        var tree = new ElementTree(new StyleScope(outer, new StyleScope(inner, group("x"))));
        var leaf = tree.root().children().getFirst().children().getFirst();

        assertEquals(List.of(inner, outer), leaf.scopes());
        assertTrue(new ElementTree(group("x")).root().scopes().isEmpty());
    }
}
