package io.github.digitalsmile.goldberry.html.view;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.html.Html;
import io.github.digitalsmile.goldberry.paint.BoxPainter;
import io.github.digitalsmile.goldberry.text.font.Fonts;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;

/// What a page actually looks like (§14, [ADR-0050]).
///
/// `HtmlViewTest` checks which widgets are built and which classes they carry, and no
/// assertion about a class can say whether the result **reads as a page**: that a
/// heading is bigger than its paragraph, that emphasis leans, that a `pre` is a block
/// of monospace on a sunken panel, that a link looks like a link rather than like a
/// button somebody forgot to colour in. That is a picture, and this is the picture.
///
/// It is also the only test that exercises `html.css` at all. Every rule in that file
/// is a claim about the cascade — `button.html-a` taking a control's height back down
/// to its line, `transform: skewX` on a word, a quotation's bar — and a rule the
/// parser dropped silently would be invisible everywhere else.
///
/// `./gradlew :html:test -Dgoldberry.golden.update=true` rewrites them.
class HtmlGoldenTest {

    /// One of everything the renderer can draw, in the order a reader meets them.
    ///
    /// Deliberately not a pretty page: every block and every inline mark appears once,
    /// and two of the lines are *malformed* — an unclosed `<p>` and two `<li>`s with no
    /// close tags — because those are the recoveries an authored document actually
    /// needs and a golden of well-formed HTML would not notice them going wrong.
    private static final String PAGE = """
            <h1>A page</h1>
            <p>Prose with <em>emphasis</em>, <strong>strength</strong>, <del>a change of mind</del>,
            <code>code()</code> and <a href="https://example.com">a link</a>.
            <blockquote><p>A quotation, which holds blocks of its own.</p></blockquote>
            <ul>
              <li>An item
              <li>Another, with <mark>a highlight</mark> in it
            </ul>
            <ol start="3"><li>Third</li><li>Fourth</li></ol>
            <dl><dt>Term</dt><dd>What it means</dd></dl>
            <pre><code>var document = Html.parse(source);
            var view = HtmlView.of(document);</code></pre>
            <table>
              <thead><tr><th>Face</th><th align="right">Weight</th></tr></thead>
              <tbody>
                <tr><td>Inter</td><td align="right">400</td></tr>
                <tr><td>Inter</td><td align="right">600</td></tr>
              </tbody>
            </table>
            <hr>
            <p>AT&amp;T, &#38; the last word. <img src="none.png" alt="[a picture]"></p>
            """;

    private void paint(String name, Theme theme, int width, int height) {
        RendererRequirement.enforce();
        var tree = new ElementTree(view());
        var renderer = new WidgetRenderer(
                List.of(
                        Controls.baseStylesheet(),
                        HtmlStyles.stylesheet(),
                        theme.load(),
                        // The frame round the page, so the golden shows the page rather
                        // than the page plus whatever the buffer was cleared to.
                        Stylesheet.parse(CascadeLayer.APPLICATION, """
                                /* On `selection-host`, which is the node a selectable
                                   document is rooted at (ADR-0301): a background on the
                                   column inside it stops where the words do. */
                                selection-host { padding: 12px; background: var(--gb-bg); flex-grow: 1 }
                                """)),
                fonts());

        GoldenImage.assertMatches(name, width, height, 1.0f, frame -> BoxPainter.paint(frame, renderer.render(tree)));
    }

    /// The bundled faces. `TestFonts` is the Markdown view's package-private helper and
    /// this is the same four lines, for the reason that one gives about not putting a
    /// test helper in the module's surface.
    private static Fonts fonts() {
        var fonts = Fonts.bundled();
        fonts.of(BundledFont.UI, 13);
        return fonts;
    }

    private static Widget view() {
        return HtmlView.of(Html.parse(PAGE)).id("page");
    }

    @Test
    @DisplayName("a page of one of everything, on the dark theme")
    void dark() {
        paint("html-dark", Theme.NORD_DARK, 460, 720);
    }

    @Test
    @DisplayName("the same page on the light theme, which is a different set of tokens")
    void light() {
        paint("html-light", Theme.NORD_LIGHT, 460, 720);
    }

    /// The `button.link` variant is a control with states, and a golden of a page at
    /// rest cannot show them — but it can show that a link in a sentence sits **on the
    /// line**, which is the one rule in `html.css` that overrides a deliberate decision
    /// of the toolkit's (ADR-0293's 32px floor; see the rule's own comment).
    @Test
    @DisplayName("a link inside a sentence, on the line rather than in a 32px box")
    void linkOnItsLine() {
        RendererRequirement.enforce();
        var tree = new ElementTree(
                HtmlView.of("<p>Before <a href=\"/x\">the link</a> after.</p>").id("page"));
        var renderer = new WidgetRenderer(
                List.of(
                        Controls.baseStylesheet(),
                        HtmlStyles.stylesheet(),
                        Theme.NORD_DARK.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, """
                                /* On `selection-host`, which is the node a selectable
                                   document is rooted at (ADR-0301): a background on the
                                   column inside it stops where the words do. */
                                selection-host { padding: 12px; background: var(--gb-bg); flex-grow: 1 }
                                """)),
                fonts());

        GoldenImage.assertMatches("html-link", 320, 60, 1.0f, frame -> BoxPainter.paint(frame, renderer.render(tree)));
    }
}
