package io.github.digitalsmile.goldberry.widgets.form.textarea;

import io.github.digitalsmile.goldberry.input.Extent;
import io.github.digitalsmile.goldberry.text.Paragraph;

/// What [TextAreaBox] tells its state — `text-input`'s seam, with a second
/// dimension in it.
///
/// The same shape and for the same reason: the node that takes the keys is a
/// value and cannot hold the text, and every method answers **whether anything
/// came of it**, because that is what decides whether the key is consumed.
///
/// What is new is [#moveLine], which is the one movement a model cannot perform.
/// `Up` keeps the column, and a column is an *x* — so it needs the layout, the
/// font and the width the text wrapped at, none of which a `TextEdit` has or
/// should have.
interface AreaEditor {

    /// Where a movement key goes. `text-input`'s four, plus the two that only
    /// make sense when there is more than one line.
    enum Motion {

        /// One position back, or one word back.
        LEFT,

        /// One forward.
        RIGHT,

        /// The start of the **soft** line — what `Home` means to a reader, and
        /// not `TextEdit`'s hard-line start.
        LINE_START,

        /// The end of the soft line.
        LINE_END,

        /// The start of the whole text — `Ctrl+Home`.
        START,

        /// The end of it — `Ctrl+End`.
        END
    }

    /// Moves the caret within a line.
    boolean move(Motion motion, boolean byWord, boolean extend);

    /// Moves the caret `lines` visual lines, keeping the column it was in.
    ///
    /// The column is remembered across a run of them, which is what every editor
    /// does and what nobody notices until it is missing: walking down through a
    /// short line and out the other side should come back to the column you
    /// started in, not to the end of the short line.
    ///
    /// @param lines  -1 for `Up`, 1 for `Down`, a page for `PageUp`/`PageDown`
    /// @param extend whether `Shift` is held
    boolean moveLine(int lines, boolean extend);

    /// Selects everything — `Ctrl+A`.
    boolean selectAll();

    /// `Backspace`, by character or by word.
    boolean deleteBefore(boolean byWord);

    /// `Delete`.
    boolean deleteAfter(boolean byWord);

    /// Committed text arrived, or `Enter` produced a newline.
    boolean type(String text);

    /// The pointer went down or was dragged to a point in this control.
    void pointerAt(double x, double y, boolean extend, int clickCount);

    /// The wheel turned over it.
    boolean scrollBy(double dy);

    /// Focus arrived or left.
    void focusChanged(boolean focused, boolean fromKeyboard);

    /// How big the last frame made this control.
    void measured(Extent bounds);

    /// A frame is being described: the wrapped paragraph, and the padding the
    /// text starts at.
    ///
    /// @return how far the content is scrolled **up**, in logical pixels
    double laidOut(Paragraph paragraph, double leftPadding, double topPadding);

    /// The width the text wraps at — this control's width less its padding, from
    /// the last frame.
    ///
    /// The last frame's, because `render` runs before Yoga and a box does not
    /// know its width there. It is wrong on the first frame and on the frame a
    /// resize lands, and neither is visible: both are followed immediately by
    /// another. ADR-0116 settled the same question for a scroll view.
    double contentWidth();

    /// `Ctrl+C`, `Ctrl+X`, `Ctrl+V`, `Ctrl+Z`, `Ctrl+Y`.
    boolean copy();

    boolean cut();

    boolean paste();

    boolean undo();

    boolean redo();
}
