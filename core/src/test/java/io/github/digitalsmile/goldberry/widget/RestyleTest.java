package io.github.digitalsmile.goldberry.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Transform;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.motion.Clock;
import io.github.digitalsmile.goldberry.text.Font;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// [Styled#restyle], which is §8's `inline` layer with a type instead of a
/// string.
///
/// The mechanism exists for one shape of problem: a value that **only the widget
/// can compute** and that still has to move under a `transition`. A segmented
/// control's indicator is the first — a stylesheet cannot say "one fifth along"
/// because it cannot count the segments — and the property that makes it worth a
/// seam rather than a special case is *where* it applies. A value written in
/// [Paints#render] is applied after the animation has already looked at the
/// style, so it snaps; the same value written here is part of what the animation
/// observes ([ADR-0099](../../../../../../book/src/adr/0099-an-indicator-travels-on-a-grid.md)).
///
/// So these tests are about ordering, and each of the three is a way the ordering
/// could be wrong: applied before the cache (and frozen), applied after the
/// observation (and snapping), or not applied to what children inherit.
class RestyleTest {

    /// A node that translates itself by a number it was built with — the shape of
    /// `SegmentedIndicator`, with the counting taken out.
    private record Marker(double percent, Attributes attributes)
            implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "marker";
        }

        @Override
        public Set<String> classes() {
            return attributes.classes();
        }

        @Override
        public ComputedStyle restyle(ComputedStyle resolved) {
            return resolved.transform(Transform.of(new Transform.Function.Translate(
                    Transform.Length.percent(percent), Transform.Length.ZERO)));
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            return Box.of().style(style).children(boxes.toArray(Box[]::new));
        }
    }

    /// A node that inherits — `color` is the property CSS passes down, so it is
    /// what says whether a restyled parent reached its children.
    private record Label(Attributes attributes) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "label";
        }

        @Override
        public Set<String> classes() {
            return attributes.classes();
        }

        /// Text, because `color` is the property under test and a box carries a
        /// colour only where it draws something.
        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            return Box.text(context.paragraph(style, "x"), style.color());
        }
    }

    /// A parent that recolours itself, to see what its child inherits.
    private record Tinted(int argb, List<Widget> children, Attributes attributes)
            implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "tinted";
        }

        @Override
        public Set<String> classes() {
            return attributes.classes();
        }

        @Override
        public List<Widget> children() {
            return children;
        }

        @Override
        public ComputedStyle restyle(ComputedStyle resolved) {
            return resolved.color(argb);
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            return Box.of().style(style).children(boxes.toArray(Box[]::new));
        }
    }

    private static final int GREEN = 0xFF00FF00;

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

    private static Attributes none() {
        return Attributes.NONE;
    }

    @Test
    @DisplayName("a widget's own value reaches the box")
    void applied() {
        var renderer = renderer("marker { width: 40px }");
        var box = renderer.render(new ElementTree(new Marker(200, none())));

        assertEquals(
                Transform.of(new Transform.Function.Translate(
                        Transform.Length.percent(200), Transform.Length.ZERO)),
                box.transform());
    }

    /// The cache holds the **cascade's** answer, and the widget's word is applied
    /// on top of it every frame. Cached the other way round, a widget that
    /// changed would keep the first frame's value for as long as its selectors
    /// kept matching — which is a stuck indicator and a perfectly valid style.
    @Test
    @DisplayName("the cache keeps the cascade's style, not the widget's")
    void appliedAfterTheCache() {
        var renderer = renderer("marker { width: 40px }");
        var tree = new ElementTree(new Marker(0, none()));

        var box = renderer.render(tree);
        var cached = tree.root().cachedStyle(renderer.resolver(), null);

        assertTrue(cached.transform().isNone(),
                "the cache is the cascade's answer; the widget's word is applied on top of it"
                        + " every frame, or a widget that changed would keep the first frame's"
                        + " value for as long as its selectors kept matching");
        // The box has the widget's transform even at zero -- a `translate(0%)`
        // is a transform that changes nothing, not the absence of one, and the
        // painter drops it when it resolves to the identity.
        assertEquals(0, ((Transform.Function.Translate) box.transform().functions().getFirst())
                .x().value());
        assertTrue(box.transform().functions().getFirst().resolve(40, 32).isIdentity(),
                "and it costs the painter nothing");
    }

    /// The same node, rebuilt with a different number: nothing about the cascade
    /// changed — same type, same classes, same stylesheet — and the box moves
    /// anyway, which is the whole point of the seam.
    @Test
    @DisplayName("a widget that changes moves, though no selector did")
    void followsTheWidget() {
        var renderer = renderer("marker { width: 40px }");
        var tree = new ElementTree(new Marker(0, none()));
        renderer.render(tree);

        tree.root().update(new Marker(300, none()));
        var moved = renderer.render(tree);

        assertEquals(300, ((Transform.Function.Translate) moved.transform().functions().getFirst())
                .x().value());
    }

    /// The reason the seam exists at all. A `transition` on the property the
    /// widget writes has to *catch* the change, which it can only do if the
    /// widget wrote it before the animation looked.
    @Test
    @DisplayName("a widget-written value transitions rather than snapping")
    void transitions() {
        var clock = Clock.virtual();
        var renderer = renderer("marker { width: 40px; transition: transform 160ms ease-enter }")
                .clock(clock);
        var tree = new ElementTree(new Marker(0, none()));

        renderer.render(tree);
        assertFalse(renderer.isAnimating(), "nothing moves on a first frame");

        tree.root().update(new Marker(200, none()));
        renderer.render(tree);
        assertTrue(renderer.isAnimating(), "the widget moved its own value, so a transition began");

        clock.advance(80);
        var midway = renderer.render(tree);
        var x = ((Transform.Function.Translate) midway.transform().functions().getFirst()).x().value();
        assertTrue(x > 0 && x < 200, "caught between the two: " + x);

        clock.advance(200);
        renderer.render(tree);
        assertFalse(renderer.isAnimating(), "and it settles like any other transition");
    }

    /// The node has one style and this is it, so an inherited property written
    /// here reaches the subtree — CSS's rule for an inline style, and the reason
    /// this returns a whole style rather than a patch.
    @Test
    @DisplayName("what a widget writes is what its children inherit")
    void childrenInherit() {
        var renderer = renderer("tinted { width: 40px } label { width: 10px }");
        var box = renderer.render(new ElementTree(
                new Tinted(GREEN, List.of(new Label(none())), none())));

        assertEquals(GREEN, box.children().getFirst().text().argb(),
                "the child drew itself in the colour its parent wrote, not the cascade's");
    }

    /// And the default is exactly nothing: a widget that says nothing here gets
    /// the cascade's style back, instance and all.
    @Test
    @DisplayName("a widget that declines gets the cascade's own answer")
    void defaultIsIdentity() {
        var renderer = renderer("label { width: 10px }");
        var tree = new ElementTree(new Label(none()));

        renderer.render(tree);
        var first = tree.root().cachedStyle(renderer.resolver(), null);
        renderer.render(tree);

        assertSame(first, tree.root().cachedStyle(renderer.resolver(), null));
        assertNotSame(ComputedStyle.INITIAL, first);
    }
}
