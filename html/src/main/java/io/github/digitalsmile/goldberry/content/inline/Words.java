package io.github.digitalsmile.goldberry.content.inline;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.content.select.WordMinter;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.core.Row;

/// Turns a paragraph's styled fragments into the widgets a wrapping row lays out.
///
/// **Shared by both content halves**, which is why it is in a package of its own and
/// not beside either of them: `markdown-view` and `html-view` disagree about what a
/// paragraph *is* — one gets it from md4c and the other from a tag — and agree
/// exactly about what a line of mixed faces has to become
/// (ADR-0295, ADR-0298). The package is
/// not exported: this is how the two views are built and not something an
/// application composes.
///
/// A separate class from the folds that use it for one plain reason beside that: a
/// fold imports a *model* node called `Text` or `HtmlText`, and the word it produces
/// is a third thing again — [WordMinter]'s [io.github.digitalsmile.goldberry.content.select.Word],
/// which draws what a `text` widget draws and also says where it landed (ADR-0301).
///
/// ## Why a word is not a fragment
///
/// The text stack shapes one font per paragraph, so a line with a bold word in it
/// cannot be one shaped run — which is why an inline run is split into words with a
/// `text` widget each and a row that wraps (see `markdown.css`, and ADR-0295).
///
/// The first version of this split each *fragment* on whitespace and stopped there,
/// and the golden image said why that is not enough. `*emphasis*, and` is three
/// fragments — the emphasised word, then `, and` — so the comma became a word of its
/// own and the row put a space in front of it: **"emphasis , and"**. The comma belongs
/// to the word before it, and the word before it is in a different face.
///
/// So the unit is a **token**: everything between two spaces, however many styles it
/// spans. A token of one fragment is one `text`; a token of several is a row of them
/// with no gap, which is what puts the comma back where it was written. The line still
/// breaks between tokens, which is where a line may break.
///
/// ## Why the class names are a parameter
///
/// `md-word` and `html-word` are two namespaces because they are styled by two
/// stylesheets an application adds separately, and a document should not be restyled
/// by rules meant for the other kind. The prefix is the only thing the two callers
/// disagree about, so it is the only thing this takes.
///
/// @param wordClass the class every word carries — the hook a stylesheet restyles
///        all of them through
/// @param tokenClass the class on the row that holds a word spanning two styles
/// @param minter where the words come from, and what records where they land
public record Words(String wordClass, String tokenClass, WordMinter minter) {

    /// The two class names for `prefix`: `md` gives `md-word` and `md-token`.
    public static Words prefixed(String prefix, WordMinter minter) {
        return new Words(prefix + "-word", prefix + "-token", minter);
    }

    /// One thing in a line: text the author wrote, or a widget somebody built.
    ///
    /// Two kinds because of the anchor. `markdown-view` produces nothing but text and
    /// could take a list of fragments; `html-view` turns an `<a href>` into a
    /// `button.link` in the middle of a sentence (ADR-0298), and that button has to be
    /// part of the **token** it is written in — otherwise the full stop after a link
    /// becomes a word of its own with a space in front of it, which is the exact
    /// mistake ADR-0295 recorded about `*emphasis*, and`.
    public sealed interface Piece permits Fragment, Node {}

    /// One piece of a paragraph, with the marks it is inside.
    ///
    /// @param text the piece's text
    /// @param marks the CSS classes for the inline marks enclosing it
    /// @param atomic whether its own whitespace is content — true for a code span,
    ///        where `a  b` is two spaces the author wrote, and for raw markup
    public record Fragment(String text, Set<String> marks, boolean atomic) implements Piece {

        /// A fragment that stands for the space between two tokens rather than for
        /// anything drawn. A line break is one: nothing is drawn, and the tokens on
        /// either side of it do not join.
        public static final Fragment SEPARATOR = new Fragment(" ", Set.of(), false);
    }

    /// A widget in the middle of a line — an anchor's button, an image's picture.
    ///
    /// Atomic like a code span: it joins the token being built and never splits, so the
    /// punctuation on either side of it stays where it was written.
    ///
    /// @param widget what draws
    /// @param text what it contributes to a **copied** selection: a link's label, and
    ///        nothing for a picture, which is what a browser copies for an image
    public record Node(Widget widget, String text) implements Piece {}

    /// The widgets for `pieces`, one per token.
    public List<Widget> tokens(List<? extends Piece> pieces) {
        var widgets = new ArrayList<Widget>();
        var token = new ArrayList<Widget>();
        for (var piece : pieces) {
            // `token.isEmpty()` is the whole of "does a space belong in front of this":
            // a token is what sits between two spaces, so the piece that opens one is
            // preceded by a space and the pieces after it are not -- which is the same
            // rule that puts a comma against the emphasised word before it.
            if (piece instanceof Node(var widget, var label)) {
                // **No classes on the wrapper.** It draws nothing itself -- the button
                // or the picture inside it does -- so a rule meant for the *text* of a
                // word would be applying to a box round a control. What it contributes
                // is the label, to a copied selection.
                token.add(minter.wrapping(widget, label, Attributes.NONE, token.isEmpty()));
                continue;
            }
            var fragment = (Fragment) piece;
            if (fragment.atomic()) {
                token.add(text(fragment.text(), fragment.marks(), token.isEmpty()));
                continue;
            }
            var text = fragment.text();
            var word = new StringBuilder();
            for (var i = 0; i < text.length(); i++) {
                var c = text.charAt(i);
                if (Character.isWhitespace(c)) {
                    flush(word, token, fragment.marks());
                    close(token, widgets);
                } else {
                    word.append(c);
                }
            }
            flush(word, token, fragment.marks());
        }
        close(token, widgets);
        return widgets;
    }

    /// One widget for `text` exactly as it is, whitespace included — for a caller that
    /// is not building a paragraph at all: a list's bullet, a line of a code fence.
    public Widget whole(String text, Set<String> marks) {
        return text(text, marks, true);
    }

    /// The pending word, as a piece of the token being built.
    private void flush(StringBuilder word, List<Widget> token, Set<String> marks) {
        if (word.isEmpty()) {
            return;
        }
        token.add(text(word.toString(), marks, token.isEmpty()));
        word.setLength(0);
    }

    /// The token, as one widget — or as a row of its pieces when it spans more than one
    /// style.
    ///
    /// The row carries no gap of its own, so `emphasis` and the comma after it touch;
    /// `align-items: baseline` in the stylesheet is what keeps a code span from riding
    /// high against the punctuation beside it.
    private void close(List<Widget> token, List<Widget> widgets) {
        if (token.isEmpty()) {
            return;
        }
        // The row is not a word and does not carry the word class: a rule meant for
        // the text of a word would otherwise apply to the box around three of them.
        var row = new Row(List.copyOf(token), Attributes.NONE.classes(tokenClass));
        widgets.add(token.size() == 1 ? token.getFirst() : row);
        token.clear();
    }

    private Widget text(String text, Set<String> marks, boolean startsToken) {
        return minter.word(text, attributes(marks), startsToken);
    }

    /// The word class, plus whatever marks the piece is inside.
    ///
    /// The word class first and always: it is the hook an application restyles every
    /// word through, and a word with no marks still has it.
    private Attributes attributes(Set<String> marks) {
        var classes = new LinkedHashSet<String>(marks.size() + 1);
        classes.add(wordClass);
        classes.addAll(marks);
        return Attributes.NONE.classes(classes.toArray(String[]::new));
    }

    /// `marks` with `mark` added, without touching the caller's set.
    ///
    /// The mark stack is passed down the inline tree rather than mutated, so that a
    /// bold word inside emphasis carries both classes and the word after the emphasis
    /// carries neither.
    public static Set<String> and(Set<String> marks, String mark) {
        var all = new ArrayList<String>(marks.size() + 1);
        all.addAll(marks);
        all.add(mark);
        return Set.copyOf(all);
    }
}
