package dev.goldberry.example.ui.overlays;

import java.util.ArrayList;
import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.example.ui.gallery.Wall;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;

/// The **Overlays** screen: what opens over the window, and the banner that
/// opens in it.
///
/// `chapter-overlays.kdl` holds the cards whose buttons press what the window
/// registered: the dialog, the HUD and the toasts. The Java cards here keep
/// state of their own.
///
/// Read more: [Overlays](https://goldberry.dev/docs/components/overlays.html).
///
/// @param context what the screen is built from
public record OverlaysChapter(GalleryContext context) implements Widget.Stateless {

    /// The chapter this screen mirrors.
    static final DocLink CHAPTER = DocLink.page("components/overlays");

    private static final String SUMMARY = "A dialog, a HUD, toasts and a tour float in the window's own layer;"
            + " a popover and a tooltip open in a window of their own. A message does neither: it is part of the"
            + " layout and stays.";

    @Override
    public Widget build(BuildContext buildContext) {
        var cards = new ArrayList<Widget>();
        cards.add(new PopoverCard());
        cards.addAll(Messages.cards());
        cards.add(new TourCard(context.startTour()));
        cards.add(new TooltipsCard());
        return Wall.of(
                "overlays",
                "Overlays",
                SUMMARY,
                CHAPTER,
                context.documents().wall("chapter-overlays.kdl"),
                List.copyOf(cards));
    }
}
