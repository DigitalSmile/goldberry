package dev.goldberry.example.ui.menus;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.example.ui.gallery.Wall;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;

/// The **Menus** screen: a second menu bar, menus opened from a button,
/// accelerators, context menus, the macOS menu bar and the tray.
///
/// `chapter-menus.kdl` holds the cards whose controls press what the window
/// registered; the Java cards here keep state of their own.
///
/// Read more: [Menus and the tray](https://goldberry.dev/docs/components/menus.html).
///
/// @param context what the screen is built from
public record MenusChapter(GalleryContext context) implements Widget.Stateless {

    /// The chapter this screen mirrors.
    static final DocLink CHAPTER = DocLink.page("components/menus");

    private static final String SUMMARY = "A menu is a widget, and opening one is not: a menu bar opens its own"
            + " headings, and anything else goes through Menus.open. The same menu value is a context menu or"
            + " a tray's.";

    /// The macOS bar, which a window on another desktop cannot show.
    private static final ShowcaseCard MAC = new ShowcaseCard(
            "menus-macos",
            "The macOS menu bar",
            "On macOS a window's menubar becomes the application's menu bar at the top of the screen, after the"
                    + " application menu AppKit provides, and draws nothing in the window. Ctrl in an accelerator"
                    + " reads as Cmd there.",
            DocLink.to("components/menus", "the-macos-menu-bar"));

    @Override
    public Widget build(BuildContext buildContext) {
        return Wall.of(
                "menus",
                "Menus",
                SUMMARY,
                CHAPTER,
                context.documents().wall("chapter-menus.kdl"),
                List.of(
                        new RowMenuCard(),
                        new AcceleratorsCard(),
                        MAC.reference(),
                        new TrayCard(context.model(), context.actions())));
    }
}
