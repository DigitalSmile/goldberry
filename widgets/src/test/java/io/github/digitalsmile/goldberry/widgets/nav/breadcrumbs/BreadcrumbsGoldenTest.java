package io.github.digitalsmile.goldberry.widgets.nav.breadcrumbs;

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
import io.github.digitalsmile.goldberry.widgets.core.Column;

/// What a trail looks like (§14, [ADR-0050]).
///
/// Two of §6's rules are only checkable in an image:
///
/// - **The current crumb is not a link.** It is the strong weight in full ink
///   with no fill, and what would go wrong — a filled current crumb that reads as
///   a button — is a stylesheet change no assertion would catch.
/// - **The separator is a mark, not a character.** A chevron drawn in
///   `--gb-text-muted` beside labels in `--gb-text` is the one thing that proves
///   it never joined the text run; a `>` typed between two labels would take
///   their colour and shape with them, and would look almost right
///   ([ADR-0306]).
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them.
class BreadcrumbsGoldenTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private void paint(String name, Theme theme, int width, int height, Widget content) {
        var renderer = new WidgetRenderer(
                List.of(Controls.baseStylesheet(), theme.load(), Stylesheet.parse(CascadeLayer.APPLICATION, """
                                #page { gap: 12px; padding: 12px; background: var(--gb-bg) }
                                """)),
                TestFont.get());

        GoldenImage.assertMatches(
                name, width, height, 1.0f, frame -> BoxPainter.paint(frame, renderer.render(new ElementTree(content))));
    }

    private static Widget trail(String... steps) {
        var crumbs = new Widget[steps.length];
        for (var index = 0; index < steps.length; index++) {
            crumbs[index] = new Crumb(steps[index], () -> {});
        }
        return new Breadcrumbs(crumbs);
    }

    /// A short path and a collapsed one, one above the other: the comparison is
    /// the point, because what the `…` replaces is invisible in either alone.
    @Test
    @DisplayName("a path that fits, and one that does not")
    void shortAndCollapsed() {
        paint(
                "breadcrumbs-dark",
                Theme.NORD_DARK,
                420,
                96,
                new Column(
                        List.of(
                                trail("Home", "Library", "The Red Book"),
                                trail("Home", "Library", "Reference", "Shire", "Hobbiton", "The Red Book")),
                        id("page")));
    }

    @Test
    @DisplayName("and the same on light, where the muted ink has the least room")
    void onLight() {
        // §1.2's 4.5:1 is hardest for `--gb-text-muted` on the light theme, and a
        // trail is nearly all muted ink — so this is the theme where a separator
        // that vanished would vanish.
        paint(
                "breadcrumbs-light",
                Theme.NORD_LIGHT,
                420,
                96,
                new Column(
                        List.of(
                                trail("Home", "Library", "The Red Book"),
                                trail("Home", "Library", "Reference", "Shire", "Hobbiton", "The Red Book")),
                        id("page")));
    }
}
