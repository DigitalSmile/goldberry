package dev.goldberry.widget;

import static dev.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
import dev.goldberry.input.PointerRouter;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.input.hit.HitTest;
import dev.goldberry.layout.Display;
import dev.goldberry.layout.Presence;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.TestFrames;
import dev.goldberry.paint.tree.RenderTree;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.text.font.Font;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// `visibility`, `pointer-events` and `user-select`: inherited, per box, and
/// read by the painter, the hit test and the focus order.
class VisibilityTest {

    /// A focusable square that paints its background, owner-tagged by name.
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

    private static final String SHEET = """
            node { padding: 10px; background: #0000ff }
            .outer { width: 60px; height: 60px }
            .inner { width: 20px; height: 20px; background: #ff0000 }
            """;

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

    /// `outer( inner )` under `css`, painted into an 80x80 target.
    private record Painted(ElementTree tree, TestFrames.Target target, RenderTree render) {}

    private Painted paint(String css) {
        var tree = new ElementTree(new Node("outer", new Node("inner")));
        var renderer = new WidgetRenderer(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, SHEET + css)), font);
        var target = TestFrames.of(80, 80, 1f);
        var render = RenderTree.create();
        render.update(target.frame(), renderer.render(tree));
        render.paint(target.frame());
        target.end();
        return new Painted(tree, target, render);
    }

    @Nested
    @DisplayName("the painter")
    class Painting {

        @Test
        @DisplayName("leaves a hidden box out and draws a visible child under it")
        void hiddenParentVisibleChild() {
            var painted = paint(".outer { visibility: hidden } .inner { visibility: visible }");
            try (var _ = painted.render()) {
                assertEquals(0, painted.target().alphaAt(2, 2), "the outer box is not drawn");
                assertEquals(0xFFFF0000, painted.target().pixel(15, 15), "the inner one is");
            }
        }

        @Test
        @DisplayName("hides the subtree when nothing below says otherwise")
        void inherited() {
            var painted = paint(".outer { visibility: hidden }");
            try (var _ = painted.render()) {
                assertEquals(0, painted.target().alphaAt(15, 15), "the inner box inherits hidden");
            }
        }
    }

    @Nested
    @DisplayName("the pointer")
    class Pointer {

        private String hitAt(Painted painted, float x, float y) {
            var hit = HitTest.at(HitTest.capture(painted.render()), x, y);
            return hit.map(owner -> ((Element) owner).id()).orElse("(nothing)");
        }

        @Test
        @DisplayName("passes through pointer-events: none to what is under it")
        void passesThrough() {
            var painted = paint(".inner { pointer-events: none }");
            try (var _ = painted.render()) {
                assertEquals("outer", hitAt(painted, 15, 15));
            }
        }

        @Test
        @DisplayName("finds a child that says auto under a parent that says none")
        void childOptsBackIn() {
            var painted = paint(".outer { pointer-events: none } .inner { pointer-events: auto }");
            try (var _ = painted.render()) {
                assertEquals("inner", hitAt(painted, 15, 15));
                assertEquals("(nothing)", hitAt(painted, 2, 2));
            }
        }

        @Test
        @DisplayName("does not find a hidden box")
        void hiddenIsNotHit() {
            var painted = paint(".inner { visibility: hidden }");
            try (var _ = painted.render()) {
                assertEquals("outer", hitAt(painted, 15, 15));
            }
        }
    }

    @Test
    @DisplayName("a hidden box takes no focus, and a visible one under it does")
    void focus() {
        var painted = paint(".outer { visibility: hidden } .inner { visibility: visible }");
        try (var _ = painted.render()) {
            var router = new PointerRouter();
            router.focusRoot(painted.tree().root());
            router.windowBounds(LogicalRect.of(0, 0, 80, 80));
            var visited = new ArrayList<String>();
            for (var i = 0; i < 3; i++) {
                router.moveFocus(1);
                visited.add(
                        router.focused() == null ? "(none)" : router.focused().id());
            }
            assertFalse(visited.contains("outer"), visited::toString);
            assertTrue(visited.contains("inner"), visited::toString);
        }
    }

    @Test
    @DisplayName("the three are read, inherited, and keep the rest of the presence")
    void cascade() {
        var sheet = Stylesheet.parse(
                CascadeLayer.APPLICATION,
                "outer { visibility: collapse; pointer-events: none; -webkit-user-select: none; aspect-ratio: 2 }");
        var root = element("outer");
        root.with(element("inner"));
        var resolver = new StyleResolver(List.of(sheet));
        var context = new CssLength.Context(16, 16);
        var outer = ComputedStyle.of(resolver.resolve(root), context, null);
        var inner = ComputedStyle.of(resolver.resolve(root.descend(1)), context, outer);
        var expected = new Presence(
                Display.FLEX,
                Double.NaN,
                Presence.Visibility.HIDDEN,
                Presence.PointerEvents.NONE,
                Presence.UserSelect.NONE);
        assertEquals(expected, inner.presence(), "inherited, without the ratio");
        assertEquals(2.0, outer.presence().aspectRatio());
        assertFalse(inner.inheritsSameAs(ComputedStyle.INITIAL), "a child of each sees the difference");
    }
}
