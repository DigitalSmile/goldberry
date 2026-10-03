package dev.goldberry.example.ui.controls;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.example.ui.gallery.Wall;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;

/// The **Values** screen: `slider`, `knob`, `progress` and `spinner`.
///
/// Everything bound to the application's gain is `values.kdl`, so moving one
/// control moves the others. The decibel fader, whose values are its own, is
/// built here.
///
/// Read more: [Values and progress](https://goldberry.dev/docs/components/values.html).
///
/// @param context what the screen is built from
public record ValuesChapter(GalleryContext context) implements Widget.Stateless {

    /// The chapter this screen mirrors.
    static final DocLink CHAPTER = DocLink.page("components/values");

    private static final String FILE = "values.kdl";

    @Override
    public Widget build(BuildContext buildContext) {
        var document = context.documents().wall(FILE);
        return Wall.of(
                "values",
                "Values",
                "Two controls whose value is a number, and two that report one back. The slider, the knobs and the"
                        + " first bar read one value, so moving any of them moves the rest.",
                CHAPTER,
                List.of(
                        DocumentCards.card(document, FILE, "value-card"),
                        new DecibelCard(),
                        DocumentCards.card(document, FILE, "values-knob"),
                        DocumentCards.card(document, FILE, "report-card"),
                        DocumentCards.card(document, FILE, "values-spinner")));
    }
}
