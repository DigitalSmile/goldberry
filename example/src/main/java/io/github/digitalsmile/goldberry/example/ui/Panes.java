package io.github.digitalsmile.goldberry.example.ui;

import io.github.digitalsmile.goldberry.kdl.KdlInflater;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.panel.masonry.Masonry;

/// The parts of the window that are **documents**, inflated once.
///
/// Once and not per build: a document does not change while the window is open,
/// and re-parsing a file on every rebuild would put a tokenizer on the frame
/// path. What comes out is an ordinary widget — the element tree cannot tell it
/// came from markup, which is the whole point of §9.
///
/// ## What is in markup and what is not
///
/// The bar and four of the seven screens' walls are here because every value in
/// them flows through `bind=` and every gesture through `change=`: they need no
/// Java at all, and a designer can move a card or rename a class in them without
/// a compiler.
///
/// What is **not** here is not missing — each absence is a sentence about §8's
/// markup being *data*:
///
/// - **The Basic screen's prose card** is in the tree only while a checkbox says
///   so, and its road card disables Turn back when the count is zero. Markup has
///   no expressions and is not going to grow any: `disabled=#true` is a constant,
///   and a document that could evaluate `clicks == 0` would be code in a data
///   file with no stack trace when it went wrong ([ADR-0062]).
/// - **The Overlays screen's banners** are a list the application edits — one
///   arrives when something describes it and goes when the description stops
///   ([ADR-0175]).
/// - **The Forms screen's three choosers** each need a channel a document does
///   not have: a `multiple` holds a set the application toggles, an
///   `autocomplete` renders whatever options it is handed *back*, and a `tree`'s
///   model is nodes with suppliers under them ([ADR-0182], [ADR-0183],
///   [ADR-0184]).
/// - **The Navigation and Collections screens** hold lists that change while the
///   window is open, and KDL can write three chapters rather than "however many
///   the model has" ([ADR-0109]).
/// - **The Charts screen** is series data, which is `double[]` and not text.
///
/// So a screen is a document *and* a class rather than one or the other, and the
/// wall is where they meet: the document supplies the cards it can and Java
/// appends the rest to the same masonry
/// ([ADR-0222](../../../../../../../book/src/adr/0222-a-showcase-is-a-window-a-bar-and-seven-screens.md)).
public final class Panes {

    private Panes() {}

    /// The window's bar — the leagues, the two startup readings and the light
    /// switch.
    public static Widget bar(KdlInflater<Widget> inflater) {
        return inflate(inflater, "statusbar.kdl");
    }

    /// The **Basic** screen's cards: §1's type scale, and every §3 control whose
    /// value is a state or a number.
    public static Masonry basic(KdlInflater<Widget> inflater) {
        return wallOf(inflater, "basic.kdl");
    }

    /// The **Panels** screen's cards: §5's containers.
    ///
    /// The only wall in the gallery with nothing appended to it. Nothing on that
    /// screen holds a value — they are all containers — so it needs no `bind=`,
    /// no `change=` and no Java at all, and the two widgets on it with state keep
    /// it themselves.
    public static Masonry panels(KdlInflater<Widget> inflater) {
        return wallOf(inflater, "panels.kdl");
    }

    /// The **Overlays** screen's cards: the three places something opens over
    /// this window.
    public static Masonry overlays(KdlInflater<Widget> inflater) {
        return wallOf(inflater, "overlays.kdl");
    }

    /// The **Forms** screen's cards: §4's fields, in every state they have.
    public static Masonry forms(KdlInflater<Widget> inflater) {
        return wallOf(inflater, "forms.kdl");
    }

    /// A document whose root is a wall of cards.
    ///
    /// The cast is checked and the failure names the file, because the shape is
    /// load-bearing: a screen appends its own cards to *these* children, and a
    /// document that had grown a `column` around its masonry would put the Java
    /// cards in a second wall under the first, laid out against different
    /// columns. That reads as a screen with a hole in the middle of it, which is
    /// a long way from the edit that caused it.
    public static Masonry wallOf(KdlInflater<Widget> inflater, String document) {
        if (inflate(inflater, document) instanceof Masonry cards) {
            return cards;
        }
        throw new IllegalStateException(document + " must have a masonry of cards at its root, because a screen"
                + " appends its own cards to it");
    }

    private static Widget inflate(KdlInflater<Widget> inflater, String document) {
        return inflater.inflate(KdlParser.resource(Panes.class, document).getFirst());
    }
}
