package dev.goldberry.widgets.overlay.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.kdl.KdlParser;
import dev.goldberry.paint.Box;
import dev.goldberry.stats.FrameStats;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.Widgets;
import dev.goldberry.widgets.controls.TestFont;

/// `hud` — the numbers, where they come from, and the two ways it can be wrong.
///
/// The interesting half is not the arithmetic
/// ([dev.goldberry.stats.FrameRingTest]
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
                        List.of(
                                Controls.baseStylesheet(),
                                Theme.NORD_DARK.load(),
                                Stylesheet.parse(CascadeLayer.APPLICATION, "")),
                        TestFont.get())
                .frames(stats);
    }

    /// The text of every reading in a rendered HUD, in order.
    ///
    /// **Without the caption**, which is the last child and is not a reading: it
    /// says what the numbers are rather than being one. [#caption]
    /// asserts it on its own.
    private static List<String> readings(Box box) {
        var all = box.children();
        return all.subList(0, all.size() - 1).stream()
                .map(child -> child.text().paragraph().text())
                .toList();
    }

    /// The last child, which explains the ones above it.
    private static String caption(Box box) {
        return box.children().getLast().text().paragraph().text();
    }

    @Test
    @DisplayName("a bare hud shows the rate and the paint time")
    void defaultReadings() {
        var box = renderer(FrameStats.of(60, 16.7, 2.1, 500, 0, 0, 0, 0, 60)).render(new ElementTree(new Hud()));

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

    /// The breakdown into stages.
    ///
    /// A total tells you a frame is slow; the stages tell you *which* part of it
    /// is. The showcase spent a month at 10ms a frame with the cascade running
    /// uncached, and nothing on screen could have said which of the four it was.
    @Test
    @DisplayName("`stages` shows where the frame went, two decimals at a time")
    void stages() {
        var box = renderer(FrameStats.of(60, 16.7, 2.1, 500, 0.05, 0.29, 0.11, 1.34, 60))
                .render(new ElementTree(Hud.stages()));

        assertEquals(
                List.of(
                        "60 fps",
                        "refresh 60 Hz",
                        "late 0",
                        "paint 2.1 / 2.1 / 2.1 ms",
                        "build 0.05 / 0.05 / 0.05 ms",
                        "style 0.29 / 0.29 / 0.29 ms",
                        "layout 0.11 / 0.11 / 0.11 ms",
                        "raster 1.34 / 1.34 / 1.34 ms"),
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
        var hud = (Hud) dev.goldberry.widgets.Widgets.inflater()
                .inflate(dev.goldberry.kdl.KdlParser.parse("hud readings=\"stages\"")
                        .getFirst());

        assertEquals(Hud.STAGES, hud.readings());
    }

    /// A source that does not measure the stages reports zero for them, which is
    /// every source but the frame loop's own — and a HUD with no loop over it
    /// still says so with dashes rather than with four zeroes.
    @Test
    @DisplayName("no frame loop reads as dashes for the stages too")
    void stagesWithNoLoop() {
        var box = renderer(FrameStats.none()).render(new ElementTree(Hud.stages()));

        assertEquals(
                List.of("— fps", "refresh —", "late —", "paint —", "build —", "style —", "layout —", "raster —"),
                readings(box));
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
        var stalled = renderer(FrameStats.of(0, 0, 0, 900)).render(new ElementTree(new Hud(Reading.FPS)));
        assertEquals(List.of("0 fps"), readings(stalled));
    }

    /// The toolkit never formats a number by the machine's locale, and that
    /// applies to numbers that are the toolkit's own: they are formatted the one
    /// way that is the same on every machine, or the golden images are a lottery.
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

        // Two readings and the caption that says what they are.
        assertEquals(3, children.size());
        assertEquals("hud-reading", ((Styled) children.getFirst()).cssType());
        assertTrue(((Styled) children.getFirst()).classes().contains("fps"));
        assertTrue(((Styled) children.get(1)).classes().contains("paint"));
        assertEquals("hud-caption", ((Styled) children.get(2)).cssType());
    }

    /// **What the numbers are.**
    ///
    /// Every reading is a mean over the ring's whole window, and `paint 2.1 ms`
    /// reads as "this frame" until something says otherwise. A spike looks like a
    /// plateau on the way in and a plateau looks like a spike on the way out, and
    /// a reader with the wrong model draws the wrong conclusion from all of them.
    @Test
    @DisplayName("the caption says the numbers are per-frame means over the ring")
    void caption() {
        var live = renderer(FrameStats.of(60, 16.7, 2.1, 500, 0, 0, 0, 0, 60)).render(new ElementTree(new Hud()));
        // A fixed source keeps no window, so there is no length to name and the
        // caption says only what it can stand behind.
        assertEquals("ms/frame · min / mean / max", caption(live));

        var empty = renderer(FrameStats.none()).render(new ElementTree(new Hud()));
        assertEquals(
                "no frames measured",
                caption(empty),
                "and a HUD with no loop behind it does not describe a window it has not filled");
    }

    /// **The frames that did not happen.**
    ///
    /// Every other reading is a mean over the frames that were painted, so a
    /// frame the loop never reached leaves no record and a frame the platform
    /// refused after it was painted is in the mean as though somebody had seen
    /// it. This is the only number here that is about neither.
    @Test
    @DisplayName("`late` counts the frames nobody saw, as whole frames")
    void lateFrames() {
        var box = renderer(FrameStats.of(58, 17.2, 2.1, 500, 0, 0, 0, 0, 60, 3))
                .render(new ElementTree(new Hud(Reading.FPS, Reading.LATE)));

        // No unit and no decimal: these are whole frames, and there is no such
        // thing as two thirds of one.
        assertEquals(List.of("58 fps", "late 3"), readings(box));
    }

    /// A dropped frame is not automatically an alarm. A resize refuses a frame
    /// that was painted for the size the window has just stopped being, and that
    /// is ordinary rather than exotic — so one is worth noticing and four in a
    /// window of sixty is stutter somebody can see.
    @Test
    @DisplayName("one late frame is worth noticing and four is a problem")
    void lateLevels() {
        var none = FrameStats.of(60, 16.7, 2.1, 500, 0, 0, 0, 0, 60, 0);
        var some = FrameStats.of(60, 16.7, 2.1, 500, 0, 0, 0, 0, 60, 1);
        var many = FrameStats.of(60, 16.7, 2.1, 500, 0, 0, 0, 0, 60, 4);

        assertTrue(new HudReading(Reading.LATE).classes(none).contains("ok"), "nothing dropped is nothing to say");
        assertTrue(new HudReading(Reading.LATE).classes(some).contains("near"), "one is worth noticing");
        assertTrue(new HudReading(Reading.LATE).classes(many).contains("over"), "four in sixty is visible stutter");
        // And a HUD with no loop over it is not reporting a healthy one: dashes
        // in red would be an alarm about nothing.
        assertTrue(new HudReading(Reading.LATE).classes(FrameStats.none()).contains("ok"));
    }

    @Test
    @DisplayName("`late` is one of the names a document may ask for")
    void lateFromMarkup() {
        var hud = (Hud) dev.goldberry.widgets.Widgets.inflater()
                .inflate(dev.goldberry.kdl.KdlParser.parse("hud readings=\"fps late\"")
                        .getFirst());

        assertEquals(List.of(Reading.FPS, Reading.LATE), hud.readings());
    }

    /// **A reading colours itself against a budget.**
    ///
    /// The class comes from
    /// [Styled#classes(FrameStats)] rather than
    /// from `classes()`, because the cascade reads a node's classes before its
    /// `render` runs and the statistics only arrive in `render`.
    @Test
    @DisplayName("a reading over its budget classes itself `over`, and near it `near`")
    void budgetLevels() {
        var reading = Reading.PAINT;

        assertEquals(
                8.33,
                reading.budgetMillis(FrameStats.of(60, 16.7, 2, 9, 0, 0, 0, 0, 60)),
                0.01,
                "half a frame of a 60 Hz display");
        assertEquals(
                4.17,
                reading.budgetMillis(FrameStats.of(120, 8.3, 2, 9, 0, 0, 0, 0, 120)),
                0.01,
                "and half a frame of a 120 Hz one — the budget follows the display");
        assertTrue(
                new HudReading(reading)
                        .classes(FrameStats.of(60, 16.7, 2.0, 9, 0, 0, 0, 0, 60))
                        .contains("ok"),
                "2 ms of an 8 ms budget is fine");
        assertTrue(
                new HudReading(reading)
                        .classes(FrameStats.of(60, 16.7, 6.5, 9, 0, 0, 0, 0, 60))
                        .contains("near"),
                "6.5 of 8 is three quarters of the way there");
        assertTrue(
                new HudReading(reading)
                        .classes(FrameStats.of(60, 16.7, 9.0, 9, 0, 0, 0, 0, 60))
                        .contains("over"),
                "9 of 8 is over");
    }

    /// **The rate is never coloured, and that is the decision.**
    ///
    /// The loop idles when nothing asks for a frame, so a rate counted
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
            assertTrue(
                    new HudReading(Reading.FPS).classes(stats).contains("ok"),
                    () -> "an idle loop is not a fault: " + stats.fps() + " fps");
        }
    }

    /// **The display's refresh rate is the only rate a platform can be asked
    /// for**, and it is what every budget is a share of — so the same paint time
    /// is fine at 60 Hz and over budget at 120.
    @Test
    @DisplayName("a budget is a share of the display's frame, not of a hard-coded 60 Hz")
    void budgetsFollowTheDisplay() {
        var sixty = FrameStats.of(60, 16.7, 5.0, 9, 0, 0, 0, 0, 60);
        var oneTwenty = FrameStats.of(120, 8.3, 5.0, 9, 0, 0, 0, 0, 120);

        assertTrue(
                new HudReading(Reading.PAINT).classes(sixty).contains("ok"),
                "5 ms of a 60 Hz display's 8.3 ms paint budget");
        assertTrue(
                new HudReading(Reading.PAINT).classes(oneTwenty).contains("over"),
                "the same 5 ms against a 120 Hz display's 4.2 ms");
    }

    /// A platform that will not say what the display does — a headless backend,
    /// or a mode SDL cannot describe — falls back to 60 Hz, and says so in the
    /// reading rather than pretending to know.
    @Test
    @DisplayName("an unknown display rate reads as dashes and budgets as 60 Hz")
    void unknownDisplayRate() {
        var unknown = FrameStats.of(60, 16.7, 2.0, 9, 0, 0, 0, 0, 0);

        assertEquals(
                List.of("refresh —"), readings(renderer(unknown).render(new ElementTree(new Hud(Reading.REFRESH)))));
        assertEquals(8.33, Reading.PAINT.budgetMillis(unknown), 0.01);
    }

    /// A HUD with no loop behind it draws dashes, and dashes in red would be an
    /// alarm about nothing.
    @Test
    @DisplayName("nothing measured is not over budget")
    void nothingMeasuredIsNotAnAlarm() {
        for (var reading : Reading.values()) {
            assertTrue(
                    new HudReading(reading).classes(FrameStats.none()).contains("ok"),
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
                .inflate(KdlParser.parse("hud readings=\"fps refresh\" class=\"dim\"")
                        .getFirst());
        assertEquals(List.of(Reading.FPS, Reading.REFRESH), ((Hud) chosen).readings());
        assertTrue(((Hud) chosen).classes().contains("dim"));
    }

    @Test
    @DisplayName("a reading nobody has heard of is a refusal that names the ones there are")
    void refusesAnUnknownReading() {
        var refused = assertThrows(
                IllegalArgumentException.class,
                () -> Widgets.inflater()
                        .inflate(KdlParser.parse("hud readings=\"gpu\"").getFirst()));

        assertTrue(refused.getMessage().contains("fps"), refused.getMessage());
    }

    @Test
    @DisplayName("an empty list of readings is the default, not an empty hud")
    void emptyIsTheDefault() {
        assertEquals(Hud.DEFAULT, new Hud(List.of(), Attributes.NONE).readings());
    }

    /// A composited window's present: three readings over the
    /// composited frames, and dashes where there are none.
    @Test
    @DisplayName("`present` shows where a composited frame's present went, and dashes on a surface")
    void presentReadings() {
        var ring = new dev.goldberry.stats.FrameRing();
        ring.displayHertz(60);
        ring.record(0, 2_000_000);
        ring.presented(new dev.goldberry.render.PresentTimings(100_000, 7_900_000, 150_000, 12_800, true));
        var box = renderer(ring).render(new ElementTree(new Hud(Reading.UPLOAD, Reading.ACQUIRE, Reading.SUBMIT)));
        assertEquals(
                List.of(
                        "upload 0.10 / 0.10 / 0.10 ms, 12.5 KiB",
                        "acquire 7.90 / 7.90 / 7.90 ms",
                        "submit 0.15 / 0.15 / 0.15 ms"),
                readings(box));

        var surface = renderer(FrameStats.of(60, 16.7, 2.1, 500, 0, 0, 0, 0, 60))
                .render(new ElementTree(new Hud(Reading.UPLOAD, Reading.ACQUIRE, Reading.SUBMIT)));
        assertEquals(List.of("upload —", "acquire —", "submit —"), readings(surface));
    }

    @Test
    @DisplayName("`readings=\"present\"` is the composited present in one word, and the three parse")
    void presentFromMarkup() {
        var hud = (Hud) Widgets.inflater()
                .inflate(KdlParser.parse("hud readings=\"present\"").getFirst());
        assertEquals(Hud.PRESENT, hud.readings());
        assertEquals(Reading.ACQUIRE, Reading.parse("acquire"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> Reading.parse("draw"))
                .getMessage()
                .contains("upload, acquire, submit"));
    }

    @Test
    @DisplayName("an upload over an eighth of a frame is over budget, and waiting for the display never is")
    void presentBudgets() {
        var ring = new dev.goldberry.stats.FrameRing();
        ring.displayHertz(60);
        ring.record(0, 2_000_000);
        ring.presented(new dev.goldberry.render.PresentTimings(3_000_000, 16_000_000, 100_000, 33_000_000, true));
        assertEquals(Reading.Level.OVER, Reading.UPLOAD.level(ring));
        assertEquals(Reading.Level.OK, Reading.ACQUIRE.level(ring));
        assertEquals(Reading.Level.OK, Reading.SUBMIT.level(ring));
        assertEquals("31.5 MiB", Reading.bytes(33_000_000));
        assertEquals("512 B", Reading.bytes(512));
    }
}
