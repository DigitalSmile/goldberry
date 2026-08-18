package io.github.digitalsmile.goldberry.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.CssLength;
import io.github.digitalsmile.goldberry.css.StyleResolver;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.text.Text;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// That the showcase's screens have a typographic hierarchy at all.
///
/// ## Why this is not a golden test
///
/// [GalleryGoldenTest] cannot see any of this. It builds its renderer with the
/// **single-font** constructor — the one whose own documentation says it "ignores
/// `font-family`, `font-size` and `font-weight` entirely" — because a golden
/// image is about layout and colour and a font book would make it a test of
/// whichever Inter happened to be on the machine.
///
/// The consequence is that every screenshot in the gallery draws its prose, its
/// headings and its button labels at one size, and a screen with no hierarchy at
/// all looks exactly like a screen with one. That is how a title and the
/// paragraph under it came to be the same 13px without anything catching it: the
/// tokens were right, the cascade applied them, and the only test that looks at
/// the gallery is blind to the difference.
///
/// So the sizes are asserted through the cascade instead, which is where they are
/// decided.
class ShowcaseTypographyTest {

    private static final double BODY = 13;
    private static final double HEADING = 15;

    private StyleResolver resolver() {
        return new StyleResolver(List.of(
                Controls.baseStylesheet(), Theme.NORD_DARK.load(),
                Stylesheet.resource(CascadeLayer.APPLICATION, Showcase.class, "showcase.css")));
    }

    private ComputedStyle styleOf(StyleResolver resolver, Element element) {
        return ComputedStyle.of(resolver.resolve(element), CssLength.Context.DEFAULT);
    }

    @Test
    @DisplayName("a screen title is larger than the prose under it, not merely bolder")
    void titleOutranksProse() {
        var resolver = resolver();
        var tree = new ElementTree(new Column(
                new Text("A title").styled("screen-title"),
                new Text("Some prose").id("prose")).id("screen-text"));
        tree.flush();

        var title = styleOf(resolver, tree.root().children().getFirst());
        var prose = styleOf(resolver, tree.root().children().get(1));

        // Both were 13px and told apart only by weight, which on a 900px screen
        // reads as a page with no headings on it.
        assertEquals(HEADING, title.typography().size(),
                "a screen title is not distinguishable by size");
        assertTrue(title.typography().size() > prose.typography().size()
                        || title.typography().weight() != prose.typography().weight(),
                "a title and its prose are typographically identical");
    }

    @Test
    @DisplayName("prose is set larger than a control's label, because it is read rather than scanned")
    void proseIsNotChrome() {
        var resolver = resolver();
        var tree = new ElementTree(new Column(new Text("Some prose").id("prose")).id("screen-text"));
        tree.flush();

        var prose = styleOf(resolver, tree.root().children().getFirst());

        // §1.4's `body` is 13/18 and is right for a label beside a control. A
        // paragraph set at it across this window is technically correct and hard
        // to read; `heading`'s 15/20 is the largest thing in the scale that is
        // still body copy.
        assertEquals(HEADING, prose.typography().size());
        assertTrue(prose.typography().size() > BODY,
                "prose is set at the same size as a button's label");
    }

    @Test
    @DisplayName("an unclassed text node still gets the design system's body size")
    void defaultIsStillBody() {
        var resolver = resolver();
        var tree = new ElementTree(new Column(new Text("A label")).id("screen-text"));
        tree.flush();

        // The other half of the finding, and the reason nothing in the toolkit
        // was changed for this: 13px is exactly what §1.4 specifies and it was
        // being applied correctly all along. What was missing was the showcase
        // choosing between the sizes it had.
        assertEquals(BODY, styleOf(resolver, tree.root().children().getFirst())
                .typography().size());
    }
}
