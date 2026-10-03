package dev.goldberry.example.ui.overlays;

import java.util.List;

import dev.goldberry.widgets.overlay.tour.Stop;

/// The guided tour the Help menu and the Overlays screen start: which screen it
/// runs over, and its stops.
///
/// Starting one needs a host, which a widget does not have, so the application
/// starts it; what it says is here, beside the screen whose widgets it names.
///
/// Read more: [Tours](https://goldberry.dev/docs/components/overlays.html#tours).
public final class ShowcaseTour {

    /// The screen the tour's targets are on. The application switches to it first,
    /// because a tour whose targets are not built skips every stop.
    public static final String SCREEN = "overlays";

    private ShowcaseTour() {}

    /// The stops, in order: each names a widget by id, a title and a line of text.
    ///
    /// All but the last are on the Overlays screen; the last is the strip.
    public static List<Stop> stops() {
        return List.of(
                new Stop(
                        "dialog-button",
                        "A modal dialog",
                        "Opens a dialog over the whole window. The keyboard stays inside it until it is answered."),
                new Stop(
                        PopoverCard.BUTTON,
                        "A popover",
                        "Opens a small panel in a window of its own, placed against this button."),
                new Stop(
                        "notice-kinds",
                        "Messages",
                        "Banners that are part of the layout. They stay until the application stops describing them."),
                new Stop(
                        "gallery",
                        "The gallery strip",
                        "One screen per chapter of the guide. Ctrl and a digit opens the first ten, and Edit ▸ Go to"
                                + " opens any of them."));
    }
}
