package dev.goldberry.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.example.ui.gallery.SectionHeader;
import dev.goldberry.example.ui.shell.ReadingSize;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.text.Text;

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

    /// The toolkit's own body size, which the showcase reads above.
    private static final double TOOLKIT_BODY = 13;

    /// The showcase's body size, at the reading size it opens with.
    private static final double BODY = 15;

    /// A screen's heading: the `title` rank at that reading size.
    private static final double TITLE = 24;

    private StyleResolver resolver() {
        return new StyleResolver(List.of(
                Controls.baseStylesheet(),
                Theme.NORD_DARK.load(),
                Stylesheet.resource(CascadeLayer.APPLICATION, Showcase.class, "showcase.css"),
                ReadingSize.MEDIUM.stylesheet()));
    }

    private ComputedStyle styleOf(StyleResolver resolver, Element element) {
        return ComputedStyle.of(resolver.resolve(element), CssLength.Context.DEFAULT);
    }

    /// `element`'s style inheriting from `parent`'s, which is how the window
    /// resolves a node: a `text` nobody classed reads its size from the root.
    private ComputedStyle inherited(StyleResolver resolver, Element parent, Element element) {
        var above = ComputedStyle.of(resolver.resolve(parent), CssLength.Context.DEFAULT);
        return ComputedStyle.of(resolver.resolve(element), CssLength.Context.DEFAULT, above);
    }

    @Test
    @DisplayName("a screen title is larger than the prose under it, not merely bolder")
    void titleOutranksProse() {
        var resolver = resolver();
        // The real widget, not a `text.screen-title`: a screen's heading is a
        // `section-header` element now, which is what lets a stylesheet select it
        // as a *kind* rather than as a class any node can wear.
        var tree = new ElementTree(
                new Column(new SectionHeader("A title"), new Text("Some prose").id("prose")).id("screen-text"));
        tree.flush();

        var title = styleOf(resolver, tree.root().children().getFirst());
        var prose = styleOf(resolver, tree.root().children().get(1));

        // Both were 13px and told apart only by weight, which on a 900px screen
        // reads as a page with no headings on it.
        assertEquals(TITLE, title.typography().size(), "a screen title is not distinguishable by size");
        assertTrue(
                title.typography().size() > prose.typography().size()
                        || title.typography().weight() != prose.typography().weight(),
                "a title and its prose are typographically identical");
    }

    @Test
    @DisplayName("prose is set at the reading size's body, larger than the toolkit's")
    void proseIsReadable() {
        var resolver = resolver();
        var tree = new ElementTree(new Column(new Text("Some prose").id("prose")).id("screen-text"));
        tree.flush();

        var prose = styleOf(resolver, tree.root().children().getFirst());

        // Prose borrowed `heading`'s size while body text was 13px. The reading
        // size sets body text at 15 now, so prose is body text again and grows
        // with the rest when a reader asks for larger text.
        assertEquals(BODY, prose.typography().size());
        assertTrue(prose.typography().size() > TOOLKIT_BODY, "prose is set at the toolkit's label size");
    }

    @Test
    @DisplayName("an unclassed text node still gets the design system's body size")
    void defaultIsStillBody() {
        var resolver = resolver();
        var tree = new ElementTree(new Column(new Text("A label")).id("screen-text"));
        tree.flush();

        // The reading size moves the body token itself, so a label nobody
        // classed grows with the prose rather than staying at the toolkit's 13.
        assertEquals(
                BODY,
                inherited(resolver, tree.root(), tree.root().children().getFirst())
                        .typography()
                        .size());
    }

    @Test
    @DisplayName("each reading size moves the body text, smallest to largest")
    void readingSizesStep() {
        var sizes = new ArrayList<Double>();
        for (var size : ReadingSize.values()) {
            var resolver = new StyleResolver(List.of(
                    Controls.baseStylesheet(),
                    Theme.NORD_DARK.load(),
                    Stylesheet.resource(CascadeLayer.APPLICATION, Showcase.class, "showcase.css"),
                    size.stylesheet()));
            var tree = new ElementTree(new Column(new Text("A label")).id("screen-text"));
            tree.flush();
            sizes.add(inherited(resolver, tree.root(), tree.root().children().getFirst())
                    .typography()
                    .size());
        }

        assertEquals(List.of(TOOLKIT_BODY, BODY, 17.0), sizes);
    }
}
