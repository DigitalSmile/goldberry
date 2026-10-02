package dev.goldberry.widgets.controls.progressbar;

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
import dev.goldberry.motion.Clock;
import dev.goldberry.paint.BoxPainter;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.controls.TestFont;
import dev.goldberry.widgets.controls.spinner.Spinner;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.Row;

/// What a progress bar and a spinner look like, held as golden images.
///
/// These are the only tests that can see two of the things this change is about.
/// The spinner's ring is **three cubics and a stroke** — a value assertion can say
/// the mark is an `ARC` and cannot say the arc closed, or drew inside its box, or
/// left the gap that makes the rotation visible. And a sweep is a picture of a
/// moment: the whole point of a virtual clock is that a frame 300 ms into a
/// 1.2 s loop is a still image that is exactly reproducible, which is
/// impossible against a wall clock.
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them.
class ProgressGoldenTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private void paint(String name, Theme theme, int width, int height, double now, boolean reduced, Widget content) {
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

        GoldenImage.assertMatches(
                name, width, height, 1.0f, frame -> BoxPainter.paint(frame, renderer.render(new ElementTree(content))));
    }

    @Test
    @DisplayName("0%, 40% and 100% — the fill is a width and the track shows through")
    void determinate() {
        // The image that says the value became a *width*. Every way of getting
        // that wrong -- the fraction against the wrong denominator, the fill
        // grown instead of sized, the track painted over -- lays out perfectly.
        paint(
                "progress-determinate",
                Theme.NORD_DARK,
                300,
                100,
                0,
                false,
                new Column(List.of(new Progress(0), new Progress(0.4), new Progress(1)), id("scene")));
    }

    @Test
    @DisplayName("the same on light")
    void light() {
        paint(
                "progress-light",
                Theme.NORD_LIGHT,
                300,
                100,
                0,
                false,
                new Column(List.of(new Progress(0), new Progress(0.4), new Progress(1)), id("scene")));
    }

    /// The bar **part way in**, cut off by the track's leading edge — 180 ms into
    /// the loop, where its left edge is still 0.105 of a track to the left of the
    /// groove and only the trailing two thirds of it are drawn.
    ///
    /// This is the image that says the clip is real. Without `overflow: hidden` on
    /// `progress` the bar would be drawn whole, hanging into the gap above the
    /// second bar, and the picture would look perfectly plausible.
    ///
    /// Two bars in one frame, and they are **identical on purpose**: a sweep is a
    /// function of the clock alone, so two of them in a window are in step. A
    /// controller started at mount would draw this image with the two bars in
    /// different places, and nothing about it would look broken enough to
    /// investigate.
    @Test
    @DisplayName("a sweep 180ms into its loop, entering under the leading edge")
    void sweeping() {
        paint(
                "progress-sweeping",
                Theme.NORD_DARK,
                300,
                80,
                180,
                false,
                new Column(List.of(Progress.sweeping(), Progress.sweeping()), id("scene")));
    }

    /// The other end, at 1080 ms — the bar **leaving**, cut off by the far edge,
    /// with its leading tenth already outside the track.
    ///
    /// The pair is what says the travel goes **through** the track rather than
    /// bouncing inside it: the same bar is clipped on the left in one image and on
    /// the right in the other, and there is no frame in which it sits flush against
    /// an edge and waits. A bar that reversed would show a whole bar in both.
    ///
    /// 1080 rather than 1200, because at 1200 the bar is entirely outside the clip
    /// and the picture is an empty groove — true, and a golden of nothing.
    @Test
    @DisplayName("and at 1080ms, leaving under the far edge")
    void sweepingAtTheEnd() {
        paint(
                "progress-sweeping-end",
                Theme.NORD_DARK,
                300,
                60,
                1080,
                false,
                new Column(List.of(Progress.sweeping()), id("scene")));
    }

    /// Under reduced motion the sweep becomes an opacity pulse, and the thing to
    /// look for is that there is
    /// still a bar: a reduced-motion user gets a control that says "working",
    /// not an empty groove.
    @Test
    @DisplayName("reduced motion holds the bar still across a third of the track")
    void sweepingReduced() {
        paint(
                "progress-reduced",
                Theme.NORD_DARK,
                300,
                60,
                600,
                true,
                new Column(List.of(Progress.sweeping()), id("scene")));
    }

    /// The picture the arc exists for, and the only thing that can see it: a
    /// value assertion can say the mark is an `ARC` and cannot say the arc closed,
    /// drew inside its box, or left the gap that makes a rotation visible.
    ///
    /// At zero the gap is at the top, because the sweep starts at −90°.
    @Test
    @DisplayName("a ring with a gap, three of them in step")
    void spinner() {
        paint(
                "spinner-turning",
                Theme.NORD_DARK,
                160,
                60,
                0,
                false,
                new Row(List.of(new Spinner(id("a")), new Spinner(id("b")), new Spinner(id("c"))), id("row")));
    }

    /// Half a turn later, so the gap is at the bottom. Two images rather than one
    /// with three angles in it, because a spinner has no per-instance phase to
    /// stagger — which is the property a stateless sweep has, and the reason it
    /// takes a second frame to show that the thing turns at all.
    @Test
    @DisplayName("450ms later, the gap is at the bottom")
    void spinnerHalfTurn() {
        paint(
                "spinner-half-turn",
                Theme.NORD_DARK,
                160,
                60,
                450,
                false,
                new Row(List.of(new Spinner(id("a")), new Spinner(id("b")), new Spinner(id("c"))), id("row")));
    }
}
