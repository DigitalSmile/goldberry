package dev.goldberry.example.ui.navigation;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.example.ui.gallery.Wall;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;

/// The **Navigation** screen: a trail of crumbs, rows of steps, and a wizard.
///
/// Read more: [Navigation](https://goldberry.dev/docs/components/navigation.html).
///
/// @param context what the screen is built from
public record NavigationChapter(GalleryContext context) implements Widget.Stateless {

    /// The chapter this screen mirrors.
    static final DocLink CHAPTER = DocLink.page("components/navigation");

    private static final String SUMMARY = "Three widgets that say where you are in a sequence. Each draws an"
            + " ordered list and a current index; the application moves the index and says which entries are"
            + " reachable.";

    @Override
    public Widget build(BuildContext buildContext) {
        // Two columns, not as many as fit: the cards on this screen hold rows that
        // do not wrap, and a narrow column cuts them at a large text scale.
        return Wall.inColumns(
                "navigation",
                "Navigation",
                SUMMARY,
                CHAPTER,
                2,
                List.of(new TrailCard(context.model(), context.actions()), new StepsCard(), new WizardCard()));
    }
}
