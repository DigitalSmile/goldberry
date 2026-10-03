package dev.goldberry.example.ui.windows;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.example.ui.gallery.Wall;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;

/// The **Windows** screen: a card for every section of the guide's chapter on
/// windows, popups and the host. Each live card asks this window's host and
/// shows the answer, including the answer "this desktop cannot".
///
/// Read more: [Windows, popups and the host](https://goldberry.dev/docs/guide/windows.html).
///
/// @param context what the screen is built from
public record WindowsChapter(GalleryContext context) implements Widget.Stateless {

    /// The chapter this screen mirrors.
    static final DocLink CHAPTER = DocLink.page("guide/windows");

    @Override
    public Widget build(BuildContext buildContext) {
        return Wall.of(
                "windows",
                "Windows",
                "Everything an application may ask of its window is a method on Host. Each card here asks this"
                        + " window and shows what the desktop answered, including no.",
                CHAPTER,
                List.of(
                        lifecycle(),
                        new FileDialogsCard(),
                        layers(),
                        new OverlayCard(),
                        new PopupCard(),
                        new SystemThemeCard(),
                        new CapabilitiesCard(),
                        new NotifyCard(),
                        new BadgeCard(),
                        new FullscreenCard(),
                        new PositionCard(),
                        new DisplaysCard(),
                        new AttentionCard(),
                        new SecondWindowCard(),
                        new ThreadsCard(),
                        closing(),
                        lowLevel()));
    }

    static Widget lifecycle() {
        return new ShowcaseCard(
                        "windows-lifecycle",
                        "The lifecycle",
                        "Goldberry.launch opens the window hidden, builds the renderer, runs start(host), mounts"
                                + " root() once, then runs the frame loop until the window closes, and stop() after."
                                + " Everything but root() has a default.",
                        DocLink.to("guide/windows", "the-lifecycle"))
                .reference();
    }

    static Widget layers() {
        return new ShowcaseCard(
                        "windows-layers",
                        "Overlays and popups",
                        "Something opens over a window in one of two places: an overlay inside the window, which"
                                + " is clipped to it, or a popup in a platform window of its own, which the platform"
                                + " may refuse. The next two cards open one of each.",
                        DocLink.to("guide/windows", "overlays-and-popups"))
                .reference();
    }

    static Widget closing() {
        return new ShowcaseCard(
                        "windows-closing",
                        "Closing",
                        "window().close() closes a window, and the loop ends with the last one. onCloseRequest sees"
                                + " the close button first and may refuse it; Goldberry.stop() asks the loop to"
                                + " finish from any thread.",
                        DocLink.to("guide/windows", "closing"))
                .reference();
    }

    static Widget lowLevel() {
        return new ShowcaseCard(
                        "windows-low-level",
                        "The low-level path",
                        "Window.open and Goldberry.run are the whole API for a window with no widgets: paint in"
                                + " logical coordinates with straight ARGB colours, on the platform's own buffer"
                                + " wherever it lends one.",
                        DocLink.to("guide/windows", "the-low-level-path"))
                .reference();
    }
}
