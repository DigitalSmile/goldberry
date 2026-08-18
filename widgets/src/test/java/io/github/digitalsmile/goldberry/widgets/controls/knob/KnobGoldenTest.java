package io.github.digitalsmile.goldberry.widgets.controls.knob;

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
import io.github.digitalsmile.goldberry.widgets.controls.knob.Knob;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// What a knob looks like (§14, [ADR-0050]).
///
/// The only tests that can see the thing this control is: **an arc whose sweep is
/// a number**. A value assertion says `KnobArc` was handed 0.5 and cannot say the
/// ring went half way round, started at seven-thirty, or left its 90° gap at the
/// bottom — and every way of getting an arc wrong (the sign of the sweep, the
/// start measured from twelve instead of three, the two rings drawn at different
/// radii) lays out perfectly and reports nothing ([ADR-0089]).
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them.
class KnobGoldenTest {

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
                                #row   { gap: 16px; padding: 12px; align-items: center;
                                         background: var(--gb-bg) }
                                #panel { gap: 16px; padding: 12px; align-items: center;
                                         background: var(--gb-surface) }
                                """)),
                TestFont.get());

        GoldenImage.assertMatches(name, width, height, 1.0f,
                frame -> BoxPainter.paint(frame, renderer.render(new ElementTree(content))));
    }

    private static Attributes id(String id) {
        return new Attributes(id, Set.of(), id);
    }

    private static Widget row(String rowId, Widget... knobs) {
        return new Row(List.of(knobs), id(rowId));
    }

    /// The image the whole change is for. Five knobs at 0, ¼, ½, ¾ and 1, and what
    /// it asserts is that **the arc grows clockwise from seven-thirty** and that
    /// the gap at the bottom is the same 90° in all five — the track is drawn by
    /// one node and the value by another, so a start angle that disagreed between
    /// them would show here and nowhere else.
    ///
    /// The knob at 0 has **no arc at all** rather than a stub: a zero sweep draws
    /// nothing, which `Arc.addTo` already did and `KnobArc` relies on.
    @Test
    @DisplayName("the arc runs clockwise from seven-thirty, and zero draws none of it")
    void travel() {
        paint("knob-travel", Theme.NORD_DARK, 280, 56, row("row",
                new Knob(0, 1, 0.0, 0, null),
                new Knob(0, 1, 0.25, 0, null),
                new Knob(0, 1, 0.5, 0, null),
                new Knob(0, 1, 0.75, 0, null),
                new Knob(0, 1, 1.0, 0, null)));
    }

    @Test
    @DisplayName("the same travel on light")
    void light() {
        paint("knob-light", Theme.NORD_LIGHT, 280, 56, row("row",
                new Knob(0, 1, 0.0, 0, null),
                new Knob(0, 1, 0.25, 0, null),
                new Knob(0, 1, 0.5, 0, null),
                new Knob(0, 1, 0.75, 0, null),
                new Knob(0, 1, 1.0, 0, null)));
    }

    /// §3's two diameters, side by side and at the same value.
    ///
    /// The ring's stroke is a **constant** 2px rather than a proportion (§1.6's
    /// line weight, which is [io.github.digitalsmile.goldberry.widgets.controls.spinner.Spinner]'s answer to the same gap), so this is the
    /// image that says a 48px knob is a bigger circle and not a scaled drawing.
    @Test
    @DisplayName("32 and 48, and the ring is the same weight on both")
    void diameters() {
        paint("knob-diameters", Theme.NORD_DARK, 140, 72, row("row",
                new Knob(0, 1, 0.6, 0, null),
                new Knob(0, 1, 0.6, 0, null).styled("large")));
    }

    /// On `--gb-surface` rather than `--gb-bg`, which is the gap
    /// `controls-on-surface-*` exists to close ([ADR-0073]) — and the knob is a
    /// real candidate for it, because its track is `--gb-border` and its body is
    /// one step from the panel it sits on.
    @Test
    @DisplayName("the track still reads as a channel on a panel")
    void onSurface() {
        paint("knob-on-surface", Theme.NORD_DARK, 200, 56, new Panel(
                List.of(
                        new Knob(0, 1, 0.0, 0, null),
                        new Knob(0, 1, 0.4, 0, null),
                        new Knob(0, 1, 1.0, 0, null)),
                id("panel")));
    }

    /// §2.1's disabled: 45% on the whole control, never a colour remap. Both rings
    /// fade with the body, which is what one `opacity` on a subtree buys and eight
    /// muted tokens would not ([ADR-0071]).
    @Test
    @DisplayName("disabled fades the rings with the body")
    void disabled() {
        paint("knob-disabled", Theme.NORD_DARK, 140, 56, row("row",
                new Knob(0, 1, 0.6, 0, null),
                new Knob(0, 1, 0.6, 0, 0, null, null, true, Attributes.NONE)));
    }
}
