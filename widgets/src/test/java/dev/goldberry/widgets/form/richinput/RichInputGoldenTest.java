package dev.goldberry.widgets.form.richinput;

import java.net.URI;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.golden.GoldenImage;
import dev.goldberry.paint.BoxPainter;
import dev.goldberry.text.mark.MarkKind;
import dev.goldberry.text.mark.MarkedText;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.controls.TestFont;
import dev.goldberry.widgets.core.Column;

/// What the toolkit's stylesheet makes of each kind of mark in a `rich-input`:
/// bold, italic and both; underline and strike-through; code on its step of
/// surface; a link, a mention and a spoiler's wash; and an empty field's
/// placeholder. That a code word in another face sits on the field's own
/// baseline and row is what the picture checks and an assertion cannot.
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them.
class RichInputGoldenTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private void paint(String name, Theme theme) {
        var renderer = new WidgetRenderer(
                List.of(Controls.baseStylesheet(), theme.load(), Stylesheet.parse(CascadeLayer.APPLICATION, """
                                #page { width: 320px; gap: 8px; padding: 16px; background: var(--gb-bg); color: var(--gb-text) }
                                """)),
                TestFont.get());
        GoldenImage.assertMatches(
                name, 320, 180, 1.0f, frame -> BoxPainter.paint(frame, renderer.render(new ElementTree(page()))));
    }

    private static Widget page() {
        var styles = MarkedText.of("bold italic both under strike")
                .mark(0, 4, MarkKind.Style.BOLD)
                .mark(5, 11, MarkKind.Style.ITALIC)
                .mark(12, 16, MarkKind.Style.BOLD)
                .mark(12, 16, MarkKind.Style.ITALIC)
                .mark(17, 22, MarkKind.Style.UNDERLINE)
                .mark(23, 29, MarkKind.Style.STRIKE);
        var references = MarkedText.of("run() see link, @Ann, hidden")
                .mark(0, 5, MarkKind.Style.CODE)
                .mark(10, 14, new MarkKind.Link(URI.create("https://goldberry.dev")))
                .mark(16, 20, new MarkKind.Mention("user:1"))
                .mark(22, 28, MarkKind.Style.SPOILER);
        return new Column(
                List.of(
                        new RichInput(styles, next -> {}),
                        new RichInput(references, next -> {}),
                        new RichInput().placeholder("Message")),
                new Attributes("page", Set.of(), "page"));
    }

    @Test
    @DisplayName("every kind of mark on dark")
    void dark() {
        paint("rich-input-dark", Theme.NORD_DARK);
    }

    @Test
    @DisplayName("and the same on light")
    void light() {
        paint("rich-input-light", Theme.NORD_LIGHT);
    }
}
