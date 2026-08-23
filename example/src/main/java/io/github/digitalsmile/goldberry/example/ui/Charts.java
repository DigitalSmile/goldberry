package io.github.digitalsmile.goldberry.example.ui;

import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.data.Series;
import io.github.digitalsmile.goldberry.widgets.data.areachart.AreaChart;
import io.github.digitalsmile.goldberry.widgets.data.barchart.BarChart;
import io.github.digitalsmile.goldberry.widgets.data.donutchart.DonutChart;
import io.github.digitalsmile.goldberry.widgets.data.linechart.LineChart;
import io.github.digitalsmile.goldberry.widgets.data.sparkline.Sparkline;
import io.github.digitalsmile.goldberry.widgets.panel.card.Card;
import io.github.digitalsmile.goldberry.widgets.panel.masonry.Masonry;
import io.github.digitalsmile.goldberry.widgets.panel.statistic.Statistic;
import io.github.digitalsmile.goldberry.widgets.text.Text;
import java.util.List;
import java.util.Set;

/// The **Charts** screen: `docs/core-widgets.md` §11's five data widgets, in the
/// wall of cards a dashboard is actually made of.
///
/// ## Why a masonry rather than a grid of equal cards
///
/// A `donut-chart` is square, a `statistic` is three lines tall and a
/// `line-chart` is whatever height it was given. Laid out in equal rows every
/// card is as tall as the tallest in its row, so the statistics sit in acres of
/// empty surface and the wall reads as badly aligned rather than as varied. That
/// is the case `masonry` exists for
/// ([ADR-0196](../../../../../../../book/src/adr/0196-a-masonry-is-a-layout-that-reads-last-frame.md)),
/// and this screen is what asked for it.
///
/// ## The data is a constant, and that is deliberate
///
/// Every number here is written down rather than generated. A showcase screen is
/// also a golden image: data from a clock or a random source would make a picture
/// that cannot be compared with the one from yesterday, which is the same rule
/// `hud` follows by never asking for a frame it did not already deserve
/// ([ADR-0101](../../../../../../../book/src/adr/0101-a-diagnostic-must-not-be-the-thing-it-measures.md)).
///
/// ## What the screen shows that a single chart cannot
///
/// - **The palette is assigned by position and shared across widgets.** Cache is
///   the same green in the area chart and in the donut, because both take slot 1
///   — which is what makes a wall of charts about one system readable
///   ([ADR-0194](../../../../../../../book/src/adr/0194-a-series-colour-is-derived-from-nord-not-taken-from-it.md)).
/// - **One rule recolours a chart.** The `#latency` card overrides
///   `--gb-chart-1`, and the line and its legend swatch both follow, because a
///   custom property inherits
///   ([ADR-0195](../../../../../../../book/src/adr/0195-a-painter-reads-the-theme-through-a-custom-property.md)).
/// - **A sparkline inherits `color`**, so the one inside a `statistic` is drawn
///   in the delta's hue without being told.
public record Charts() implements Widget.Stateful {

    @Override
    public State<?> createState() {
        return new ChartsState();
    }

    static final class ChartsState extends State<Charts> {

        private static Attributes id(String id, String... classes) {
            return new Attributes(id, Set.of(classes), id);
        }

        /// A titled card, which is what every tile on this screen is.
        private static Widget card(String title, Widget content, Attributes attributes) {
            return new Card(List.of(
                    new Text(title, Attributes.NONE.classes("card-title")),
                    content), attributes);
        }

        @Override
        public Widget build(BuildContext context) {
            return new Column(List.of(
                    new SectionHeader("Charts"),
                    new Text("§11's five, on the canvas primitive. A wall of cards in a"
                            + " masonry: each card goes under whichever column is shortest,"
                            + " read from the frame before.",
                            Attributes.NONE.classes("caption")),
                    new Masonry(List.of(
                            card("Requests per day",
                                    new LineChart(List.of(
                                            Series.of("Downloads", 12, 19, 15, 27, 31, 28, 36),
                                            Series.of("Installs", 8, 11, 9, 18, 21, 19, 24)),
                                            DAYS, id("requests")),
                                    id("requests-card")),

                            card("Uptime",
                                    new Statistic("Uptime", "99.98", "%", "+0.02",
                                            Statistic.Direction.UP,
                                            new Sparkline(UPTIME, true, true, Attributes.NONE),
                                            Attributes.NONE),
                                    id("uptime-card")),

                            card("Cache hit rate",
                                    new DonutChart(List.of(
                                            Series.of("Cache", 62),
                                            Series.of("Origin", 24),
                                            Series.of("Miss", 14)), id("cache")),
                                    id("cache-card")),

                            card("Bytes served",
                                    new AreaChart(List.of(
                                            Series.of("Cache", 40, 52, 44, 61, 58, 66, 71),
                                            Series.of("Origin", 12, 9, 15, 11, 14, 10, 13)),
                                            DAYS, id("bytes")),
                                    id("bytes-card")),

                            card("Errors by status",
                                    new BarChart(List.of(
                                            Series.of("4xx", 18, 24, 14, 9, 21),
                                            Series.of("5xx", 3, 2, 5, 1, 4)),
                                            List.of("Mon", "Tue", "Wed", "Thu", "Fri"),
                                            id("errors")),
                                    id("errors-card")),

                            // The card whose stylesheet rule recolours one series
                            // -- see the class note.
                            card("p99 latency",
                                    new LineChart(List.of(new Series("p99", LATENCY)),
                                            List.of(), id("latency")),
                                    id("latency-card")),

                            card("Active users",
                                    new Statistic("Active users", "12,480", null, "-3.1%",
                                            Statistic.Direction.DOWN,
                                            new Sparkline(USERS, false, true, Attributes.NONE),
                                            Attributes.NONE),
                                    id("users-card"))),
                            3, id("wall"))),
                    id("charts"));
        }

        private static final List<String> DAYS =
                List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun");

        private static final List<Double> UPTIME = List.of(
                99.94, 99.97, 99.91, 99.99, 99.96, 99.98, 99.99, 99.97, 99.98);

        private static final List<Double> USERS = List.of(
                13100.0, 12980.0, 13040.0, 12760.0, 12610.0, 12550.0, 12480.0);

        private static final List<Double> LATENCY = List.of(
                128.0, 131.0, 126.0, 149.0, 142.0, 138.0, 133.0, 129.0, 124.0, 121.0);
    }
}
