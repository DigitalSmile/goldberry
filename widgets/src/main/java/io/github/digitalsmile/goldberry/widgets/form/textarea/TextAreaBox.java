package io.github.digitalsmile.goldberry.widgets.form.textarea;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Corners;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.event.PreeditEvent;
import io.github.digitalsmile.goldberry.input.event.TextEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.input.handler.Measured;
import io.github.digitalsmile.goldberry.input.hit.Extent;
import io.github.digitalsmile.goldberry.layout.Insets;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.layout.Overflow;
import io.github.digitalsmile.goldberry.layout.Position;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.render.Cursor;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.text.document.DocumentLines;
import io.github.digitalsmile.goldberry.text.document.TextDocument;
import io.github.digitalsmile.goldberry.text.edit.TextEdit;
import io.github.digitalsmile.goldberry.text.edit.keys.EditCommand;
import io.github.digitalsmile.goldberry.text.edit.keys.EditKeys;
import io.github.digitalsmile.goldberry.text.edit.keys.EditSurface;
import io.github.digitalsmile.goldberry.text.flow.TextAlign;
import io.github.digitalsmile.goldberry.text.flow.TextDecoration;
import io.github.digitalsmile.goldberry.text.flow.TextFlow;
import io.github.digitalsmile.goldberry.text.flow.TextOverflow;
import io.github.digitalsmile.goldberry.text.flow.WhiteSpace;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widget.semantics.Semantics;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollBar;
import io.github.digitalsmile.goldberry.widgets.form.Carets;
import io.github.digitalsmile.goldberry.widgets.form.parts.Caret;
import io.github.digitalsmile.goldberry.widgets.form.parts.Composing;
import io.github.digitalsmile.goldberry.widgets.form.parts.Highlight;
import io.github.digitalsmile.goldberry.widgets.form.parts.Underline;
import io.github.digitalsmile.goldberry.widgets.form.parts.Value;

/// The node a stylesheet calls `text-area`.
///
/// [TextArea] is stateful and styles nothing, so this carries the CSS type, the
/// `id` and the classes — the arrangement every stateful widget in this catalog
/// uses.
///
/// ## What it is made of
///
/// ```
/// text-area            this node. Clips, focuses, takes the keys and the pointer
/// ├── text-selection   × n — one per **visual** line the selection covers
/// ├── text-value       the text, wrapped at the control's width
/// ├── text-caret       the insertion point
/// └── text-area-gutter the number column's fill and rule, when there is one
/// ```
///
/// ## Only the rows on screen are shaped and drawn
///
/// A paragraph is shaped and measured whole, which is right for a label and
/// wrong for a document. A `text-area` is the one control in this catalog whose
/// text can be half a megabyte, and every keystroke makes a different string —
/// so a note held in one paragraph was re-shaped from the beginning on every
/// keystroke. That is `docs/gaps.md` G44.
///
/// Two things changed, and between them everything here is bounded by the
/// window rather than by the note ([ADR-0388]):
///
/// - **The geometry** — where the lines break, where the caret is, what a click
///   hits, how tall the content is — comes from a [TextDocument], which shapes
///   one hard line at a time and re-shapes only the line an edit touched.
/// - **The glyphs** are one paragraph of the rows in view, taken as a slice of
///   the text between two line starts. Greedy wrapping restarts at every line
///   start, so re-wrapping that slice at the same width gives back exactly the
///   rows the document said.
///
/// One box for the text and not one per row, and that is not tidiness: Yoga
/// rounds every box it places onto the pixel grid, so a box per row would put
/// each row at its own rounded offset while the rows *inside* a wrapped line
/// stayed exact. The numbers are placed from the same origin for the same
/// reason — one rounding, shared, is what keeps them from drifting.
///
/// What the `text-value` node is still for is the cascade: it resolves the ink,
/// the `white-space` and the `.placeholder` rule, and this node draws with them.
/// See [Value#carrier].
///
/// ## The line numbers are drawn here, and that is deliberate
///
/// [TextAreaGutter] is the strip; the numbers **on** it are a text box this node
/// builds, because they cannot be child widgets. A widget's children are
/// described before anything is laid out, and where a hard line ended up is a
/// fact about the wrap — so a column of number nodes would be a frame behind the
/// text on every keystroke that changed the line structure, which is exactly the
/// "looks like it works" failure `docs/gaps.md` G37 is about.
///
/// Drawn as **one** paragraph, with a blank line for every line a hard line
/// wrapped into: `"1\n2\n\n\n3"` is lines one, two — which wrapped into three —
/// and three. One paragraph in the control's own font at the control's own line
/// height, scrolled by the control's own offset, so the numbers cannot drift from
/// the text by construction rather than by agreement ([ADR-0331]).
///
/// Their ink is `--gb-gutter-color`, which is [Context#color]'s job: a widget
/// that draws something the cascade has no property for reads a custom property
/// for it (ADR-0195). The strip behind them is an ordinary node with ordinary
/// rules.
///
/// The same three parts `text-input` has, and it reuses their stylesheet rules
/// unchanged — the two controls should not look like they were designed by
/// different people, and the surest way to that is one set of rules.
///
/// **The selection is a list**, and that is the whole of what a second dimension
/// costs here: a selection covering three lines is three rectangles, because a
/// run of text that wraps is not a rectangle. Each is one line's slice of the
/// range, which is why `Paragraph`'s two measurements take a *line's* range
/// rather than an offset — they were written for this
/// (ADR-0167).
///
/// ## It sizes itself
///
/// §4 asks for "optional auto-grow between min/max rows, scrollbar beyond". The
/// height is set **here** rather than by the cascade, because it is a function of
/// how many lines the text wrapped into, which no selector can ask. A stylesheet
/// still owns the padding, the border, the fill and the line height; what it
/// cannot own is a number that changes as somebody types.
///
/// @param display     the text to draw, or the placeholder
/// @param placeholder whether `display` is the placeholder
/// @param edit        where the caret and the selection are
/// @param composing   which part of `display` an input method has not finished
///                    with — [Composing#NONE] almost always
/// @param focused     whether it has the keyboard
/// @param caretShown  whether this is the lit half of the blink
/// @param rows        its minimum height in lines
/// @param maxRows     the height it grows to before it scrolls — for a filling area,
///                    how many lines its measured height holds
/// @param fill        whether the height comes from the container rather than from
///                    the text ([TextArea#fill])
/// @param gutter      whether the hard lines are numbered down the left edge —
///                    [TextArea#gutter(boolean)]
/// @param disabled    whether it refuses everything
/// @param readOnly    whether it takes a caret but no edits
/// @param attributes  the `id` and classes the document wrote
/// @param editor      what to tell about a key, a click or a measurement
record TextAreaBox(
        String display,
        boolean placeholder,
        TextEdit edit,
        Composing composing,
        boolean focused,
        boolean caretShown,
        int rows,
        int maxRows,
        boolean fill,
        boolean gutter,
        boolean disabled,
        boolean readOnly,
        Attributes attributes,
        @Nullable ScrollBar scrollbar,
        AreaEditor editor)
        implements Widget.Leaf, Styled, Paints, Handles, Measured, Semantics {

    /// How many lines a wheel notch moves. Three, which is what every scroll view
    /// on every desktop does and what `scroll` itself uses —
    /// [io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollViewport#LINES_PER_NOTCH],
    /// stated again here because the two are the same convention and not the
    /// same number: a viewport's line is a stylesheet token and this one is a
    /// line of the text being edited.
    ///
    /// **It was declared and never used.** The wheel handler below multiplied by
    /// nothing and negated, so a notch over a `text-area` moved the document one
    /// pixel backwards — which in the Markdown screen puts an editor and a
    /// preview side by side scrolling opposite ways at wildly different speeds
    /// ([ADR-0314]).
    static final int WHEEL_LINES = 3;

    /// How much room there is on each side of a line number, in logical pixels.
    ///
    /// Eight, which is `text-area`'s own horizontal padding — the number column
    /// reads as a second gutter of the same rhythm rather than as a strip that was
    /// measured by a different hand. One on each side, so the numbers end a gap
    /// before the text starts and begin a gap in from the border.
    ///
    /// A **token** first, like every other metric a widget has to know in Java:
    /// `--gb-gutter-gap` moves it, and this is what it falls back to
    /// (`docs/gaps.md` G37, [ADR-0331]).
    static final String GUTTER_GAP_TOKEN = "--gb-gutter-gap";

    static final double GUTTER_GAP = 8;

    /// The fewest digits the column is made wide enough for.
    ///
    /// Two, so a note of nine lines and a note of ninety have the same margin and
    /// a document does not visibly shift left the first time it passes line nine.
    static final int MINIMUM_DIGITS = 2;

    /// The colour the numbers are drawn in — `--gb-gutter-color`, falling back to
    /// `--gb-text-muted` and then to the control's own ink.
    ///
    /// A custom property because the cascade has no declaration for "the ink of
    /// something this widget draws itself", which is precisely what
    /// [Context#color] exists for (ADR-0195).
    static final String GUTTER_COLOR_TOKEN = "--gb-gutter-color";

    @Override
    public String cssType() {
        return "text-area";
    }

    @Override
    public @Nullable String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public Object key() {
        return attributes.key();
    }

    @Override
    public boolean isFocusable() {
        return !disabled;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public void measured(Extent bounds, Extent part) {
        editor.measured(bounds);
    }

    @Override
    public void onFocusChanged(boolean gained, boolean fromKeyboard) {
        editor.focusChanged(gained, fromKeyboard);
    }

    // --- the pointer ----------------------------------------------------------

    @Override
    public void onPointer(PointerEvent event) {
        if (disabled) {
            return;
        }
        switch (event.kind()) {
            case PRESSED -> {
                if (event.button() != PointerEvent.Button.PRIMARY) {
                    return;
                }
                editor.pointerAt(
                        event.local().x(), event.local().y(), event.modifiers().shift(), event.clickCount());
                event.consume();
            }
            case MOVED -> {
                // A drag, not a hover: `dragX()` is NaN when no button is down,
                // which is how the router says "no gesture" (ADR-0075). The
                // button is deliberately not tested here — a motion carries none
                // ([ADR-0168]).
                if (!Double.isNaN(event.dragX())) {
                    editor.pointerAt(event.local().x(), event.local().y(), true, 1);
                    event.consume();
                }
            }
            case WHEEL -> {
                // Only when there is somewhere to go. A control that swallowed
                // every wheel would trap the page's scroll the moment the pointer
                // crossed it, which is §2.4's complaint about nested scrollers
                // arriving through the back door.
                //
                // **Not negated.** `deltaY` is positive down the document and so
                // is the editor's offset, which is `scroll`'s convention and the
                // one every scrollable thing in the toolkit shares.
                if (editor.scrollByLines(event.deltaY() * WHEEL_LINES)) {
                    event.consume();
                }
            }
            default -> {}
        }
    }

    // --- the keyboard ---------------------------------------------------------

    /// §4's editing keys, through the map all three editors read ([ADR-0376]).
    ///
    /// A `text-area` is [EditSurface#DOCUMENT]: `Up` and `Down` are lines, a page
    /// is this control's own `rows`, and **`Enter` is taken here** — a multi-line
    /// control is where a newline comes from, and a form's default button cannot
    /// have it. `Escape` still is not: that belongs to the dialog around this.
    @Override
    public void onKey(KeyEvent event) {
        if (disabled) {
            return;
        }
        var command = EditKeys.of(event, EditSurface.DOCUMENT);
        if (command == null) {
            return;
        }
        var handled =
                switch (command) {
                    case EditCommand.Simple simple -> simple(simple);
                    case EditCommand.Move(var motion, var word, var extend) ->
                        switch (motion) {
                            case LEFT -> editor.move(AreaEditor.Motion.LEFT, word, extend);
                            case RIGHT -> editor.move(AreaEditor.Motion.RIGHT, word, extend);
                            case LINE_START -> editor.move(AreaEditor.Motion.LINE_START, word, extend);
                            case LINE_END -> editor.move(AreaEditor.Motion.LINE_END, word, extend);
                            // `Ctrl+Home` and `Ctrl+End` are the whole text, and
                            // the word flag is spent saying so: passing it on
                            // would ask for a word move that has already
                            // happened.
                            case DOCUMENT_START -> editor.move(AreaEditor.Motion.START, false, extend);
                            case DOCUMENT_END -> editor.move(AreaEditor.Motion.END, false, extend);
                        };
                    case EditCommand.MoveLine(var lines, var byPage, var extend) ->
                        editor.moveLine(byPage ? lines * Math.max(1, rows) : lines, extend);
                    case EditCommand.Delete(var before, var word) ->
                        !readOnly && (before ? editor.deleteBefore(word) : editor.deleteAfter(word));
                    // Consumed either way when it is taken, so a form's default
                    // button does not also fire.
                    case EditCommand.Type(var text) -> !readOnly && editor.type(text);
                };
        if (handled) {
            event.consume();
        }
    }

    /// The accelerators, and the read-only refusal they share.
    private boolean simple(EditCommand.Simple command) {
        if (readOnly && command.isEdit()) {
            return false;
        }
        return switch (command) {
            case SELECT_ALL -> editor.selectAll();
            case COPY -> editor.copy();
            case CUT -> editor.cut();
            case PASTE -> editor.paste();
            case UNDO -> editor.undo();
            case REDO -> editor.redo();
        };
    }

    @Override
    public void onText(TextEvent event) {
        if (disabled || readOnly || event.text().isEmpty()) {
            return;
        }
        if (editor.type(event.text())) {
            event.consume();
        }
    }

    /// The composition an input method is assembling — `docs/gaps.md` G16, and
    /// `text-input`'s handler exactly. Not an edit: see [AreaEditor#compose].
    @Override
    public void onPreedit(PreeditEvent event) {
        if (disabled || readOnly) {
            return;
        }
        if (editor.compose(event.text(), event.caret(), event.start(), event.length())) {
            event.consume();
        }
    }

    /// Where this control's caret is, so the platform can place a candidate
    /// window beside it (ADR-0289).
    @Override
    public Optional<LogicalRect> caretArea() {
        return editor.caretArea();
    }

    @Override
    public double caretOffsetIn(LogicalRect area) {
        return editor.caretOffset();
    }

    // --- drawing --------------------------------------------------------------

    @Override
    public List<Widget> children() {
        // **[#maxRows] highlights, always.** How many a selection actually needs
        // is a question about the layout, and `children()` is asked before there
        // is one -- so the choice is between a mutable field on a value, a count
        // one frame stale, or the bound. The bound wins and is small: a selection
        // can cover at most as many *visible* lines as the control shows, because
        // the ones outside it are scrolled away and draw nothing.
        //
        // The ones with nothing to cover render an empty box, which is what
        // `text-value` already does for an empty field. Keeping the count fixed
        // also keeps the value and the caret at stable positions, so the
        // reconciler matches them by position through every edit.
        var parts = new ArrayList<Widget>(2 * maxRows + 2);
        // While a composition is open the highlights draw its converting clause:
        // there is no selection to draw, because a composition replaces one when
        // it commits (ADR-0292).
        var wash = focused && (composing.hasClause() || (!composing.isActive() && edit.hasSelection()));
        for (var i = 0; i < maxRows; i++) {
            parts.add(new Highlight(wash));
        }
        // A carrier rather than the text: this node draws the visible lines
        // itself, and what it needs from `text-value` is the ink the cascade
        // resolved there ([ADR-0388]).
        parts.add(Value.carrier(placeholder));
        parts.add(new Caret(focused && caretShown && !edit.hasSelection()));
        // And [#maxRows] underlines after them, for the highlights' reason: a
        // composition can wrap, and a run of wrapped text is not a rectangle.
        for (var i = 0; i < maxRows; i++) {
            parts.add(new Underline(focused && composing.isActive()));
        }
        // The gutter **last**, so it paints over anything that reached its
        // column, and appended rather than prepended so the indices every
        // existing part is placed by do not move. As many numbers as there are
        // visible lines, for the highlights' reason: a fixed count keeps the
        // reconciler matching them by position, and one with no line renders
        // nothing ([ADR-0331]).
        if (gutter) {
            parts.add(new TextAreaGutter());
        }
        // §4's "scrollbar beyond": `scroll`'s own bar, only while the text is
        // taller than the control, and last so it paints over the text it sits on
        // (ADR-0362).
        if (scrollbar != null) {
            parts.add(scrollbar);
        }
        return parts;
    }

    /// The index of [TextAreaGutter] in [#children()].
    private int gutterIndex() {
        return 2 * maxRows + 2;
    }

    /// How wide the number column is, or 0 when there is none.
    ///
    /// Wide enough for the **document's** last line number rather than for the
    /// one on screen, so the text does not slide left and right as a long note is
    /// scrolled. Measured in this node's own font — a `mono` editor and a body one
    /// have different digits — which is why it is computed here and handed down
    /// rather than guessed at either end.
    private double gutterWidth(ComputedStyle style, Context context, int hardLines) {
        if (!gutter) {
            return 0;
        }
        var digits = Math.max(
                MINIMUM_DIGITS, Integer.toString(Math.max(1, hardLines)).length());
        // Zeros rather than the real digits: every digit in every font this
        // toolkit ships is the same width as every other, and a column measured
        // from "18" would be a column that changed width at "11".
        var sample = context.paragraph(style, "0".repeat(digits));
        return sample.widthBetween(0, digits) + 2 * context.length(GUTTER_GAP_TOKEN, GUTTER_GAP);
    }

    /// How many lines somebody typed — the count the numbers run to.
    ///
    /// One while the placeholder is showing: a line number belongs to the
    /// document, and the placeholder is not one.
    ///
    /// The document counted them when it split itself, so this is a field read
    /// rather than the scan over the whole text it used to be ([ADR-0388]).
    private int hardLines(TextDocument document) {
        return placeholder ? 1 : document.hardLineCount();
    }

    /// The gutter's contents for the rows on screen, as one paragraph with one
    /// line per **visual** line.
    ///
    /// A hard line contributes its number; every line it wrapped into contributes
    /// an empty one. So `"1\n2\n\n\n3"` is a three-line document whose second
    /// line wrapped into three, and drawing it at the text's own line height puts
    /// every number beside the line it belongs to — by construction, rather than
    /// by two pieces of arithmetic that have to be kept agreeing.
    ///
    /// This is the whole of `docs/gaps.md` G37: a column of numbers built outside
    /// the control is right until the first line that wraps and wrong for every
    /// line below it, because nothing outside knows where the wrap fell.
    ///
    /// **Only the rows in view**, which is the one thing ADR-0388 changed here:
    /// numbering a ten-thousand-line note built a fifty-kilobyte string and
    /// shaped it again whenever the line count moved, to draw forty numbers. The
    /// paragraph starts at the first visible row rather than at the top of the
    /// document, at the same origin the text does, so it still scrolls with the
    /// text by construction.
    ///
    /// @param first the first visual row in view
    /// @param last  the last, inclusive
    private String gutterText(DocumentLines lines, int first, int last) {
        if (!gutter) {
            return "";
        }
        var out = new StringBuilder();
        for (var i = first; i <= last; i++) {
            if (i > first) {
                out.append('\n');
            }
            // A row carries a number only where a hard line began. A row a wrap
            // produced carries nothing, which is what puts the next number
            // beside the line it belongs to.
            var k = lines.hardLineOf(i);
            if (lines.firstVisualOf(k) == i) {
                out.append(k + 1);
            }
        }
        return out.toString();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var padding = AreaPadding.of(style);
        var font = context.font(style);
        // One paragraph per hard line, re-using last frame's for every line the
        // edit did not touch. The shaper is the renderer's cache, so a line this
        // control has drawn before is not shaped again even when the document
        // around it was rebuilt ([ADR-0388]).
        var document = editor.shaped(display, font, line -> context.paragraph(style, line));
        // The gutter's width is decided before the text is laid out, because the
        // text wraps at what is left over — which is why it is computed from the
        // document's line count rather than from the layout it is about to cause
        // ([ADR-0331]).
        var gutterWidth = gutterWidth(style, context, hardLines(document));
        var offset = editor.laidOut(document, padding, gutterWidth, style.textAlign());

        var lineHeight = font.lineHeight();
        var width = editor.contentWidth();
        var lines = document.lines(width);

        // The rows on screen, and one more for the row a partial scroll shows
        // half of. Everything below is drawn for these and for nothing else.
        var firstVisual = lineHeight > 0 ? (int) Math.floor(offset / lineHeight) : 0;
        firstVisual = Math.clamp(firstVisual, 0, Math.max(0, lines.size() - 1));
        var lastVisual = Math.min(lines.size() - 1, firstVisual + Math.max(1, maxRows));
        // Where the window is drawn: the first visible row's own top, less how
        // far the content has scrolled. The glyphs and the numbers are both
        // placed from this, so the one rounding Yoga does is shared.
        var windowTop = firstVisual * lineHeight - offset;

        var boxes = new ArrayList<Box>(children.size());
        // In the padding box's coordinates, not the border box's: `ContainingBlock`
        // shifts every absolutely positioned child by its containing block's
        // padding on the way to Yoga (ADR-0272), so a rectangle that added the
        // padding itself — which is what these three did until that landed —
        // would now be a padding's width too far in and a line too far down.
        // The selection, or the clause an input method is converting -- never
        // both, because there is never both (ADR-0292).
        var washStart = composing.hasClause() ? composing.clauseStart() : edit.start();
        var washEnd = composing.hasClause() ? composing.clauseEnd() : edit.end();
        var drawWash = composing.hasClause() || (!composing.isActive() && edit.hasSelection());
        var align = style.textAlign();
        var rects = drawWash
                ? spanRects(document, lines, washStart, washEnd, offset, lineHeight, align, width)
                : List.<Rect>of();
        for (var i = 0; i < maxRows; i++) {
            if (i < rects.size()) {
                var rect = rects.get(i);
                boxes.add(children.get(i)
                        .position(Position.ABSOLUTE)
                        .inset(leftTop(gutterWidth + rect.x(), rect.y()))
                        .size(Length.points((float) rect.width()), Length.points((float) lineHeight)));
            } else {
                boxes.add(Box.of());
            }
        }

        // The text: the rows on screen, as one paragraph — a slice of the
        // document between two line starts, which re-wraps to exactly the rows
        // the document said it would ([ADR-0388]). `text-value` resolved the ink
        // and the flow and drew nothing, which is what [Value#carrier] is for.
        var value = children.get(maxRows).text();
        var ink = value == null ? style.color() : value.argb();
        var flow = value == null ? style.textFlow() : value.flow();
        var visible = display.substring(
                lines.get(firstVisual).start(), lines.get(lastVisual).end());
        boxes.add(Box.text(context.paragraph(style, visible), ink, flow)
                .position(Position.ABSOLUTE)
                .inset(leftTop(gutterWidth, windowTop))
                // A definite width, because an absolutely positioned box has no
                // parent width to wrap against -- and it is the same number the
                // caret was measured against, which is what keeps the two from
                // disagreeing about where a line ends.
                //
                // Undefined before anything has been measured, which is what
                // `contentWidth` reports as "do not wrap": a definite width of
                // one point would put every word on a line of its own for one
                // frame, which is exactly what the Forms golden showed.
                .size(Double.isFinite(width) ? Length.points((float) width) : Length.UNDEFINED, Length.UNDEFINED));

        var caretWidth = context.length(Carets.WIDTH_TOKEN, Carets.WIDTH);
        var caret = caretRect(document, lines, offset, lineHeight, caretWidth, align, width);
        boxes.add(children.get(maxRows + 1)
                .position(Position.ABSOLUTE)
                .inset(leftTop(gutterWidth + caret.x(), caret.y()))
                .size(Length.points((float) caretWidth), Length.points((float) lineHeight)));

        // The rules under the composition, last so they are drawn over the
        // glyphs -- a mark on them rather than a wash behind them. Each sits on
        // the foot of its own line.
        var composed = composing.isActive()
                ? spanRects(document, lines, composing.start(), composing.end(), offset, lineHeight, align, width)
                : List.<Rect>of();
        for (var i = 0; i < maxRows; i++) {
            if (i < composed.size()) {
                var rect = composed.get(i);
                boxes.add(children.get(maxRows + 2 + i)
                        .position(Position.ABSOLUTE)
                        .inset(leftTop(gutterWidth + rect.x(), rect.y() + lineHeight - Underline.THICKNESS))
                        .size(Length.points((float) rect.width()), Length.points((float) Underline.THICKNESS)));
            } else {
                boxes.add(Box.of());
            }
        }

        Box strip = null;
        if (gutter) {
            strip = strip(children.get(gutterIndex()), style, padding, gutterWidth);

            var numbers = gutterText(lines, firstVisual, lastVisual);
            if (!numbers.isEmpty()) {
                var gap = context.length(GUTTER_GAP_TOKEN, GUTTER_GAP);
                var numberInk = context.color(GUTTER_COLOR_TOKEN, context.color("--gb-text-muted", style.color()));
                // Right-aligned against a box one gap narrower than the column, so
                // every number ends the same distance from the text — and never
                // wrapped, whatever `white-space` the cascade resolved for the
                // control, because a line number that wrapped would be nonsense.
                var numberFlow = new TextFlow(WhiteSpace.NOWRAP, TextOverflow.CLIP, TextAlign.END, TextDecoration.NONE);
                var column = Math.max(1, gutterWidth - gap);
                boxes.add(Box.text(context.paragraph(style, numbers), numberInk, numberFlow)
                        .position(Position.ABSOLUTE)
                        // The **same** origin the text is drawn at, which is what
                        // makes the two scroll together rather than nearly — one
                        // number, rounded once, for both.
                        .inset(leftTop(0, windowTop))
                        .size(Length.points((float) column), Length.UNDEFINED));
            }
        }

        // **Two layers, and the clip is on the inner one** (`docs/gaps.md` G43,
        // ADR-0350). Everything that scrolls -- the text, the washes, the caret,
        // the numbers -- has to stop at the content box, which is where a box's
        // own `overflow: hidden` clips. The strip has to reach the border, which
        // is outside that clip by exactly the padding. On one box those two ask
        // for different clips. So the scrolling parts go in a
        // box pinned to the content box that clips, and the strip is its sibling
        // under a control that does not.
        //
        // The inner box's insets are zero on all four edges: `ContainingBlock`
        // shifts each by the control's padding, which is what lands it on the
        // content box, and it has no padding of its own, so every part inside it
        // is placed in the same coordinates it was before.
        var content = Box.of()
                .position(Position.ABSOLUTE)
                .inset(new Insets(ZERO, ZERO, ZERO, ZERO))
                .overflow(Overflow.HIDDEN)
                .children(boxes.toArray(Box[]::new));
        var layerList = new ArrayList<Box>(3);
        if (strip != null) {
            layerList.add(strip);
        }
        layerList.add(content);
        if (scrollbar != null) {
            layerList.add(bar(children.getLast(), style, padding));
        }
        var layers = layerList.toArray(Box[]::new);

        var box = Box.of().style(style).children(layers);
        // **A filling area takes what its parent gives it.** `flex-grow` rather than
        // a height, because the height is the layout's answer and not this widget's:
        // what it then does with it -- how many lines are on screen, how far the text
        // may scroll -- comes back through the measurement (ADR-0296).
        box = fill
                ? box.grow(1).shrink(1).size(Length.UNDEFINED, Length.UNDEFINED)
                : box.size(Length.UNDEFINED, Length.points((float) height(lines.size(), lineHeight, padding)));
        return box.cursor(disabled ? Cursor.DEFAULT : Cursor.TEXT).overflow(Overflow.VISIBLE);
    }

    /// A zero inset, which [io.github.digitalsmile.goldberry.paint.tree.ContainingBlock]
    /// moves onto the padding edge.
    private static final Length ZERO = Length.points(0);

    /// The number column's fill, placed from the inside of the border to the
    /// start of the text.
    ///
    /// **Inside the border, not on it.** A control that no longer clips its
    /// children would otherwise have the strip painted over its own 1px edge,
    /// so each inset is the padding less the border's width. The two corners on
    /// the leading side take the control's radius less that width as well,
    /// because a square strip in a rounded field shows its corners outside the
    /// curve.
    ///
    /// The insets are **negative paddings** on purpose. `ContainingBlock` adds the
    /// control's padding to every inset it is given, so `-padding + border` is
    /// where it lands: the border's inner edge.
    private static Box strip(Box part, ComputedStyle style, AreaPadding padding, double gutterWidth) {
        var border = style.decoration().borderWidth();
        var corners = style.decoration().corners();
        var fitted =
                new Corners(Math.max(0, corners.topLeft() - border), 0, 0, Math.max(0, corners.bottomLeft() - border));
        return part.position(Position.ABSOLUTE)
                .inset(new Insets(
                        Length.points((float) (border - padding.top())),
                        Length.UNDEFINED,
                        Length.points((float) (border - padding.bottom())),
                        Length.points((float) (border - padding.left()))))
                .size(Length.points((float) Math.max(0, gutterWidth + padding.left() - border)), Length.UNDEFINED)
                .decoration(part.decoration().corners(fitted));
    }

    /// The scrollbar, down the content box's height and against the inside of the
    /// right border — the right inset is a negative padding for [#strip]'s reason.
    /// Beside the clipped layer rather than in it, so the part of it over the
    /// padding is drawn and can be pressed.
    private static Box bar(Box part, ComputedStyle style, AreaPadding padding) {
        var border = style.decoration().borderWidth();
        return part.position(Position.ABSOLUTE)
                .inset(new Insets(ZERO, Length.points((float) (border - padding.right())), ZERO, Length.UNDEFINED));
    }

    /// The control's height: as many lines as the text has, between [#rows] and
    /// [#maxRows], plus the padding.
    ///
    /// §4's auto-grow. Set here and not in the stylesheet because it is a
    /// function of how many lines the text wrapped into, which no selector can
    /// ask — a `height` a stylesheet set would be a control that stopped growing
    /// the moment somebody themed it.
    private double height(int lines, double lineHeight, AreaPadding padding) {
        var shown = Math.clamp(lines, rows, Math.max(rows, maxRows));
        return shown * lineHeight + padding.vertical();
    }

    /// One rectangle per visual line a span of the display covers — the
    /// selection, the clause an input method is converting, or the composition
    /// being underlined.
    ///
    /// A run of wrapped text is not a rectangle, which is the whole of what a
    /// second dimension costs the selection — and the reason `Paragraph`'s two
    /// measurements take a **line's** range rather than an offset.
    /// Walked from the line the span **starts** on rather than from the top of
    /// the document, and stopped at the line it ends on: a selection near the
    /// end of a long note used to cost a walk over every line above it
    /// ([ADR-0388]).
    private List<Rect> spanRects(
            TextDocument document,
            DocumentLines lines,
            int start,
            int end,
            double offset,
            double lineHeight,
            TextAlign align,
            double width) {

        var rects = new ArrayList<Rect>();
        if (!focused || end <= start) {
            return rects;
        }
        var from = Math.clamp(start, 0, display.length());
        var to = Math.clamp(end, 0, display.length());
        for (var i = lines.indexOf(from); i < lines.size() && rects.size() < maxRows; i++) {
            var line = lines.get(i);
            if (line.start() >= to) {
                break;
            }
            var left = Math.max(from, line.start());
            var right = Math.min(to, line.end());
            if (left >= right) {
                continue;
            }
            rects.add(new Rect(
                    align.indentOf(line.width(), width) + document.widthBetween(line.start(), left),
                    i * lineHeight - offset,
                    Math.max(1, document.widthBetween(left, right))));
        }
        return rects;
    }

    /// Where the caret goes — which line it is on, and how far along.
    ///
    /// The **last** line that starts at or before it, which is what decides a
    /// caret sitting exactly on a wrap: that offset is the end of one line and
    /// the start of the next, and somebody who has just pressed `Right` means the
    /// next.
    private Rect caretRect(
            TextDocument document,
            DocumentLines lines,
            double offset,
            double lineHeight,
            double caretWidth,
            TextAlign align,
            double width) {

        var at = Math.clamp(edit.caret(), 0, display.length());
        // Asked of the document rather than walked for, which is the same answer
        // and does not read a line the caret is nowhere near ([ADR-0388]).
        var index = lines.indexOf(at);
        var line = lines.isEmpty() ? null : lines.get(index);
        // The line's own indent, because the paint gave each line its own share of
        // the slack — a caret measured from the paragraph's origin drifts by half of
        // it under `center` and by all of it under `end` ([ADR-0324]).
        var x = line == null
                ? align.indentOf(0, width)
                : align.indentOf(line.width(), width) + document.widthBetween(line.start(), Math.max(at, line.start()));
        return new Rect(x, index * lineHeight - offset, caretWidth);
    }

    private static Insets leftTop(double left, double top) {
        return new Insets(Length.points((float) top), Length.UNDEFINED, Length.UNDEFINED, Length.points((float) left));
    }

    /// A placed rectangle, one line tall.
    private record Rect(double x, double y, double width) {}

    @Override
    public Role role() {
        return Role.TEXT_FIELD;
    }

    /// No name of its own: as `TextField`.
    @Override
    public @Nullable String accessibleName() {
        return null;
    }
}
