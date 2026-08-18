package io.github.digitalsmile.goldberry.widgets.controls.badge;

import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import io.github.digitalsmile.goldberry.widgets.panel.Panel;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.layout.BoxPainter;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.controls.badge.Badge;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// What a badge looks like (§14, [ADR-0050]).
///
/// Two things here can only be seen in an image. **The pill is a pill** —
/// `border-radius: 10px` on a 20px box is §1.5's `full`, and a chip whose height
/// drifted off 20 would draw a rounded rectangle that is not obviously wrong at
/// any single value assertion. And **the text is centred in a height it did not
/// choose**: the chip pins 20px with no vertical padding, so the label sits where
/// `align-items: center` puts it, and the failure mode — text hard against the
/// top with 6px of fill below — is a layout that reports no error at all
/// ([ADR-0087]).
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them.
class BadgeGoldenTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private void paint(String name, Theme theme, int width, int height, Widget content) {
        var renderer = new WidgetRenderer(
                List.of(
                        Controls.baseStylesheet(),
                        theme.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, """
                                #row   { gap: 8px; padding: 12px; align-items: center;
                                         background: var(--gb-bg) }
                                #panel { gap: 8px; padding: 12px; align-items: center;
                                         background: var(--gb-surface) }
                                """)),
                TestFont.get());

        GoldenImage.assertMatches(name, width, height, 1.0f,
                frame -> BoxPainter.paint(frame, renderer.render(new ElementTree(content))));
    }

    private static Attributes id(String id) {
        return new Attributes(id, Set.of(), id);
    }

    private static Widget everyVariant(String rowId) {
        return new Row(
                List.of(
                        new Badge("3"),
                        new Badge("12").styled("accent"),
                        new Badge("offline").styled("danger"),
                        new Badge("beta").styled("warning"),
                        new Badge("live").styled("success"),
                        new Badge("new").styled("info")),
                id(rowId));
    }

    /// The row this change exists for: six chips, six fills, six foregrounds —
    /// and three of those foregrounds are `--nord0` on a theme whose text is
    /// `--nord6`, which is exactly the thing [ContrastTest] proves and this shows.
    @Test
    @DisplayName("every variant, on dark")
    void variantsDark() {
        paint("badge-variants-dark", Theme.NORD_DARK, 340, 44, everyVariant("row"));
    }

    /// The same six on light, and the pairings are **identical** for the four
    /// aurora hues: those palette entries do not change between themes, so neither
    /// does the foreground they can carry. Only the neutral and the accent differ,
    /// and this is the image that says so.
    @Test
    @DisplayName("every variant, on light")
    void variantsLight() {
        paint("badge-variants-light", Theme.NORD_LIGHT, 340, 44, everyVariant("row"));
    }

    /// On `--gb-surface` rather than `--gb-bg`, which is the gap
    /// `controls-on-surface-*` exists to close ([ADR-0073]). A badge paints its
    /// own opaque fill in every variant, so it cannot disappear against a panel —
    /// but the *default* chip is filled with `--gb-surface-2`, which is one step
    /// from the panel it sits on, and one step is exactly the distance worth
    /// having an image of.
    @Test
    @DisplayName("a default chip is still a chip on a panel")
    void onSurface() {
        paint("badge-on-surface", Theme.NORD_DARK, 200, 44, new Panel(
                List.of(new Badge("3"), new Badge("128"), new Badge("1024")),
                id("panel")));
    }

    /// One, two, three and four digits. The chip grows with its content and its
    /// height does not move, which is the whole of what `padding: 0 8px` on a
    /// pinned height means.
    ///
    /// There is no minimum width, so `3` is a stadium and not the circle a badge
    /// usually is — §8's subset has no `min-width` at all, and this is the image
    /// that records it rather than a comment claiming it.
    @Test
    @DisplayName("it grows sideways and never taller")
    void digits() {
        paint("badge-digits", Theme.NORD_DARK, 240, 44, new Row(
                List.of(new Badge("3"), new Badge("12"), new Badge("128"), new Badge("1024")),
                id("row")));
    }
}
