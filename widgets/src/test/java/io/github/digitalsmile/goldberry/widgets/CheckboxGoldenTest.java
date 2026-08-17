package io.github.digitalsmile.goldberry.widgets;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Selector;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.layout.BoxPainter;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.Widgets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// What a checkbox actually looks like (§14, [ADR-0050]).
///
/// [CheckboxTest] checks what the cascade resolved. These check what Blend2D
/// drew, and they are the only thing that can: a tick is a stroked path with
/// round caps, and no value assertion anywhere says whether it landed inside the
/// 16px glyph or across the label.
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them.
class CheckboxGoldenTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

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
                                #row { padding: 12px; gap: 16px; align-items: center;
                                       background: var(--gb-bg) }
                                """)),
                TestFont.get());

        GoldenImage.assertMatches(name, 460, 56, 1.0f,
                frame -> BoxPainter.paint(frame, renderer.render(tree)));
    }

    /// Which element gets which pseudo-class. `part` reaches inside a control to
    /// its glyph, which is the one thing a checkbox needs and a button did not.
    private record PseudoState(int child, Integer part, Selector.PseudoClass pseudoClass) {

        static PseudoState on(int child, Selector.PseudoClass pseudoClass) {
            return new PseudoState(child, null, pseudoClass);
        }

        void applyTo(Element root) {
            var element = root.children().get(child);
            if (part != null) {
                element = element.children().get(part);
            }
            element.setPseudoClass(pseudoClass, true);
        }
    }

    private static Widgets.Attributes id(String id, String... classes) {
        return new Widgets.Attributes(id, Set.of(classes), id);
    }

    private Widget row(Widget... children) {
        return new Widgets.Row(List.of(children), id("row"));
    }

    private static Checkbox box(String label, Checkbox.Value value, String id) {
        return new Checkbox(label, value, null, null, false, id(id));
    }

    @Test
    @DisplayName("the three states, on the dark theme")
    void statesDark() {
        // The one image that says mixed is distinguishable from both of the
        // others at a glance -- which is the whole argument for `:indeterminate`
        // being its own pseudo-class rather than a modifier on `:checked`.
        paint("checkbox-states-dark", Theme.NORD_DARK, row(
                box("Off", Checkbox.Value.UNCHECKED, "a"),
                box("On", Checkbox.Value.CHECKED, "b"),
                box("Some", Checkbox.Value.MIXED, "c")));
    }

    @Test
    @DisplayName("the same three on the light theme, which is a different set of tokens")
    void statesLight() {
        // The tick is nord0 on dark and nord6 on light: a light fill needs a dark
        // mark and a dark fill needs a light one, which is why both are tokens
        // rather than one shared value (§1.2's 4.5:1).
        paint("checkbox-states-light", Theme.NORD_LIGHT, row(
                box("Off", Checkbox.Value.UNCHECKED, "a"),
                box("On", Checkbox.Value.CHECKED, "b"),
                box("Some", Checkbox.Value.MIXED, "c")));
    }

    @Test
    @DisplayName("hovered, keyboard-focused and disabled")
    void interactionStates() {
        // The ring is around the *control*, not the glyph, and clears the label:
        // a checkbox is one Tab stop and one hit target, so it gets one ring.
        // The disabled one fades its glyph, its border and its label together at
        // 45%, because the painter multiplies opacity down the subtree.
        paint("checkbox-interaction", Theme.NORD_DARK, row(
                        box("Hover", Checkbox.Value.UNCHECKED, "a"),
                        box("Focus", Checkbox.Value.CHECKED, "b"),
                        new Checkbox("Off", Checkbox.Value.CHECKED, null, null, true, id("c"))),
                new PseudoState(0, 0, Selector.PseudoClass.HOVER),
                PseudoState.on(1, Selector.PseudoClass.FOCUS_VISIBLE));
    }
}
