package io.github.digitalsmile.goldberry.widgets.controls.toggle;

import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widgets.core.Row;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Selector;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.layout.BoxPainter;
import io.github.digitalsmile.goldberry.motion.Clock;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.controls.toggle.Toggle;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// What a switch actually looks like (§14, [ADR-0050]).
///
/// [ToggleTest] checks the numbers the cascade resolved. These check where the
/// thumb *landed*, and they are the only thing that can: §3's travel 16 is a
/// transform applied to a box inside another box, and every way of getting it
/// wrong — the thumb centred, the thumb overhanging the pill, the pill sliding
/// with the thumb because the transform went on the wrong node — resolves to
/// exactly the same values.
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them.
class ToggleGoldenTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private void paint(String name, Theme theme, Widget row, PseudoState... states) {
        var tree = new ElementTree(row);
        for (var state : states) {
            state.applyTo(tree.root());
        }
        GoldenImage.assertMatches(name, 460, 56, 1.0f,
                frame -> BoxPainter.paint(frame, renderer(theme, null).render(tree)));
    }

    private static WidgetRenderer renderer(Theme theme, Clock.Virtual clock) {
        var renderer = new WidgetRenderer(
                List.of(
                        Controls.baseStylesheet(),
                        theme.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, """
                                #row { padding: 12px; gap: 16px; align-items: center;
                                       background: var(--gb-bg) }
                                """)),
                TestFont.get());
        return clock == null ? renderer : renderer.clock(clock);
    }

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

    private static Attributes id(String id) {
        return new Attributes(id, Set.of(), id);
    }

    private Widget row(Widget... children) {
        return new Row(List.of(children), id("row"));
    }

    @Test
    @DisplayName("off and on, dark — the thumb is at each end of its travel")
    void statesDark() {
        // The image that says the pill adds up: 2 + 16 + 16 + 2 = 36, so the
        // thumb sits flush inside each end rather than hanging over one.
        paint("toggle-states-dark", Theme.NORD_DARK, row(
                new Toggle("Off", false, null, null, false, id("a")),
                new Toggle("On", true, null, null, false, id("b"))));
    }

    @Test
    @DisplayName("the same two on light, where the thumb inverts")
    void statesLight() {
        // The two themes reach opposite answers for the thumb: light-on-grey to
        // dark-on-accent on the dark theme, dark-on-grey to light-on-accent
        // here, because no one colour clears §1.2 against both pills. This is
        // the image where that is visible rather than argued (ADR-0075).
        paint("toggle-states-light", Theme.NORD_LIGHT, row(
                new Toggle("Off", false, null, null, false, id("a")),
                new Toggle("On", true, null, null, false, id("b"))));
    }

    @Test
    @DisplayName("hovered, keyboard-focused and disabled")
    void interactionStates() {
        // The ring is around the *control* and clears both the pill and the
        // label: a toggle is one Tab stop and one hit target, so it gets one
        // ring. The disabled one fades the pill, the thumb and the label
        // together at 45%, because the painter multiplies opacity down the
        // subtree -- which is why there is no `toggle-track:disabled` rule.
        paint("toggle-interaction", Theme.NORD_DARK, row(
                        new Toggle("Hover", false, null, null, false, id("a")),
                        new Toggle("Focus", true, null, null, false, id("b")),
                        new Toggle("Off", true, null, null, true, id("c"))),
                PseudoState.on(0, Selector.PseudoClass.HOVER),
                PseudoState.on(1, Selector.PseudoClass.FOCUS_VISIBLE));
    }

    @Test
    @DisplayName("a frame 80ms into the slide, where the thumb is between the ends")
    void midTravel() {
        // §3.1: "thumb translate base; track color base (**same clock -- they
        // arrive together**)". This is the frame that asserts they do: at half
        // of the 160ms base duration the thumb is mid-track *and* the pill is
        // mid-colour -- and the pill is still 36 wide, which is what fails if
        // the transform went on the track instead of the thumb, since a
        // transform applies down its whole subtree.
        //
        // A picture no wall clock can take (ADR-0067's virtual clock).
        var clock = Clock.virtual();
        var renderer = renderer(Theme.NORD_DARK, clock);

        // Bound, and driven by setting the property, because the elements have
        // to survive: a node built with its new value has no previous style to
        // move from and would snap. This is also the shipping route -- data down
        // through the binding, exactly as an application would (ADR-0063).
        var offToOn = Property.of(false);
        var onToOff = Property.of(true);
        var tree = new ElementTree(row(
                Toggle.of("Off", offToOn, value -> { }),
                Toggle.of("On", onToOff, value -> { })));

        // Frame one establishes the resting style. Nothing transitions on a
        // first frame -- a control appearing is not a control changing.
        renderer.render(tree);
        assertFalse(renderer.isAnimating());

        offToOn.set(true);
        onToOff.set(false);
        if (tree.needsBuild()) {
            tree.flush();
        }
        renderer.render(tree);
        assertTrue(renderer.isAnimating(), "both switches started moving");

        clock.advance(80);
        var midway = renderer.render(tree);
        assertTrue(renderer.isAnimating(), "and neither has arrived");

        GoldenImage.assertMatches("toggle-mid-travel", 460, 56, 1.0f,
                frame -> BoxPainter.paint(frame, midway));
    }
}
