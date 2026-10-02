package dev.goldberry.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Arrays;
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
import dev.goldberry.paint.Box;
import dev.goldberry.text.font.Font;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// `:first-child` and its kin in a real element tree: an element's position is
/// set when its parent reconciles its children, and only the children whose
/// structural answer changed are restyled.
///
/// Lives in `widget` to read [Element#cachedStyle] directly, as
/// `StyleCacheTest` does: the claim is about which caches are thrown away.
class StructuralRestyleTest {

    /// A styled container whose children are keyed, so a removal moves the
    /// survivors rather than re-describing them in place.
    private record Group(List<Widget> children, Attributes attributes) implements Widget.Leaf, Styled, Paints {

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

    /// A row of keyed items.
    private static Widget row(String... keys) {
        return new Group(
                Arrays.stream(keys)
                        .<Widget>map(key -> new Group(List.of(), new Attributes(null, Set.of("item"), key)))
                        .toList(),
                new Attributes(null, Set.of("row"), null));
    }

    private static int background(Box root, int child) {
        return root.children().get(child).background();
    }

    private static Element child(ElementTree tree, int index) {
        return tree.root().children().get(index);
    }

    @Test
    @DisplayName("removing the first child makes the next one first, and it is restyled")
    void removalRestyles() {
        var renderer = renderer("group.item:first-child { background: red }");
        var tree = new ElementTree(row("a", "b", "c"));
        var box = renderer.render(tree);
        assertEquals(RED, background(box, 0));
        assertEquals(0, background(box, 1));

        tree.root().update(row("b", "c"));
        box = renderer.render(tree);
        assertEquals(RED, background(box, 0), "b is the first child now");
        assertEquals(0, background(box, 1));
        assertEquals(0, child(tree, 0).indexInParent());
        assertEquals(2, child(tree, 0).siblingCount());
    }

    @Test
    @DisplayName("appending under :first-child rules restyles nobody")
    void appendingKeepsFirstChildCaches() {
        var renderer = renderer("group.item:first-child { background: red }");
        var tree = new ElementTree(row("a", "b"));
        renderer.render(tree);
        var first = child(tree, 0).cachedStyle(renderer.resolver(), tree.root().cachedStyle(renderer.resolver(), null));
        assertNotNull(first);

        tree.root().update(row("a", "b", "c"));
        assertSame(
                first,
                child(tree, 0).cachedStyle(renderer.resolver(), tree.root().cachedStyle(renderer.resolver(), null)),
                "a's position changed in count only, which no :first-child can see");
    }

    @Test
    @DisplayName("appending under :last-child rules restyles the old last child and only it")
    void appendingRestylesTheOldLast() {
        var renderer = renderer("group.item:last-child { background: red }");
        var tree = new ElementTree(row("a", "b"));
        renderer.render(tree);
        var parent = tree.root().cachedStyle(renderer.resolver(), null);
        var first = child(tree, 0).cachedStyle(renderer.resolver(), parent);
        assertNotNull(first);

        tree.root().update(row("a", "b", "c"));
        assertSame(first, child(tree, 0).cachedStyle(renderer.resolver(), parent), "a was not last and is not");
        assertNull(child(tree, 1).cachedStyle(renderer.resolver(), parent), "b was last and is not");

        var box = renderer.render(tree);
        assertEquals(0, background(box, 1));
        assertEquals(RED, background(box, 2));
    }

    @Test
    @DisplayName("sheets with no structural pseudo-class restyle nobody when children move")
    void noStructuralRules() {
        var renderer = renderer("group.item { background: red }");
        var tree = new ElementTree(row("a", "b", "c"));
        renderer.render(tree);
        var parent = tree.root().cachedStyle(renderer.resolver(), null);
        var b = child(tree, 1).cachedStyle(renderer.resolver(), parent);
        assertNotNull(b);

        tree.root().update(row("b", "c"));
        assertSame(b, child(tree, 0).cachedStyle(renderer.resolver(), parent));
    }
}
