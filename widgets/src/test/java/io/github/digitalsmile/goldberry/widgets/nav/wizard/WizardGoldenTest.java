package io.github.digitalsmile.goldberry.widgets.nav.wizard;

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
import io.github.digitalsmile.goldberry.widgets.controls.checkbox.Checkbox;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// What a wizard looks like (§14, [ADR-0050]): the indicator on top with 24
/// below, the page, and a dialog's bar at the bottom with the affirmative on
/// the right.
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them.
class WizardGoldenTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private void paint(String name, Theme theme, Widget content) {
        var renderer = new WidgetRenderer(
                List.of(Controls.baseStylesheet(), theme.load(), Stylesheet.parse(CascadeLayer.APPLICATION, """
                                #page { padding: 16px; background: var(--gb-bg) }
                                #signup { height: 248px }
                                """)),
                TestFont.get());

        GoldenImage.assertMatches(
                name, 520, 280, 1.0f, frame -> BoxPainter.paint(frame, renderer.render(new ElementTree(content))));
    }

    private static Widget wizard() {
        return new Column(
                List.of(new Wizard(
                                1,
                                new WizardPage("Account", new Text("Who you are.")).describe("Who you are"),
                                new WizardPage(
                                        "Payment",
                                        new Text("How you pay."),
                                        new Checkbox("Remember this card", Checkbox.Value.UNCHECKED)),
                                new WizardPage("Review", new Text("One last look.")))
                        .onBack(() -> {})
                        .onNext(() -> {})
                        .onFinish(() -> {})
                        .withAttributes(id("signup"))),
                id("page"));
    }

    @Test
    @DisplayName("page two of three, on dark")
    void dark() {
        paint("wizard-dark", Theme.NORD_DARK, wizard());
    }

    @Test
    @DisplayName("and the same on light")
    void light() {
        paint("wizard-light", Theme.NORD_LIGHT, wizard());
    }
}
