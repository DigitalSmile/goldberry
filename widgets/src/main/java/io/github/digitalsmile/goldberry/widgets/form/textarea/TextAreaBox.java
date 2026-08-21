package io.github.digitalsmile.goldberry.widgets.form.textarea;

import io.github.digitalsmile.goldberry.backend.Cursor;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.Extent;
import io.github.digitalsmile.goldberry.input.Handles;
import io.github.digitalsmile.goldberry.input.KeyEvent;
import io.github.digitalsmile.goldberry.input.Measured;
import io.github.digitalsmile.goldberry.input.PointerEvent;
import io.github.digitalsmile.goldberry.input.TextEvent;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.natives.yoga.Insets;
import io.github.digitalsmile.goldberry.natives.yoga.Overflow;
import io.github.digitalsmile.goldberry.natives.yoga.PositionType;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import io.github.digitalsmile.goldberry.text.Paragraph;
import io.github.digitalsmile.goldberry.text.TextLine;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.form.parts.Caret;
import io.github.digitalsmile.goldberry.widgets.form.parts.Highlight;
import io.github.digitalsmile.goldberry.widgets.form.parts.Value;
import io.github.digitalsmile.goldberry.widgets.form.textinput.TextEdit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
/// ([ADR-0167](../../../../../../../../book/src/adr/0167-a-field-owns-its-caret-and-the-model-is-told.md)).
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
/// @param focused     whether it has the keyboard
/// @param caretShown  whether this is the lit half of the blink
/// @param rows        its minimum height in lines
/// @param maxRows     the height it grows to before it scrolls
/// @param disabled    whether it refuses everything
/// @param readOnly    whether it takes a caret but no edits
/// @param attributes  the `id` and classes the document wrote
/// @param editor      what to tell about a key, a click or a measurement
record TextAreaBox(
        String display, boolean placeholder, TextEdit edit, boolean focused, boolean caretShown,
        int rows, int maxRows, boolean disabled, boolean readOnly, Attributes attributes,
        AreaEditor editor)
        implements Widget.Leaf, Styled, Paints, Handles, Measured {

    /// How wide the caret is, in logical pixels — `text-input`'s, and for its
    /// reason: the width is set in the same call that sets the position, so a
    /// stylesheet that disagreed would move the caret rather than resize it.
    private static final double CARET_WIDTH = 1;

    /// How many lines a wheel notch moves. Three, which is what every scroll view
    /// on every desktop does and what `scroll` itself uses.
    static final int WHEEL_LINES = 3;

    @Override
    public String cssType() {
        return "text-area";
    }

    @Override
    public String id() {
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
                editor.pointerAt(event.local().x(), event.local().y(),
                        event.modifiers().shift(), event.clickCount());
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
                if (editor.scrollBy(-event.deltaY())) {
                    event.consume();
                }
            }
            default -> {
            }
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
            var handled = switch (event.key()) {
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

        var handled = switch (event.key()) {
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
        var parts = new ArrayList<Widget>(maxRows + 2);
        for (var i = 0; i < maxRows; i++) {
            parts.add(new Highlight(focused && edit.hasSelection()));
        }
        parts.add(new Value(display, placeholder));
        parts.add(new Caret(focused && caretShown && !edit.hasSelection()));
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
        var rects = selectionRects(paragraph, lines, padding, offset, lineHeight);
        for (var i = 0; i < maxRows; i++) {
            if (i < rects.size()) {
                var rect = rects.get(i);
                boxes.add(children.get(i)
                        .position(PositionType.ABSOLUTE)
                        .inset(leftTop(rect.x(), rect.y()))
                        .size(StyleLength.points((float) rect.width()),
                                StyleLength.points((float) lineHeight)));
            } else {
                boxes.add(Box.of());
            }
        }

        boxes.add(children.get(children.size() - 2)
                .position(PositionType.ABSOLUTE)
                .inset(leftTop(padding.left(), padding.top() - offset))
                // A definite width, because an absolutely positioned box has no
                // parent width to wrap against -- and it is the same number the
                // caret was measured against, which is what keeps the two from
                // disagreeing about where a line ends.
                //
                // Undefined before anything has been measured, which is what
                // `contentWidth` reports as "do not wrap": a definite width of
                // one point would put every word on a line of its own for one
                // frame, which is exactly what the Forms golden showed.
                .size(Double.isFinite(width)
                                ? StyleLength.points((float) width) : StyleLength.UNDEFINED,
                        StyleLength.UNDEFINED));

        var caret = caretRect(paragraph, lines, padding, offset, lineHeight);
        boxes.add(children.get(children.size() - 1)
                .position(PositionType.ABSOLUTE)
                .inset(leftTop(caret.x(), caret.y()))
                .size(StyleLength.points((float) CARET_WIDTH),
                        StyleLength.points((float) lineHeight)));

        return Box.of().style(style)
                .children(boxes.toArray(Box[]::new))
                .size(StyleLength.UNDEFINED,
                        StyleLength.points((float) height(lines.size(), lineHeight, padding)))
                .cursor(disabled ? Cursor.DEFAULT : Cursor.TEXT)
                .overflow(Overflow.HIDDEN);
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

    /// One rectangle per visual line the selection covers.
    ///
    /// A run of wrapped text is not a rectangle, which is the whole of what a
    /// second dimension costs the selection — and the reason `Paragraph`'s two
    /// measurements take a **line's** range rather than an offset.
    private List<Rect> selectionRects(Paragraph paragraph, List<TextLine> lines, Insets2 padding,
            double offset, double lineHeight) {
        var rects = new ArrayList<Rect>();
        if (!edit.hasSelection() || !focused) {
            return rects;
        }
        var from = Math.clamp(edit.start(), 0, display.length());
        var to = Math.clamp(edit.end(), 0, display.length());
        for (var i = 0; i < lines.size() && rects.size() < maxRows; i++) {
            var line = lines.get(i);
            var start = Math.max(from, line.start());
            var end = Math.min(to, line.end());
            if (start >= end) {
                continue;
            }
            rects.add(new Rect(
                    padding.left() + paragraph.widthBetween(line.start(), start),
                    padding.top() + i * lineHeight - offset,
                    Math.max(1, paragraph.widthBetween(start, end))));
        }
        return rects;
    }

    /// Where the caret goes — which line it is on, and how far along.
    ///
    /// The **last** line that starts at or before it, which is what decides a
    /// caret sitting exactly on a wrap: that offset is the end of one line and
    /// the start of the next, and somebody who has just pressed `Right` means the
    /// next.
    private Rect caretRect(Paragraph paragraph, List<TextLine> lines, Insets2 padding,
            double offset, double lineHeight) {
        var at = Math.clamp(edit.caret(), 0, display.length());
        var index = 0;
        for (var i = 0; i < lines.size(); i++) {
            if (lines.get(i).start() <= at) {
                index = i;
            }
        }
        var line = lines.isEmpty() ? null : lines.get(index);
        var x = line == null ? 0 : paragraph.widthBetween(line.start(), Math.max(at, line.start()));
        return new Rect(padding.left() + x, padding.top() + index * lineHeight - offset,
                CARET_WIDTH);
    }

    private static Insets leftTop(double left, double top) {
        return new Insets(
                StyleLength.points((float) top),
                StyleLength.UNDEFINED,
                StyleLength.UNDEFINED,
                StyleLength.points((float) left));
    }

    private static Insets2 padding(ComputedStyle style) {
        return new Insets2(points(style.padding().left()), points(style.padding().top()),
                points(style.padding().bottom()));
    }

    private static double points(StyleLength length) {
        return length instanceof StyleLength.Points p ? p.value() : 0;
    }

    /// The three padding edges this control reads. Not `Insets`, which is four
    /// `StyleLength`s and needs resolving at every use.
    private record Insets2(double left, double top, double bottom) {
    }

    /// A placed rectangle, one line tall.
    private record Rect(double x, double y, double width) {
    }
}
