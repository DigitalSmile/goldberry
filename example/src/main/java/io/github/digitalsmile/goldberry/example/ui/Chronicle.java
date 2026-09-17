package io.github.digitalsmile.goldberry.example.ui;

import java.util.List;

import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.controls.badge.Badge;
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
///
/// Rivendell's marker is a `badge`, §10's third kind: a widget in the entry's
/// `marker` slot, drawn on the axis in place of the dot ([ADR-0356]). It is
/// where the company became nine, so the marker says so.
public record Chronicle() implements Widget.Stateless {

    @Override
    public Widget build(BuildContext context) {
        var timeline = new Timeline(
                        new Entry("Left the Shire", new Text("By the back gate, before dawn.", caption())).at("22 Sep"),
                        new Entry("Reached Bree").at("29 Sep"),
                        new Entry("Weathertop", new Text("Five of the Nine.", caption()))
                                .at("6 Oct")
                                .colour(0xFFBF616A),
                        new Entry("Rivendell", new Text("The company is nine.", caption()))
                                .at("20 Oct")
                                .withMarker(new Badge("IX", null, Attributes.NONE.classes("success"))))
                .pending(true)
                .id("road-so-far");
        return Notifications.card(
                "timeline-card",
                "The road so far",
                List.of(
                        timeline,
                        new Text(
                                "An ordered list along an axis. The unfilled marker at the end says the"
                                        + " story goes on, the red one is the entry's own colour — a dot"
                                        + " is data when the kinds of event differ — and a marker can be a"
                                        + " badge, written in the entry's marker slot.",
                                caption())));
    }

    private static Attributes caption() {
        return Attributes.NONE.classes("caption");
    }
}
