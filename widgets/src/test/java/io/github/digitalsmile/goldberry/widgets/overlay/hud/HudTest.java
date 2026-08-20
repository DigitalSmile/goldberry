package io.github.digitalsmile.goldberry.widgets.overlay.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.FrameStats;
import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import io.github.digitalsmile.goldberry.widgets.Widgets;

/// `hud` — the numbers, where they come from, and the two ways it can be wrong.
///
/// The interesting half is not the arithmetic ([io.github.digitalsmile.goldberry.FrameRingTest]
/// covers that): it is that the numbers arrive on the **render context** rather
/// than in the widget, which is what lets a bare `hud` node in a document show
/// live figures and lets this test show figures it chose.
class HudTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static WidgetRenderer renderer(FrameStats stats) {
        return new WidgetRenderer(
                        List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load(),
                                Stylesheet.parse(CascadeLayer.APPLICATION, "")),
                        TestFont.get())
                .frames(stats);
    }

    /// The text of every reading in a rendered HUD, in order.
    ///
    /// **Without the caption**, which is the last child and is not a reading: it
    /// says what the numbers are rather than being one (ADR-0150). [#caption]
    /// asserts it on its own.
    private static List<String> readings(Box box) {
        var all = box.children();
        return all.subList(0, all.size() - 1).stream()
                .map(child -> child.text().paragraph().text()).toList();
    }

    /// The last child, which explains the ones above it.
    private static String caption(Box box) {
        return box.children().getLast().text().paragraph().text();
    }

    @Test
    @DisplayName("a bare hud shows the rate and the paint time")
    void defaultReadings() {
        var box = renderer(FrameStats.of(60, 16.7, 2.1, 500, 0, 0, 0, 0, 60))
                .render(new ElementTree(new Hud()));

        assertEquals(List.of("60 fps", "paint 2.1 / 2.1 / 2.1 ms"), readings(box));
    }

    /// The one that would have been easy to get wrong: `frame` is `1000 / fps`,
    /// so a HUD showing both by default would spend a third of its width
    /// restating its first number.
    @Test
    @DisplayName("the frame interval is available but not shown unless asked for")
    void frameIsOptional() {
        assertEquals(List.of(Reading.FPS, Reading.PAINT), Hud.DEFAULT);

        var box = renderer(FrameStats.of(60, 16.7, 2.1, 500, 0, 0, 0, 0, 60))
                .render(new ElementTree(new Hud(Reading.FPS, Reading.REFRESH, Reading.PAINT)));

        assertEquals(List.of("60 fps", "refresh 60 Hz", "paint 2.1 / 2.1 / 2.1 ms"), readings(box));
    }

    /// The breakdown — [ADR-0146].
    ///
    /// A total tells you a frame is slow; the stages tell you *which* part of it
    /// is. The showcase spent a month at 10ms a frame with the cascade running
    /// uncached, and nothing on screen could have said which of the four it was.
    @Test
    @DisplayName("`stages` shows where the frame went, two decimals at a time")
    void stages() {
        var box = renderer(FrameStats.of(60, 16.7, 2.1, 500, 0.05, 0.29, 0.11, 1.34, 60))
                .render(new ElementTree(Hud.stages()));

        assertEquals(List.of("60 fps", "refresh 60 Hz", "paint 2.1 / 2.1 / 2.1 ms",
                        "build 0.05 / 0.05 / 0.05 ms", "style 0.29 / 0.29 / 0.29 ms", "layout 0.11 / 0.11 / 0.11 ms", "raster 1.34 / 1.34 / 1.34 ms"),
                readings(box));
    }

    /// **Two decimals for a stage and one for a total**, which is not a
    /// preference: a stage that reads `0.0 ms` cannot be told from a stage that
    /// is not running, and telling those apart is the whole use of a breakdown.
    @Test
    @DisplayName("a stage under a tenth of a millisecond still reads as a number")
    void stagesKeepTheirPrecision() {
        var box = renderer(FrameStats.of(60, 16.7, 2.1, 500, 0.04, 0.0, 0, 0, 60))
                .render(new ElementTree(new Hud(Reading.BUILD, Reading.STYLE)));

        assertEquals(List.of("build 0.04 / 0.04 / 0.04 ms", "style 0.00 / 0.00 / 0.00 ms"), readings(box));
    }

    @Test
    @DisplayName("`readings=\"stages\"` is the whole breakdown in one word")
    void stagesFromMarkup() {
        var hud = (Hud) io.github.digitalsmile.goldberry.widgets.Widgets.inflater()
                .inflate(io.github.digitalsmile.goldberry.kdl.KdlParser
                        .parse("hud readings=\"stages\"").getFirst());

        assertEquals(Hud.STAGES, hud.readings());
    }

    /// A source that does not measure the stages reports zero for them, which is
    /// every source but the frame loop's own — and a HUD with no loop over it
    /// still says so with dashes rather than with four zeroes.
    @Test
    @DisplayName("no frame loop reads as dashes for the stages too")
    void stagesWithNoLoop() {
        var box = renderer(FrameStats.none()).render(new ElementTree(Hud.stages()));

        assertEquals(List.of("— fps", "refresh —", "paint —",
                "build —", "style —", "layout —", "raster —"), readings(box));
    }

    /// A zero is a measurement. A HUD rendered with no frame loop over it has not
    /// measured anything, and saying `0 fps` there would be a claim about a loop
    /// that is not being observed at all.
    @Test
    @DisplayName("no frame loop reads as dashes, not as zero")
    void noLoopIsNotZero() {
        var box = renderer(FrameStats.none())
                .render(new ElementTree(new Hud(Reading.FPS, Reading.REFRESH, Reading.PAINT)));

        assertEquals(List.of("— fps", "refresh —", "paint —"), readings(box));

        // A loop that genuinely stopped dead is a different thing and reads
        // differently, which is the distinction the dashes exist for.
        var stalled = renderer(FrameStats.of(0, 0, 0, 900))
                .render(new ElementTree(new Hud(Reading.FPS)));
        assertEquals(List.of("0 fps"), readings(stalled));
    }

    /// §5's rule about a locale-formatted number inside the toolkit, applied to
    /// numbers that are the toolkit's own: they are formatted the one way that is
    /// the same on every machine, or the golden images below are a lottery.
    @Test
    @DisplayName("the numbers are formatted in the root locale, whatever the machine's is")
    void rootLocale() {
        var previous = Locale.getDefault();
        try {
            // A locale whose decimal separator is a comma, which is what would
            // turn `16.7 ms` into `16,7 ms` on a CI runner in Berlin.
            Locale.setDefault(Locale.GERMANY);
            var box = renderer(FrameStats.of(60, 16.7, 2.1, 500, 0, 0, 0, 0, 60))
                    .render(new ElementTree(new Hud(Reading.REFRESH)));

            assertEquals(List.of("refresh 60 Hz"), readings(box));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    @DisplayName("each reading is selectable on its own")
    void readingsAreParts() {
        var hud = new Hud(Reading.FPS, Reading.PAINT);
        var children = hud.children();

        assertEquals("hud", hud.cssType());
        // Two readings and the caption that says what they are (ADR-0150).
        assertEquals(3, children.size());
        assertEquals("hud-reading", ((Styled) children.getFirst()).cssType());
        assertTrue(((Styled) children.getFirst()).classes().contains("fps"));
        assertTrue(((Styled) children.get(1)).classes().contains("paint"));
        assertEquals("hud-caption", ((Styled) children.get(2)).cssType());
    }

    /// **What the numbers are** — [ADR-0150].
    ///
    /// Every reading is a mean over the ring's whole window, and `paint 2.1 ms`
    /// reads as "this frame" until something says otherwise. A spike looks like a
    /// plateau on the way in and a plateau looks like a spike on the way out, and
    /// a reader with the wrong model draws the wrong conclusion from all of them.
    @Test
    @DisplayName("the caption says the numbers are per-frame means over the ring")
    void caption() {
        var live = renderer(FrameStats.of(60, 16.7, 2.1, 500, 0, 0, 0, 0, 60))
                .render(new ElementTree(new Hud()));
        // A fixed source keeps no window, so there is no length to name and the
        // caption says only what it can stand behind.
        assertEquals("ms/frame · min / mean / max", caption(live));

        var empty = renderer(FrameStats.none()).render(new ElementTree(new Hud()));
        assertEquals("no frames measured", caption(empty),
                "and a HUD with no loop behind it does not describe a window it has not filled");
    }

    /// **A reading colours itself against a budget** — [ADR-0150].
    ///
    /// The class comes from
    /// [Styled#classes(io.github.digitalsmile.goldberry.FrameStats)] rather than
    /// from `classes()`, because the cascade reads a node's classes before its
    /// `render` runs and the statistics only arrive in `render`.
    @Test
    @DisplayName("a reading over its budget classes itself `over`, and near it `near`")
    void budgetLevels() {
        var reading = Reading.PAINT;

        assertEquals(8.33, reading.budgetMillis(
                FrameStats.of(60, 16.7, 2, 9, 0, 0, 0, 0, 60)), 0.01,
                "half a frame of a 60 Hz display");
        assertEquals(4.17, reading.budgetMillis(
                FrameStats.of(120, 8.3, 2, 9, 0, 0, 0, 0, 120)), 0.01,
                "and half a frame of a 120 Hz one — the budget follows the display");
        assertTrue(new HudReading(reading).classes(FrameStats.of(60, 16.7, 2.0, 9, 0, 0, 0, 0, 60))
                .contains("ok"), "2 ms of an 8 ms budget is fine");
        assertTrue(new HudReading(reading).classes(FrameStats.of(60, 16.7, 6.5, 9, 0, 0, 0, 0, 60))
                .contains("near"), "6.5 of 8 is three quarters of the way there");
        assertTrue(new HudReading(reading).classes(FrameStats.of(60, 16.7, 9.0, 9, 0, 0, 0, 0, 60))
                .contains("over"), "9 of 8 is over");
    }

    /// **The rate is never coloured, and that is the decision** — [ADR-0153].
    ///
    /// §1.7 makes the loop idle when nothing asks for a frame, so a rate counted
    /// between frames measures how long the user did not touch the window. It
    /// collapses the moment they stop clicking and stays low for the next sixty
    /// frames, and colouring it turned normal idling into an alarm.
    @Test
    @DisplayName("the rate is context, not a budget: it is never coloured")
    void rateIsNeverColoured() {
        for (var stats : List.of(
                FrameStats.of(60, 16.7, 2, 9, 0, 0, 0, 0, 60),
                FrameStats.of(45, 22, 2, 9, 0, 0, 0, 0, 60),
                FrameStats.of(2, 500, 2, 9, 0, 0, 0, 0, 60))) {
            assertTrue(new HudReading(Reading.FPS).classes(stats).contains("ok"),
                    () -> "an idle loop is not a fault: " + stats.fps() + " fps");
        }
    }

    /// **The display's refresh rate is the only rate a platform can be asked
    /// for**, and it is what every budget is a share of — so the same paint time
    /// is fine at 60 Hz and over budget at 120 (ADR-0153).
    @Test
    @DisplayName("a budget is a share of the display's frame, not of a hard-coded 60 Hz")
    void budgetsFollowTheDisplay() {
        var sixty = FrameStats.of(60, 16.7, 5.0, 9, 0, 0, 0, 0, 60);
        var oneTwenty = FrameStats.of(120, 8.3, 5.0, 9, 0, 0, 0, 0, 120);

        assertTrue(new HudReading(Reading.PAINT).classes(sixty).contains("ok"),
                "5 ms of a 60 Hz display's 8.3 ms paint budget");
        assertTrue(new HudReading(Reading.PAINT).classes(oneTwenty).contains("over"),
                "the same 5 ms against a 120 Hz display's 4.2 ms");
    }

    /// A platform that will not say what the display does — a headless backend,
    /// or a mode SDL cannot describe — falls back to 60 Hz, and says so in the
    /// reading rather than pretending to know.
    @Test
    @DisplayName("an unknown display rate reads as dashes and budgets as 60 Hz")
    void unknownDisplayRate() {
        var unknown = FrameStats.of(60, 16.7, 2.0, 9, 0, 0, 0, 0, 0);

        assertEquals(List.of("refresh —"),
                readings(renderer(unknown).render(new ElementTree(new Hud(Reading.REFRESH)))));
        assertEquals(8.33, Reading.PAINT.budgetMillis(unknown), 0.01);
    }

    /// A HUD with no loop behind it draws dashes, and dashes in red would be an
    /// alarm about nothing.
    @Test
    @DisplayName("nothing measured is not over budget")
    void nothingMeasuredIsNotAnAlarm() {
        for (var reading : Reading.values()) {
            assertTrue(new HudReading(reading).classes(FrameStats.none()).contains("ok"),
                    () -> reading + " raised an alarm about a loop it has not seen");
        }
    }

    @Test
    @DisplayName("a document writes `hud`, with or without a list of readings")
    void fromKdl() {
        var bare = Widgets.inflater().inflate(KdlParser.parse("hud").getFirst());
        assertInstanceOf(Hud.class, bare);
        assertEquals(Hud.DEFAULT, ((Hud) bare).readings());

        var chosen = Widgets.inflater()
                .inflate(KdlParser.parse("hud readings=\"fps refresh\" class=\"dim\"").getFirst());
        assertEquals(List.of(Reading.FPS, Reading.REFRESH), ((Hud) chosen).readings());
        assertTrue(((Hud) chosen).classes().contains("dim"));
    }

    @Test
    @DisplayName("a reading nobody has heard of is a refusal that names the ones there are")
    void refusesAnUnknownReading() {
        var refused = assertThrows(IllegalArgumentException.class,
                () -> Widgets.inflater().inflate(KdlParser.parse("hud readings=\"gpu\"").getFirst()));

        assertTrue(refused.getMessage().contains("fps"), refused.getMessage());
    }

    @Test
    @DisplayName("an empty list of readings is the default, not an empty hud")
    void emptyIsTheDefault() {
        assertEquals(Hud.DEFAULT, new Hud(List.of(), Attributes.NONE).readings());
    }
}
