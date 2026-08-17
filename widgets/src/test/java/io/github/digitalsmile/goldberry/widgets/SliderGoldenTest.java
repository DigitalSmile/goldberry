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

/// What a slider actually looks like (§14, [ADR-0050]).
///
/// [SliderTest] checks the arithmetic. These check that the arithmetic reached
/// the screen, and they are the only thing that can: the thumb is placed by the
/// **ratio between two flex children** ([ADR-0079]), and every way of getting that
/// wrong — the fill and the rest swapped, the thumb centred regardless, the ratio
/// taken against the range instead of `0..1` — produces a valid layout and a
/// perfectly plausible value assertion.
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them.
class SliderGoldenTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private void paint(String name, Theme theme, int width, int height, Widget content,
            PseudoState... states) {
        var tree = new ElementTree(content);
        for (var state : states) {
            state.applyTo(tree.root());
        }
        var renderer = new WidgetRenderer(
                List.of(
                        Controls.baseStylesheet(),
                        theme.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, """
                                #scene { flex-direction: column; padding: 12px; gap: 8px;
                                         background: var(--gb-bg) }
                                /* A fader is sized by whatever holds it, exactly
                                   as any vertical thing is -- so the scene has to
                                   have a height for `height: 100%` to resolve
                                   against, and it takes the frame's. */
                                #row   { gap: 16px; padding: 12px; height: 100%;
                                         background: var(--gb-bg) }
                                """)),
                TestFont.get());

        GoldenImage.assertMatches(name, width, height, 1.0f,
                frame -> BoxPainter.paint(frame, renderer.render(tree)));
    }

    private record PseudoState(int child, Selector.PseudoClass pseudoClass) {
        void applyTo(Element root) {
            root.children().get(child).setPseudoClass(pseudoClass, true);
        }
    }

    private static Widgets.Attributes id(String id, String... classes) {
        return new Widgets.Attributes(id, Set.of(classes), id);
    }

    private static Slider at(double fraction, String id) {
        return new Slider(0, 100, fraction * 100, 0, null, null, false, id(id));
    }

    @Test
    @DisplayName("0%, 25%, 50%, 75% and 100% — the fill grows and the thumb rides its end")
    void positions() {
        // The image that says the ratio is right. Five sliders in a column, and
        // the thumb centres should step evenly across — which is exactly what a
        // fraction taken against the wrong denominator gets wrong while still
        // laying out perfectly.
        //
        // The ends are the interesting ones: at 0 the thumb sits flush at the
        // left with no fill showing, and at 100 flush at the right with no rest.
        paint("slider-positions", Theme.NORD_DARK, 300, 200, new Widgets.Column(
                List.of(at(0, "a"), at(0.25, "b"), at(0.5, "c"), at(0.75, "d"), at(1, "e")),
                id("scene")));
    }

    @Test
    @DisplayName("the same on light, which is a different set of tokens")
    void light() {
        // The groove darkens where the dark theme's lightens, and the thumb is
        // white on both -- but for opposite reasons: nord6 is the *window* on
        // this theme, so the thumb is `#ffffff`, a step past the palette in the
        // direction it does not otherwise go. That is the lesson ADR-0075 paid
        // for twice on the switch, applied here for free.
        paint("slider-light", Theme.NORD_LIGHT, 300, 200, new Widgets.Column(
                List.of(at(0, "a"), at(0.25, "b"), at(0.5, "c"), at(0.75, "d"), at(1, "e")),
                id("scene")));
    }

    @Test
    @DisplayName("hovered, keyboard-focused and disabled")
    void interactionStates() {
        // The ring is around the *control*, which is the 32-tall hit target and
        // not the 4px groove -- §1.3 gives a slider a target eight times what it
        // can see, and this is the image that shows the difference.
        paint("slider-interaction", Theme.NORD_DARK, 300, 140, new Widgets.Column(
                        List.of(at(0.4, "a"), at(0.4, "b"),
                                new Slider(0, 100, 40, 0, null, null, true, id("c"))),
                        id("scene")),
                new PseudoState(0, Selector.PseudoClass.HOVER),
                new PseudoState(1, Selector.PseudoClass.FOCUS_VISIBLE));
    }

    @Test
    @DisplayName("`slider.vertical` is §3's fader, with its minimum at the bottom")
    void vertical() {
        // `column-reverse` is what puts the minimum at the bottom, and the widget
        // inverts the pointer fraction to match. The two have to agree, and a
        // still frame of a fader at 25% is what says they do: the fill should be
        // a quarter of the way up, not a quarter of the way down.
        paint("slider-vertical", Theme.NORD_DARK, 200, 180, new Widgets.Row(
                List.of(
                        new Slider(0, 100, 25, 0, null, null, false, id("a", "vertical")),
                        new Slider(0, 100, 75, 0, null, null, false, id("b", "vertical"))),
                id("row")));
    }

    /// §3's tick marks, and the image that says the arithmetic behind them is
    /// right ([ADR-0080]).
    ///
    /// The marks are placed by a ratio and the thumb is placed by a *different*
    /// ratio — the marks' row is inset by half a thumb and the thumb's is not —
    /// so every way of getting the inset wrong still lays out, still draws five
    /// evenly spaced marks, and puts the thumb between two of them instead of on
    /// one. Three sliders at 0%, 50% and 100% is what catches it: the thumb has
    /// to cover the first mark, the middle one and the last.
    @Test
    @DisplayName("the thumb sits on a tick mark at both ends and in the middle")
    void ticks() {
        paint("slider-ticks", Theme.NORD_DARK, 300, 140, new Widgets.Column(
                List.of(ticked(0, 5, "a"), ticked(0.5, 5, "b"), ticked(1, 5, "c")),
                id("scene")));
    }

    /// §3's value label, and the two things it changes about the control.
    ///
    /// The track is shorter by the label's width — that is what makes the value a
    /// position along the *track* rather than along the control (ADR-0080) — and
    /// the label's own width is fixed, so 9 and 100 leave the track the same
    /// length. The three rows here are one slider at three values, and the thumb
    /// positions must still step evenly across.
    @Test
    @DisplayName("a value label takes its width off the track and never moves it")
    void valueLabel() {
        paint("slider-value", Theme.NORD_DARK, 300, 140, new Widgets.Column(
                List.of(labelled(0.09, "a"), labelled(0.5, "b"), labelled(1, "c")),
                id("scene")));
    }

    /// The picture the decibel scale exists for.
    ///
    /// Both faders hold **the same gain**, 0.5. The linear one puts its thumb
    /// half way up; the dB one puts it at 90%, because −6 dB is a small step down
    /// from unity and a mixing desk's whole working range is in the top third of
    /// the travel. A scale that reached only one of the two directions — the
    /// thumb but not the pointer, or the reverse — draws exactly this image and
    /// is still wrong, which is what [SliderTest] holds down instead.
    @Test
    @DisplayName("the same gain, linear and in decibels")
    void decibelFader() {
        paint("slider-fader-db", Theme.NORD_DARK, 200, 200, new Widgets.Row(
                List.of(
                        new Slider(0, 1, 0.5, 0, 5, null, Scale.LINEAR, null, null, false,
                                id("a", "vertical")),
                        new Slider(0, 1, 0.5, 0, 5, null, Scale.decibels(), null, null, false,
                                id("b", "vertical"))),
                id("row")));
    }

    private static Slider ticked(double fraction, int ticks, String id) {
        return new Slider(0, 100, fraction * 100, 0, ticks, null, null, null, null, false, id(id));
    }

    private static Slider labelled(double fraction, String id) {
        return new Slider(0, 100, fraction * 100, 0, 0, "%.0f%%", null, null, null, false, id(id));
    }
}
