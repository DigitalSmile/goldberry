package io.github.digitalsmile.goldberry.widgets.form.textinput;

import io.github.digitalsmile.goldberry.input.Extent;
import io.github.digitalsmile.goldberry.text.Paragraph;

/// What [TextField] tells its state, and the only thing the two share.
///
/// A widget is a value and a state is not, so the node that takes the keys cannot
/// hold the text. This is the seam.
///
/// ## The field names intents; it does not build edits
///
/// It would be shorter to hand a finished [TextEdit] across — and it would be
/// wrong, because **the field's edit is not the field's text**. A `password`
/// draws bullets, and the caret and the selection it draws are offsets into those
/// bullets; a field that applied `edit.backspace()` to what it was drawing would
/// delete a bullet and leave the password as a row of them. So the field says
/// *left*, *delete a word*, *select everything*, and the state performs it
/// against the real text.
///
/// It also puts the one rule a masked field has in a single place: there are no
/// visible words in a row of bullets, so [Motion#LEFT] by word in a password is
/// [Motion#START] — the caret must not step by an amount that says how long the
/// words are.
///
/// Every method answers **whether anything came of it**, because that is exactly
/// what decides whether the key event is consumed.
///
/// Package-private, and deliberately not an extension point. `text-area` and
/// `code-input` will reuse [TextEdit] and [EditHistory], which are the parts with
/// rules in them; this is one widget's wiring.
interface TextEditor {

    /// Where a movement key goes.
    enum Motion {

        /// One position back, or one word back.
        LEFT,

        /// One forward.
        RIGHT,

        /// The start of the text — `Home`, and `Up` on a single line.
        START,

        /// The end — `End`, and `Down`.
        END
    }

    /// Moves the caret.
    ///
    /// @param byWord whether `Ctrl` was held
    /// @param extend whether `Shift` was — the difference between moving the
    ///               caret and dragging the selection with it
    /// @return whether the caret moved
    boolean move(Motion motion, boolean byWord, boolean extend);

    /// Selects everything — `Ctrl+A`. @return whether the selection changed
    boolean selectAll();

    /// `Backspace`, by character or by word. @return whether anything went
    boolean deleteBefore(boolean byWord);

    /// `Delete`. @return whether anything went
    boolean deleteAfter(boolean byWord);

    /// Committed text arrived — the one edit that folds into a typing run.
    ///
    /// @return whether anything was inserted
    boolean type(String text);

    /// The pointer went down or was dragged to `x`, measured from this field's
    /// left edge.
    ///
    /// @param extend     whether this extends the selection — `Shift` on a press,
    ///                   and always on a drag
    /// @param clickCount 1 places the caret, 2 selects a word, 3 selects the lot
    void pointerAt(double x, boolean extend, int clickCount);

    /// Focus arrived or left. What turns the platform's text input on and off,
    /// and what starts and stops the blink.
    void focusChanged(boolean focused, boolean fromKeyboard);

    /// How big the last frame made this field — the width a scroll offset is
    /// clamped against.
    void measured(Extent bounds);

    /// A frame is being described: here is the paragraph the field's text shaped
    /// into, and how far in from the left edge the text starts.
    ///
    /// Called from `render`, which is the only place a widget is handed anything
    /// that can measure text — so it is also the only place the scroll offset can
    /// be worked out and the only place the pointer's mapping can be prepared.
    ///
    /// @return how far the content is scrolled left, in logical pixels
    double laidOut(Paragraph paragraph, double leftPadding);

    /// `Ctrl+C`. @return whether there was a selection this field would let out
    boolean copy();

    /// `Ctrl+X`. @return whether anything was cut
    boolean cut();

    /// `Ctrl+V`. @return whether anything was pasted
    boolean paste();

    /// `Ctrl+Z`. @return whether there was anything to undo
    boolean undo();

    /// `Ctrl+Shift+Z` or `Ctrl+Y`. @return whether there was anything to redo
    boolean redo();
}
