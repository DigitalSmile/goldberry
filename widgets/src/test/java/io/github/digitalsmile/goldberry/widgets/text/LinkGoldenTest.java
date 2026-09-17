package io.github.digitalsmile.goldberry.widgets.text;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.paint.BoxPainter;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.core.Column;

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
