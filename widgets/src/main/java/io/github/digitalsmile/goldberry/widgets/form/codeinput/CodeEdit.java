package io.github.digitalsmile.goldberry.widgets.form.codeinput;

import java.util.Objects;

/// What a [CodeInput] holds, and which box is next — §4's editing model for the
/// one-time-code field.
///
/// **A value**, for [io.github.digitalsmile.goldberry.widgets.form.textinput.TextEdit]'s
/// reasons: a `State` holds one and swaps it, and every rule §4 states can be
/// tested with no font, no frame and no window.
///
/// ## Not `TextEdit`, and that is the whole reason this widget exists
///
/// §4 says `code-input` "exists as its own widget rather than a styled
/// `text-input` because its *editing model* is different, and that is the whole
/// of the specification". A `TextEdit` is a string, a caret and an anchor; this
/// is a string and nothing else, because everything a caret is for has one answer
/// here:
///
/// - **There is no selection.** Six boxes are one value; there is no range of it
///   to drag over.
/// - **There is no caret to place.** The active box is the first empty one, which
///   is a *function* of what is held rather than a second piece of state that
///   could disagree with it — and §4's "focus lands wherever the first empty box
///   is" is then a consequence rather than a rule anything has to implement.
/// - **There are no holes.** Backspace clears the last filled box, so the filled
///   boxes are always a prefix. A code with a gap in the middle is not a state a
///   one-time code has, and admitting one would mean deciding what its *value*
///   was — six boxes are announced as a single textbox (§4), and a textbox
///   holding "12" and "56" with a hole between them has no honest string.
///
/// So [#caret] is derived, and the four sentences §4 spends on this widget fall
/// out of two operations.
///
/// ## Code points, not chars
///
/// A box holds one code point, so a letter outside the basic plane takes one box
/// rather than half of a surrogate pair being drawn in each of two. See
/// [CodeType#accepts].
///
/// @param value  what is held — between zero and [#length] code points, in order
/// @param length how many boxes there are
public record CodeEdit(String value, int length) {

    /// §4's `length=6`, which is what an SMS code is nearly everywhere.
    public static final int DEFAULT_LENGTH = 6;

    public CodeEdit {
        Objects.requireNonNull(value, "value");
        if (length < 1) {
            throw new IllegalArgumentException("a code has at least one box, and " + length + " is not a length");
        }
        value = truncate(value, length);
    }

    /// An empty code of `length` boxes.
    public static CodeEdit empty(int length) {
        return new CodeEdit("", length);
    }

    /// How many boxes are filled.
    public int filled() {
        return value.codePointCount(0, value.length());
    }

    /// Whether nothing has been typed.
    public boolean isEmpty() {
        return value.isEmpty();
    }

    /// Whether every box is filled — what §4's `complete` fires on.
    public boolean isComplete() {
        return filled() == length;
    }

    /// The box the next character goes into, and the one that wears the focus
    /// ring.
    ///
    /// Clamped to the last box rather than running off the end, because a full
    /// code still has to show a ring somewhere and the last box is where a
    /// `Backspace` would act.
    public int caret() {
        return Math.min(filled(), length - 1);
    }

    /// What box `index` shows, or the empty string if it is empty.
    ///
    /// @throws IndexOutOfBoundsException if `index` is not a box
    public String boxAt(int index) {
        Objects.checkIndex(index, length);
        if (index >= filled()) {
            return "";
        }
        var start = value.offsetByCodePoints(0, index);
        return value.substring(start, value.offsetByCodePoints(start, 1));
    }

    /// Typing, and pasting, which are the same operation.
    ///
    /// §4 asks for two things that are one rule: "typing advances", and "a paste
    /// of the full code fills every box at once". Committed text arrives as a
    /// string — one character from a keystroke, six from a paste — so appending
    /// whatever `type` accepts does both, and a paste that arrives one character
    /// at a time from a platform that splits it comes out the same.
    ///
    /// Everything `type` declines is dropped rather than refusing the edit; see
    /// [CodeType] for why this is the one place in the toolkit that corrects
    /// instead of rejecting. Anything past the last box is dropped too — there is
    /// nowhere to put it, and a code field is not a field somebody meant to
    /// overflow.
    public CodeEdit type(String text, CodeType type) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(type, "type");
        if (text.isEmpty() || isComplete()) {
            return this;
        }
        var room = length - filled();
        var added = new StringBuilder(value);
        var taken = 0;
        for (var i = 0; i < text.length() && taken < room; ) {
            var codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            if (type.accepts(codePoint)) {
                added.appendCodePoint(codePoint);
                taken++;
            }
        }
        return taken == 0 ? this : new CodeEdit(added.toString(), length);
    }

    /// `Backspace` — §4's "on an empty box moves back and clears the previous
    /// one".
    ///
    /// Which is what dropping the last code point *is*, given that the active box
    /// is always the first empty one: the box the ring is on is empty, so the
    /// thing to clear is the one before it. There is no second case to write.
    public CodeEdit backspace() {
        if (value.isEmpty()) {
            return this;
        }
        return new CodeEdit(value.substring(0, value.offsetByCodePoints(value.length(), -1)), length);
    }

    /// Every box emptied — what `Escape` and a `clear` from the application mean.
    public CodeEdit cleared() {
        return value.isEmpty() ? this : new CodeEdit("", length);
    }

    /// This code holding `text`, filtered and truncated as though it had been
    /// pasted into an empty field.
    ///
    /// What an application setting a value goes through, so a `bind=` carrying a
    /// letter into a `digits` field leaves the boxes empty rather than drawing
    /// something the user could never have typed.
    public CodeEdit withValue(String text, CodeType type) {
        return empty(length).type(text, type);
    }

    /// This code in `boxes` boxes, keeping what still fits.
    ///
    /// A `length=` that changes at runtime is unusual and is not forbidden: the
    /// widget is a value like every other, and an application may rebuild it with
    /// a different one.
    public CodeEdit resized(int boxes) {
        return boxes == length ? this : new CodeEdit(value, boxes);
    }

    /// `value` cut to at most `length` code points.
    private static String truncate(String value, int length) {
        var count = value.codePointCount(0, value.length());
        return count <= length ? value : value.substring(0, value.offsetByCodePoints(0, length));
    }
}
