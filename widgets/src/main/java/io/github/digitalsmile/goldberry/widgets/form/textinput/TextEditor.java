package io.github.digitalsmile.goldberry.widgets.form.textinput;

import java.util.Optional;

import io.github.digitalsmile.goldberry.input.hit.Extent;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.text.Paragraph;
import io.github.digitalsmile.goldberry.text.edit.EditHistory;
import io.github.digitalsmile.goldberry.text.edit.TextEdit;
import io.github.digitalsmile.goldberry.text.flow.TextAlign;

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

    /// The composition an input method is assembling — `docs/gaps.md` G16.
    ///
    /// **Nothing is inserted.** What changes is what the field *draws*: the
    /// composition is spliced into the display string and marked with a span, and
    /// the text, the caret offset, the undo history and the bound value are
    /// untouched until the accepted candidate arrives through [#type] (ADR-0292).
    ///
    /// A **`password` refuses**, and that is the one decision in G16 that was not
    /// mechanical: the candidate window an input method opens is a separate,
    /// unmasked window showing what is being typed, so a masked field that
    /// composed would put the password on screen beside itself. Windows and macOS
    /// both disable the IME for a secure field, and this does the same —
    /// committed text still arrives, so the field still takes every character; it
    /// simply shows nothing inline.
    ///
    /// @param text        the composition so far, or `""` when it has ended
    /// @param caret       where the caret sits inside it, as a char offset
    /// @param clauseStart where the converting clause begins, or -1 for none
    /// @param clauseEnd   where it ends, or -1
    /// @return whether anything changed, which is whether to consume the event
    boolean compose(String text, int caret, int clauseStart, int clauseEnd);

    /// The line being typed on, in this field's **content** coordinates, or empty
    /// when it is not being typed into.
    ///
    /// [io.github.digitalsmile.goldberry.input.handler.Handles#caretArea]'s
    /// answer: what the platform is told so an input method can put its candidate
    /// window beside the text rather than over it (ADR-0289). Answered here
    /// rather than in `render` because the router asks after every event, and
    /// only the state has the last frame's shaped paragraph.
    Optional<LogicalRect> caretArea();

    /// Where the caret is inside [#caretArea], as an x offset from its left edge.
    double caretOffset();

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

    /// Where the last frame painted this field, in the window's coordinates, and
    /// what clips it.
    ///
    /// A **rectangle** where [#measured] is a size, and the difference is the
    /// whole reason both exist: a caret is placed from a width, and a popover of
    /// suggestions is anchored to a *position* that no widget can compute and
    /// only the painted frame knows ([ADR-0119]). §4's autocomplete is what
    /// needed it.
    void located(
            io.github.digitalsmile.goldberry.render.model.LogicalRect self,
            io.github.digitalsmile.goldberry.render.model.LogicalRect clip);

    /// A frame is being described: here is the paragraph the field's text shaped
    /// into, and how far in from the left edge the text starts.
    ///
    /// Called from `render`, which is the only place a widget is handed anything
    /// that can measure text — so it is also the only place the scroll offset can
    /// be worked out and the only place the pointer's mapping can be prepared.
    ///
    /// @param leftPadding  where the text starts, in from the left edge
    /// @param rightPadding what comes off the far end; a field's two paddings need
    ///                     not match, and doubling the left one scrolled the caret
    ///                     into view late under `padding: 0 16px 0 4px`
    ///                     (`docs/gaps.md` G43, ADR-0355)
    /// @param align what the cascade said about `text-align`, which decides where
    ///              a line **narrower** than the field sits in it — and therefore
    ///              where the caret, the highlight and the composition's rule go
    ///              ([ADR-0324])
    /// @return how far the content is shifted left of the content box's leading
    ///         edge: the scroll, **less** the alignment's indent. One number
    ///         because the two can never both be non-zero — a line that overflows
    ///         has no slack to be aligned in, and one that fits does not scroll
    double laidOut(Paragraph paragraph, double leftPadding, double rightPadding, double caretWidth, TextAlign align);

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
