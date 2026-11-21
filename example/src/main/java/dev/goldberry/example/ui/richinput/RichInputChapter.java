package dev.goldberry.example.ui.richinput;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.example.ui.gallery.Wall;
import dev.goldberry.example.ui.richinput.composer.ComposerCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;

/// The **Rich input** screen: `rich-input` with every option it takes, a card
/// for each, and a message composer that puts them together.
///
/// One card is `rich-input.kdl`, to show the field in markup bound to a model
/// value. The rest are Java, because a suggestion list, a send handler and a
/// clipboard format are code. The composer and the conversions card need the
/// width, so they go in the wide wall under the others.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html).
///
/// @param context what the screen is built from
public record RichInputChapter(GalleryContext context) implements Widget.Stateless {

    /// The screen's name in the gallery.
    public static final String NAME = "rich-input";

    /// The chapter `rich-input` belongs to.
    static final DocLink CHAPTER = DocLink.page("components/forms");

    /// The document holding the markup card.
    static final String DOCUMENT = "rich-input.kdl";

    /// The cards, in the order the wall places them.
    static List<Widget> cards() {
        return List.of(
                new KindsCard(),
                new RowsCard(),
                new StatesCard(),
                new BarCard(),
                new TriggersCard(),
                new StylesCard(),
                new ClipboardCard(),
                new EditorCard(),
                new ComposerCard(),
                new ConvertCard());
    }

    @Override
    public Widget build(BuildContext buildContext) {
        return Wall.of(
                NAME,
                "Rich input",
                "A field that takes formatting: marks over the words, a bar over a selection, suggestions after a"
                        + " trigger, and the clipboard, Markdown and HTML on either side of it.",
                CHAPTER,
                context.documents().wall(DOCUMENT),
                cards());
    }
}
