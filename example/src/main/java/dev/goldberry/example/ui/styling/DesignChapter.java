package dev.goldberry.example.ui.styling;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.example.ui.gallery.Wall;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;

/// The **Design system** screen: the tokens every built-in widget is drawn to,
/// and the rules that keep them honest, one card per section of the chapter.
///
/// The swatches read the tokens themselves rather than pictures of them, so the
/// screen is the system's own answer in whichever theme and density the window
/// is in.
///
/// Read more: [The design system](https://goldberry.dev/docs/guide/design-system.html).
///
/// @param context what the screen is built from
public record DesignChapter(GalleryContext context) implements Widget.Stateless {

    /// The chapter this screen mirrors.
    static final DocLink CHAPTER = DocLink.page("guide/design-system");

    @Override
    public Widget build(BuildContext buildContext) {
        return Wall.of(
                "design",
                "Design system",
                "What every built-in widget is drawn to: Nord's sixteen colours behind alias tokens, one type"
                        + " scale, a 4 px ramp, three motion durations, and the rules that keep them honest.",
                CHAPTER,
                List.of(
                        DesignCards.principles(),
                        TokenCards.palette(),
                        TokenCards.aliases(context.actions()),
                        TokenCards.spacing(),
                        TokenCards.type(),
                        TokenCards.shape(),
                        DesignCards.icons(context.plus()),
                        new DesignCards.Durations(),
                        DesignCards.states(),
                        DesignCards.focus(),
                        DesignCards.keyboard(),
                        DesignCards.density(context.model(), context.actions()),
                        DesignCards.scrollbars(context.actions()),
                        DesignCards.metrics(),
                        DesignCards.accessibility(),
                        DesignCards.governance()));
    }
}
