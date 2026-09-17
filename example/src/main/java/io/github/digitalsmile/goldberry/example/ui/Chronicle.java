package io.github.digitalsmile.goldberry.example.ui;

import java.util.List;

import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.panel.timeline.Entry;
import io.github.digitalsmile.goldberry.widgets.panel.timeline.Timeline;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// §10's `timeline`: the road so far, and a pending marker for the rest of it
/// ([ADR-0345]).
///
/// One entry carries its own colour, which is `chip`'s rule for a dot — a
/// colour is data when the kinds of event differ — and the last marker is
/// unfilled, because the story is not over. That marker is what tells a
/// timeline from a list with dots.
public record Chronicle() implements Widget.Stateless {

    @Override
    public Widget build(BuildContext context) {
        var timeline = new Timeline(
                        new Entry("Left the Shire", new Text("By the back gate, before dawn.", caption())).at("22 Sep"),
                        new Entry("Reached Bree").at("29 Sep"),
                        new Entry("Weathertop", new Text("Five of the Nine.", caption()))
                                .at("6 Oct")
                                .colour(0xFFBF616A),
                        new Entry("Rivendell").at("20 Oct").colour(0xFFA3BE8C))
                .pending(true)
                .id("road-so-far");
        return Notifications.card(
                "timeline-card",
                "The road so far",
                List.of(
                        timeline,
                        new Text(
                                "An ordered list along an axis. The unfilled marker at the end says the"
                                        + " story goes on, and the red one is the entry's own colour — a dot"
                                        + " is data when the kinds of event differ.",
                                caption())));
    }

    private static Attributes caption() {
        return Attributes.NONE.classes("caption");
    }
}
