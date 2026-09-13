package io.github.digitalsmile.goldberry.markdown.view;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.markdown.Markdown;
import io.github.digitalsmile.goldberry.paint.BoxPainter;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;

/// What a document actually looks like (§14, [ADR-0050]).
///
/// [MarkdownViewTest] checks which widgets are built and which classes they carry, and
/// no assertion about a class can say whether the result **reads as a document**: that
/// a heading is bigger than its paragraph, that emphasis leans, that a fence is a
/// block of monospace on a sunken panel, that a table's rules line up. That is a
/// picture, and this is the picture.
///
/// It is also the only test that exercises `markdown.css` at all. Every rule in that
/// file is a claim about the cascade — `em` resolving against the inherited font size,
/// `border-left` on a quote, `transform: skewX` on a word — and a rule that the parser
/// dropped silently would be invisible everywhere else.
///
/// `./gradlew :html:test -Dgoldberry.golden.update=true` rewrites them.
class MarkdownGoldenTest {

    /// One of everything the renderer can draw, in the order a reader meets them.
    ///
    /// Deliberately not a pretty document: every block type and every inline mark
    /// appears once, because a golden of a paragraph and a heading would not notice a
    /// table losing its borders.
    private static final String DOCUMENT = """
            # A document

            Prose with *emphasis*, **strength**, ~~a change of mind~~, `code()` and
            [a link](http://example.com).

            > A quotation, which holds blocks of its own.

            - [x] Something done
            - [ ] Something not
            - An ordinary bullet

            1. First
            2. Second

            ```java
            var document = Markdown.parse(source);
            ```

            | Face | Weight |
            |:-----|-------:|
            | Inter | 400 |
            | Inter | 600 |

            ---

            The last word.
            """;

    private void paint(String name, Theme theme, int width, int height) {
        RendererRequirement.enforce();
        var tree = new ElementTree(view());
        var renderer = new WidgetRenderer(
                List.of(
                        Controls.baseStylesheet(),
                        MarkdownStyles.stylesheet(),
                        theme.load(),
                        // The frame round the document, so the golden shows the
                        // document rather than the document plus whatever the buffer
                        // was cleared to.
                        Stylesheet.parse(CascadeLayer.APPLICATION, """
                                /* On `selection-host`, which is the node a selectable
                                   document is rooted at (ADR-0301): a background on the
                                   column inside it stops where the words do. */
                                selection-host { padding: 12px; background: var(--gb-bg); flex-grow: 1 }
                                """)),
                TestFonts.get());

        GoldenImage.assertMatches(name, width, height, 1.0f, frame -> BoxPainter.paint(frame, renderer.render(tree)));
    }

    private static Widget view() {
        return MarkdownView.of(Markdown.parse(DOCUMENT)).id("note");
    }

    @Test
    @DisplayName("a document of one of everything, on the dark theme")
    void dark() {
        paint("markdown-dark", Theme.NORD_DARK, 420, 560);
    }

    @Test
    @DisplayName("the same document on the light theme, which is a different set of tokens")
    void light() {
        paint("markdown-light", Theme.NORD_LIGHT, 420, 560);
    }

    /// A selection, as a picture — the only thing that says the wash is **behind** the
    /// words and in the right place.
    ///
    /// `SelectionTest` proves what a drag selects, which is a fact about strings and
    /// was true throughout the whole time the highlight was being painted at the wrong
    /// origin or over the top of the text it highlights. Paint order and coordinate
    /// spaces are pixels (ADR-0301).
    ///
    /// The drag goes through the **router**, because that is what tells each word where
    /// it is: the geometry this draws from is the hit-test capture of a painted frame.
    @Test
    @DisplayName("a selection is washed behind the words, not over them")
    void selection() {
        RendererRequirement.enforce();
        var tree = new ElementTree(view());
        var renderer = renderer(Theme.NORD_DARK);
        var router = new PointerRouter();
        router.focusRoot(tree.root());
        router.windowBounds(LogicalRect.of(0, 0, 420, 560));

        var warm = TestFrames.of(420, 560, 1.0f, 0);
        try (var render = RenderTree.create()) {
            for (var i = 0; i < 2; i++) {
                tree.flush();
                render.update(warm.frame(), renderer.render(tree));
                router.updateRegions(HitTest.capture(render));
            }
            // Across the first paragraph and into the quotation under it, which is two
            // blocks, three lines and a code span -- everything a single-rectangle
            // implementation would get wrong.
            router.pointerPressed(60, 55, PointerEvent.Button.PRIMARY, 1);
            router.pointerMoved(200, 115);
            router.pointerReleased(200, 115, PointerEvent.Button.PRIMARY, 1);
        } finally {
            warm.end();
        }

        GoldenImage.assertMatches("markdown-selection", 420, 560, 1.0f, frame -> {
            try (var render = RenderTree.create()) {
                tree.flush();
                render.update(frame, renderer.render(tree));
                render.paint(frame);
            }
        });
    }

    /// The renderer the pictures above build for themselves, extracted so the drag can
    /// use the same stylesheets.
    private WidgetRenderer renderer(Theme theme) {
        return new WidgetRenderer(
                List.of(
                        Controls.baseStylesheet(),
                        MarkdownStyles.stylesheet(),
                        theme.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, """
                                selection-host { padding: 12px; background: var(--gb-bg); flex-grow: 1 }
                                """)),
                TestFonts.get());
    }
}
