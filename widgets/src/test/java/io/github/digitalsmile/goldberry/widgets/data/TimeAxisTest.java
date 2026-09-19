package io.github.digitalsmile.goldberry.widgets.data;

import static io.github.digitalsmile.goldberry.widgets.data.ChartFrame.HEIGHT;
import static io.github.digitalsmile.goldberry.widgets.data.ChartFrame.WIDTH;
import static io.github.digitalsmile.goldberry.widgets.data.ChartFrame.framed;
import static io.github.digitalsmile.goldberry.widgets.data.ChartFrame.pixels;
import static io.github.digitalsmile.goldberry.widgets.data.ChartFrame.plot;
import static io.github.digitalsmile.goldberry.widgets.data.ChartFrame.renderer;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.paint.BoxPainter;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widgets.data.barchart.BarChart;
import io.github.digitalsmile.goldberry.widgets.data.linechart.LineChart;

/// What a time axis draws — `content-widgets.md` §3.1's `java.time`-driven axis.
///
/// [TimeTicksTest] has the ladder, which is where the calendar arithmetic lives.
/// This is the half that only a picture can answer: **does the chart actually
/// place its points in time**, or has it merely relabelled the indices?
class TimeAxisTest {

    /// Every test here reads a fixed zone, for the reason every label in this
    /// toolkit is formatted in the root locale: a golden taken in the machine's
    /// own zone is a picture that changes when the developer flies somewhere.
    private static final java.time.ZoneId UTC = ZoneOffset.UTC;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static final Series READINGS = Series.of("p99", 128, 131, 126, 149, 142, 138);

    /// Six readings a minute apart.
    private static List<Instant> evenly() {
        var start = Instant.parse("2026-03-14T09:00:00Z");
        var out = new ArrayList<Instant>();
        for (var i = 0; i < 6; i++) {
            out.add(start.plusSeconds(i * 60L));
        }
        return List.copyOf(out);
    }

    /// The same six readings, with **ten minutes missing** between the third and
    /// the fourth — a scrape that did not happen.
    private static List<Instant> withAGap() {
        var start = Instant.parse("2026-03-14T09:00:00Z");
        return List.of(
                start,
                start.plusSeconds(60),
                start.plusSeconds(120),
                start.plusSeconds(720),
                start.plusSeconds(780),
                start.plusSeconds(840));
    }

    private static LineChart chart() {
        return new LineChart(List.of(READINGS), List.of(), plot());
    }

    @Test
    @DisplayName("a gap in the sampling is a gap on the axis")
    void unevenSamplingIsDrawnUnevenly() {
        // The claim the whole feature rests on. The same six numbers, timed two
        // ways: evenly, and with ten minutes missing in the middle.
        var even = pixels(chart().times(evenly(), UTC));
        var gapped = pixels(chart().times(withAGap(), UTC));

        assertFalse(java.util.Arrays.equals(even, gapped), "ten missing minutes have to be ten minutes wide");
    }

    @Test
    @DisplayName("and without a time axis those two are the same picture")
    void withoutOneThereIsNothingToSee() {
        // Which is what makes the assertion above about *time* rather than about
        // the numbers: with x as the point index the two are indistinguishable,
        // because the index does not know when anything happened.
        assertArrayEquals(pixels(chart()), pixels(chart()), "the same chart twice");
        assertArrayEquals(
                pixels(new LineChart(List.of(READINGS), List.of(), plot())),
                pixels(new LineChart(List.of(READINGS), List.of(), plot())));
    }

    @Test
    @DisplayName("a timed chart labels its axis with times")
    void theLabelsAreTimes() {
        var plain = pixels(chart());
        var timed = pixels(chart().times(evenly(), UTC));

        // A chart with no categories has no x labels at all; a timed one labels
        // itself, so the plot gets shorter as well as differently annotated.
        assertFalse(java.util.Arrays.equals(plain, timed));
    }

    @Test
    @DisplayName("an axis that does not cover the data falls back to the index")
    void halfATimeAxisIsNotATimeAxis() {
        // Three instants for six points. An axis that ran out would put the
        // remaining readings at a time nobody measured, which is worse than
        // admitting the axis is an index.
        var short_ = evenly().subList(0, 3);

        assertArrayEquals(
                pixels(chart()),
                pixels(chart().times(short_, UTC)),
                "it draws exactly what it drew with no time axis at all");
    }

    @Test
    @DisplayName("a bar chart keeps its bands")
    void barsAreCategorical() {
        // A bar has a width and sits *in* a band; bands of unequal width are a
        // different chart. So a time axis on a bar chart is ignored rather than
        // half-applied.
        var plain = new BarChart(List.of(READINGS), List.of(), plot());

        assertArrayEquals(pixels(plain), pixels(plain.times(withAGap(), UTC)));
    }

    @Test
    @DisplayName("the zone is the application's, and changing it moves the labels")
    void theZoneReaches() {
        var utc = pixels(chart().times(evenly(), UTC));
        var chatham = pixels(chart().times(evenly(), java.time.ZoneId.of("Pacific/Chatham")));

        // 09:00 UTC is 22:45 in Chatham, so every label reads differently -- and
        // an axis that ignored the zone would draw one picture for both.
        assertFalse(java.util.Arrays.equals(utc, chatham));
    }

    @Test
    @DisplayName("a time axis is one axis, and it belongs to the chart")
    void oneAxisPerChart() {
        var axis = TimeAxis.of(evenly()).in(UTC);

        assertTrue(axis.covers(6));
        assertFalse(axis.covers(7), "six instants do not cover seven points");
        assertFalse(axis.covers(0), "and nothing covers nothing");
        assertTrue(axis.first().isBefore(axis.last()));
    }

    @Test
    @DisplayName("a hole still breaks the line, and the gap still widens the axis")
    void theTwoComposeRatherThanCompete() {
        // Worth stating because they answer different halves of one question. A
        // time axis makes an unscraped stretch *wide*; it does not make it a
        // hole, and the long segment across it is still a line between two
        // readings that both happened. An application that means "and nothing was
        // measured in between" says so in the data — a `NaN` — and `NullPolicy`
        // draws the break it already knows how to draw.
        var joined = pixels(chart().times(withAGap(), UTC));
        var broken =
                pixels(new LineChart(List.of(Series.of("p99", 128, 131, 126, Double.NaN, 142, 138)), List.of(), plot())
                        .times(withAGap(), UTC));

        assertFalse(
                java.util.Arrays.equals(joined, broken), "the hole is drawn as a hole, on an axis that is still time");
    }

    @Test
    @DisplayName("ten minutes missing, and the axis says so")
    void golden() {
        var render = renderer();
        var tree = new ElementTree(framed(new LineChart(List.of(READINGS), List.of(), plot()).times(withAGap(), UTC)));

        GoldenImage.assertMatches(
                "line-chart-time-dark", WIDTH, HEIGHT, 1.0f, frame -> BoxPainter.paint(frame, render.render(tree)));
    }
}
