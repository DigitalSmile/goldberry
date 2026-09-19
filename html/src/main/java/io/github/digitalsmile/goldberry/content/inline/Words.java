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

    /// One thing in a line: text the author wrote, a widget somebody built, or the end
    /// of a line the author asked for.
    ///
    /// Two of the three kinds are there because of the anchor. `markdown-view` produces
    /// nothing but text and could take a list of fragments; `html-view` turns an
    /// `<a href>` into a `button.link` in the middle of a sentence (ADR-0298), and that
    /// button has to be part of the **token** it is written in — otherwise the full stop
    /// after a link becomes a word of its own with a space in front of it, which is the
    /// exact mistake ADR-0295 recorded about `*emphasis*, and`.
    ///
    /// The third is [Break], and it is why a run of pieces is a list and not a row: a
    /// hard break is a *boundary* between two lines rather than anything drawn, so
    /// [#lines] cuts the list where it finds one (ADR-0426).
    public sealed interface Piece permits Fragment, Node, Break {}

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

    /// The end of a line, because the author said so — two trailing spaces, a
    /// backslash, a `<br>`.
    ///
    /// Nothing is drawn for it and it carries no marks: what it *is* is the place
    /// [#lines] cuts, and a fold that lays a run out as a single row treats it as a
    /// token boundary and no more (ADR-0426).
    public record Break() implements Piece {

        /// The only one there is, because a break has nothing to say about itself.
        public static final Break HARD = new Break();
    }

    /// The widgets for `pieces`, one per token — **one line's worth**.
    ///
    /// A [Break] in `pieces` ends the token and nothing else here, which is what a
    /// caller laying out a box that is one line by construction wants: a table cell is
    /// one row of words, and md4c cannot put a hard break in one anyway, since a table
    /// row is a single line of source. A caller that has a paragraph asks [#lines]
    /// instead.
    public List<Widget> tokens(List<? extends Piece> pieces) {
        var widgets = new ArrayList<Widget>();
        var token = new ArrayList<Widget>();
        for (var piece : pieces) {
            // `token.isEmpty()` is the whole of "does a space belong in front of this":
            // a token is what sits between two spaces, so the piece that opens one is
            // preceded by a space and the pieces after it are not -- which is the same
            // rule that puts a comma against the emphasised word before it.
            switch (piece) {
                // **No classes on the wrapper.** It draws nothing itself -- the button
                // or the picture inside it does -- so a rule meant for the *text* of a
                // word would be applying to a box round a control. What it contributes
                // is the label, to a copied selection.
                case Node(var widget, var label) ->
                    token.add(minter.wrapping(widget, label, Attributes.NONE, token.isEmpty()));
                case Break _ -> close(token, widgets);
                case Fragment fragment -> words(fragment, token, widgets);
            }
        }
        close(token, widgets);
        return widgets;
    }

    /// The tokens of `pieces`, **one list per line**: one line unless the author ended
    /// one inside the run, and one more for every [Break] that they did.
    ///
    /// The caller builds the boxes, because what a paragraph *is* is the one thing the
    /// two folds disagree about — this says where its lines are and nothing about their
    /// shape (ADR-0426).
    ///
    /// Two things happen here that a caller could not do afterwards, and both are about
    /// the order words are minted in:
    ///
    /// - **Every line after the first opens a block** ([WordMinter#block()]), so a copy
    ///   of the selection has the newline the author wrote in it — which is what
    ///   `Inlines.text` and the `<br>` of the HTML writer already say a hard break
    ///   means. The cost is that a triple-click takes one line of the paragraph rather
    ///   than all of it, and that is the right trade for the case hard breaks exist
    ///   for: an address, a stanza, a signature block.
    /// - **A line with nothing on it gets one space**, for the reason a fence's blank
    ///   line does: a row holding no words measures zero high, so two breaks in a row
    ///   would collapse into one and the blank line the author typed would be the bug
    ///   this fixed.
    ///
    /// **A run with no break in it is minted exactly as [#tokens] would mint it** — no
    /// boundary, no space, the same words in the same order. That is not an
    /// optimisation: it is what keeps every golden image of every document nobody put a
    /// break in unmoved, and a run that is *empty* is one of those. An `<img>` with no
    /// alt text contributes no pieces at all, and a space in the paragraph it was the
    /// whole of would be a word the page does not contain.
    public List<List<Widget>> lines(List<? extends Piece> pieces) {
        var split = new ArrayList<List<Piece>>();
        var line = new ArrayList<Piece>();
        for (var piece : pieces) {
            if (piece instanceof Break) {
                split.add(List.copyOf(line));
                line.clear();
                continue;
            }
            line.add(piece);
        }
        split.add(List.copyOf(line));
        if (split.size() == 1) {
            return List.of(tokens(split.getFirst()));
        }
        var lines = new ArrayList<List<Widget>>(split.size());
        for (var i = 0; i < split.size(); i++) {
            if (i > 0) {
                minter.block();
            }
            var only = split.get(i);
            lines.add(only.isEmpty() ? List.of(whole(" ", Set.of())) : tokens(only));
        }
        return List.copyOf(lines);
    }

    /// `fragment` split into the words of the token being built.
    private void words(Fragment fragment, List<Widget> token, List<Widget> widgets) {
        if (fragment.atomic()) {
            token.add(text(fragment.text(), fragment.marks(), token.isEmpty()));
            return;
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
