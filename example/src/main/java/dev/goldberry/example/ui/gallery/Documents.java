package dev.goldberry.example.ui.gallery;

import java.util.HashMap;
import java.util.Map;

import dev.goldberry.kdl.KdlInflater;
import dev.goldberry.kdl.KdlParser;
import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.panel.masonry.Masonry;

/// The gallery's markup documents, each inflated the first time a screen asks for
/// it and kept for as long as the window is open.
///
/// Once and not per build: a document does not change while the window is open,
/// and re-parsing one on every rebuild would put a tokenizer on the frame path.
/// Lazily, because a gallery of thirty screens shows one at a time, and parsing
/// the other twenty-nine before the first frame would be start-up time spent on
/// nothing a reader can see. What comes out is an ordinary widget: the element
/// tree cannot tell it came from markup.
///
/// The documents live beside the screens, under `dev/goldberry/example/ui`, in the
/// one package the module opens to the toolkit for resources.
///
/// Confined to the UI thread, as every widget is.
///
/// Read more: [Inflating a document](https://goldberry.dev/docs/applications.html#inflating-a-document).
public final class Documents {

    /// Where the documents are, as a resource path.
    private static final String FOLDER = "/dev/goldberry/example/ui/";

    private final KdlInflater<Widget> inflater;
    private final Map<String, Widget> inflated = new HashMap<>();

    /// @param inflater what turns a document into widgets, with the application's
    ///                 registries in it
    public Documents(KdlInflater<Widget> inflater) {
        this.inflater = inflater;
    }

    /// The document called `name`, such as `buttons.kdl`, inflated.
    public Widget document(String name) {
        return inflated.computeIfAbsent(
                name,
                file -> inflater.inflate(
                        KdlParser.resource(Documents.class, FOLDER + file).getFirst()));
    }

    /// A document whose root is a wall of cards.
    ///
    /// The cast is checked and the failure names the file, because the shape is
    /// load-bearing: a screen appends its own cards to *these* children, and a
    /// document that had grown a `column` around its masonry would put the Java
    /// cards in a second wall under the first, laid out against different columns.
    public Masonry wall(String name) {
        if (document(name) instanceof Masonry cards) {
            return cards;
        }
        throw new IllegalStateException(
                name + " must have a masonry of cards at its root, because a screen appends its own cards to it");
    }
}
