package io.github.digitalsmile.goldberry.widgets.nav.steps;

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
import io.github.digitalsmile.goldberry.widgets.core.Row;

/// What a list of steps looks like (§14, [ADR-0050]).
///
/// Three of §6's rules are only checkable in an image: that a done step has a
/// tick and a failed one a cross, so the state is not carried by colour alone;
/// that the connector is filled only behind a done step; and that a vertical
/// list is the same steps turned rather than a different widget.
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them.
class StepsGoldenTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private void paint(String name, Theme theme, int width, int height, Widget content) {
        var renderer = new WidgetRenderer(
                List.of(Controls.baseStylesheet(), theme.load(), Stylesheet.parse(CascadeLayer.APPLICATION, """
                                #page { gap: 24px; padding: 16px; background: var(--gb-bg) }
                                #pair { gap: 32px; align-items: flex-start }
                                """)),
                TestFont.get());

        GoldenImage.assertMatches(
                name, width, height, 1.0f, frame -> BoxPainter.paint(frame, renderer.render(new ElementTree(content))));
    }

    private static Widget page(Theme theme) {
        var horizontal = new Steps(
                2,
                new Step("Account", "Who you are").reachable(true),
                new Step("Payment", "How you pay"),
                new Step("Review", "One last look"),
                new Step("Done"));
        var failed = new Steps(
                3, new Step("Download"), new Step("Verify").error(true), new Step("Install"), new Step("Run"));
        var vertical = new Steps(1, new Step("Draft", "Write it"), new Step("Review", "Read it"), new Step("Publish"))
                .direction(Steps.Direction.VERTICAL);
        var notStarted =
                new Steps(-1, new Step("One"), new Step("Two"), new Step("Three")).direction(Steps.Direction.VERTICAL);
        return new Column(List.of(horizontal, failed, new Row(List.of(vertical, notStarted), id("pair"))), id("page"));
    }

    @Test
    @DisplayName("a row with a current step, a row with a failed one, and two columns")
    void dark() {
        paint("steps-dark", Theme.NORD_DARK, 560, 320, page(Theme.NORD_DARK));
    }

    @Test
    @DisplayName("and the same on light")
    void light() {
        paint("steps-light", Theme.NORD_LIGHT, 560, 320, page(Theme.NORD_LIGHT));
    }
}
