package io.github.digitalsmile.goldberry.text.edit;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.paint.Frame;
import io.github.digitalsmile.goldberry.render.Clipboard;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.text.Paragraph;
import io.github.digitalsmile.goldberry.text.TextLayout;
import io.github.digitalsmile.goldberry.text.font.Font;

/// A text editor an application drives itself — on a `canvas`, at any transform,
/// over text no widget owns.
///
/// ```java
/// var editor = new Editor(font).wrapWidth(240).multiline(true).clipboard(window.clipboard());
///
/// new Canvas((frame, size) -> editor.paint(frame, 8, 8, INK, focused), new Input() {
///     public boolean wantsText()                { return true; }   // or nothing is ever typed
///     public void onPointer(PointerEvent event) { … editor.pointerAt(x, y, extend, event.clickCount()); }
///     public void onKey(KeyEvent event)         { if (editor.onKey(event)) event.consume(); }
///     public void onText(TextEvent event)       { if (editor.onText(event.text())) event.consume(); }
///     public void onFocusChanged(boolean focused, boolean fromKeyboard) { … }
/// });
/// ```
///
/// ## What it is, and what `text-input` is
///
/// `text-input` and `text-area` are **controls**: a box with a border, a
/// placeholder, a label, a validation state and a focus ring, which take their
/// keys through the element tree. This is the editor **without** any of that — a
/// caret, a selection, an undo stack and a key map over a string — for the case
/// `docs/gaps.md` G6 named: a sticky on a board, a label on a shape, a cell in a
/// drawing. Those are not widgets and cannot be, because what they are drawn into
/// is an application's own canvas at an application's own transform (ADR-0285).
///
/// It holds three things and nothing else: a [TextEdit] (the string, the caret
/// and the anchor), an [EditHistory] (undo, with a typing run folded into one
/// step), and a [Paragraph] it re-shapes when the text changes. Everything
/// visual — where to draw it, what colour, whether the caret blinks, whether
/// there is a border — stays the caller's.
///
/// ## A canvas holding one must ask for the keyboard
///
/// `Input.wantsText()` — override it to `true`, or nothing will ever be typed
/// into this. Committed text is delivered to whatever has the focus, and on a
/// desktop the platform does not *produce* any until it is told that something is
/// being typed into: an editor on a canvas that has not said so receives every
/// arrow key and no characters at all (ADR-0285).
///
/// ## Coordinates
///
/// Points handed to [#pointerAt] and rectangles handed back are in the **text's**
/// own space: `(0, 0)` is the top-left of the first line. A caller drawing at an
/// offset subtracts it first, which is one line at the call site and is why this
/// class has no notion of padding, scrolling or a canvas transform.
///
/// ## Threads and lifetime
///
/// The UI thread, like everything else that touches input. Nothing here is closed:
/// the [Font] is the caller's, and a paragraph holds no foreign memory
/// (ADR-0282).
public final class Editor {

    private final Font font;

    private TextEdit edit = TextEdit.EMPTY;

    private final EditHistory history = new EditHistory();

    private double wrapWidth = Paragraph.UNCONSTRAINED;

    private boolean multiline;

    private boolean readOnly;

    private @Nullable Clipboard clipboard;

    /// The shaped text, rebuilt when the text changes and not before.
    ///
    /// Shaping a sticky's worth of text is microseconds and a keystroke is a
    /// human, so this is a cache for correctness rather than for speed: the
    /// caret, the hit test and the paint must all measure the *same* shaping, and
    /// the way to guarantee that is for there to be one.
    private @Nullable Paragraph paragraph;

    private @Nullable TextLayout layout;

    /// The column a run of `Up`/`Down` is keeping, or NaN when the last thing the
    /// caret did was not vertical.
    private double desiredX = Double.NaN;

    /// A new, empty editor whose text is shaped with `font`.
    public Editor(Font font) {
        this.font = Objects.requireNonNull(font, "font");
    }

    /// The width the text wraps at, in logical units.
    ///
    /// [Paragraph#UNCONSTRAINED] by default, which is one long line — a label or a
    /// single-line field. Set it to the box the text is drawn in and the caret,
    /// the hit test and `Up`/`Down` all follow the wrap.
    public Editor wrapWidth(double width) {
        if (width != wrapWidth) {
            this.wrapWidth = width;
            this.layout = null;
        }
        return this;
    }

    /// Whether `Enter` inserts a newline. False by default.
    ///
    /// A single-line editor lets `Enter` through **unhandled**, so the surrounding
    /// application can submit on it — which is what a label being edited on a
    /// board wants, and what a field in a form wants.
    public Editor multiline(boolean value) {
        this.multiline = value;
        return this;
    }

    /// Whether edits are refused. Movement, selection and copy still work — which
    /// is the difference between read-only and disabled.
    public Editor readOnly(boolean value) {
        this.readOnly = value;
        return this;
    }

    /// The clipboard cut, copy and paste use. Without one they are no-ops that
    /// report `false`, so a key is not swallowed by a feature that is not there.
    public Editor clipboard(Clipboard value) {
        this.clipboard = Objects.requireNonNull(value, "clipboard");
        return this;
    }

    // --- the text ------------------------------------------------------------

    public String text() {
        return edit.text();
    }

    /// The whole state — the text, the caret and the anchor.
    public TextEdit edit() {
        return edit;
    }

    /// Replaces the text, putting the caret at the end and **clearing the undo
    /// history**.
    ///
    /// This is a document being loaded rather than an edit being made: an undo
    /// that reached back past it would restore text the user never typed into a
    /// document they had moved on from.
    public Editor text(String replacement) {
        Objects.requireNonNull(replacement, "replacement");
        edit = TextEdit.of(replacement);
        history.clear();
        invalidate();
        return this;
    }

    /// Puts the caret at `offset`, or extends the selection to it.
    public Editor caretTo(int offset, boolean extend) {
        edit = edit.caretTo(offset, extend);
        desiredX = Double.NaN;
        return this;
    }

    public boolean canUndo() {
        return history.canUndo();
    }

    public boolean canRedo() {
        return history.canRedo();
    }

    public boolean undo() {
        var before = edit;
        edit = history.undo(edit);
        return applied(before);
    }

    public boolean redo() {
        var before = edit;
        edit = history.redo(edit);
        return applied(before);
    }

    // --- input ---------------------------------------------------------------

    /// Committed text — what the platform decided was typed, after its own
    /// keyboard layout, dead keys and IME.
    ///
    /// This is [io.github.digitalsmile.goldberry.input.handler.Handles#onText]'s
    /// payload and not a key: an editor must never build characters out of key
    /// codes itself, or it is a US keyboard pretending to be every keyboard.
    ///
    /// @return whether anything was inserted, which is whether to consume the event
    public boolean onText(String typed) {
        Objects.requireNonNull(typed, "typed");
        if (readOnly || typed.isEmpty()) {
            return false;
        }
        return apply(edit.insert(typed), EditHistory.Kind.TYPING);
    }

    /// The key map — movement, deletion, selection, undo and the clipboard.
    ///
    /// Deliberately the same map `text-input` has, key for key, because two
    /// editors in one toolkit that disagree about what `Ctrl+Shift+Z` does is a
    /// toolkit with a bug in one of them.
    ///
    /// @return whether the key did something, which is whether to consume it. A
    ///         key this does not handle is left alone — `Tab` still moves focus,
    ///         `Escape` still closes what it closes, and `Enter` in a single-line
    ///         editor still submits.
    public boolean onKey(KeyEvent event) {
        Objects.requireNonNull(event, "event");
        if (event.kind() != KeyEvent.Kind.PRESSED) {
            return false;
        }
        var modifiers = event.modifiers();
        var word = modifiers.control();
        var extend = modifiers.shift();

        // The accelerators first, so Ctrl+A means "select all" here rather than
        // reaching the window's shortcut map.
        if (modifiers.control() && !modifiers.alt()) {
            var handled =
                    switch (event.key()) {
                        case A -> select(edit.selectAll());
                        case C -> copy();
                        case X -> !readOnly && cut();
                        case V -> !readOnly && paste();
                        case Z -> !readOnly && (modifiers.shift() ? redo() : undo());
                        case Y -> !readOnly && redo();
                        default -> false;
                    };
            if (handled) {
                return true;
            }
        }

        return switch (event.key()) {
            case LEFT -> move(word ? edit.wordLeft(extend) : edit.left(extend));
            case RIGHT -> move(word ? edit.wordRight(extend) : edit.right(extend));
            // `Ctrl` jumps to the ends of the text; plain Home and End are the
            // ends of the *visual* line, which is a question only the layout can
            // answer and is why they go through the geometry.
            case HOME -> move(word ? edit.toStart(extend) : toLineEdge(true, extend));
            case END -> move(word ? edit.toEnd(extend) : toLineEdge(false, extend));
            case UP -> verticalBy(-1, extend);
            case DOWN -> verticalBy(1, extend);
            case PAGE_UP -> verticalBy(-pageLines(), extend);
            case PAGE_DOWN -> verticalBy(pageLines(), extend);
            case BACKSPACE -> !readOnly && apply(word ? edit.deleteWordBefore() : edit.backspace(), deleting());
            case DELETE -> !readOnly && apply(word ? edit.deleteWordAfter() : edit.delete(), deleting());
            case ENTER -> multiline && !readOnly && apply(edit.insert("\n"), EditHistory.Kind.TYPING);
            default -> false;
        };
    }

    /// The pointer went down, or was dragged, at a point in the text's own space.
    ///
    /// @param extend     whether this extends the selection — `Shift` on a press,
    ///                   and always on a drag
    /// @param clickCount 1 places the caret, 2 selects a word, 3 selects the lot
    public void pointerAt(double x, double y, boolean extend, int clickCount) {
        var offset = TextGeometry.offsetAt(paragraph(), layout(), x, y);
        edit = switch (Math.min(clickCount, 3)) {
            case 2 -> edit.wordAt(offset);
            case 3 -> edit.selectAll();
            default -> edit.caretTo(offset, extend);
        };
        desiredX = Double.NaN;
        history.endRun();
    }

    /// Copies the selection. `false` when there is nothing selected or no
    /// clipboard, so the key is not consumed by a copy that did not happen.
    public boolean copy() {
        var board = clipboard;
        if (board == null || !edit.hasSelection()) {
            return false;
        }
        return board.text(edit.selectedText());
    }

    public boolean cut() {
        if (readOnly || !copy()) {
            return false;
        }
        return apply(edit.insert(""), EditHistory.Kind.OTHER);
    }

    public boolean paste() {
        var board = clipboard;
        if (readOnly || board == null || !board.hasText()) {
            return false;
        }
        var text = board.text();
        if (text.isEmpty()) {
            return false;
        }
        // A pasted newline in a single-line editor becomes a space rather than
        // being dropped: the text arrived from somewhere the user chose, and
        // silently losing half of it is worse than flattening it.
        return apply(edit.insert(multiline ? text : text.replaceAll("\\R", " ")), EditHistory.Kind.OTHER);
    }

    // --- geometry and painting -----------------------------------------------

    /// The shaped text, for a caller that paints it itself.
    ///
    /// The same instance the caret and the hit test are measured against, which is
    /// the point of it being handed out rather than rebuilt by the caller.
    public Paragraph paragraph() {
        var current = paragraph;
        if (current == null) {
            current = Paragraph.of(font, edit.text());
            paragraph = current;
            layout = null;
        }
        return current;
    }

    /// The line breaking at the current [#wrapWidth(double)].
    public TextLayout layout() {
        var current = layout;
        if (current == null) {
            current = paragraph().layout(wrapWidth);
            if (current == null) {
                // A paragraph with nothing in it lays out to nothing; a caret
                // still has to be somewhere, so it is one empty line.
                current = new TextLayout(
                        List.of(new io.github.digitalsmile.goldberry.text.TextLine(0, 0, 0, 0, 0)),
                        0,
                        font.lineHeight());
            }
            layout = current;
        }
        return current;
    }

    /// Where to draw the caret, in the text's own space.
    public TextGeometry.Caret caret() {
        return TextGeometry.caretAt(paragraph(), layout(), edit.caret());
    }

    /// The selection, one rectangle per visual line. Empty when nothing is
    /// selected.
    public List<LogicalRect> selectionRects() {
        return TextGeometry.selectionRects(paragraph(), layout(), edit.start(), edit.end());
    }

    /// What a caller draws with: the three colours an edited string is made of.
    ///
    /// A record rather than three `int` parameters because two of them are the
    /// same type and the wrong order is a selection drawn in the caret's colour —
    /// which looks like a bug in the editor rather than at the call site.
    ///
    /// @param text      the glyphs
    /// @param selection the highlight behind them
    /// @param caret     the caret, which is often the text's colour and is not
    ///                  always
    public record Ink(int text, int selection, int caret) {

        /// One colour for the text and its caret, with a highlight behind them.
        public static Ink of(int text, int selection) {
            return new Ink(text, selection, text);
        }
    }

    /// Draws the selection, the text and the caret, at `(x, top)`.
    ///
    /// In that order, which is the only order that works: the highlight is behind
    /// the glyphs and the caret is in front of them.
    ///
    /// The caret is drawn only when `caretVisible` — a blink is a clock and a
    /// repaint, and both are the application's. A caller that is not focused
    /// passes `false`.
    ///
    /// @param caretWidth in logical units; one is what every desktop draws
    public void paint(Frame frame, double x, double top, Ink ink, boolean caretVisible, double caretWidth) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(ink, "ink");

        for (var rect : selectionRects()) {
            frame.fillRect(
                    (float) (x + rect.left()),
                    (float) (top + rect.top()),
                    rect.width(),
                    rect.height(),
                    ink.selection());
        }
        paragraph().paint(frame, x, top, wrapWidth, ink.text());
        if (caretVisible) {
            var caret = caret();
            frame.fillRect(
                    (float) (x + caret.x()),
                    (float) (top + caret.top()),
                    (float) caretWidth,
                    (float) caret.height(),
                    ink.caret());
        }
    }

    /// [#paint(Frame, double, double, Ink, boolean, double)] with a one-unit
    /// caret.
    public void paint(Frame frame, double x, double top, Ink ink, boolean caretVisible) {
        paint(frame, x, top, ink, caretVisible, 1);
    }

    // --- the plumbing --------------------------------------------------------

    /// `Home` and `End` on the **visual** line, which is what a reader means by
    /// them and is not [TextEdit]'s hard-line start.
    private TextEdit toLineEdge(boolean start, boolean extend) {
        var line = layout().lines().get(TextGeometry.lineOf(layout(), edit.caret()));
        return edit.caretTo(start ? line.start() : line.end(), extend);
    }

    private boolean verticalBy(int lines, boolean extend) {
        var keep = Double.isNaN(desiredX) ? caret().x() : desiredX;
        var offset = TextGeometry.moveLine(paragraph(), layout(), edit.caret(), lines, keep);
        var before = edit;
        edit = edit.caretTo(offset, extend);
        // Set *after* the move and not before it, so that a run of Up/Down keeps
        // the column it started with rather than the one it has drifted to.
        desiredX = keep;
        if (edit.equals(before)) {
            return false;
        }
        history.endRun();
        return true;
    }

    /// Lines to a page. Ten, and it is a guess: a page is the height of a
    /// viewport, and an editor drawn on a canvas has none. A caller that knows
    /// better moves the caret itself.
    private int pageLines() {
        return 10;
    }

    private boolean move(TextEdit moved) {
        if (moved.equals(edit)) {
            return false;
        }
        edit = moved;
        desiredX = Double.NaN;
        // A movement ends the typing run, so undo after typing-then-moving takes
        // back the typing and not the whole sentence.
        history.endRun();
        return true;
    }

    private boolean select(TextEdit selected) {
        return move(selected);
    }

    private boolean apply(TextEdit next, EditHistory.Kind kind) {
        if (next.equals(edit)) {
            return false;
        }
        history.record(edit, next, kind);
        edit = next;
        desiredX = Double.NaN;
        invalidate();
        return true;
    }

    /// Backspace and Delete are their own run so that a held key undoes as one
    /// step, and so that deleting after typing does not fold into the typing.
    private EditHistory.Kind deleting() {
        return EditHistory.Kind.DELETING;
    }

    private boolean applied(TextEdit before) {
        if (edit.equals(before)) {
            return false;
        }
        desiredX = Double.NaN;
        invalidate();
        return true;
    }

    private void invalidate() {
        paragraph = null;
        layout = null;
    }

    @Override
    public String toString() {
        return "Editor[" + edit + (multiline ? ", multiline" : "") + (readOnly ? ", read-only" : "") + "]";
    }
}
