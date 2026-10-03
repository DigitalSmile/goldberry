package dev.goldberry.example.ui.panels;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.example.ui.gallery.Wall;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.data.sparkline.Sparkline;
import dev.goldberry.widgets.panel.statistic.Statistic;

/// The **Panels** screen: a card for every section of the Panels chapter.
///
/// The containers are `panels-cards.kdl`, which needs no Java at all. Three cards
/// are added here: the tab strip, whose tabs are a list the model changes, the
/// timeline, and a statistic with a sparkline, which only Java can give one.
///
/// Read more: [Panels](https://goldberry.dev/docs/components/panels.html).
///
/// @param context what the screen is built from
public record PanelsChapter(GalleryContext context) implements Widget.Stateless {

    /// The chapter this screen mirrors.
    static final DocLink CHAPTER = DocLink.page("components/panels");

    /// The document holding the screen's markup cards.
    static final String DOCUMENT = "panels-cards.kdl";

    /// The leagues left to walk, week by week.
    private static final List<Double> REMAINING = List.of(1520.0, 1487.0, 1442.0, 1409.0, 1381.0, 1358.0, 1340.0);

    @Override
    public Widget build(BuildContext buildContext) {
        return Wall.of(
                "panels",
                "Panels",
                "The containers a window is made of: surfaces, a titled frame, sections that fold, slides, "
                        + "placeholders, a labelled number, tabs and a timeline.",
                CHAPTER,
                context.documents().wall(DOCUMENT),
                List.of(trend(), new TabsCard(context.model(), context.actions()), new TimelineCard()));
    }

    /// A statistic with a sparkline under its number.
    ///
    /// Read more: [`statistic`](https://goldberry.dev/docs/components/panels.html#statistic).
    static Widget trend() {
        return new ShowcaseCard(
                        "panels-trend",
                        "A number and its trend",
                        "A statistic can draw a sparkline under its number, the last point marked. "
                                + "The sparkline is given in Java.",
                        DocLink.to("components/panels", "statistic"))
                .of(new Statistic(
                                "Leagues to go",
                                "1,340",
                                null,
                                "-42",
                                Statistic.Direction.DOWN,
                                new Sparkline(REMAINING, false, true, Attributes.NONE),
                                Attributes.NONE)
                        .id("leagues-to-go"));
    }
}
