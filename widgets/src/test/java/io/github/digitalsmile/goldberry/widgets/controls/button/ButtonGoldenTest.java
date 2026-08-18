package io.github.digitalsmile.goldberry.widgets.controls.button;

import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widgets.core.Row;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Selector;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.layout.BoxPainter;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// What a button actually looks like (§14, [ADR-0050]).
///
/// The value assertions in [ButtonTest] check what the cascade *resolved*. These
/// check what Blend2D *drew* — which is a different question, and the one that
/// catches a padding applied to the wrong edge, an icon at the wrong origin, or
/// a variant whose colour never reached the fill.
///
/// No window, no compositor, no `xvfb`: `Frame` paints into memory, so these run
/// identically on all three platforms — which is the point, because Blend2D
/// JITs its pipelines per CPU.
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them. That
/// is a review step: the diff is the only thing that says the change was meant.
class ButtonGoldenTest {

    private Icon icon;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        icon = Icon.bundled("plus", 16);
    }

    @AfterEach
    void tearDown() {
        if (icon != null) {
            icon.close();
        }
    }

    /// A row of buttons, styled by the real cascade and painted.
    ///
    /// The pseudo-classes are set on the elements by hand rather than by moving
    /// a pointer: a golden image is about what a state *looks* like, and
    /// [io.github.digitalsmile.goldberry.input.PointerRouterTest] is about
    /// whether input arrives at it.
    private void paint(String name, Theme theme, Widget row, PseudoState... states) {
        var tree = new ElementTree(row);
        for (var state : states) {
            state.applyTo(tree.root());
        }
        var renderer = new WidgetRenderer(
                List.of(
                        Controls.baseStylesheet(),
                        theme.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, """
                                #row { padding: 12px; gap: 8px; align-items: center;
                                       background: var(--gb-bg) }
                                """)),
                TestFont.get());

        GoldenImage.assertMatches(name, 420, 56, 1.0f,
                frame -> BoxPainter.paint(frame, renderer.render(tree)));
    }

    /// Which element in the row gets which pseudo-class.
    private record PseudoState(int child, Selector.PseudoClass pseudoClass) {
        void applyTo(Element root) {
            root.children().get(child).setPseudoClass(pseudoClass, true);
        }
    }

    private static Attributes id(String id, String... classes) {
        return new Attributes(id, Set.of(classes), id);
    }

    private Widget row(Widget... buttons) {
        return new Row(List.of(buttons), id("row"));
    }

    @Test
    @DisplayName("the four variants, at rest, on the dark theme")
    void variantsDark() {
        paint("button-variants-dark", Theme.NORD_DARK, row(
                new Button("Default", null, null, false, id("a")),
                new Button("Primary", null, null, false, id("b", "primary")),
                new Button("Danger", null, null, false, id("c", "danger")),
                new Button("Ghost", null, null, false, id("d", "ghost"))));
    }

    @Test
    @DisplayName("the same four on the light theme, which is a different set of tokens")
    void variantsLight() {
        // Two files, not one shared rule: the light theme's hover darkens where
        // the dark theme's lightens, and this is where that stops being a claim.
        paint("button-variants-light", Theme.NORD_LIGHT, row(
                new Button("Default", null, null, false, id("a")),
                new Button("Primary", null, null, false, id("b", "primary")),
                new Button("Danger", null, null, false, id("c", "danger")),
                new Button("Ghost", null, null, false, id("d", "ghost"))));
    }

    @Test
    @DisplayName("resting, hovered, pressed, focused and disabled, side by side")
    void states() {
        paint("button-states", Theme.NORD_DARK, row(
                        new Button("Rest", null, null, false, id("a")),
                        new Button("Hover", null, null, false, id("b")),
                        new Button("Active", null, null, false, id("c")),
                        new Button("Focus", null, null, false, id("d")),
                        new Button("Off", null, null, true, id("e"))),
                new PseudoState(1, Selector.PseudoClass.HOVER),
                new PseudoState(2, Selector.PseudoClass.ACTIVE),
                new PseudoState(3, Selector.PseudoClass.FOCUS_VISIBLE));
    }

    @Test
    @DisplayName("an icon sits before the label, and an icon-only button is square-ish")
    void withIcon() {
        // The icon is a box beside the label, laid out by Yoga at the 6-point gap
        // the design system asks for -- not drawn over the top of the button,
        // which is what ADR-0043 had to leave it as.
        paint("button-icon", Theme.NORD_DARK, row(
                new Button("New", icon, null, false, id("a", "primary")),
                new Button("", icon, null, false, id("b")),
                new Button("Disabled", icon, null, true, id("c"))));
    }
}
