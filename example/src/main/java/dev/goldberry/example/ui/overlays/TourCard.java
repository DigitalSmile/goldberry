package dev.goldberry.example.ui.overlays;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.core.Row;

/// The guided tour's card: one button, because everything else about a tour
/// happens over the other cards on this screen. Starting one needs the window,
/// so the application starts it.
///
/// Read more: [Tours](https://goldberry.dev/docs/components/overlays.html#tours).
///
/// @param startTour the application's, which switches to this screen and starts
///                  the stops [ShowcaseTour] names
record TourCard(Runnable startTour) implements Widget.Stateless {

    private static final ShowcaseCard CARD = new ShowcaseCard(
            "tour-card",
            "A guided walk",
            "A tour dims the window except for one widget and puts a card beside it, stop after stop. Right"
                    + " and Left move between stops, Escape skips the rest.",
            DocLink.to("components/overlays", "tours"));

    @Override
    public Widget build(BuildContext context) {
        return CARD.of(new Row(
                List.of(new Button("Take the tour", startTour).styled("primary").id("tour-button")),
                Attributes.NONE.classes("toolbar")));
    }
}
