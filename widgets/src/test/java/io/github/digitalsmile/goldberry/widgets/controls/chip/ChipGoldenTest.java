package io.github.digitalsmile.goldberry.widgets.controls.chip;

import static io.github.digitalsmile.goldberry.widgets.TestAttributes.id;

import java.util.List;

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
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.core.Row;

/// What a chip looks like (§14, [ADR-0050]).
///
/// Three things here can only be seen in an image, and each is the kind of defect
/// that reports no error at all:
///
/// - **The stadium is a stadium.** `border-radius: 12px` on a 24px box is §1.5's
///   `full`; a height that drifted off 24 would draw a rounded rectangle, and no
///   value assertion could tell.
/// - **The dot is not the ink.** A [ChipDot] takes `background` rather than
///   `color` precisely so a muted chip can carry a live hue, and the failure mode
///   is a dot the same colour as the label — which looks deliberate.
/// - **A chosen chip is findable in a row of unchosen ones**, which is the whole
///   argument for a filled `:checked` rather than an outline ([ADR-0305]).
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them.
class ChipGoldenTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private void paint(String name, Theme theme, int width, int height, Widget content) {
        var renderer = new WidgetRenderer(
                List.of(Controls.baseStylesheet(), theme.load(), Stylesheet.parse(CascadeLayer.APPLICATION, """
                                #row { gap: 8px; padding: 12px; align-items: center;
                                       background: var(--gb-bg) }
                                """)),
                TestFont.get());

        GoldenImage.assertMatches(
                name, width, height, 1.0f, frame -> BoxPainter.paint(frame, renderer.render(new ElementTree(content))));
    }

    /// A row of filters: one chosen, the rest not. The comparison this widget is
    /// for, and the one a single chip could not show.
    @Test
    @DisplayName("chosen and unchosen, side by side")
    void selection() {
        paint(
                "chip-selection-dark",
                Theme.NORD_DARK,
                300,
                48,
                new Row(
                        List.of(
                                new Chip("Unread", true, null),
                                new Chip("Starred"),
                                new Chip("Archived"),
                                new Chip("Muted").disabled(true)),
                        id("row")));
    }

    /// The leading slot, both ways it can be filled — and the × beside a label,
    /// which is the arrangement that needs the label to be a node of its own.
    @Test
    @DisplayName("a dot, and a dismiss")
    void dotAndDismiss() {
        paint(
                "chip-dot-dismiss-dark",
                Theme.NORD_DARK,
                460,
                48,
                new Row(
                        List.of(
                                // Outlined, which is the pairing a dot is for: a
                                // quiet pill with a live status on it.
                                new Chip("Live").withDot(true).styled("outlined", "success"),
                                new Chip("Degraded").withDot(true).styled("outlined", "warning"),
                                // And filled, where the dot has to take the
                                // foreground the fill guarantees contrast against.
                                new Chip("Down").withDot(true).styled("danger"),
                                new Chip("typescript").onDismiss(() -> {}),
                                new Chip("rust").onDismiss(() -> {})),
                        id("row")));
    }

    /// The semantic hues, which share `badge`'s tokens rather than copying them —
    /// so this image is also what would catch the two sets drifting apart.
    @Test
    @DisplayName("every variant, on light")
    void variantsLight() {
        paint(
                "chip-variants-light",
                Theme.NORD_LIGHT,
                450,
                48,
                new Row(
                        List.of(
                                new Chip("default"),
                                new Chip("outlined").styled("outlined"),
                                new Chip("danger").styled("danger"),
                                new Chip("warning").styled("warning"),
                                new Chip("success").styled("success"),
                                new Chip("info").styled("info")),
                        id("row")));
    }
}
