package dev.goldberry.widgets.nav.wizard;

import static dev.goldberry.widgets.TestAttributes.id;

import java.util.List;

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
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.controls.TestFont;
import dev.goldberry.widgets.controls.checkbox.Checkbox;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.text.Text;

/// What a wizard looks like: the indicator on top with 24
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
