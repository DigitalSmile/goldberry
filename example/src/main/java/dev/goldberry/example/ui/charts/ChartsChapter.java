package dev.goldberry.example.ui.charts;

import java.util.ArrayList;
import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.example.ui.gallery.Wall;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;

/// The **Charts** screen: the five dashboard charts, a card for each, and the line
/// chart's knobs on cards of their own.
///
/// The line chart written with `series` and `point` nodes is a document,
/// `charts-markup.kdl`, because that is the form the guide shows those nodes in.
/// The rest is Java, because the data a chart draws in an application comes from
/// a model.
///
/// Read more: [Charts](https://goldberry.dev/docs/components/charts.html).
///
/// @param context what the screen is built from
public record ChartsChapter(GalleryContext context) implements Widget.Stateless {

    /// The chapter this screen mirrors.
    static final DocLink CHAPTER = DocLink.page("components/charts");

    @Override
    public Widget build(BuildContext buildContext) {
        var cards = new ArrayList<Widget>();
        cards.add(new SharedCrosshair());
        cards.addAll(context.documents().wall("charts-markup.kdl").children());
        cards.add(ChartCards.watch());
        cards.add(ChartCards.dropouts());
        cards.add(ChartCards.sightings());
        cards.add(ChartCards.provisions());
        cards.add(ChartCards.packs());
        cards.add(ChartCards.sparklines());
        cards.add(ChartCards.notHere());
        return Wall.of(
                "charts",
                "Charts",
                "Five dashboard charts drawn on the canvas primitive: a line, grouped bars, a stacked area, a"
                        + " donut and a sparkline. Their colours are the theme's, and each reads from the keyboard.",
                CHAPTER,
                List.copyOf(cards));
    }
}
