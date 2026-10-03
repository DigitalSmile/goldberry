package dev.goldberry.example.ui.panels;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.badge.Badge;
import dev.goldberry.widgets.panel.timeline.Entry;
import dev.goldberry.widgets.panel.timeline.Timeline;
import dev.goldberry.widgets.text.Text;

/// The `timeline`: the road so far, and a pending marker for the rest of it.
///
/// One entry carries its own colour, because a dot is data when the kinds of
/// event differ, and the last marker is unfilled, because the story is not over.
/// Rivendell's marker is a `badge` in the entry's marker slot, drawn on the axis
/// in place of the dot.
///
/// Read more: [`timeline`](https://goldberry.dev/docs/components/panels.html#timeline).
public record TimelineCard() implements Widget.Stateless {

    /// The card's id.
    static final String ID = "timeline-card";

    private static final ShowcaseCard CARD = new ShowcaseCard(
            ID,
            "The road so far",
            "An ordered list of entries along an axis. The unfilled marker at the end says the story goes on, "
                    + "Weathertop's dot has its own colour, and Rivendell's marker is a badge.",
            DocLink.to("components/panels", "timeline"));

    @Override
    public Widget build(BuildContext context) {
        return CARD.of(new Timeline(
                        new Entry("Left the Shire", new Text("By the back gate, before dawn.", caption())).at("22 Sep"),
                        new Entry("Reached Bree").at("29 Sep"),
                        new Entry("Weathertop", new Text("Five of the Nine.", caption()))
                                .at("6 Oct")
                                .colour(0xFFBF616A),
                        new Entry("Rivendell", new Text("The company is nine.", caption()))
                                .at("20 Oct")
                                .withMarker(new Badge("IX", null, Attributes.NONE.classes("success"))))
                .pending(true)
                .id("road-so-far"));
    }

    private static Attributes caption() {
        return Attributes.NONE.classes("caption");
    }
}
