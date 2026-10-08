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

/// What a styled paragraph looks like: an ability's sentence in a 300 px
/// column, the keyword in gold and bold where it stands and a number tinted,
/// wrapping as one text. That the lines run on across the keyword, and that the
/// bold word sits on the same baseline as the words beside it, is what the
/// picture checks and an assertion cannot.
///
/// Read more: [Text and links](https://goldberry.dev/docs/components/text.html#text).
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them.
class RichTextGoldenTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private void paint(String name, Theme theme) {
        var renderer = new WidgetRenderer(
                List.of(Controls.baseStylesheet(), theme.load(), Stylesheet.parse(CascadeLayer.APPLICATION, """
                                #page { width: 300px; gap: 8px; padding: 16px; background: var(--gb-bg); color: var(--gb-text) }
                                rich-text > run.keyword { color: var(--gb-warning-text); font-weight: bold }
                                rich-text > run.number { color: var(--gb-accent) }
                                """)),
                TestFont.get());

        GoldenImage.assertMatches(
                name, 300, 100, 1.0f, frame -> BoxPainter.paint(frame, renderer.render(new ElementTree(page()))));
    }

    private static Widget page() {
        return new Column(
                List.of(
                        new RichText(
                                new Run("Give it "),
                                new Run("Bleeding", Set.of("keyword")),
                                new Run(" equal to the amount of boost it lost, then reset its power to "),
                                new Run("3", Set.of("number")),
                                new Run(".")),
                        new RichText(new Run("Order", Set.of("keyword")), new Run(": Reset the power of a unit."))),
                new Attributes("page", Set.of(), "page"));
    }

    @Test
    @DisplayName("an ability's text on dark")
    void dark() {
        paint("rich-text-dark", Theme.NORD_DARK);
    }

    @Test
    @DisplayName("and the same on light")
    void light() {
        paint("rich-text-light", Theme.NORD_LIGHT);
    }
}
