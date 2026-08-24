package io.github.digitalsmile.goldberry.example.ui;

import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.data.Curve;
import io.github.digitalsmile.goldberry.widgets.data.NullPolicy;
import io.github.digitalsmile.goldberry.widgets.data.Series;
import io.github.digitalsmile.goldberry.widgets.data.Threshold;
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
/// - **A monotone curve.** The `Bytes served` stack is drawn with
///   [io.github.digitalsmile.goldberry.widgets.data.Curve#SMOOTH], which cannot
///   overshoot: a spline that swung past its own readings would put a band below
///   zero on a chart of a byte count
///   ([ADR-0204](../../../../../../../book/src/adr/0204-a-smooth-line-cannot-overshoot.md)).
/// - **A `java.time` axis.** The `p99 latency` card's x is *when* rather than
///   *which*: its ninth scrape is twenty minutes after its eighth, and the axis
///   is twenty minutes wide there rather than one step like every other
///   ([ADR-0203](../../../../../../../book/src/adr/0203-a-time-axis-is-time-not-a-relabelled-index.md)).
/// - **A limit is not a series.** The `p99 latency` card carries a threshold
///   band in the semantic warning hue, which is the one colour on this screen
///   that is *not* from the palette — a limit is a statement about the data
///   rather than one of the things being compared
///   ([ADR-0202](../../../../../../../book/src/adr/0202-a-limit-is-not-a-series.md)).
/// - **A hole is not a zero**, which is the last card: the same twelve readings
///   drawn twice, under the two
///   [io.github.digitalsmile.goldberry.widgets.data.NullPolicy] settings that
///   disagree about what a missing one means
///   ([ADR-0201](../../../../../../../book/src/adr/0201-a-hole-is-not-a-zero.md)).
///   **One card and not two**, because the wrong picture is only obviously wrong
///   *beside* the right one — on its own a line diving to the baseline looks like
///   data — and a masonry places by column height, so two cards could not be
///   promised to stay together.
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

        /// A card of alternating charts and captions — the one tile on this
        /// screen that is making an argument rather than showing a number.
        ///
        /// Captions only here: one under every tile would be a wall of reading
        /// rather than a wall of charts.
        private static Widget captioned(String title, Attributes attributes, Widget... parts) {
            var children = new java.util.ArrayList<Widget>(parts.length + 1);
            children.add(new Text(title, Attributes.NONE.classes("card-title")));
            children.addAll(List.of(parts));
            return new Card(List.copyOf(children), attributes);
        }

        /// A line of prose under a chart.
        private static Widget caption(String text) {
            return new Text(text, Attributes.NONE.classes("caption"));
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
                                            DAYS, id("bytes"))
                                            // Both edges of both bands, so the
                                            // stack still nests exactly -- a
                                            // curved top over a straight
                                            // underside would be a band thicker
                                            // than its own numbers (ADR-0204).
                                            .curve(Curve.SMOOTH),
                                    id("bytes-card")),

                            card("Errors by status",
                                    new BarChart(List.of(
                                            Series.of("4xx", 18, 24, 14, 9, 21),
                                            Series.of("5xx", 3, 2, 5, 1, 4)),
                                            List.of("Mon", "Tue", "Wed", "Thu", "Fri"),
                                            id("errors")),
                                    id("errors-card")),

                            // The card whose stylesheet rule recolours one series
                            // -- see the class note -- and the one with a limit
                            // drawn across it.
                            card("p99 latency",
                                    new LineChart(List.of(new Series("p99", LATENCY)),
                                            List.of(), id("latency"))
                                            // §3.1's `java.time` axis. The ninth
                                            // scrape is twenty minutes after the
                                            // eighth, and the axis shows that as
                                            // twenty minutes rather than as one
                                            // more step (ADR-0203).
                                            .times(SCRAPES, java.time.ZoneOffset.UTC)
                                            // A band rather than a line, because
                                            // what matters is the *region* the
                                            // series went into. In a semantic hue,
                                            // never a series slot: a limit is a
                                            // statement about the data rather than
                                            // one of the things being compared
                                            // (ADR-0202).
                                            .threshold(Threshold
                                                    .above(145, Threshold.Level.WARNING)
                                                    .labelled("SLO")),
                                    id("latency-card")),

                            card("Active users",
                                    new Statistic("Active users", "12,480", null, "-3.1%",
                                            Statistic.Direction.DOWN,
                                            new Sparkline(USERS, false, true, Attributes.NONE),
                                            Attributes.NONE),
                                    id("users-card")),

                            // The card that makes §3.1's sentence visible: the
                            // same twelve readings, three of them missing, drawn
                            // under the two policies that disagree about what
                            // that means (ADR-0201).
                            captioned("Signal strength", id("dropouts-card"),
                                    new LineChart(List.of(new Series("dBm", SIGNAL)),
                                            List.of(), id("signal")),
                                    caption("Gap — the default. The line stops where"
                                            + " readings never arrived; a lone one between"
                                            + " two holes is a dot."),
                                    new LineChart(List.of(new Series("dBm", SIGNAL)),
                                            List.of(), id("signal-zero"))
                                            .nulls(NullPolicy.ZERO),
                                    caption("Zero — the same numbers, claiming the signal"
                                            + " died. It did not: nobody was listening."))),
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

        /// When each of [#LATENCY]'s scrapes happened — every five minutes, with
        /// **one missed window** between the eighth and the ninth.
        ///
        /// Written down rather than counted from a clock, for this screen's rule
        /// about constants: a showcase screen is also a golden image. **And in
        /// `UTC`**, which an application would not do — the zone is the user's,
        /// and `times(list)` reads the machine's. A picture that changes when the
        /// developer flies somewhere is not a picture you can compare with
        /// yesterday's, which is the same argument that keeps every label in this
        /// toolkit in the root locale.
        private static final List<java.time.Instant> SCRAPES = List.of(
                java.time.Instant.parse("2026-03-14T09:00:00Z"),
                java.time.Instant.parse("2026-03-14T09:05:00Z"),
                java.time.Instant.parse("2026-03-14T09:10:00Z"),
                java.time.Instant.parse("2026-03-14T09:15:00Z"),
                java.time.Instant.parse("2026-03-14T09:20:00Z"),
                java.time.Instant.parse("2026-03-14T09:25:00Z"),
                java.time.Instant.parse("2026-03-14T09:30:00Z"),
                java.time.Instant.parse("2026-03-14T09:35:00Z"),
                java.time.Instant.parse("2026-03-14T09:55:00Z"),
                java.time.Instant.parse("2026-03-14T10:00:00Z"));

        /// Twelve readings with three missing, arranged so that both shapes a
        /// hole can make are on screen.
        ///
        /// A **two-sample dropout** (indices 3 and 4) leaves a hole between two
        /// segments. A single reading with a hole on either side (index 5) leaves
        /// a **dot**: a run of one point has no segment to draw, and dropping it
        /// would be the chart quietly omitting a reading it was given — the same
        /// objection `Lttb` exists to answer.
        ///
        /// `NaN` is how a hole is spelled; a `null` in this list would be read as
        /// one ([Series]).
        private static final List<Double> SIGNAL = java.util.Arrays.asList(
                -62.0, -58.0, -61.0, Double.NaN, Double.NaN, -57.0,
                Double.NaN, -59.0, -60.0, -56.0, -55.0, -54.0);
    }
}
