package io.github.digitalsmile.goldberry.widgets.form.textarea;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
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
import io.github.digitalsmile.goldberry.text.Paragraph;
import io.github.digitalsmile.goldberry.text.TextLine;
import io.github.digitalsmile.goldberry.text.edit.TextEdit;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widget.semantics.Semantics;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
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
/// text-area           this node. Clips, focuses, takes the keys and the pointer
/// ├── text-selection  × n — one per **visual** line the selection covers
/// ├── text-value      the text, wrapped at the control's width
/// └── text-caret      the insertion point
/// ```
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
        boolean disabled,
        boolean readOnly,
        Attributes attributes,
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

    /// §4's editing keys, and the two things a second dimension changes.
    ///
    /// **`Enter` is taken here**, which is the one key whose meaning differs from
    /// `text-input`'s: a multi-line control is where a newline comes from, and a
    /// form's default button cannot have it. `Escape` still is not — that belongs
    /// to the dialog around this.
    @Override
    public void onKey(KeyEvent event) {
        if (disabled || event.kind() != KeyEvent.Kind.PRESSED) {
            return;
        }
        var modifiers = event.modifiers();
        var word = modifiers.control();
        var extend = modifiers.shift();

        if (modifiers.control() && !modifiers.alt()) {
            var handled =
                    switch (event.key()) {
                        case A -> editor.selectAll();
                        case C -> editor.copy();
                        case X -> !readOnly && editor.cut();
                        case V -> !readOnly && editor.paste();
                        case Z -> !readOnly && (modifiers.shift() ? editor.redo() : editor.undo());
                        case Y -> !readOnly && editor.redo();
                        // Ctrl+Home and Ctrl+End are the whole text, which is what the
                        // modifier means everywhere it appears on these two keys.
                        case HOME -> editor.move(AreaEditor.Motion.START, false, extend);
                        case END -> editor.move(AreaEditor.Motion.END, false, extend);
                        default -> false;
                    };
            if (handled) {
                event.consume();
                return;
            }
        }

        var handled =
                switch (event.key()) {
                    case LEFT -> editor.move(AreaEditor.Motion.LEFT, word, extend);
                    case RIGHT -> editor.move(AreaEditor.Motion.RIGHT, word, extend);
                    case UP -> editor.moveLine(-1, extend);
                    case DOWN -> editor.moveLine(1, extend);
                    case PAGE_UP -> editor.moveLine(-Math.max(1, rows), extend);
                    case PAGE_DOWN -> editor.moveLine(Math.max(1, rows), extend);
                    case HOME -> editor.move(AreaEditor.Motion.LINE_START, word, extend);
                    case END -> editor.move(AreaEditor.Motion.LINE_END, word, extend);
                    case BACKSPACE -> !readOnly && editor.deleteBefore(word);
                    case DELETE -> !readOnly && editor.deleteAfter(word);
                    // The one key that means something here and nothing in a
                    // `text-input`. Consumed either way when it is taken, so a form's
                    // default button does not also fire.
                    case ENTER -> !readOnly && editor.type("\n");
                    default -> false;
                };
        if (handled) {
            event.consume();
        }
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
        parts.add(new Value(display, placeholder));
        parts.add(new Caret(focused && caretShown && !edit.hasSelection()));
        // And [#maxRows] underlines after them, for the highlights' reason: a
        // composition can wrap, and a run of wrapped text is not a rectangle.
        for (var i = 0; i < maxRows; i++) {
            parts.add(new Underline(focused && composing.isActive()));
        }
        return parts;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var padding = padding(style);
        var paragraph = context.paragraph(style, display);
        var offset = editor.laidOut(paragraph, padding.left(), padding.top());

        var lineHeight = paragraph.font().lineHeight();
        var width = editor.contentWidth();
        var lines = paragraph.layout(width).lines();

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
        var rects = drawWash ? spanRects(paragraph, lines, washStart, washEnd, offset, lineHeight) : List.<Rect>of();
        for (var i = 0; i < maxRows; i++) {
            if (i < rects.size()) {
                var rect = rects.get(i);
                boxes.add(children.get(i)
                        .position(Position.ABSOLUTE)
                        .inset(leftTop(rect.x(), rect.y()))
                        .size(Length.points((float) rect.width()), Length.points((float) lineHeight)));
            } else {
                boxes.add(Box.of());
            }
        }

        boxes.add(children.get(maxRows)
                .position(Position.ABSOLUTE)
                .inset(leftTop(0, -offset))
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
        var caret = caretRect(paragraph, lines, offset, lineHeight, caretWidth);
        boxes.add(children.get(maxRows + 1)
                .position(Position.ABSOLUTE)
                .inset(leftTop(caret.x(), caret.y()))
                .size(Length.points((float) caretWidth), Length.points((float) lineHeight)));

        // The rules under the composition, last so they are drawn over the
        // glyphs -- a mark on them rather than a wash behind them. Each sits on
        // the foot of its own line.
        var composed = composing.isActive()
                ? spanRects(paragraph, lines, composing.start(), composing.end(), offset, lineHeight)
                : List.<Rect>of();
        for (var i = 0; i < maxRows; i++) {
            if (i < composed.size()) {
                var rect = composed.get(i);
                boxes.add(children.get(maxRows + 2 + i)
                        .position(Position.ABSOLUTE)
                        .inset(leftTop(rect.x(), rect.y() + lineHeight - Underline.THICKNESS))
                        .size(Length.points((float) rect.width()), Length.points((float) Underline.THICKNESS)));
            } else {
                boxes.add(Box.of());
            }
        }

        var box = Box.of().style(style).children(boxes.toArray(Box[]::new));
        // **A filling area takes what its parent gives it.** `flex-grow` rather than
        // a height, because the height is the layout's answer and not this widget's:
        // what it then does with it -- how many lines are on screen, how far the text
        // may scroll -- comes back through the measurement (ADR-0296).
        box = fill
                ? box.grow(1).shrink(1).size(Length.UNDEFINED, Length.UNDEFINED)
                : box.size(Length.UNDEFINED, Length.points((float) height(lines.size(), lineHeight, padding)));
        return box.cursor(disabled ? Cursor.DEFAULT : Cursor.TEXT).overflow(Overflow.HIDDEN);
    }

    /// The control's height: as many lines as the text has, between [#rows] and
    /// [#maxRows], plus the padding.
    ///
    /// §4's auto-grow. Set here and not in the stylesheet because it is a
    /// function of how many lines the text wrapped into, which no selector can
    /// ask — a `height` a stylesheet set would be a control that stopped growing
    /// the moment somebody themed it.
    private double height(int lines, double lineHeight, Insets2 padding) {
        var shown = Math.clamp(lines, rows, Math.max(rows, maxRows));
        return shown * lineHeight + padding.top() + padding.bottom();
    }

    /// One rectangle per visual line a span of the display covers — the
    /// selection, the clause an input method is converting, or the composition
    /// being underlined.
    ///
    /// A run of wrapped text is not a rectangle, which is the whole of what a
    /// second dimension costs the selection — and the reason `Paragraph`'s two
    /// measurements take a **line's** range rather than an offset.
    private List<Rect> spanRects(
            Paragraph paragraph, List<TextLine> lines, int start, int end, double offset, double lineHeight) {
        var rects = new ArrayList<Rect>();
        if (!focused || end <= start) {
            return rects;
        }
        var from = Math.clamp(start, 0, display.length());
        var to = Math.clamp(end, 0, display.length());
        for (var i = 0; i < lines.size() && rects.size() < maxRows; i++) {
            var line = lines.get(i);
            var left = Math.max(from, line.start());
            var right = Math.min(to, line.end());
            if (left >= right) {
                continue;
            }
            rects.add(new Rect(
                    paragraph.widthBetween(line.start(), left),
                    i * lineHeight - offset,
                    Math.max(1, paragraph.widthBetween(left, right))));
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
            Paragraph paragraph, List<TextLine> lines, double offset, double lineHeight, double caretWidth) {
        var at = Math.clamp(edit.caret(), 0, display.length());
        var index = 0;
        for (var i = 0; i < lines.size(); i++) {
            if (lines.get(i).start() <= at) {
                index = i;
            }
        }
        var line = lines.isEmpty() ? null : lines.get(index);
        var x = line == null ? 0 : paragraph.widthBetween(line.start(), Math.max(at, line.start()));
        return new Rect(x, index * lineHeight - offset, caretWidth);
    }

    private static Insets leftTop(double left, double top) {
        return new Insets(Length.points((float) top), Length.UNDEFINED, Length.UNDEFINED, Length.points((float) left));
    }

    private static Insets2 padding(ComputedStyle style) {
        return new Insets2(
                points(style.padding().left()),
                points(style.padding().top()),
                points(style.padding().bottom()));
    }

    private static double points(Length length) {
        return length instanceof Length.Points p ? p.value() : 0;
    }

    /// The three padding edges this control reads. Not `Insets`, which is four
    /// `Length`s and needs resolving at every use.
    private record Insets2(double left, double top, double bottom) {}

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
