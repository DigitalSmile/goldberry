package dev.goldberry.html.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.assets.BundledFont;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.kdl.KdlParser;
import dev.goldberry.paint.Box;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widget.style.StyleScope;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.markup.Wiring;

/// A document's own `<style>` and `style=`, as `html-view` applies them: on the page's
/// widgets, in a scope of their own, with the colours as [AuthorStyles] says.
@DisplayName("html-view's author styles")
class HtmlAuthorStylesTest {

    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;

    private static List<Element> walk(Element root) {
        var all = new ArrayList<Element>();
        all.add(root);
        root.children().forEach(child -> all.addAll(walk(child)));
        return all;
    }

    private static List<Box> boxes(Box root) {
        var all = new ArrayList<Box>();
        all.add(root);
        root.children().forEach(child -> all.addAll(boxes(child)));
        return all;
    }

    /// The box of the word `word`, from the first frame of `view` under `css`.
    private static Box word(Widget view, String css, String word) {
        return boxes(render(view, css)).stream()
                .filter(box ->
                        box.text() != null && box.text().paragraph().text().equals(word))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no word \"" + word + "\" was painted"));
    }

    private static Box render(Widget view, String css) {
        RendererRequirement.enforce();
        var fonts = Fonts.bundled();
        fonts.of(BundledFont.UI, 13);
        var renderer = new WidgetRenderer(
                List.of(
                        Controls.baseStylesheet(),
                        HtmlStyles.stylesheet(),
                        Theme.NORD_DARK.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, css)),
                fonts);
        return renderer.render(new ElementTree(view));
    }

    private static Optional<Box> withBackground(Box root, int argb) {
        return boxes(root).stream().filter(box -> box.background() == argb).findFirst();
    }

    @Nested
    @DisplayName("in the tree")
    class InTheTree {

        @Test
        @DisplayName("puts the page in a scope holding its rules, and a generated class on the styled element")
        void scope() {
            var elements = walk(new ElementTree(HtmlView.of("<p style=\"padding: 4px\">x</p>")).root());

            var scope = elements.stream()
                    .map(Element::widget)
                    .filter(StyleScope.class::isInstance)
                    .map(StyleScope.class::cast)
                    .findFirst()
                    .orElseThrow();
            assertFalse(scope.rules().isEmpty());
            assertTrue(elements.stream()
                    .anyMatch(e -> e.classes().contains("html-p")
                            && e.classes().stream().anyMatch(name -> name.startsWith("html-author-"))));
        }

        @Test
        @DisplayName("keeps the scope and an empty rule list for a page with no styles of its own")
        void emptyScope() {
            var elements = walk(new ElementTree(HtmlView.of("<p>x</p>")).root());

            assertTrue(elements.stream()
                    .map(Element::widget)
                    .anyMatch(widget ->
                            widget instanceof StyleScope scope && scope.rules().isEmpty()));
        }

        @Test
        @DisplayName("applies nothing when told to ignore them")
        void ignored() {
            var view = HtmlView.of("<p style=\"padding: 4px\">x</p>").authorStyles(AuthorStyles.IGNORED);
            var elements = walk(new ElementTree(view).root());

            assertTrue(elements.stream()
                    .noneMatch(e -> e.classes().stream().anyMatch(name -> name.startsWith("html-author-"))));
        }

        @Test
        @DisplayName("refuses to forward a document's class that reads as a generated one")
        void noBorrowing() {
            var elements = walk(new ElementTree(HtmlView.of("<p class=\"author-1 note\">x</p>")).root());

            assertTrue(elements.stream().anyMatch(e -> e.classes().contains("html-note")));
            assertTrue(elements.stream().noneMatch(e -> e.classes().contains("html-author-1")));
        }

        @Test
        @DisplayName("reads author-styles= from markup, adapted when it says nothing")
        void markup() {
            var themed = KdlParser.parse("html-view author-styles=\"theme-colors\" \"<p>x</p>\"")
                    .getFirst();
            var plain = KdlParser.parse("html-view \"<p>x</p>\"").getFirst();

            assertEquals(
                    AuthorStyles.THEME_COLOURS,
                    assertInstanceOf(HtmlView.class, HtmlView.inflate(themed, List.of(), Wiring.none()))
                            .styles());
            assertEquals(
                    AuthorStyles.ADAPTED,
                    assertInstanceOf(HtmlView.class, HtmlView.inflate(plain, List.of(), Wiring.none()))
                            .styles());
        }
    }

    @Nested
    @DisplayName("on screen")
    class OnScreen {

        @Test
        @DisplayName("a mail's own rule beats the application's more specific one for the same element")
        void beatsTheApplication() {
            var view =
                    HtmlView.of("<p style=\"background-color: #ff0000\">x</p>").authorStyles(AuthorStyles.AS_WRITTEN);

            var root = render(view, ".html .html-p.html-prose { background: #0000ff }");

            assertTrue(withBackground(root, RED).isPresent());
            assertTrue(withBackground(root, BLUE).isEmpty());
        }

        @Test
        @DisplayName("an application's !important still wins, which is how it insists")
        void importantWins() {
            var view =
                    HtmlView.of("<p style=\"background-color: #ff0000\">x</p>").authorStyles(AuthorStyles.AS_WRITTEN);

            var root = render(view, ".html-p { background: #0000ff !important }");

            assertTrue(withBackground(root, BLUE).isPresent());
        }

        @Test
        @DisplayName("one mail's <style> reaches its own page and not the page beside it")
        void staysOnItsPage() {
            var mail = HtmlView.of("<style>p { background-color: #ff0000 }</style><p>one</p>")
                    .authorStyles(AuthorStyles.AS_WRITTEN);
            var other = HtmlView.of("<p>two</p>");

            var root = render(new Column(mail, other), "");

            assertEquals(
                    1,
                    boxes(root).stream().filter(box -> box.background() == RED).count());
        }

        @Test
        @DisplayName("text on a white card the mail paints is dark, even on the dark theme")
        void adaptedInk() {
            var view = HtmlView.of("<div style=\"background:#ffffff\"><p style=\"color:#eeeeee\">faint</p></div>");

            assertEquals(0xFF1F2328, word(view, "", "faint").text().argb());
        }

        @Test
        @DisplayName("a colour with no ground of the mail's is the theme's when adapted, and as written when asked")
        void looseInk() {
            var html = "<p style=\"color:#cc0000\">loose</p>";

            assertNotEquals(
                    0xFFCC0000, word(HtmlView.of(html), "", "loose").text().argb());
            assertEquals(
                    0xFFCC0000,
                    word(HtmlView.of(html).authorStyles(AuthorStyles.AS_WRITTEN), "", "loose")
                            .text()
                            .argb());
        }

        @Test
        @DisplayName("an inline element's colour reaches its words, the innermost winning")
        void inlineWords() {
            var view = HtmlView.of("<p><span style=\"color:#00ff00\">outer <b style=\"color:#0000ff\">inner</b></span>"
                            + "</p>")
                    .authorStyles(AuthorStyles.AS_WRITTEN);

            var paragraph = word(view, "", "outer inner").text();
            assertEquals(
                    0xFF00FF00,
                    paragraph.spans().stream()
                            .filter(s -> s.covers(0))
                            .findFirst()
                            .orElseThrow()
                            .argb());
            assertEquals(
                    BLUE,
                    paragraph.spans().stream()
                            .filter(s -> s.covers(6))
                            .findFirst()
                            .orElseThrow()
                            .argb());
        }

        @Test
        @DisplayName("display: none hides the preview line a mail puts first")
        void hidden() {
            var view = HtmlView.of("<div style=\"display:none\">preheader</div><p>body</p>");
            var root = render(view, "");

            assertTrue(boxes(root).stream()
                    .noneMatch(box ->
                            box.text() != null && box.text().paragraph().text().equals("preheader")));
        }

        @Test
        @DisplayName("nothing a mail writes moves a box out of the page or fetches a picture")
        void refused() {
            var view = HtmlView.of("<p style=\"position:fixed; margin-top:-500px;"
                            + " background:url(https://tracker.example/p.gif)\">x</p>")
                    .authorStyles(AuthorStyles.AS_WRITTEN);
            var elements = walk(new ElementTree(view).root());

            assertTrue(elements.stream()
                    .noneMatch(e -> e.classes().stream().anyMatch(name -> name.startsWith("html-author-"))));
        }
    }
}
