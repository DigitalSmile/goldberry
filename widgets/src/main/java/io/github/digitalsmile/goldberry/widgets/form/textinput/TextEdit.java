package io.github.digitalsmile.goldberry.widgets.form.textinput;

import java.text.BreakIterator;
import java.util.Objects;

/// The text of a field, and where the caret and the selection are in it.
///
/// **A value.** Every operation returns a new `TextEdit` rather than changing
/// this one, which is the same choice `Widget` makes and it buys the same three
/// things: a `State` holds one and swaps it, undo is a stack of these rather
/// than a log of inverse operations, and every editing rule in `docs/core-widgets.md`
/// §4 can be tested without a window, a font or a frame.
///
/// The cost is a `String` copy per keystroke. For a single-line field that is a
/// few hundred characters of `System.arraycopy` and is not measurable beside the
/// shaping the same keystroke causes. A `text-area` holding a document large
/// enough for that to matter wants a rope, and will want one whether or not this
/// is a value — the shape is not what would have to change.
///
/// ## Caret and anchor
///
/// Two offsets, not a caret and a length. The **anchor** is where a selection
/// started and the **caret** is where it has been dragged to, so a selection made
/// right-to-left keeps its direction and `Shift+Left` from it shrinks the
/// selection rather than jumping it. [#start()] and [#end()] are the ordered
/// pair for anyone who wants a range instead.
///
/// No selection is `anchor == caret`. There is no separate "nothing selected"
/// state to fall out of step with the offsets.
///
/// ## Everything steps by grapheme, not by `char`
///
/// `java.text.BreakIterator`'s character instance decides where the caret may
/// sit — the same class `Paragraph` uses to find where a click landed, so the two
/// cannot disagree. `Backspace` on `é` written as `e` plus a combining accent
/// deletes both, and `Left` never lands between the halves of a surrogate pair.
/// Word movement uses the word instance, which knows what a word is in the
/// locale's terms rather than in `Character.isLetterOrDigit`'s.
///
/// Offsets that arrive from outside — a click, an application setting a value —
/// are **snapped** to the nearest legal position rather than refused, because
/// they come from geometry and geometry has no opinion about clusters.
///
/// @param text   what the field holds
/// @param anchor where the current selection started
/// @param caret  where the caret is, and where a selection extends to
public record TextEdit(String text, int anchor, int caret) {

    /// An empty field.
    public static final TextEdit EMPTY = new TextEdit("", 0, 0);

    public TextEdit {
        Objects.requireNonNull(text, "text");
        anchor = Math.clamp(anchor, 0, text.length());
        caret = Math.clamp(caret, 0, text.length());
    }

    /// `text` with the caret at its end and nothing selected — a field that has
    /// just been given a value.
    ///
    /// The end rather than the start, because a field showing a value somebody is
    /// about to correct is one they want to type at the end of.
    public static TextEdit of(String text) {
        Objects.requireNonNull(text, "text");
        return new TextEdit(text, text.length(), text.length());
    }

    // --- what is selected -----------------------------------------------------

    /// The lower of the two offsets.
    public int start() {
        return Math.min(anchor, caret);
    }

    /// The higher of the two offsets.
    public int end() {
        return Math.max(anchor, caret);
    }

    /// Whether anything is selected.
    public boolean hasSelection() {
        return anchor != caret;
    }

    /// The selected text, or `""`.
    public String selectedText() {
        return text.substring(start(), end());
    }

    /// How many characters the field holds.
    public int length() {
        return text.length();
    }

    /// Whether the field is empty — what decides between drawing the value and
    /// drawing the placeholder.
    public boolean isEmpty() {
        return text.isEmpty();
    }

    // --- moving ---------------------------------------------------------------

    /// The caret at `offset`, snapped to the nearest legal position.
    ///
    /// @param extend whether this extends the selection (`Shift` is held) or
    ///               collapses it — which is the whole difference between a click
    ///               and a shift-click, and between `Left` and `Shift+Left`
    public TextEdit caretTo(int offset, boolean extend) {
        var target = snap(Math.clamp(offset, 0, text.length()));
        return new TextEdit(text, extend ? anchor : target, target);
    }

    /// One position left. Collapses a selection to its **start** when not
    /// extending, which is what `Left` does in every editor: the arrow that made
    /// a selection undoes it rather than moving past it.
    public TextEdit left(boolean extend) {
        if (!extend && hasSelection()) {
            return caretTo(start(), false);
        }
        return caretTo(previousPosition(caret), extend);
    }

    /// One position right, collapsing a selection to its **end**.
    public TextEdit right(boolean extend) {
        if (!extend && hasSelection()) {
            return caretTo(end(), false);
        }
        return caretTo(nextPosition(caret), extend);
    }

    /// To the start of the word to the left — `Ctrl+Left`.
    public TextEdit wordLeft(boolean extend) {
        return caretTo(previousWord(caret), extend);
    }

    /// Past the end of the word to the right — `Ctrl+Right`.
    public TextEdit wordRight(boolean extend) {
        return caretTo(nextWord(caret), extend);
    }

    /// To the start of the text — `Home` on a single line.
    public TextEdit toStart(boolean extend) {
        return caretTo(0, extend);
    }

    /// To the end of the text — `End` on a single line.
    public TextEdit toEnd(boolean extend) {
        return caretTo(text.length(), extend);
    }

    // --- lines ----------------------------------------------------------------

    /// The offset of the start of the **hard** line `offset` is on.
    ///
    /// Hard, meaning a line break somebody typed. A soft-wrapped line is a fact
    /// about a width and belongs to whatever laid the text out — the model has no
    /// width and cannot have an opinion.
    ///
    /// This is what `Home` means in a `text-area`, and what a `text-input` gets
    /// for free: a single line's start is always 0.
    public int lineStart(int offset) {
        var at = Math.clamp(offset, 0, text.length());
        var newline = text.lastIndexOf('\n', at - 1);
        return newline + 1;
    }

    /// One past the end of the hard line `offset` is on, not counting the break
    /// itself — so `End` puts the caret before the newline rather than after it.
    public int lineEnd(int offset) {
        var at = Math.clamp(offset, 0, text.length());
        var newline = text.indexOf('\n', at);
        return newline < 0 ? text.length() : newline;
    }

    /// To the start of the current hard line.
    public TextEdit toLineStart(boolean extend) {
        return caretTo(lineStart(caret), extend);
    }

    /// To the end of the current hard line.
    public TextEdit toLineEnd(boolean extend) {
        return caretTo(lineEnd(caret), extend);
    }

    /// How many hard lines the text has. One for empty text, which is what an
    /// empty field occupies.
    public int lineCount() {
        var lines = 1;
        for (var i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    /// Everything selected, with the caret at the end.
    public TextEdit selectAll() {
        return new TextEdit(text, 0, text.length());
    }

    /// The selection dropped, leaving the caret where it is.
    public TextEdit collapse() {
        return new TextEdit(text, caret, caret);
    }

    /// The word containing `offset`, selected — a double-click.
    ///
    /// A click in the whitespace between two words selects that whitespace, which
    /// is what a word iterator says is there and what every editor does. Selecting
    /// the nearer word instead would make a double-click's result depend on which
    /// half of a space it landed in.
    public TextEdit wordAt(int offset) {
        if (text.isEmpty()) {
            return this;
        }
        var target = Math.clamp(offset, 0, text.length());
        var words = words();
        var from = target == 0 ? 0 : boundaryOrStart(words.preceding(Math.min(target + 1, text.length())));
        var to = words.following(from);
        if (to == BreakIterator.DONE) {
            to = text.length();
        }
        return new TextEdit(text, from, to);
    }

    // --- editing --------------------------------------------------------------

    /// `insertion` in place of the selection, with the caret after it.
    ///
    /// One method for typing, for pasting and for an IME commit, because all three
    /// are the same edit: what is selected is what a keystroke replaces.
    public TextEdit insert(String insertion) {
        Objects.requireNonNull(insertion, "insertion");
        if (insertion.isEmpty() && !hasSelection()) {
            return this;
        }
        var from = start();
        var replaced = text.substring(0, from) + insertion + text.substring(end());
        var at = from + insertion.length();
        return new TextEdit(replaced, at, at);
    }

    /// The selection, or one position back, removed.
    ///
    /// `Backspace`. With a selection it deletes the selection and nothing more,
    /// which is why this is not "delete one character".
    public TextEdit backspace() {
        if (hasSelection()) {
            return insert("");
        }
        if (caret == 0) {
            return this;
        }
        return deleteBetween(previousPosition(caret), caret);
    }

    /// The selection, or one position forward, removed — `Delete`.
    public TextEdit delete() {
        if (hasSelection()) {
            return insert("");
        }
        if (caret == text.length()) {
            return this;
        }
        return deleteBetween(caret, nextPosition(caret));
    }

    /// The word before the caret removed — `Ctrl+Backspace`.
    public TextEdit deleteWordBefore() {
        if (hasSelection()) {
            return insert("");
        }
        return deleteBetween(previousWord(caret), caret);
    }

    /// The word after the caret removed — `Ctrl+Delete`.
    public TextEdit deleteWordAfter() {
        if (hasSelection()) {
            return insert("");
        }
        return deleteBetween(caret, nextWord(caret));
    }

    /// `replacement` as the whole text, with the caret kept where it was if it
    /// still fits.
    ///
    /// What a `bind=` value changing under a field does. The caret is preserved
    /// rather than reset to the end: a model that reformats what was typed — a
    /// date picker, a numeric filter — must not move the caret out from under
    /// somebody mid-word.
    public TextEdit withText(String replacement) {
        Objects.requireNonNull(replacement, "replacement");
        if (replacement.equals(text)) {
            return this;
        }
        var edit = new TextEdit(replacement, anchor, caret);
        // Clamping in the constructor can land on an illegal position when the
        // new text ends inside a cluster, so snap after rather than trusting it.
        return new TextEdit(replacement, edit.snap(edit.anchor), edit.snap(edit.caret));
    }

    private TextEdit deleteBetween(int from, int to) {
        if (from >= to) {
            return this;
        }
        var remaining = text.substring(0, from) + text.substring(to);
        return new TextEdit(remaining, from, from);
    }

    // --- boundaries -----------------------------------------------------------

    /// `offset` if a caret may sit there, otherwise the legal position before it.
    ///
    /// Before rather than after, so a caret snapped out of a cluster does not step
    /// over a character the user can see.
    private int snap(int offset) {
        var graphemes = BreakIterator.getCharacterInstance();
        graphemes.setText(text);
        if (graphemes.isBoundary(offset)) {
            return offset;
        }
        return boundaryOrStart(graphemes.preceding(offset));
    }

    private int nextPosition(int offset) {
        var graphemes = BreakIterator.getCharacterInstance();
        graphemes.setText(text);
        var next = graphemes.following(offset);
        return next == BreakIterator.DONE ? text.length() : next;
    }

    private int previousPosition(int offset) {
        var graphemes = BreakIterator.getCharacterInstance();
        graphemes.setText(text);
        return boundaryOrStart(graphemes.preceding(offset));
    }

    /// The start of the word before `offset`.
    ///
    /// `BreakIterator`'s word instance reports a boundary at both ends of a word
    /// *and* at both ends of the run of spaces between two words, so stepping it
    /// once from inside a space lands on the space's own edge and moves the caret
    /// nowhere a user would call a word. This skips those: it walks back over
    /// whitespace first, then to the boundary before the word it found.
    private int previousWord(int offset) {
        var at = offset;
        while (at > 0 && Character.isWhitespace(text.charAt(at - 1))) {
            at--;
        }
        if (at == 0) {
            return 0;
        }
        var words = words();
        return boundaryOrStart(words.preceding(at));
    }

    /// Past the end of the word after `offset`, whitespace included.
    ///
    /// The trailing space goes with the word, which is what makes `Ctrl+Delete`
    /// close the gap rather than leave one behind.
    private int nextWord(int offset) {
        if (offset >= text.length()) {
            return text.length();
        }
        var words = words();
        var at = words.following(offset);
        if (at == BreakIterator.DONE) {
            return text.length();
        }
        while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
            at++;
        }
        return at;
    }

    private BreakIterator words() {
        // Root locale, not the default: what counts as a word here decides what
        // Ctrl+Left does, and a field's keyboard behaviour changing with the
        // machine's locale is the kind of difference nobody can reproduce. The
        // same argument `hud` makes for formatting its numbers in the root locale.
        var words = BreakIterator.getWordInstance(java.util.Locale.ROOT);
        words.setText(text);
        return words;
    }

    private static int boundaryOrStart(int boundary) {
        return boundary == BreakIterator.DONE ? 0 : boundary;
    }

    @Override
    public String toString() {
        var marker = hasSelection() ? start() + ".." + end() : String.valueOf(caret);
        return "TextEdit[" + text.length() + " chars, caret " + marker + "]";
    }
}
