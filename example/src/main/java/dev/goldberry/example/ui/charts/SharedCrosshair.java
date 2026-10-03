package dev.goldberry.example.ui.charts;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.data.CrosshairGroup;
import dev.goldberry.widgets.data.Series;
import dev.goldberry.widgets.data.barchart.BarChart;
import dev.goldberry.widgets.data.linechart.LineChart;

/// The card for what the five charts share: a palette by position, a legend, and
/// a crosshair that two charts of the same days hold together.
///
/// What travels in a [CrosshairGroup] is the point **index**, so the charts in one
/// are sampled on the same seven days. The group is mutable and belongs to this
/// window, so it lives on the card's state rather than in a constant.
///
/// Read more: [What the five share](https://goldberry.dev/docs/components/charts.html#what-the-five-share).
record SharedCrosshair() implements Widget.Stateful {

    @Override
    public State<?> createState() {
        return new SharedState();
    }

    static final class SharedState extends State<SharedCrosshair> {

        private final CrosshairGroup week = new CrosshairGroup();

        @Override
        public Widget build(BuildContext context) {
            return new ShowcaseCard(
                            "marched-card",
                            "One crosshair, two charts",
                            "Every chart takes its colours from the theme by position, hovers a crosshair and"
                                    + " reads out the point. These two share one crosshair: point at a day on"
                                    + " either and both mark it.",
                            DocLink.to("components/charts", "what-the-five-share"))
                    .of(
                            new LineChart(
                                            List.of(new Series("Leagues", March.LEAGUES)),
                                            March.DAYS,
                                            Attributes.NONE.id("marched"))
                                    .crosshair(week),
                            new BarChart(
                                            List.of(new Series("Hours of rest", March.RESTS)),
                                            March.DAYS,
                                            Attributes.NONE.id("rests"))
                                    .crosshair(week));
        }
    }
}
