package dev.goldberry.example.ui.text;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.example.ui.gallery.Wall;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.panel.card.Card;
import dev.goldberry.widgets.panel.masonry.Masonry;
import dev.goldberry.widgets.text.Text;

/// The **Text** screen: `text` and `link`, then the guide's chapter on faces,
/// paragraphs, text scale and editing, a card per section.
///
/// The ranks, the cut labels, the links and the faces are `text.kdl`, because
/// a class and a link are what a document says well. The bound label, the
/// paragraph, the two reference cards and the editor are built here, and the
/// wall puts every card in the guide's order.
///
/// Read more: [Text and links](https://goldberry.dev/docs/components/text.html).
///
/// @param context what the screen is built from
public record TextChapter(GalleryContext context) implements Widget.Stateless {

    /// The chapter this screen mirrors.
    static final DocLink CHAPTER = DocLink.page("components/text");

    /// The chapter on faces, paragraphs and editing.
    static final DocLink GUIDE = DocLink.page("guide/text");

    private static final String SUMMARY = "A text is a run of words that wraps where layout puts it, and a link is"
            + " a word that goes somewhere. Under them are the faces, the paragraphs and the editor every label"
            + " and field is drawn with.";

    /// The paragraph, here to be re-wrapped rather than read: sentences of very
    /// different lengths, so dragging the window's edge visibly moves the breaks.
    static final String PROSE = """
            Layout proposes a width and this paragraph answers with a height, which is all a flexbox \
            needs to know about text. The words were shaped once, when the paragraph was first drawn. \
            Every wrap since has been arithmetic over the glyphs that shaping produced.

            Drag the window's edge and watch the breaks move. Short sentences fall one way and long \
            ones another, and none of it is shaped again.

            Right-to-left text is shaped in logical order and drawn mirrored, and the paragraph says \
            when that happened rather than refusing to draw it.""";

    @Override
    public Widget build(BuildContext buildContext) {
        var document = context.documents().wall("text.kdl");
        return Wall.of(
                "text",
                "Text",
                SUMMARY,
                CHAPTER,
                List.of(
                        card(document, "text-card"),
                        card(document, "text-wrapping"),
                        new BoundTextCard(),
                        card(document, "links-card"),
                        card(document, "text-faces"),
                        card(document, "text-bundled-faces"),
                        shippingAFace(),
                        prose(),
                        textScale(),
                        new SelectionCard()));
    }

    /// The card called `id` in `document`, refused by name when the document
    /// has none, so a renamed card is a failure that says which.
    static Widget card(Masonry document, String id) {
        return document.children().stream()
                .filter(child -> child instanceof Card card
                        && id.equals(card.attributes().id()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("text.kdl has no card with id " + id));
    }

    private static Widget shippingAFace() {
        return new ShowcaseCard(
                        "text-shipping-a-face",
                        "Shipping a face",
                        "An application ships a face by listing it in Application.fonts(), and a stylesheet names"
                                + " it with font-family. The bundled families are searched first, and a file that"
                                + " is missing is said at start rather than on the first frame that needs it.",
                        DocLink.to(GUIDE.page(), "shipping-a-face"))
                .reference();
    }

    /// The paragraph card. Always in the tree, and always long enough to wrap.
    private static Widget prose() {
        return new ShowcaseCard(
                        "prose-card",
                        "A paragraph to re-wrap",
                        "A paragraph is shaped once, and every re-wrap after that is arithmetic over the glyphs."
                                + " Drag the window's edge: the lines break again without the words being shaped"
                                + " again.",
                        DocLink.to(GUIDE.page(), "paragraphs"))
                .of(new Text(PROSE).id("prose"));
    }

    private static Widget textScale() {
        return new ShowcaseCard(
                        "text-scale",
                        "Text scale",
                        "Text scales from 90% to 150% without the boxes around it moving, which every control is"
                                + " built to survive. The factor is applied where a resolved style becomes a font,"
                                + " and nowhere in the cascade.",
                        DocLink.to(GUIDE.page(), "text-scale"))
                .reference();
    }
}
