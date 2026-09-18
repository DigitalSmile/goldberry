package io.github.digitalsmile.goldberry.widgets.form.textarea;

import java.util.Optional;

import io.github.digitalsmile.goldberry.input.hit.Extent;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.text.document.TextDocument;
import io.github.digitalsmile.goldberry.text.flow.TextAlign;
import io.github.digitalsmile.goldberry.text.font.Font;

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

    /// The composition an input method is assembling — `docs/gaps.md` G16.
    ///
    /// `text-input`'s
    /// [compose][io.github.digitalsmile.goldberry.widgets.form.textinput.TextEditor]
    /// exactly: nothing is inserted, the composition is spliced into what is
    /// *drawn*, and the text, the caret, the undo history and the bound value
    /// only move when the accepted candidate arrives through [#type]
    /// (ADR-0292). There is no masked `text-area`, so the one refusal that
    /// control has does not arise here.
    ///
    /// `clauseLength` is a **length** and not an end — the platform reports a
    /// clause that way and every caller passes
    /// [io.github.digitalsmile.goldberry.input.event.PreeditEvent#length()]. See
    /// `text-input`'s note; both controls share the
    /// [io.github.digitalsmile.goldberry.widgets.form.parts.Preedit] that turns
    /// it into the end a painter wants.
    ///
    /// @return whether anything changed, which is whether to consume the event
    boolean compose(String text, int caret, int clauseStart, int clauseLength);

    /// The **line** the caret is on, in this control's content coordinates, or
    /// empty when it is not being typed into.
    ///
    /// The line rather than the whole control, unlike `text-input`: a `text-area`
    /// is many lines tall, and a candidate window kept clear of all of them would
    /// be pushed a long way from the text it belongs to.
    Optional<LogicalRect> caretArea();

    /// Where the caret is inside [#caretArea], as an x offset from its left edge.
    double caretOffset();

    /// The pointer went down or was dragged to a point in this control.
    void pointerAt(double x, double y, boolean extend, int clickCount);

    /// The wheel turned over it, by `lines` — **positive is down the document**,
    /// which is [io.github.digitalsmile.goldberry.input.event.PointerEvent#deltaY()]'s
    /// own sign and convention.
    ///
    /// In lines rather than in logical pixels, because a wheel event is in lines
    /// and only this side knows what a line of *this* control's text is tall.
    /// Handing over a pixel distance meant the caller guessing, and what it
    /// guessed was one pixel per notch — with the sign inverted, so the one
    /// scrollable control in the toolkit that is not a `scroll` moved the wrong
    /// way, a pixel at a time ([ADR-0314]).
    ///
    /// @return whether anything moved, which is what decides whether the wheel is
    ///         consumed or left for the page behind this control
    boolean scrollByLines(double lines);

    /// Focus arrived or left.
    void focusChanged(boolean focused, boolean fromKeyboard);

    /// How big the last frame made this control.
    void measured(Extent bounds);

    /// A frame is being described: the shaped document, the padding the text
    /// starts at, and where each line sits in the width it wrapped at.
    ///
    /// The alignment comes down here rather than being asked for later because it
    /// is the **cascade's** answer for the frame being described, and because the
    /// caret, the hit test and `Up`/`Down` all have to use the same one the paint
    /// did — a caret measured from the paragraph's origin drifts from centred
    /// glyphs by half the line's slack, and by a different amount on every line
    /// (`docs/gaps.md` G30, [ADR-0324]).
    ///
    /// The **gutter** comes down here for the same reason: it is a width the
    /// paint computed from this frame's line count and this node's font, and the
    /// hit test, the caret and the wrap all measure from the far side of it
    /// (`docs/gaps.md` G37, [ADR-0331]).
    ///
    /// All four edges of the padding come down, not the leading two: the wrap
    /// comes off both sides and the visible height off both ends, and a stylesheet
    /// may make them differ (`docs/gaps.md` G43, ADR-0350).
    ///
    /// @param document the text as the frame shaped it, a hard line at a time
    ///                 ([ADR-0388])
    /// @param padding the control's resolved padding
    /// @param gutter  how wide the line-number column is, or 0 when there is none
    /// @return how far the content is scrolled **up**, in logical pixels
    double laidOut(TextDocument document, AreaPadding padding, double gutter, TextAlign align);

    /// The text shaped for this frame, re-using whatever the last frame shaped.
    ///
    /// Asked **before** [#laidOut], because the gutter's width is decided from
    /// the document's line count and the text then wraps at what is left over.
    ///
    /// It goes through the editor rather than being built in the box because the
    /// re-use is the whole point: the previous document is what makes a keystroke
    /// re-shape one hard line instead of half a megabyte, and only the state
    /// lives long enough to hold it ([ADR-0388]).
    ///
    /// @param text   what is being drawn — the value, or the placeholder
    /// @param font   the face and size the cascade resolved for this frame
    /// @param shaper what turns one hard line into glyphs, normally the
    ///               renderer's paragraph cache
    TextDocument shaped(String text, Font font, TextDocument.Shaper shaper);

    /// The width the text wraps at — this control's width less its padding **and
    /// less its gutter**, from the last frame.
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
