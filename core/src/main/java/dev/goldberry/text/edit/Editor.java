package dev.goldberry.text.edit;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.event.PreeditEvent;
import dev.goldberry.paint.Frame;
import dev.goldberry.render.clipboard.Clipboard;
import dev.goldberry.render.clipboard.PrimarySelection;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.text.Paragraph;
import dev.goldberry.text.document.DocumentLines;
import dev.goldberry.text.document.TextDocument;
import dev.goldberry.text.edit.keys.EditCommand;
import dev.goldberry.text.edit.keys.EditKeys;
import dev.goldberry.text.edit.keys.EditSurface;
import dev.goldberry.text.flow.TextAlign;
import dev.goldberry.text.flow.TextFlow;
import dev.goldberry.text.font.Font;

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
/// `text-input` and `text-area` are controls: a box with a border, a placeholder,
/// a label, a validation state and a focus ring, which take their keys through
/// the element tree. This is the editor without any of that — a caret, a
/// selection, an undo stack and a key map over a string — for a sticky on a
/// board, a label on a shape, a cell in a drawing: things that are not widgets,
/// because they are drawn into an application's own canvas at its own transform.
///
/// It holds three things: a [TextEdit] (the string, the caret and the anchor), an
/// [EditHistory] (undo, with a typing run folded into one step), and a
/// [TextDocument] — the text shaped one hard line at a time, so a keystroke
/// re-shapes the line it landed on and not the whole text. The key map is
/// [EditKeys], the one `text-input` and `text-area` read. Everything visual —
/// where to draw, what colour, whether the caret blinks, whether there is a
/// border — stays the caller's.
///
/// A canvas holding one must ask for the keyboard: override `Input.wantsText()`
/// to `true`, or nothing will ever be typed into it. The platform produces no
/// committed text until it is told something is being typed into, so an editor
/// that has not said so receives every arrow key and no characters at all.
///
/// Points handed to [#pointerAt] and rectangles handed back are in the text's
/// own space: `(0, 0)` is the top-left of the first line. A caller drawing at an
/// offset subtracts it first, which is why this class has no notion of padding,
/// scrolling or a canvas transform.
///
/// Used on the UI thread, like everything else that touches input. Nothing here
/// is closed: the [Font] is the caller's, and a shaped paragraph holds no native
/// memory.
///
/// Read more: [Selection and editing](https://goldberry.dev/docs/guide/text.html#selection-and-editing).
public final class Editor {

    private final Font font;

    private TextEdit edit = TextEdit.EMPTY;

    private final EditHistory history = new EditHistory();

    private double wrapWidth = Paragraph.UNCONSTRAINED;

    /// Where a line narrower than [#wrapWidth] sits in it.
    ///
    /// Held here rather than passed to [#paint] because it is not only a painting
    /// question: the caret, the hit test and the selection are measured from the
    /// same edge the glyphs were drawn from, or the caret drifts off the glyphs.
    private TextAlign textAlign = TextAlign.START;

    private boolean multiline;

    private boolean readOnly;

    private @Nullable Clipboard clipboard;

    private @Nullable PrimarySelection primarySelection;

    /// The shaped text — one [Paragraph] per hard line, re-shaped a hard line at a
    /// time.
    ///
    /// A cache for correctness before it is one for speed: the caret, the hit test
    /// and the paint must all measure the same shaping, and the way to guarantee
    /// that is for there to be one. It is a cache for speed as well because the
    /// next one is built from this one, so a keystroke into a long text re-shapes
    /// the line it landed on and nothing else.
    private @Nullable TextDocument document;

    /// Whether [#document] describes text this editor no longer holds.
    ///
    /// A flag rather than nulling the document, because the stale document is the
    /// input to the new one. And a flag rather than letting
    /// [TextDocument#of] notice: that comparison is two passes over the text, and
    /// a frame asks for the caret, the selection, the lines and the paint. One
    /// comparison per edit is the cost this is for; four per frame is not.
    private boolean stale = true;

    /// What turns one hard line into glyphs — [#shaper(TextDocument.Shaper)].
    private TextDocument.Shaper shaper;

    /// How tall the caller's viewport is, in the text's own space, or `NaN` when
    /// it has not said — [#viewportHeight(double)].
    private double viewportHeight = Double.NaN;

    /// The column a run of `Up`/`Down` is keeping, or NaN when the last thing the
    /// caret did was not vertical.
    private double desiredX = Double.NaN;

    /// What an input method is composing, or `""` when it is not.
    ///
    /// Not part of [#edit]. A composition is not an edit until the user accepts
    /// it: `にほんご` becomes `日本語` and every character of what was typed is
    /// replaced. An editor that inserted this into its text would put characters
    /// the user has not chosen into the undo history and into whatever is
    /// watching the value, and would then take them out again.
    ///
    /// It is displayed inside the text — spliced at the caret, so the following
    /// words move along as they do in every native field — and that splice lives
    /// in [#displayText] and nowhere else.
    private String preedit = "";

    /// Where the caret sits inside [#preedit], as a char offset.
    private int preeditCaret;

    /// The clause the input method is currently converting, within [#preedit];
    /// `-1` when the platform reports none, which several do not.
    private int preeditClauseStart = -1;

    private int preeditClauseEnd = -1;

    /// A new, empty editor whose text is shaped with `font`.
    public Editor(Font font) {
        this.font = Objects.requireNonNull(font, "font");
        this.shaper = line -> Paragraph.of(font, line);
    }

    /// Where a hard line's glyphs come from. `Paragraph.of` with this editor's
    /// font by default, which caches nothing.
    ///
    /// A caller inside a frame hands over the renderer's paragraph cache and gets
    /// the sharing the rest of the frame gets; a caller that has no frame — a
    /// test, a headless measurement — leaves it alone. The seam is
    /// [TextDocument]'s own and this only passes it along: the class that shapes
    /// does not decide who caches.
    ///
    /// Lines already shaped keep the paragraphs they have. A new shaper is asked
    /// for the lines that change from here, so handing one over on the first
    /// frame that has a cache to offer is cheap.
    public Editor shaper(TextDocument.Shaper value) {
        this.shaper = Objects.requireNonNull(value, "value");
        return this;
    }

    /// How tall the box this editor is drawn into is, in the text's own space —
    /// what `PageUp` and `PageDown` move by.
    ///
    /// A page is a screenful, and only the caller knows what a screenful is: a
    /// `Canvas` is handed its size, and a sticky is a rectangle on a board. It is
    /// in logical units and not in lines because a height is what a caller has;
    /// dividing by a line's height needs the font, which this editor holds.
    ///
    /// Leave it unset — or pass `NaN`, `0` or a negative — and a page is ten
    /// lines, the only defensible answer for a caller that has not said.
    ///
    /// Nothing is invalidated: how tall the viewport is does not change where a
    /// line breaks or where a caret goes, only what one key means.
    public Editor viewportHeight(double height) {
        this.viewportHeight = height;
        return this;
    }

    /// See [#viewportHeight(double)] — `NaN` when the caller has not said.
    public double viewportHeight() {
        return viewportHeight;
    }

    /// The width the text wraps at, in logical units.
    ///
    /// [Paragraph#UNCONSTRAINED] by default, which is one long line — a label or a
    /// single-line field. Set it to the box the text is drawn in and the caret,
    /// the hit test and `Up`/`Down` all follow the wrap.
    /// **The shaping survives it.** A width decides where the lines break and not
    /// what the glyphs are, and [TextDocument#lines(double)] memoises the break at
    /// one width like [Paragraph#layout] does — so a resize re-wraps and re-shapes
    /// nothing.
    public Editor wrapWidth(double width) {
        this.wrapWidth = width;
        return this;
    }

    /// Where each line sits in [#wrapWidth(double)] — `text-align`.
    ///
    /// [TextAlign#START] by default. Set it and the paint, the caret, the hit
    /// test, `Up`/`Down` and the selection all move together: a board's sticky is
    /// centred, and pressing exactly where the caret is drawn gives back the
    /// offset it was drawn for.
    ///
    /// **No layout is invalidated.** Alignment does not change where the lines
    /// break, only where each of them starts — which is the whole reason it can be
    /// a late decision.
    public Editor textAlign(TextAlign value) {
        this.textAlign = Objects.requireNonNull(value, "value");
        return this;
    }

    /// See [#textAlign(TextAlign)].
    public TextAlign textAlign() {
        return textAlign;
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

    /// The primary selection a finished selection is published to and a middle
    /// click pastes from — X11's, where the host has one.
    ///
    /// Hand it `Host.primarySelection()`'s answer when there is one and nothing
    /// when there is not: without one, [#pointerReleased()] publishes nothing and
    /// [#pastePrimaryAt] is a no-op that reports `false`, so the caller's middle
    /// button can mean something else. The editor never asks which platform it
    /// is on.
    public Editor primarySelection(PrimarySelection value) {
        this.primarySelection = Objects.requireNonNull(value, "primarySelection");
        return this;
    }

    // --- the text ------------------------------------------------------------

    /// The text, without any composition an input method has open.
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
        caretMoved();
        return this;
    }

    /// Whether there is anything to undo.
    public boolean canUndo() {
        return history.canUndo();
    }

    /// Whether there is anything to redo.
    public boolean canRedo() {
        return history.canRedo();
    }

    /// Takes back the last edit, or the last typing run. Returns whether anything
    /// changed.
    public boolean undo() {
        var before = edit;
        edit = history.undo(edit);
        return applied(before);
    }

    /// Restores what the last [#undo] took back. Returns whether anything changed.
    public boolean redo() {
        var before = edit;
        edit = history.redo(edit);
        return applied(before);
    }

    // --- input ---------------------------------------------------------------

    /// Committed text — what the platform decided was typed, after its own
    /// keyboard layout, dead keys and IME.
    ///
    /// This is `Handles.onText`'s payload and not a key: an editor must never
    /// build characters out of key codes itself, or it is a US keyboard
    /// pretending to be every keyboard.
    ///
    /// @return whether anything was inserted, which is whether to consume the event
    public boolean onText(String typed) {
        Objects.requireNonNull(typed, "typed");
        // The composition is over the moment its result arrives, and the empty
        // TEXT_EDITING that says so is not ordered against this one on every
        // platform. Clearing it here rather than waiting means an accepted
        // candidate never draws twice -- once underlined and once committed.
        var wasComposing = clearPreedit();
        if (readOnly || typed.isEmpty()) {
            return wasComposing;
        }
        return apply(edit.insert(typed), EditHistory.Kind.TYPING) || wasComposing;
    }

    /// The composition an input method is assembling.
    ///
    /// `Handles.onPreedit`'s payload. Nothing is inserted: what changes is what
    /// is drawn, and the text, the caret offset and the undo history are
    /// untouched until a [#onText] arrives with the result.
    ///
    /// An event whose text is empty ends the composition, which is what both
    /// accepting a candidate and abandoning one produce.
    ///
    /// @return whether anything changed, which is whether to consume the event
    public boolean onPreedit(PreeditEvent event) {
        Objects.requireNonNull(event, "event");
        if (readOnly) {
            return false;
        }
        var text = event.text();
        var caret = event.caret();
        var clauseStart = event.start();
        var clauseEnd = clauseStart < 0 ? -1 : Math.clamp(clauseStart + Math.max(0, event.length()), 0, text.length());
        if (preedit.equals(text)
                && preeditCaret == caret
                && preeditClauseStart == clauseStart
                && preeditClauseEnd == clauseEnd) {
            return !text.isEmpty();
        }
        preedit = text;
        preeditCaret = Math.clamp(caret, 0, text.length());
        preeditClauseStart = clauseStart;
        preeditClauseEnd = clauseEnd;
        invalidate();
        return true;
    }

    /// What is being composed, or `""` when nothing is.
    public String composing() {
        return preedit;
    }

    /// Whether an input method has a composition open over this editor.
    ///
    /// A caller that suppresses its own caret blink, or that refuses to submit on
    /// `Enter` while a candidate list is up, asks this.
    public boolean isComposing() {
        return !preedit.isEmpty();
    }

    /// The text as it is **drawn**: [#text()] with the composition spliced in at
    /// the caret.
    ///
    /// Equal to [#text()] whenever nothing is being composed, which is always on
    /// a Latin keyboard.
    public String displayText() {
        if (preedit.isEmpty()) {
            return edit.text();
        }
        return new StringBuilder(edit.text()).insert(edit.caret(), preedit).toString();
    }

    /// Drops any composition. Returns whether there was one.
    private boolean clearPreedit() {
        if (preedit.isEmpty()) {
            return false;
        }
        preedit = "";
        preeditCaret = 0;
        preeditClauseStart = -1;
        preeditClauseEnd = -1;
        invalidate();
        return true;
    }

    /// A display offset as an offset into [#text()].
    ///
    /// Inside the composition it is the caret: the composition is not in the
    /// document, so every point in it is the one place the document has for it.
    private int toDocument(int displayOffset) {
        if (preedit.isEmpty()) {
            return displayOffset;
        }
        var caret = edit.caret();
        if (displayOffset <= caret) {
            return displayOffset;
        }
        return Math.max(caret, displayOffset - preedit.length());
    }

    /// Where the caret is in the **displayed** text, which is inside the
    /// composition while there is one.
    private int displayCaret() {
        return preedit.isEmpty() ? edit.caret() : edit.caret() + preeditCaret;
    }

    /// Obeys a key — movement, deletion, selection, undo and the clipboard.
    ///
    /// The map itself is [EditKeys], shared with `text-input` and `text-area`, so
    /// the three editors cannot disagree about what a key does. What is here is
    /// what this editor does about each command.
    ///
    /// @return whether the key did something, which is whether to consume it. A
    ///         key the map has no meaning for is left alone — `Tab` still moves
    ///         focus, `Escape` still closes what it closes, and `Enter` in a
    ///         single-line editor still submits.
    public boolean onKey(KeyEvent event) {
        Objects.requireNonNull(event, "event");
        var command = EditKeys.of(event, multiline ? EditSurface.DOCUMENT : EditSurface.WRAPPED);
        if (command == null) {
            return false;
        }
        var handled = perform(command);
        if (handled && selects(command)) {
            // A selection made from the keyboard is finished the moment the key
            // lands -- there is no release to wait for, as there is for a drag.
            publishSelection();
        }
        return handled;
    }

    /// Whether `command` makes a selection rather than moving past one:
    /// `Shift` with a movement, and `Ctrl+A`.
    private static boolean selects(EditCommand command) {
        return switch (command) {
            case EditCommand.Move(var _, var _, var extend) -> extend;
            case EditCommand.MoveLine(var _, var _, var extend) -> extend;
            case EditCommand.Simple simple -> simple == EditCommand.Simple.SELECT_ALL;
            case EditCommand.Delete _, EditCommand.Type _ -> false;
        };
    }

    private boolean perform(EditCommand command) {
        return switch (command) {
            case EditCommand.Simple simple -> simple(simple);
            case EditCommand.Move(var motion, var word, var extend) ->
                switch (motion) {
                    case LEFT -> move(word ? edit.wordLeft(extend) : edit.left(extend));
                    case RIGHT -> move(word ? edit.wordRight(extend) : edit.right(extend));
                    case LINE_START -> move(toLineEdge(true, extend));
                    case LINE_END -> move(toLineEdge(false, extend));
                    case DOCUMENT_START -> move(edit.toStart(extend));
                    case DOCUMENT_END -> move(edit.toEnd(extend));
                };
            case EditCommand.MoveLine(var lines, var byPage, var extend) ->
                verticalBy(byPage ? lines * pageLines() : lines, extend);
            case EditCommand.Delete(var before, var word) ->
                !readOnly
                        && apply(
                                before
                                        ? word ? edit.deleteWordBefore() : edit.backspace()
                                        : word ? edit.deleteWordAfter() : edit.delete(),
                                deleting());
            case EditCommand.Type(var text) -> !readOnly && apply(edit.insert(text), EditHistory.Kind.TYPING);
        };
    }

    /// The commands that take no argument, and the read-only refusal they share.
    private boolean simple(EditCommand.Simple command) {
        if (readOnly && command.isEdit()) {
            return false;
        }
        return switch (command) {
            case SELECT_ALL -> select(edit.selectAll());
            case COPY -> copy();
            case CUT -> cut();
            case PASTE -> paste();
            case UNDO -> undo();
            case REDO -> redo();
        };
    }

    /// The pointer went down, or was dragged, at a point in the text's own space.
    ///
    /// @param extend     whether this extends the selection — `Shift` on a press,
    ///                   and always on a drag
    /// @param clickCount 1 places the caret, 2 selects a word, 3 selects the lot
    public void pointerAt(double x, double y, boolean extend, int clickCount) {
        // Mapped back out of the displayed text: a click during a composition
        // lands somewhere in a string that is not in the document, and the
        // document's answer for every point inside it is the caret.
        var offset = toDocument(TextGeometry.offsetAt(document(), lines(), x, y, wrapWidth, textAlign));
        edit = switch (Math.min(clickCount, 3)) {
            case 2 -> edit.wordAt(offset);
            case 3 -> edit.selectAll();
            default -> edit.caretTo(offset, extend);
        };
        desiredX = Double.NaN;
        caretMoved();
        history.endRun();
    }

    /// The pointer came up: a selection the press and drag made is finished, and
    /// goes on the primary selection if there is one.
    ///
    /// On the release and not on every drag, because every write is an ownership
    /// change the whole desktop is told about.
    ///
    /// @return whether a selection was published
    public boolean pointerReleased() {
        return publishSelection();
    }

    /// A middle click at a point in the text's own space: the caret goes there
    /// and the primary selection's text goes in at it — X11's paste, as one
    /// undoable edit.
    ///
    /// Nothing happens without a primary selection, in a read-only editor, or
    /// when the selection holds no text — and `false` says so, so the caller's
    /// middle button is free to mean something else.
    ///
    /// @return whether anything was pasted
    public boolean pastePrimaryAt(double x, double y) {
        var selection = primarySelection;
        if (readOnly || selection == null || !selection.hasText()) {
            return false;
        }
        // Read before the caret moves: the text being pasted may well be this
        // editor's own selection, which the move collapses. Collapsing it does
        // not clear the primary selection, but asking first costs nothing.
        var text = selection.text();
        if (text.isEmpty()) {
            return false;
        }
        pointerAt(x, y, false, 1);
        return apply(edit.insert(multiline ? text : text.replaceAll("\\R", " ")), EditHistory.Kind.OTHER);
    }

    /// Puts a non-empty selection on the primary selection, if there is one.
    private boolean publishSelection() {
        var selection = primarySelection;
        if (selection == null || !edit.hasSelection()) {
            return false;
        }
        return selection.text(edit.selectedText());
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

    /// Copies the selection and removes it. `false` when nothing was copied or
    /// the editor is read-only, so the key is not consumed.
    public boolean cut() {
        if (readOnly || !copy()) {
            return false;
        }
        return apply(edit.insert(""), EditHistory.Kind.OTHER);
    }

    /// Inserts the clipboard's text in place of the selection. `false` when
    /// there is no clipboard, it holds no text, or the editor is read-only.
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

    /// The shaped text, for a caller that paints it itself — a hard line at a
    /// time.
    ///
    /// The same instance the caret and the hit test are measured against, which is
    /// the point of it being handed out rather than rebuilt by the caller.
    public TextDocument document() {
        var current = document;
        if (current == null || stale) {
            // [#displayText], not [#text]: a composition is drawn *inside* the
            // text so that the words after it move along, and the caret, the hit
            // test and the paint all have to measure the same shaping.
            //
            // The old document goes in as well as coming out: it is what makes a
            // keystroke re-shape one hard line rather than the whole text.
            current = TextDocument.of(font, displayText(), current, shaper);
            document = current;
            stale = false;
        }
        return current;
    }

    /// The visual lines at the current [#wrapWidth(double)], in the whole text's
    /// own offsets.
    ///
    /// A `List<TextLine>` computed on demand rather than built, so a text of ten
    /// thousand lines does not allocate ten thousand records to answer where the
    /// caret is ([DocumentLines]).
    public DocumentLines lines() {
        // A document always has at least one hard line and a hard line always
        // occupies at least one visual line, so the caret has a line to sit on
        // without a fallback here.
        return document().lines(wrapWidth);
    }

    /// Where to draw the caret, in the text's own space.
    ///
    /// **Inside the composition while there is one**, which is where every native
    /// field puts it: an input method moves a caret through the string it is
    /// assembling, and a caret pinned to the document's own offset would sit
    /// before the characters being typed.
    public TextGeometry.Caret caret() {
        return TextGeometry.caretAt(document(), lines(), displayCaret(), wrapWidth, textAlign);
    }

    /// The visual line the caret is on, in the text's own space — what
    /// `Handles.caretArea` wants.
    ///
    /// The line and not the caret, because an input method uses it to keep its
    /// candidate window clear of the text it would otherwise cover; the caret's
    /// position within it is [#caret]`.x()`.
    ///
    /// In the text's own space, so a caller drawing at `(x, top)` adds both.
    public LogicalRect caretLine() {
        var caret = caret();
        var line = lines().get(caret.line());
        return LogicalRect.of(0, (float) caret.top(), (float) line.width(), (float) caret.height());
    }

    /// The selection, one rectangle per visual line. Empty when nothing is
    /// selected.
    ///
    /// **Empty while a composition is open.** A composition replaces the
    /// selection when it commits, and every platform's input method collapses the
    /// highlight the moment one starts; leaving it drawn would show a highlight
    /// over text that is about to go. The composition's own underline is
    /// [#composingRects] instead.
    public List<LogicalRect> selectionRects() {
        if (!preedit.isEmpty()) {
            return List.of();
        }
        return TextGeometry.selectionRects(document(), lines(), edit.start(), edit.end(), wrapWidth, textAlign);
    }

    /// The composition's rectangles, one per visual line — what an underline is
    /// drawn along. Empty when nothing is being composed.
    public List<LogicalRect> composingRects() {
        if (preedit.isEmpty()) {
            return List.of();
        }
        var start = edit.caret();
        return TextGeometry.selectionRects(document(), lines(), start, start + preedit.length(), wrapWidth, textAlign);
    }

    /// The clause the input method is currently converting, one rectangle per
    /// visual line — drawn behind the composition like a selection.
    ///
    /// Empty when nothing is being composed **and** when the platform does not
    /// report a clause, which several do not: a composition with no clause is
    /// underlined and not highlighted, which is what those platforms' own fields
    /// look like.
    public List<LogicalRect> composingClauseRects() {
        if (preedit.isEmpty() || preeditClauseStart < 0 || preeditClauseEnd <= preeditClauseStart) {
            return List.of();
        }
        var start = edit.caret();
        return TextGeometry.selectionRects(
                document(), lines(), start + preeditClauseStart, start + preeditClauseEnd, wrapWidth, textAlign);
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
    /// A composition draws two more things, between the selection and the caret:
    /// the clause an input method is converting, highlighted like a selection,
    /// and an underline along the whole of what is being composed. Both are in
    /// [Ink]'s existing colours rather than in new ones, because a composition is
    /// the same three things a selection is — a highlight, some glyphs and a
    /// caret — drawn to say "not yet".
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
        // The clause an input method is converting, behind the glyphs like a
        // selection -- because that is what it is, in the composition's own
        // little document.
        for (var rect : composingClauseRects()) {
            frame.fillRect(
                    (float) (x + rect.left()),
                    (float) (top + rect.top()),
                    rect.width(),
                    rect.height(),
                    ink.selection());
        }
        paintText(frame, x, top, ink.text());
        // And the underline, in front of them, which is the mark every platform
        // uses for "this is not text yet". One logical unit, like the caret.
        for (var rect : composingRects()) {
            frame.fillRect((float) (x + rect.left()), (float) (top + rect.bottom() - 1), rect.width(), 1, ink.text());
        }
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

    /// The glyphs, one hard line at a time.
    ///
    /// One paragraph per hard line, each drawn at the top of its own first visual
    /// line: the wrap is per hard line either way ([TextDocument]), and the rows
    /// of a wrapped line are consecutive, so the y of hard line `k` is the number
    /// of visual lines above it times the line height.
    ///
    /// Every line is drawn, in the document's order. Drawing only a screenful
    /// needs to know which rows are on screen, and that is the caller's scroll
    /// offset and the caller's transform; an editor asked to paint at `(x, top)`
    /// knows only where it was told to start. A caller with a viewport clips, as
    /// it already must.
    private void paintText(Frame frame, double x, double top, int argb) {
        var shaped = document();
        var rows = lines();
        var lineHeight = font.lineHeight();
        var flow = TextFlow.NORMAL.textAlign(textAlign);
        for (var k = 0; k < shaped.hardLineCount(); k++) {
            shaped.paragraphOf(k).paint(frame, x, top + rows.firstVisualOf(k) * lineHeight, wrapWidth, argb, flow);
        }
    }

    // --- the plumbing --------------------------------------------------------

    /// `Home` and `End` on the **visual** line, which is what a reader means by
    /// them and is not [TextEdit]'s hard-line start.
    private TextEdit toLineEdge(boolean start, boolean extend) {
        var rows = lines();
        var line = rows.get(rows.indexOf(edit.caret()));
        return edit.caretTo(start ? line.start() : line.end(), extend);
    }

    private boolean verticalBy(int lines, boolean extend) {
        var keep = Double.isNaN(desiredX) ? caret().x() : desiredX;
        var offset = TextGeometry.moveLine(document(), lines(), edit.caret(), lines, keep, wrapWidth, textAlign);
        var before = edit;
        edit = edit.caretTo(offset, extend);
        // Set *after* the move and not before it, so that a run of Up/Down keeps
        // the column it started with rather than the one it has drifted to.
        desiredX = keep;
        if (edit.equals(before)) {
            return false;
        }
        caretMoved();
        history.endRun();
        return true;
    }

    /// What a page is when the caller has not said how tall its viewport is
    /// ([#viewportHeight(double)]).
    ///
    /// Ten is a guess, and every alternative is worse: zero makes `PageUp` a key
    /// that reports `true` and does nothing, one makes it `Up` under a second
    /// name, and the whole text makes it `Ctrl+End`, which is a key the map
    /// already has. Ten lines is wrong by a factor and never wrong in kind.
    private static final int DEFAULT_PAGE_LINES = 10;

    /// Lines to a page — the whole lines the caller's viewport holds.
    ///
    /// At least one, so a viewport shorter than a line still pages: the caller has
    /// said it is drawing into something, and a `PageDown` that moves nowhere
    /// would be this editor deciding the box is too small to use.
    ///
    /// Not the viewport less a line, which is what an editor that *scrolls* pages
    /// by so that the last row of the old screen is the first of the new one. This
    /// editor does not scroll — its caller does, at a transform this class never
    /// sees — so the overlap is not ours to keep.
    private int pageLines() {
        var lineHeight = font.lineHeight();
        if (Double.isNaN(viewportHeight) || viewportHeight <= 0 || lineHeight <= 0) {
            return DEFAULT_PAGE_LINES;
        }
        return Math.max(1, (int) Math.floor(viewportHeight / lineHeight));
    }

    private boolean move(TextEdit moved) {
        if (moved.equals(edit)) {
            return false;
        }
        edit = moved;
        desiredX = Double.NaN;
        caretMoved();
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

    /// Marks the shaping stale without throwing it away — [#stale].
    private void invalidate() {
        stale = true;
    }

    /// Called by everything that moves the caret without changing the text.
    ///
    /// **A composition makes that a change to what is drawn.** [#displayText]
    /// splices the preedit in at `edit.caret()`, so a caret that moved puts it
    /// somewhere else in the string the paragraph was shaped from — and the cache
    /// would still hold it spliced at the old offset, which [#caret],
    /// [#composingRects] and [#composingClauseRects] then measure against. A
    /// click during a composition, which is exactly what a user does when the
    /// candidate list covers what they were reading, drew the underline over the
    /// wrong characters.
    ///
    /// Conditional, and not simply [#invalidate]: with nothing being composed the
    /// shaping does not depend on where the caret is, and throwing it away on
    /// every arrow key would re-shape the paragraph on every keystroke of a
    /// held-down key.
    private void caretMoved() {
        if (!preedit.isEmpty()) {
            invalidate();
        }
    }

    @Override
    public String toString() {
        return "Editor[" + edit + (multiline ? ", multiline" : "") + (readOnly ? ", read-only" : "") + "]";
    }
}
