package io.github.digitalsmile.goldberry.widgets.overlay.tooltip;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.layout.BoxPainter;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.TooltipPanel;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// What a `tooltip` actually looks like — the widget with no coverage at all
/// until it was looked at and found wanting.
///
/// A tooltip is drawn in a popup window of its own, so nothing about it appears
/// in any other image in this corpus: the launcher opens it, and a golden test
/// has no launcher. Rendered here as the panel it is, over a surface, which is
/// what it looks like on screen minus the window.
class TooltipGoldenTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private void paint(String name, Theme theme, int width, String text) {
        var scene = new Row(List.of(new TooltipPanel(text)),
                new io.github.digitalsmile.goldberry.widget.Attributes(
                        "scene", Set.of(), "scene"));
        var renderer = new WidgetRenderer(
                List.of(Controls.baseStylesheet(), theme.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, """
                                #scene { padding: 12px; align-items: center;
                                         background: var(--gb-surface) }
                                """)),
                TestFont.get());

        GoldenImage.assertMatches(name, width, 48, 1.0f,
                frame -> BoxPainter.paint(frame, renderer.render(new ElementTree(scene))));
    }

    @Test
    @DisplayName("a tooltip on the dark theme")
    void dark() {
        paint("tooltip-dark", Theme.NORD_DARK, 260, "Opens a platform popup window");
    }

    @Test
    @DisplayName("a tooltip on the light theme is the same plate")
    void light() {
        paint("tooltip-light", Theme.NORD_LIGHT, 260, "Opens a platform popup window");
    }

    @Test
    @DisplayName("a short one")
    void short_() {
        paint("tooltip-short", Theme.NORD_DARK, 120, "Save");
    }

    /// At 3× so the padding either side of the text can be counted rather than
    /// squinted at — a tooltip is 22 pixels tall, and "looks poor" is a claim
    /// about three of them.
    @Test
    @DisplayName("a tooltip, magnified")
    void magnified() {
        var scene = new Row(List.of(new TooltipPanel("Save the document")),
                new io.github.digitalsmile.goldberry.widget.Attributes(
                        "scene", Set.of(), "scene"));
        var renderer = new WidgetRenderer(
                List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, """
                                #scene { padding: 8px; align-items: center;
                                         background: var(--gb-surface) }
                                """)),
                TestFont.get());

        GoldenImage.assertMatches("tooltip-magnified", 540, 120, 3.0f,
                frame -> BoxPainter.paint(frame, renderer.render(new ElementTree(scene))));
    }
}
