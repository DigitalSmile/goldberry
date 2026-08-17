package io.github.digitalsmile.goldberry.widgets;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.layout.BoxPainter;
import io.github.digitalsmile.goldberry.motion.Clock;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.Widgets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// What a progress bar and a spinner look like (§14, [ADR-0050]).
///
/// These are the only tests that can see two of the things this change is about.
/// The spinner's ring is **three cubics and a stroke** — a value assertion can say
/// the mark is an `ARC` and cannot say the arc closed, or drew inside its box, or
/// left the gap that makes the rotation visible. And a sweep is a picture of a
/// moment: the whole point of a virtual clock is that a frame 300 ms into a
/// 1.2 s loop is a still image that is exactly reproducible
/// ([ADR-0067](../../../../../../../book/src/adr/0067-motion-is-an-overlay-on-a-frame-clock.md)),
/// which is impossible against a wall clock.
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them.
class ProgressGoldenTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private void paint(String name, Theme theme, int width, int height, double now,
            boolean reduced, Widget content) {
        var clock = Clock.virtual();
        var renderer = new WidgetRenderer(
                List.of(
                        Controls.baseStylesheet(),
                        theme.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, """
                                #scene { flex-direction: column; padding: 12px; gap: 12px;
                                         align-items: center; background: var(--gb-bg) }
                                #row   { gap: 16px; padding: 12px; align-items: center;
                                         background: var(--gb-bg) }
                                """)),
                TestFont.get())
                .clock(clock)
                .reducedMotion(reduced);
        clock.advance(now);

        GoldenImage.assertMatches(name, width, height, 1.0f,
                frame -> BoxPainter.paint(frame, renderer.render(new ElementTree(content))));
    }

    private static Widgets.Attributes id(String id) {
        return new Widgets.Attributes(id, Set.of(), id);
    }

    @Test
    @DisplayName("0%, 40% and 100% — the fill is a width and the track shows through")
    void determinate() {
        // The image that says the value became a *width*. Every way of getting
        // that wrong -- the fraction against the wrong denominator, the fill
        // grown instead of sized, the track painted over -- lays out perfectly.
        paint("progress-determinate", Theme.NORD_DARK, 300, 100, 0, false, new Widgets.Column(
                List.of(new Progress(0), new Progress(0.4), new Progress(1)),
                id("scene")));
    }

    @Test
    @DisplayName("the same on light")
    void light() {
        paint("progress-light", Theme.NORD_LIGHT, 300, 100, 0, false, new Widgets.Column(
                List.of(new Progress(0), new Progress(0.4), new Progress(1)),
                id("scene")));
    }

    /// The bar a third of the way into its travel, entering from the left.
    ///
    /// Two bars in one frame, and they are **identical on purpose**: a sweep is a
    /// function of the clock alone, so two of them in a window are in step. A
    /// controller started at mount would draw this image with the two bars in
    /// different places, and nothing about it would look broken enough to
    /// investigate ([ADR-0081]).
    @Test
    @DisplayName("a sweep 180ms into its loop, and two of them agreeing")
    void sweeping() {
        paint("progress-sweeping", Theme.NORD_DARK, 300, 80, 180, false, new Widgets.Column(
                List.of(Progress.sweeping(), Progress.sweeping()),
                id("scene")));
    }

    /// The far end of the same loop, at 600 ms — flush against the right-hand
    /// edge and about to turn back.
    ///
    /// The pair of images is what says the travel is **inside** the track: the
    /// bar reaches the edge and stops there rather than carrying on past it,
    /// which is what a toolkit with no `overflow: hidden` has to do (ADR-0081).
    @Test
    @DisplayName("and at 600ms, flush against the far end")
    void sweepingAtTheEnd() {
        paint("progress-sweeping-end", Theme.NORD_DARK, 300, 60, 600, false, new Widgets.Column(
                List.of(Progress.sweeping()),
                id("scene")));
    }

    /// §3.1's reduced-motion answer, and the thing to look for is that there is
    /// still a bar: a reduced-motion user gets a control that says "working",
    /// not an empty groove.
    @Test
    @DisplayName("reduced motion holds the bar still across a third of the track")
    void sweepingReduced() {
        paint("progress-reduced", Theme.NORD_DARK, 300, 60, 600, true, new Widgets.Column(
                List.of(Progress.sweeping()),
                id("scene")));
    }

    /// The picture the arc exists for, and the only thing that can see it: a
    /// value assertion can say the mark is an `ARC` and cannot say the arc closed,
    /// drew inside its box, or left the gap that makes a rotation visible.
    ///
    /// At zero the gap is at the top, because the sweep starts at −90°.
    @Test
    @DisplayName("a ring with a gap, three of them in step")
    void spinner() {
        paint("spinner-turning", Theme.NORD_DARK, 160, 60, 0, false, new Widgets.Row(
                List.of(new Spinner(id("a")), new Spinner(id("b")), new Spinner(id("c"))),
                id("row")));
    }

    /// Half a turn later, so the gap is at the bottom. Two images rather than one
    /// with three angles in it, because a spinner has no per-instance phase to
    /// stagger — which is the property ADR-0081 wanted and the reason it takes a
    /// second frame to show that the thing turns at all.
    @Test
    @DisplayName("450ms later, the gap is at the bottom")
    void spinnerHalfTurn() {
        paint("spinner-half-turn", Theme.NORD_DARK, 160, 60, 450, false, new Widgets.Row(
                List.of(new Spinner(id("a")), new Spinner(id("b")), new Spinner(id("c"))),
                id("row")));
    }
}
