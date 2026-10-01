package dev.goldberry.widgets.text;

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
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.controls.TestFont;
import dev.goldberry.widgets.core.Column;

/// What a link looks like (§14, [ADR-0050]): the link ink, the muted visited
/// one, and the external icon after the word — in the same ink, which is the
/// thing an image checks and an assertion cannot.
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them.
class LinkGoldenTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private void paint(String name, Theme theme, Widget content) {
        var renderer = new WidgetRenderer(
                List.of(Controls.baseStylesheet(), theme.load(), Stylesheet.parse(CascadeLayer.APPLICATION, """
                                #page { gap: 8px; padding: 16px; background: var(--gb-bg) }
                                """)),
                TestFont.get());

        GoldenImage.assertMatches(
                name, 320, 120, 1.0f, frame -> BoxPainter.paint(frame, renderer.render(new ElementTree(content))));
    }

    private static Widget page() {
        return new Column(
                List.of(
                        new Link("Read the docs", () -> {}),
                        new Link("Where you have been", () -> {}).visited(true),
                        Link.external("Goldberry on GitHub", "https://example.org")),
                new Attributes("page", Set.of(), "page"));
    }

    @Test
    @DisplayName("three links on dark")
    void dark() {
        paint("link-dark", Theme.NORD_DARK, page());
    }

    @Test
    @DisplayName("and the same on light")
    void light() {
        paint("link-light", Theme.NORD_LIGHT, page());
    }
}
