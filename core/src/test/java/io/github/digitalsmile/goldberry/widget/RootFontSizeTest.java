package io.github.digitalsmile.goldberry.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.css.value.CssLength;
import io.github.digitalsmile.goldberry.layout.Insets;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.text.font.Font;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// `rem` is the **root element's** computed `font-size` ([ADR-0416]).
///
/// ADR-0242 resolved `em` against the element's own size and left this: `rem`
/// read `CssLength.Context.rootFontSize()`, which is a number an application
/// configured rather than anything the root computed. The two agree until a root
/// declares a size, and the entry that stayed open says as much — "nothing in the
/// catalog styles a root's `font-size`, so this is exact today".
///
/// So every test here declares one. That is the case that was **not** exact, and
/// a test that did not declare one would pass against the old code.
///
/// ## Why this needs a renderer and `ComputedStyleTest` cannot cover it
///
/// Because the missing half is not arithmetic, it is reach. A node is handed its
/// *parent's* style and never the root's, so nothing inside `ComputedStyle.of`
/// can recover the root's size for a descendant — which is exactly what ADR-0242
/// said and why it left the entry open. The thing that walks the tree is the only
/// thing that ever holds the root's style and the descendant's declarations at
/// once, so the fix lives in [WidgetRenderer] and so does the test.
class RootFontSizeTest {

    /// A node that keeps the style it was rendered with.
    ///
    /// A mutable box hanging off a record, which a widget is not otherwise
    /// allowed: the widget stays a value, and what is written is the *output* of
    /// a render rather than anything the cascade reads back. Reading it off the
    /// `Box` instead would work for `padding` and not for `font-size`, which is
    /// the property under test and is not a box dimension.
    private record Probe(String type, Captured captured, List<Widget> kids) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return type;
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public List<Widget> children() {
            return kids;
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            captured.style = style;
            return Box.of().style(style).children(boxes.toArray(Box[]::new));
        }
    }

    private static final class Captured {
        private ComputedStyle style = ComputedStyle.INITIAL;
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

    /// Renders `window > button` under `css` and hands back what each computed.
    ///
    /// `Context(16, 16)` on purpose rather than the default by name: 16 is the
    /// number every assertion below has to be *distinguishable from*, because it
    /// is the answer the old code gave.
    private Captured[] render(String css) {
        var root = new Captured();
        var child = new Captured();
        var tree = new ElementTree(new Probe("window", root, List.of(new Probe("button", child, List.of()))));
        new WidgetRenderer(
                        List.of(Stylesheet.parse(CascadeLayer.APPLICATION, css)), font, new CssLength.Context(16, 16))
                .render(tree);
        return new Captured[] {root, child};
    }

    /// **The case the entry named**, and the one that fails against ADR-0242's
    /// code: a root that declares `font-size` and a descendant that uses `rem`.
    ///
    /// 20 × 2 = 40. The old answer was 32 — the configured root size, which no
    /// element in this tree has.
    @Test
    @DisplayName("a descendant's rem is the size the root declared, not the one configured")
    void aDescendantUsesTheRootsDeclaredSize() {
        var captured = render("window { font-size: 20px } button { padding: 2rem }");

        assertEquals(Insets.all(Length.points(40)), captured[1].style.padding());
    }

    /// `rem` is the **root's** size and not the nearest ancestor's, which is the
    /// whole difference between `rem` and `em` and is the thing a naive fix — hand
    /// the parent's size down as the root's — would get wrong without failing the
    /// test above.
    ///
    /// The child declares 10 and the root declares 20, so `2em` is 20 and `2rem`
    /// is 40. One assertion cannot be satisfied by the other's answer.
    @Test
    @DisplayName("rem is the root's size even where an ancestor declares a different one")
    void remIsNotTheNearestAncestor() {
        var captured = render("window { font-size: 20px } button { font-size: 10px; padding: 2rem; margin: 2em }");

        assertEquals(Insets.all(Length.points(40)), captured[1].style.padding(), "2rem is the root's 20");
        assertEquals(Insets.all(Length.points(20)), captured[1].style.margin(), "2em is the element's own 10");
    }

    /// A root that declares nothing computes `Typography.INITIAL`'s 13, and that
    /// is what `rem` means below it — **not** the configured 16.
    ///
    /// This is the one assertion that changes an answer nobody asked to change,
    /// and it is deliberate. The alternative is `rem` meaning the configured
    /// number when a root is silent and the root's size when it is not, which is
    /// one unit with two meanings depending on a declaration somewhere else.
    /// ADR-0242 already narrowed `Context.fontSize` to "what the *root's* `em`
    /// resolves against" for the same reason; this is the other field taking the
    /// same step.
    @Test
    @DisplayName("a silent root computes the initial size, and rem follows it rather than the configuration")
    void aSilentRootStillDecides() {
        var captured = render("button { padding: 2rem }");

        assertEquals(13, captured[0].style.typography().size(), 0.001, "the root computed the initial size");
        assertEquals(Insets.all(Length.points(26)), captured[1].style.padding(), "2 × 13, not 2 × 16");
    }

    /// The root's own non-font-size declarations, through the renderer rather
    /// than through `ComputedStyle.of` directly — so the field the renderer keeps
    /// cannot quietly disagree with the branch inside the method.
    @Test
    @DisplayName("the root's own rem is its own computed size too")
    void theRootAgreesWithItself() {
        var captured = render("window { font-size: 20px; padding: 2rem } button { padding: 2rem }");

        assertEquals(captured[0].style.padding(), captured[1].style.padding(), "one root, one meaning for rem");
        assertEquals(Insets.all(Length.points(40)), captured[0].style.padding());
    }

    /// A second frame must not read the first frame's number, and a root whose
    /// size moved must take its subtree with it.
    ///
    /// The field on the renderer is the only mutable style state in the walk, so
    /// the question "is it ever stale" is the one thing a reviewer will ask about
    /// this shape. It is reset at the top of every frame and set once at the root;
    /// below that, a root whose style changed hands its children a different
    /// instance and they re-resolve because of it — the cache invalidating itself
    /// (ADR-0070), which is what keeps the two in step without anything telling
    /// them to.
    @Test
    @DisplayName("rendering twice gives the same answer")
    void theFieldIsNotStale() {
        var root = new Captured();
        var child = new Captured();
        var tree = new ElementTree(new Probe("window", root, List.of(new Probe("button", child, List.of()))));
        var renderer = new WidgetRenderer(
                List.of(Stylesheet.parse(
                        CascadeLayer.APPLICATION, "window { font-size: 20px } button { padding: 2rem }")),
                font,
                new CssLength.Context(16, 16));

        renderer.render(tree);
        var first = child.style.padding();
        renderer.render(tree);

        assertEquals(first, child.style.padding());
        assertEquals(Insets.all(Length.points(40)), child.style.padding());
    }
}
