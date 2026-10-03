package dev.goldberry.example.ui.forms;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.example.ui.gallery.Wall;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;

/// The **Forms** screen: a card for every section of the Fields and forms
/// chapter.
///
/// The cards are `forms-fields.kdl`, bound to the paths the model registers,
/// and one card in Java, [FieldCard], whose required field needs a value of its
/// own to check.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html).
///
/// @param context what the screen is built from
public record FormsChapter(GalleryContext context) implements Widget.Stateless {

    /// The chapter this screen mirrors.
    static final DocLink CHAPTER = DocLink.page("components/forms");

    /// The document holding the screen's cards.
    static final String DOCUMENT = "forms-fields.kdl";

    @Override
    public Widget build(BuildContext buildContext) {
        return Wall.of(
                "forms",
                "Forms",
                "A field owns its caret and tells the model, and a form is found by its fields. "
                        + "Text, a date, a time, a colour and a one-time code.",
                CHAPTER,
                context.documents().wall(DOCUMENT),
                List.of(new FieldCard()));
    }
}
