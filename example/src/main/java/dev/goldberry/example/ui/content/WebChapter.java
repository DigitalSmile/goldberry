package dev.goldberry.example.ui.content;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.example.ui.gallery.ScreenHeader;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.Column;

/// The **Web view** screen: an address bar, a row of actions, and a `web-view`
/// filling the rest of the tab.
///
/// The page is a real child window over the widget's box, so the screen fills its
/// tab rather than sitting in the gallery's viewport: a page is clipped by the
/// window rather than by an ancestor, and a `scroll` would move the frame out from
/// under a page that stayed put. [WebPane] is the part with state.
///
/// Read more: [The web view](https://goldberry.dev/docs/components/content.html#the-web-view).
///
/// @param context what the screen is built from
public record WebChapter(GalleryContext context) implements Widget.Stateless {

    /// The section this screen mirrors.
    static final DocLink CHAPTER = DocLink.to("components/content", "the-web-view");

    /// What the header says the screen is for.
    static final String SUMMARY = "A real web page in the window, drawn by the desktop's own engine over the"
            + " widget's box. Type an address and press Go, load the page that calls into Java, or open a dialog"
            + " and watch the page stand aside for it.";

    @Override
    public Widget build(BuildContext buildContext) {
        return new Column(
                List.of(new ScreenHeader("Web view", SUMMARY, CHAPTER), new WebPane()),
                Attributes.NONE.id("screen-web").classes("screen", "fills"));
    }
}
