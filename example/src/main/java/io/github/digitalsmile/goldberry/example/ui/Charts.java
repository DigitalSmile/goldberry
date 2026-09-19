package io.github.digitalsmile.goldberry.example.ui;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.data.CrosshairGroup;
import io.github.digitalsmile.goldberry.widgets.data.Curve;
import io.github.digitalsmile.goldberry.widgets.data.Fill;
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

/// The **Charts** screen: `docs/core-widgets.md` §11's five data widgets, in the
/// wall of cards a dashboard is actually made of.
///
/// ## The data is a constant, and that is deliberate
///
/// Every number here is written down rather than generated. A showcase screen is
/// also a golden image: data from a clock or a random source would make a picture
/// that cannot be compared with the one from yesterday, which is the same rule
/// `hud` follows by never asking for a frame it did not already deserve
/// (ADR-0101).
///
/// It is a journal of one march because a chart of `Series 1` and `Series 2`
/// cannot show whether a legend, a crosshair or a shared palette is *readable* —
/// only whether it draws ([ADR-0222]).
///
/// ## What the screen shows that a single chart cannot
///
/// - **The palette is assigned by position and shared across widgets.** `Marched`
///   is the same green in the area chart and in the donut, because both take slot
///   1 — which is what makes a wall of charts about one journey readable
///   (ADR-0194).
/// - **One rule recolours a chart.** The `#watch-card` card overrides
///   `--gb-chart-1`, and the line and its legend swatch both follow, because a
///   custom property inherits
///   (ADR-0195).
/// - **A sparkline inherits `color`**, so the one inside a `statistic` is drawn
///   in the delta's hue without being told.
/// - **One crosshair over two charts.** `Leagues per day` and `Provisions` are
///   the same seven days and share a [CrosshairGroup]: pointing at the fourth day
///   on either puts the crosshair on it in both, and only the one under the
///   pointer says what the numbers are
///   (ADR-0206).
/// - **A monotone curve.** The `Provisions` stack is drawn with [Curve#SMOOTH],
///   which cannot overshoot: a spline that swung past its own readings would put
///   a band below zero on a chart of a sack of food
///   (ADR-0204).
/// - **A `java.time` axis.** The `Watch kept` card's x is *when* rather than
///   *which*: its ninth reading is three hours after its eighth, and the axis is
///   three hours wide there rather than one step like every other
///   (ADR-0203).
/// - **A limit is not a series.** The same card carries a threshold band in the
///   semantic warning hue, which is the one colour on this screen that is *not*
///   from the palette — a limit is a statement about the data rather than one of
///   the things being compared
///   (ADR-0202).
/// - **A fill is a hint, not the reading.** The same card fades from its line
///   down toward the axis, which is what a fill under a *line* is for — the
///   position is still the data, and the area says how much of it there is
///   (ADR-0207).
/// - **A hole is not a zero**, which is the last card: the same twelve readings
///   drawn twice, under the two [NullPolicy] settings that disagree about what a
///   missing one means
///   (ADR-0201).
///   **One card and not two**, because the wrong picture is only obviously wrong
///   *beside* the right one — on its own a line diving to the baseline looks like
///   data — and a masonry places by column height, so two cards could not be
///   promised to stay together.
public record Charts() implements Widget.Stateful {

    private static final String NOTE =
            "§11's five, on the canvas primitive — a journal of one march, because a chart of"
                    + " Series 1 and Series 2 shows whether a legend draws and not whether it"
                    + " reads. Each card goes under whichever column is shortest, read from the"
                    + " frame before.";

    @Override
    public State<?> createState() {
        return new ChartsState();
    }

    static final class ChartsState extends State<Charts> {

        /// The crosshair `Leagues per day` and `Provisions` share.
        ///
        /// The two of them are the same seven days, which is what a group needs:
        /// what travels is the point **index**, so the charts in one have to be
        /// sampled together. Pointing at the fourth day on either puts the
        /// crosshair on it in both, which is how a reader asks what the provisions
        /// were doing when the marching slowed (ADR-0206).
        ///
        /// A field on the state rather than a constant, because it is mutable and
        /// belongs to this window — the shape a `ToastController` has.
        private final CrosshairGroup week = new CrosshairGroup();

        private static Attributes id(String id, String... classes) {
            return new Attributes(id, Set.of(classes), id);
        }

        /// A titled card, which is what every tile on this screen is.
        private static Widget card(String title, Widget content, Attributes attributes) {
            return new Card(
                    List.of(new Text(title, Attributes.NONE.classes("card-title")), content),
                    attributes.classes("wall-card"));
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
            return new Card(List.copyOf(children), attributes.classes("wall-card"));
        }

        /// A card with **no** title of its own.
        ///
        /// For the two `statistic` tiles, and it is not a saving: a statistic
        /// carries its own label, so a titled card puts the same words on the tile
        /// twice -- once muted above and once as the reading's own caption. The
        /// wall reads as though two of its nine cards had stuttered.
        private static Widget plain(Widget content, Attributes attributes) {
            return new Card(List.of(content), attributes.classes("wall-card"));
        }

        /// A line of prose under a chart.
        private static Widget caption(String text) {
            return new Text(text, Attributes.NONE.classes("caption"));
        }

        @Override
        public Widget build(BuildContext context) {
            return new Wall(
                    "charts",
                    "Charts",
                    NOTE,
                    3,
                    Masonry.UNSET,
                    List.of(
                            card(
                                    "Leagues per day",
                                    new LineChart(
                                                    List.of(
                                                            Series.of("On the road", 12, 19, 15, 27, 31, 28, 36),
                                                            Series.of("Off it", 8, 11, 9, 18, 21, 19, 24)),
                                                    DAYS,
                                                    id("marched"))
                                            .crosshair(week),
                                    id("marched-card")),
                            plain(
                                    new Statistic(
                                            "Days without loss",
                                            "93",
                                            "d",
                                            "+7",
                                            Statistic.Direction.UP,
                                            new Sparkline(SAFE, true, true, Attributes.NONE),
                                            Attributes.NONE),
                                    id("safe-card")),
                            card(
                                    "What is left in the packs",
                                    new DonutChart(
                                            List.of(
                                                    Series.of("Lembas", 62),
                                                    Series.of("Dried meat", 24),
                                                    Series.of("Nothing", 14)),
                                            id("packs")),
                                    id("packs-card")),
                            card(
                                    "Provisions",
                                    new AreaChart(
                                                    List.of(
                                                            Series.of("Lembas", 40, 52, 44, 61, 58, 66, 71),
                                                            Series.of("Dried meat", 12, 9, 15, 11, 14, 10, 13)),
                                                    DAYS,
                                                    id("provisions"))
                                            // Both edges of both bands, so the stack still
                                            // nests exactly -- a curved top over a straight
                                            // underside would be a band thicker than its
                                            // own numbers (ADR-0204).
                                            .curve(Curve.SMOOTH)
                                            .crosshair(week),
                                    id("provisions-card")),
                            card(
                                    "Sightings",
                                    new BarChart(
                                            List.of(
                                                    Series.of("Crebain", 18, 24, 14, 9, 21),
                                                    Series.of("Riders", 3, 2, 5, 1, 4)),
                                            List.of("Mon", "Tue", "Wed", "Thu", "Fri"),
                                            id("sightings")),
                                    id("sightings-card")),

                            // The card whose stylesheet rule recolours one series -- see
                            // the class note -- and the one with a limit drawn across it.
                            card(
                                    "Watch kept, in minutes",
                                    new LineChart(List.of(new Series("Watch", WATCH)), List.of(), id("watch"))
                                            // §3.1's `java.time` axis. The ninth reading is
                                            // three hours after the eighth, and the axis
                                            // shows that as three hours rather than as one
                                            // more step (ADR-0203).
                                            .times(NIGHTS, java.time.ZoneOffset.UTC)
                                            // A band rather than a line, because what
                                            // matters is the *region* the series went into.
                                            // In a semantic hue, never a series slot: a
                                            // limit is a statement about the data rather
                                            // than one of the things being compared
                                            // (ADR-0202).
                                            .threshold(Threshold.above(145, Threshold.Level.WARNING)
                                                    .labelled("Too long alone"))
                                            // A fade rather than a wash, so the line stays
                                            // the reading and the area is a hint at
                                            // magnitude -- and it thins out before it
                                            // reaches the band, so the limit is still read
                                            // against the data rather than through it
                                            // (ADR-0207).
                                            .fill(Fill.GRADIENT),
                                    id("watch-card")),
                            plain(
                                    new Statistic(
                                            "Leagues to go",
                                            "1,340",
                                            null,
                                            "-42",
                                            Statistic.Direction.DOWN,
                                            new Sparkline(REMAINING, false, true, Attributes.NONE),
                                            Attributes.NONE),
                                    id("remaining-card")),

                            // The card that makes §3.1's sentence visible: the same twelve
                            // readings, three of them missing, drawn under the two policies
                            // that disagree about what that means (ADR-0201).
                            captioned(
                                    "Beacons answered",
                                    id("dropouts-card"),
                                    new LineChart(List.of(new Series("Beacons", BEACONS)), List.of(), id("beacons")),
                                    caption("Gap — the default. The line stops where nobody was"
                                            + " watching; a lone reading between two holes is a dot."),
                                    new LineChart(
                                                    List.of(new Series("Beacons", BEACONS)),
                                                    List.of(),
                                                    id("beacons-zero"))
                                            .nulls(NullPolicy.ZERO),
                                    caption("Zero — the same numbers, claiming the beacons went out."
                                            + " They did not: nobody was on the hill."))));
        }

        private static final List<String> DAYS = List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun");

        private static final List<Double> SAFE = List.of(61.0, 68.0, 74.0, 79.0, 83.0, 86.0, 89.0, 91.0, 93.0);

        private static final List<Double> REMAINING = List.of(1520.0, 1487.0, 1442.0, 1409.0, 1381.0, 1358.0, 1340.0);

        private static final List<Double> WATCH =
                List.of(128.0, 131.0, 126.0, 149.0, 142.0, 138.0, 133.0, 129.0, 124.0, 121.0);

        /// When each of [#WATCH]'s readings was taken — every half hour, with
        /// **one missed turn** between the eighth and the ninth.
        ///
        /// Written down rather than counted from a clock, for this screen's rule
        /// about constants: a showcase screen is also a golden image. **And in
        /// `UTC`**, which an application would not do — the zone is the user's,
        /// and `times(list)` reads the machine's. A picture that changes when the
        /// developer flies somewhere is not a picture you can compare with
        /// yesterday's, which is the same argument that keeps every label in this
        /// toolkit in the root locale.
        private static final List<java.time.Instant> NIGHTS = List.of(
                java.time.Instant.parse("2026-03-14T21:00:00Z"),
                java.time.Instant.parse("2026-03-14T21:30:00Z"),
                java.time.Instant.parse("2026-03-14T22:00:00Z"),
                java.time.Instant.parse("2026-03-14T22:30:00Z"),
                java.time.Instant.parse("2026-03-14T23:00:00Z"),
                java.time.Instant.parse("2026-03-14T23:30:00Z"),
                java.time.Instant.parse("2026-03-15T00:00:00Z"),
                java.time.Instant.parse("2026-03-15T00:30:00Z"),
                java.time.Instant.parse("2026-03-15T03:30:00Z"),
                java.time.Instant.parse("2026-03-15T04:00:00Z"));

        /// Twelve readings with three missing, arranged so that both shapes a
        /// hole can make are on screen.
        ///
        /// A **two-night gap** (indices 3 and 4) leaves a hole between two
        /// segments. A single reading with a hole on either side (index 5) leaves
        /// a **dot**: a run of one point has no segment to draw, and dropping it
        /// would be the chart quietly omitting a reading it was given — the same
        /// objection `Lttb` exists to answer.
        ///
        /// `NaN` is how a hole is spelled; a `null` in this list would be read as
        /// one ([Series]).
        private static final List<Double> BEACONS = java.util.Arrays.asList(
                7.0, 9.0, 8.0, Double.NaN, Double.NaN, 11.0, Double.NaN, 10.0, 12.0, 14.0, 15.0, 16.0);
    }
}
