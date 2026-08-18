package io.github.digitalsmile.goldberry.widget;



import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Selector.PseudoClass;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.text.Font;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// The invalidation-driven cascade — ADR-0070.
///
/// `docs/ARCHITECTURE.md` §5 has always said "style resolution (invalidated
/// nodes)"; until this existed every node was resolved every frame. The risk in
/// caching is not that it is slow, it is that a node keeps a style after
/// something that decides it has changed — and the failure is silent, because a
/// stale style is a perfectly valid style.
///
/// So the tests come in pairs: one that the cache is used, and one for each way
/// it has to be dropped. This lives in the `widget` package so it can read
/// [Element#cachedStyle] directly — asserting on the mechanism rather than
/// inferring it from a colour.
class StyleCacheTest {

    /// A styled container, defined here rather than taken from the catalog.
    ///
    /// This test is about the **cascade cache** — when a resolved style may be
    /// reused and when it must be thrown away — and it needs a node with a type,
    /// classes and children to be wrong about. `panel` used to be that node, and
    /// `:core` has no widgets since [ADR-0092]. A local one is also the more
    /// honest fixture: nothing here is a fact about `panel`.
    private record Group(List<Widget> children, Attributes attributes)
            implements Widget.Leaf, Styled, Paints {

        @Override
        public List<Widget> children() {
            return children;
        }

        @Override
        public String id() {
            return attributes.id();
        }

        @Override
        public Set<String> classes() {
            return attributes.classes();
        }

        @Override
        public Object key() {
            return attributes.key();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            return Box.of().children(boxes.toArray(Box[]::new)).style(style);
        }
    }

    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;

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
        return new WidgetRenderer(
                List.of(Stylesheet.parse(CascadeLayer.APPLICATION, css)), font);
    }

    private static Attributes classed(String... names) {
        return new Attributes(null, Set.of(names), null);
    }

    /// A group holding a group, so there is an ancestor and a descendant to be
    /// wrong about.
    private static Widget nested() {
        return new Group(
                List.of(new Group(List.of(), classed("inner"))),
                classed("outer"));
    }

    /// The element the renderer styles for the inner group.
    private static Element inner(ElementTree tree) {
        return tree.root().children().getFirst();
    }

    private static Box innerBox(Box root) {
        return root.children().getFirst();
    }

    @Nested
    @DisplayName("the cache is used")
    class Hits {

        @Test
        @DisplayName("a second frame over an unchanged tree resolves nothing")
        void staticTreeIsCached() {
            var renderer = renderer("group.outer { background: red }");
            var tree = new ElementTree(nested());

            renderer.render(tree);
            var cachedAfterFirst = tree.root().cachedStyle(renderer.resolver(), null);
            assertNotNull(cachedAfterFirst, "the first frame populates the cache");

            renderer.render(tree);
            assertSame(cachedAfterFirst, tree.root().cachedStyle(renderer.resolver(), null),
                    "the second frame reused it rather than resolving again");
        }

        @Test
        @DisplayName("an unchanged parent hands its children the same instance")
        void inheritedIdentityIsStable() {
            // The mechanism that makes inheritance invalidate itself: a child's
            // cache is keyed on the *instance* it inherited from, so a parent
            // that re-resolved hands down a different one and its children
            // re-resolve without anything telling them to.
            var renderer = renderer("group.outer { color: red }");
            var tree = new ElementTree(nested());

            renderer.render(tree);
            var parentStyle = tree.root().cachedStyle(renderer.resolver(), null);
            var childStyle = inner(tree).cachedStyle(renderer.resolver(), parentStyle);
            assertNotNull(childStyle, "the child cached against its parent's instance");

            renderer.render(tree);
            assertSame(childStyle, inner(tree).cachedStyle(renderer.resolver(), parentStyle));
        }
    }

    @Nested
    @DisplayName("and dropped when it must be")
    class Invalidation {

        @Test
        @DisplayName("a pseudo-class on the node restyles it")
        void ownPseudoClass() {
            var renderer = renderer("""
                    group.inner { background: #ff0000 }
                    group.inner:hover { background: #0000ff }
                    """);
            var tree = new ElementTree(nested());

            assertEquals(RED, innerBox(renderer.render(tree)).background());

            inner(tree).setPseudoClass(PseudoClass.HOVER, true);
            assertEquals(BLUE, innerBox(renderer.render(tree)).background(),
                    "the hover rule never reached it");
        }

        @Test
        @DisplayName("a pseudo-class on an ANCESTOR restyles the descendant")
        void ancestorPseudoClass() {
            // The case that makes invalidation a subtree walk rather than a
            // single node. The outer group has no rule of its own, so its own
            // resolved style does not change at all when it is hovered — the
            // inherited-identity check cannot see this, and only the subtree
            // walk saves it.
            var renderer = renderer("""
                    group.inner { background: #ff0000 }
                    group.outer:hover group.inner { background: #0000ff }
                    """);
            var tree = new ElementTree(nested());

            assertEquals(RED, innerBox(renderer.render(tree)).background());

            tree.root().setPseudoClass(PseudoClass.HOVER, true);
            assertEquals(BLUE, innerBox(renderer.render(tree)).background(),
                    "a descendant combinator did not survive the style cache");

            tree.root().setPseudoClass(PseudoClass.HOVER, false);
            assertEquals(RED, innerBox(renderer.render(tree)).background(),
                    "and it did not come back when the hover left");
        }

        @Test
        @DisplayName("an inherited property changing on a parent reaches the child")
        void inheritedChange() {
            // `color` inherits (ADR-0066), so the child's style depends on the
            // parent's even though no rule names the child.
            var renderer = renderer("""
                    group.outer { color: #ff0000 }
                    group.outer:hover { color: #0000ff }
                    """);
            var tree = new ElementTree(nested());

            renderer.render(tree);
            var before = inner(tree).cachedStyle(
                    renderer.resolver(), tree.root().cachedStyle(renderer.resolver(), null));
            assertEquals(RED, before.color());

            tree.root().setPseudoClass(PseudoClass.HOVER, true);
            renderer.render(tree);
            var parentAfter = tree.root().cachedStyle(renderer.resolver(), null);
            assertEquals(BLUE, inner(tree).cachedStyle(renderer.resolver(), parentAfter).color());
        }

        @Test
        @DisplayName("a new stylesheet — a theme swap — invalidates everything at once")
        void newResolver() {
            // An application builds a new renderer over the new sheets, so every
            // cached style was resolved by a resolver nobody is asking any more.
            // No explicit invalidation call anywhere, which is the point.
            var tree = new ElementTree(nested());
            var light = renderer("group.inner { background: #ff0000 }");
            assertEquals(RED, innerBox(light.render(tree)).background());

            var dark = renderer("group.inner { background: #0000ff }");
            assertEquals(BLUE, innerBox(dark.render(tree)).background(),
                    "the tree kept the style the previous renderer resolved");
        }

        @Test
        @DisplayName("a rebuild that changes a class restyles")
        void widgetChanged() {
            var renderer = renderer("""
                    group.inner { background: #ff0000 }
                    group.swapped { background: #0000ff }
                    """);
            var tree = new ElementTree(nested());
            assertEquals(RED, innerBox(renderer.render(tree)).background());

            // The same element, a different description of it.
            tree.root().update(new Group(
                    List.of(new Group(List.of(), classed("swapped"))),
                    classed("outer")));
            assertEquals(BLUE, innerBox(renderer.render(tree)).background());
        }

        @Test
        @DisplayName("a class change on an ancestor restyles the subtree under it")
        void ancestorClassChanged() {
            var renderer = renderer("""
                    group.inner { background: #ff0000 }
                    group.themed group.inner { background: #0000ff }
                    """);
            var tree = new ElementTree(nested());
            assertEquals(RED, innerBox(renderer.render(tree)).background());

            tree.root().update(new Group(
                    List.of(new Group(List.of(), classed("inner"))),
                    classed("outer", "themed")));
            assertEquals(BLUE, innerBox(renderer.render(tree)).background());
        }

        @Test
        @DisplayName("setting a pseudo-class it already had changes nothing")
        void redundantSetIsFree() {
            // `WidgetRenderer` mirrors `:disabled`, `:checked` and
            // `:indeterminate` onto every styled element on **every frame**. If a
            // no-op set invalidated, the cache would miss on every frame for
            // every control and this whole change would do nothing.
            var renderer = renderer("group.inner { background: red }");
            var tree = new ElementTree(nested());
            renderer.render(tree);

            var cached = inner(tree).cachedStyle(
                    renderer.resolver(), tree.root().cachedStyle(renderer.resolver(), null));
            assertNotNull(cached);

            inner(tree).setPseudoClass(PseudoClass.HOVER, false);
            assertSame(cached, inner(tree).cachedStyle(
                    renderer.resolver(), tree.root().cachedStyle(renderer.resolver(), null)),
                    "clearing a pseudo-class that was not set threw the cache away");
        }

        @Test
        @DisplayName("invalidation reaches through a composition node")
        void throughCompositionNodes() {
            // A `Widget.Stateless` caches nothing — the renderer passes its
            // ancestor's style straight through — so a `null` cache on one says
            // nothing about the subtree below it. That is why the walk does not
            // short-circuit on null, and this is the test that would fail if it
            // did.
            var renderer = renderer("""
                    group.inner { background: #ff0000 }
                    group.outer:hover group.inner { background: #0000ff }
                    """);
            var tree = new ElementTree(new Group(
                    List.of(new Wrapper()), classed("outer")));

            var root = renderer.render(tree);
            assertEquals(RED, root.children().getFirst().background());

            tree.root().setPseudoClass(PseudoClass.HOVER, true);
            assertEquals(BLUE, renderer.render(tree).children().getFirst().background(),
                    "the invalidation stopped at the composition node");
        }

        /// Composition and nothing else: it describes a group and paints nothing
        /// itself.
        private record Wrapper() implements Widget.Stateless {

            @Override
            public Widget build(BuildContext context) {
                return new Group(List.of(), classed("inner"));
            }
        }
    }

    @Nested
    @DisplayName("the cache does not change what is drawn")
    class Equivalence {

        @Test
        @DisplayName("a cached frame and a freshly resolved one are equal")
        void sameResult() {
            // The invariant behind everything above: caching is an optimisation,
            // so two renderers over the same sheets must agree — one that has
            // been running for ten frames and one seeing the tree for the first
            // time.
            var css = """
                    group.outer { color: #ff0000; padding: 4px }
                    group.inner { background: #0000ff; border-radius: 6px }
                    group.outer:hover group.inner { background: #00ff00 }
                    """;
            var warm = renderer(css);
            var tree = new ElementTree(nested());
            for (var i = 0; i < 10; i++) {
                warm.render(tree);
            }
            tree.root().setPseudoClass(PseudoClass.HOVER, true);
            var fromWarm = warm.render(tree);

            var cold = renderer(css);
            var freshTree = new ElementTree(nested());
            freshTree.root().setPseudoClass(PseudoClass.HOVER, true);
            var fromCold = cold.render(freshTree);

            // `owner` is the element, which differs between the two trees, so
            // the comparison is on what is drawn rather than on the whole box.
            assertEquals(fromCold.background(), fromWarm.background());
            assertEquals(innerBox(fromCold).background(), innerBox(fromWarm).background());
            assertEquals(innerBox(fromCold).decoration(), innerBox(fromWarm).decoration());
            assertNotSame(fromCold, fromWarm);
        }
    }
}
