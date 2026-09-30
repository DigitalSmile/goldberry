package io.github.digitalsmile.goldberry.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.StyleElement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.css.cascade.StyleResolver;
import io.github.digitalsmile.goldberry.css.parse.Token;
import io.github.digitalsmile.goldberry.css.select.Selector.PseudoClass;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// A node's custom properties are its parent's map plus what it changes, and
/// nothing else — ADR-0502.
///
/// Every answer here is checked twice: against the value the test expects, and
/// against the **uncached walk**, a stand-in for the same element that has no
/// cache and so re-derives every ancestor from its own cascade on every ask.
/// That walk is what `customPropertiesFor` means by definition, and the cached
/// path through [Element] has to agree with it after every frame and every
/// invalidation, or a node is drawn with a stale `--gb-*` and nothing fails.
///
/// In the `widget` package, like [StyleCacheTest], because the cache under test
/// is [Element]'s.
class CustomPropertiesCacheTest {

    /// A styled node with classes and children, and nothing to say about
    /// either — StyleCacheTest's fixture, for the same reason.
    private record Group(List<Widget> children, Attributes attributes) implements Widget.Leaf, Styled, Paints {

        @Override
        public List<Widget> children() {
            return children;
        }

        @Override
        public @Nullable String id() {
            return attributes.id();
        }

        @Override
        public Set<String> classes() {
            return attributes.classes();
        }

        @Override
        public @Nullable Object key() {
            return attributes.key();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            return Box.of().children(boxes.toArray(Box[]::new)).style(style);
        }
    }

    /// The same element with no cache: every ask walks to the root and cascades
    /// every ancestor again, which is the answer by definition.
    private record Uncached(Element element) implements StyleElement {

        @Override
        public @Nullable String type() {
            return element.type();
        }

        @Override
        public @Nullable String id() {
            return element.id();
        }

        @Override
        public Set<String> classes() {
            return element.classes();
        }

        @Override
        public @Nullable StyleElement parent() {
            return element.parent() instanceof Element parent ? new Uncached(parent) : null;
        }

        @Override
        public boolean hasState(PseudoClass state) {
            return element.hasState(state);
        }
    }

    /// Seven levels, with the classes the sheets below name spread down them:
    /// `root > one > mid > three > deep > low > leaf`.
    private static Widget chain() {
        Widget node = group("leaf");
        for (var name : List.of("low", "deep", "three", "mid", "one", "root")) {
            node = new Group(List.of(node), new Attributes(null, Set.of(name), null));
        }
        return node;
    }

    private static Widget group(String name) {
        return new Group(List.of(), new Attributes(null, Set.of(name), null));
    }

    private static Stylesheet sheet(String css) {
        return Stylesheet.parse(CascadeLayer.APPLICATION, css);
    }

    /// Every element, root first — the order the renderer resolves in.
    private static List<Element> elements(ElementTree tree) {
        var all = new ArrayList<Element>();
        collect(tree.root(), all);
        return all;
    }

    private static void collect(Element element, List<Element> into) {
        into.add(element);
        for (var child : element.children()) {
            collect(child, into);
        }
    }

    private static Element named(ElementTree tree, String name) {
        return elements(tree).stream()
                .filter(e -> e.classes().contains(name))
                .findFirst()
                .orElseThrow();
    }

    /// A frame's cascade, top down, as the renderer runs it.
    private static void frame(StyleResolver resolver, ElementTree tree) {
        tree.styleResolver(resolver);
        for (var element : elements(tree)) {
            resolver.resolve(element);
        }
    }

    /// Every node's custom properties and resolved declarations, cached against
    /// uncached.
    private static void assertMatchesTheUncachedWalk(StyleResolver resolver, ElementTree tree) {
        for (var element : elements(tree)) {
            var uncached = new Uncached(element);
            assertEquals(
                    resolver.customPropertiesFor(uncached),
                    resolver.customPropertiesFor(element),
                    () -> "custom properties on " + element.classes());
            assertEquals(resolver.resolve(uncached), resolver.resolve(element), () -> "style on " + element.classes());
        }
    }

    private static @Nullable String text(Map<String, List<Token>> resolved, String property) {
        var tokens = resolved.get(property);
        if (tokens == null) {
            return null;
        }
        var text = new StringBuilder();
        for (var token : tokens) {
            text.append(token.cssText());
        }
        return text.toString();
    }

    private static @Nullable String color(StyleResolver resolver, ElementTree tree, String name) {
        return text(resolver.resolve(named(tree, name)), "color");
    }

    @Test
    @DisplayName("a node that changes nothing hands down its parent's map itself")
    void sharedInstance() {
        // `group` matches every node, so every node re-declares `--b` -- with
        // the very tokens its parent already has, which changes nothing and so
        // must not cost its subtree a new identity.
        var resolver = new StyleResolver(List.of(sheet("""
                .root { --a: red }
                group { --b: blue }
                """)));
        var tree = new ElementTree(chain());
        frame(resolver, tree);

        var root = resolver.customPropertiesFor(tree.root());
        for (var name : List.of("one", "mid", "three", "leaf")) {
            assertSame(root, resolver.customPropertiesFor(named(tree, name)), name);
        }
        assertMatchesTheUncachedWalk(resolver, tree);
    }

    @Test
    @DisplayName("an override at depth is seen below it and not above it")
    void overrideAtDepth() {
        var resolver = new StyleResolver(List.of(sheet("""
                .root { --a: red }
                .deep { --a: green }
                .low { --a: yellow }
                .one, .three, .deep, .low, .leaf { color: var(--a) }
                """)));
        var tree = new ElementTree(chain());
        frame(resolver, tree);

        assertEquals("red", color(resolver, tree, "one"));
        assertEquals("red", color(resolver, tree, "three"));
        assertEquals("green", color(resolver, tree, "deep"));
        assertEquals("yellow", color(resolver, tree, "low"));
        assertEquals("yellow", color(resolver, tree, "leaf"));
        assertNotSame(
                resolver.customPropertiesFor(named(tree, "three")), resolver.customPropertiesFor(named(tree, "deep")));
        assertMatchesTheUncachedWalk(resolver, tree);
    }

    @Test
    @DisplayName("a var() in an inherited custom property resolves where it is used")
    void varReferencingInherited() {
        // `--b` is written once, as `var(--a)`, and `--a` changes twice below
        // it. The resolver has always substituted at use, so each node sees the
        // `--a` in force on itself -- and the merge must not have resolved
        // `--b` early on the way down.
        var resolver = new StyleResolver(List.of(sheet("""
                .root { --a: red; --b: var(--a) }
                .mid { --a: green }
                .low { --a: var(--c, yellow) }
                .one, .mid, .low, .leaf { color: var(--b) }
                """)));
        var tree = new ElementTree(chain());
        frame(resolver, tree);

        assertEquals("red", color(resolver, tree, "one"));
        assertEquals("green", color(resolver, tree, "mid"));
        assertEquals("yellow", color(resolver, tree, "low"));
        assertEquals("yellow", color(resolver, tree, "leaf"));
        assertEquals("var(--a)", text(resolver.customPropertiesFor(named(tree, "leaf")), "--b"));
        assertMatchesTheUncachedWalk(resolver, tree);
    }

    @Test
    @DisplayName("hovering a middle ancestor re-derives everything under it, and leaving restores it")
    void invalidatedMiddleAncestor() {
        var resolver = new StyleResolver(List.of(sheet("""
                .root { --a: red }
                .mid:hover { --a: purple }
                .leaf { color: var(--a) }
                """)));
        var tree = new ElementTree(chain());
        frame(resolver, tree);
        var above = resolver.customPropertiesFor(named(tree, "one"));
        assertEquals("red", color(resolver, tree, "leaf"));

        named(tree, "mid").setPseudoClass(PseudoClass.HOVER, true);
        frame(resolver, tree);
        assertEquals("purple", color(resolver, tree, "leaf"));
        assertSame(above, resolver.customPropertiesFor(named(tree, "one")), "above the hover nothing moved");
        assertMatchesTheUncachedWalk(resolver, tree);

        named(tree, "mid").setPseudoClass(PseudoClass.HOVER, false);
        frame(resolver, tree);
        assertEquals("red", color(resolver, tree, "leaf"));
        assertMatchesTheUncachedWalk(resolver, tree);
    }

    @Test
    @DisplayName("a middle ancestor's hover that reaches a descendant's own custom property")
    void invalidatedThroughADescendantRule() {
        // This time the ancestor's own map does not change at all: the hover
        // matters only through `.mid:hover .deep`, so the subtree walk is the
        // only thing that can tell `.deep` to cascade again.
        var resolver = new StyleResolver(List.of(sheet("""
                .root { --a: red; --b: blue }
                .mid:hover .deep { --b: orange }
                .leaf { color: var(--b) }
                """)));
        var tree = new ElementTree(chain());
        frame(resolver, tree);
        var mid = resolver.customPropertiesFor(named(tree, "mid"));
        assertEquals("blue", color(resolver, tree, "leaf"));

        named(tree, "mid").setPseudoClass(PseudoClass.HOVER, true);
        frame(resolver, tree);
        assertEquals("orange", color(resolver, tree, "leaf"));
        assertEquals(mid, resolver.customPropertiesFor(named(tree, "mid")));
        assertMatchesTheUncachedWalk(resolver, tree);

        named(tree, "mid").setPseudoClass(PseudoClass.HOVER, false);
        frame(resolver, tree);
        assertEquals("blue", color(resolver, tree, "leaf"));
        assertMatchesTheUncachedWalk(resolver, tree);
    }

    @Test
    @DisplayName("a deep node asked first, with nothing above it cached, gets the same answer")
    void deepNodeFirst() {
        // Out of the renderer's order: the leaf's ask fills every ancestor's
        // cache on the way up, and the frame that follows must hit those
        // entries rather than disagree with them.
        var resolver = new StyleResolver(List.of(sheet("""
                .root { --a: red }
                .three { --a: green; --c: 4px }
                .leaf { color: var(--a); padding: var(--c) }
                """)));
        var tree = new ElementTree(chain());
        tree.styleResolver(resolver);

        var leaf = resolver.resolve(named(tree, "leaf"));
        assertEquals("green", text(leaf, "color"));
        assertEquals("4px", text(leaf, "padding"));
        frame(resolver, tree);
        assertEquals(leaf, resolver.resolve(named(tree, "leaf")));
        assertMatchesTheUncachedWalk(resolver, tree);
    }
}
