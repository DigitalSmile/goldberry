package io.github.digitalsmile.goldberry.widgets.form.textarea;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.backend.EventLoop;
import io.github.digitalsmile.goldberry.input.Extent;
import io.github.digitalsmile.goldberry.text.Paragraph;
import io.github.digitalsmile.goldberry.text.TextLine;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.form.textinput.EditHistory;
import io.github.digitalsmile.goldberry.widgets.form.textinput.TextEdit;
import java.time.Duration;
import java.util.List;

/// What a [TextArea] holds — `text-input`'s state, with a column to remember.
///
/// The text, the history, the blink and the scroll offset are the same and are
/// held the same way; what is new is the **preferred column**, which is the only
/// piece of editing state a second dimension adds.
///
/// ## Why a column has to be remembered
///
/// `Up` keeps the column, and a column is an *x* rather than an offset. Walking
/// down through a short line and out the other side has to come back to the
/// column you started in — recomputing it from the caret each time would leave it
/// at the end of the short line, and every editor that gets this wrong is
/// immediately noticeable and hard to name.
///
/// So the x is captured on the first vertical move of a run and kept until
/// something horizontal happens. "Something horizontal" is every other operation,
/// which is why it is cleared in one place rather than in each of them.
final class TextAreaState extends State<TextArea> implements AreaEditor {

    /// Each half of the caret's blink — `text-input`'s interval, and its
    /// reasoning: a caret changes twice a second, so a timer produces two frames
    /// a second where `isAnimating` would produce the display's rate for as long
    /// as a field has focus.
    private static final Duration BLINK = Duration.ofMillis(530);

    private TextEdit edit = TextEdit.EMPTY;
    private final EditHistory history = new EditHistory();

    private Host host;
    private boolean focused;
    private boolean caretShown = true;
    private EventLoop.Timer blink;

    /// How far the content has been scrolled **up**, in logical pixels. Not
    /// `setState`: it is computed during `render` and applied in the same frame.
    private double scrollOffset;

    /// The x a run of `Up`/`Down` is trying to stay at, or `NaN` for "no run in
    /// progress" — which is the arithmetic saying it rather than a second flag,
    /// the same trick `dragX` uses for "this is not a drag".
    private double preferredColumn = Double.NaN;

    private Extent bounds = Extent.NONE;
    private Paragraph paragraph;
    private double leftPadding;
    private double topPadding;

    /// The value the widget last offered, so a change to it can be told from a
    /// constant that has always been there — see [io.github.digitalsmile.goldberry.widgets.form.textinput.TextInput].
    private String lastOffered;

    @Override
    protected void initState() {
        super.initState();
        lastOffered = widget().resolved();
        edit = TextEdit.of(lastOffered);
    }

    @Override
    public Widget build(BuildContext context) {
        host = context.host().orElse(null);
        follow();
        var area = widget();
        var showPlaceholder = edit.isEmpty() && !area.placeholder().isEmpty();
        return new TextAreaBox(
                showPlaceholder ? area.placeholder() : edit.text(),
                showPlaceholder,
                edit,
                focused && !area.disabled(),
                caretShown,
                area.rows(),
                area.maxRows(),
                area.disabled(),
                area.readOnly(),
                area.attributes(),
                this);
    }

    @Override
    protected void dispose() {
        stopBlinking();
        if (focused && host != null) {
            host.textInput(false);
        }
        super.dispose();
    }

    /// Takes a value the application changed, and ignores the echo of the user's
    /// own keystroke.
    private void follow() {
        var offered = widget().resolved();
        if (offered.equals(lastOffered)) {
            return;
        }
        lastOffered = offered;
        if (offered.equals(edit.text())) {
            return;
        }
        edit = edit.withText(offered);
        history.clear();
    }

    /// What it holds, for a test.
    String heldText() {
        return edit.text();
    }

    // --- AreaEditor -----------------------------------------------------------

    @Override
    public boolean move(Motion motion, boolean byWord, boolean extend) {
        var next = switch (motion) {
            case LEFT -> byWord ? edit.wordLeft(extend) : edit.left(extend);
            case RIGHT -> byWord ? edit.wordRight(extend) : edit.right(extend);
            // The **soft** line's ends, which is what a reader means by "this
            // line" — `TextEdit`'s are the hard ones, and a wrapped paragraph has
            // more of the former than the latter.
            case LINE_START -> edit.caretTo(visualLineStart(edit.caret()), extend);
            case LINE_END -> edit.caretTo(visualLineEnd(edit.caret()), extend);
            case START -> edit.toStart(extend);
            case END -> edit.toEnd(extend);
        };
        return apply(next, EditHistory.Kind.OTHER, false);
    }

    @Override
    public boolean moveLine(int lines, boolean extend) {
        var layout = lines();
        if (layout.isEmpty() || paragraph == null) {
            return false;
        }
        var index = lineIndex(layout, edit.caret());
        var target = Math.clamp(index + lines, 0, layout.size() - 1);
        if (target == index && (lines < 0 ? index == 0 : index == layout.size() - 1)) {
            // Already at the end of the document's lines. `Up` on the first line
            // goes to the very start and `Down` on the last to the very end,
            // which is what every editor does and is more useful than nothing
            // happening.
            var next = lines < 0 ? edit.toStart(extend) : edit.toEnd(extend);
            var moved = apply(next, EditHistory.Kind.OTHER, false);
            preferredColumn = Double.NaN;
            return moved;
        }

        var column = Double.isNaN(preferredColumn)
                ? paragraph.widthBetween(layout.get(index).start(), edit.caret())
                : preferredColumn;
        var line = layout.get(target);
        var offset = paragraph.offsetAt(line.start(), line.end(), column);

        var moved = apply(edit.caretTo(offset, extend), EditHistory.Kind.OTHER, false);
        // Set *after* the apply, which clears it: a run of Up/Down keeps the
        // column it started with, and everything else abandons it.
        preferredColumn = column;
        return moved;
    }

    @Override
    public boolean selectAll() {
        return apply(edit.selectAll(), EditHistory.Kind.OTHER, false);
    }

    @Override
    public boolean deleteBefore(boolean byWord) {
        return apply(byWord ? edit.deleteWordBefore() : edit.backspace(),
                EditHistory.Kind.DELETING, true);
    }

    @Override
    public boolean deleteAfter(boolean byWord) {
        return apply(byWord ? edit.deleteWordAfter() : edit.delete(),
                EditHistory.Kind.DELETING, true);
    }

    @Override
    public boolean type(String typed) {
        var room = room();
        var insertion = room < 0 ? typed : clip(typed, room);
        if (insertion.isEmpty()) {
            return false;
        }
        return apply(edit.insert(insertion), EditHistory.Kind.TYPING, true);
    }

    @Override
    public void pointerAt(double x, double y, boolean extend, int clickCount) {
        var layout = lines();
        if (paragraph == null || layout.isEmpty()) {
            return;
        }
        var lineHeight = paragraph.font().lineHeight();
        var row = (int) Math.floor((y - topPadding + scrollOffset) / lineHeight);
        var line = layout.get(Math.clamp(row, 0, layout.size() - 1));
        var offset = paragraph.offsetAt(line.start(), line.end(), x - leftPadding);

        var next = switch (Math.min(clickCount, 3)) {
            // A triple-click is "select the line", and here there really is one.
            case 3 -> new TextEdit(edit.text(), line.start(), line.end());
            case 2 -> edit.wordAt(offset);
            default -> edit.caretTo(offset, extend);
        };
        apply(next, EditHistory.Kind.OTHER, false);
    }

    @Override
    public boolean scrollBy(double dy) {
        var maximum = maximumScroll();
        if (maximum <= 0) {
            // Nothing to scroll. Reported so the wheel is *not* consumed and the
            // page behind this keeps it — a control that swallowed every wheel
            // would trap the scroll the moment the pointer crossed it.
            return false;
        }
        var next = Math.clamp(scrollOffset + dy, 0, maximum);
        if (next == scrollOffset) {
            return false;
        }
        setState(() -> scrollOffset = next);
        return true;
    }

    @Override
    public void focusChanged(boolean gained, boolean fromKeyboard) {
        setState(() -> focused = gained);
        if (host != null) {
            host.textInput(gained && !widget().disabled() && !widget().readOnly());
        }
        if (gained) {
            // **Not** select-all on a keyboard focus, which is what `text-input`
            // does: replacing a whole paragraph because somebody tabbed into it
            // is a different scale of accident from replacing a name, and the
            // next keystroke would do it.
            solid();
        } else {
            stopBlinking();
            history.endRun();
            setState(() -> caretShown = true);
        }
    }

    /// How wide the last frame made this control — and a **frame request** when
    /// that is news.
    ///
    /// `text-input` records this and asks for nothing, because the width only
    /// decides how far it has scrolled and the next keystroke redraws anyway.
    /// Here the width decides where the text *wraps*, so a control that recorded
    /// it silently would show its first frame's guess until something unrelated
    /// caused another frame — which for a form nobody has touched yet is never.
    ///
    /// It converges rather than looping, which is what ADR-0119 warns about: the
    /// only frame this asks for is one where the width **changed**, and the
    /// width the next frame measures is the same one. Two frames on mount, one
    /// per resize, none after.
    @Override
    public void measured(Extent extent) {
        var changed = Math.abs(extent.width() - bounds.width()) > 0.5f;
        bounds = extent;
        if (changed && isMounted()) {
            setState(() -> { });
        }
    }

    @Override
    public double laidOut(Paragraph shaped, double left, double top) {
        paragraph = shaped;
        leftPadding = left;
        topPadding = top;

        var lineHeight = shaped.font().lineHeight();
        var layout = lines();
        var caretLine = lineIndex(layout, edit.caret());
        var caretTop = caretLine * lineHeight;
        var visible = widget().maxRows() * lineHeight;

        // Move as little as possible to keep the caret's line in view.
        var offset = Math.max(scrollOffset, caretTop + lineHeight - visible);
        offset = Math.min(offset, caretTop);
        offset = Math.clamp(offset, 0, maximumScroll());
        scrollOffset = offset;
        return offset;
    }

    @Override
    public double contentWidth() {
        var measured = bounds.width() - 2 * leftPadding;
        if (measured > 1) {
            return measured;
        }
        // **Nothing has been measured yet**, which is every control's first
        // frame — `render` runs before Yoga, so a box cannot know its width until
        // something has laid it out once.
        //
        // The answer is "do not wrap", not "wrap at one point". `text-input` has
        // the same gap and nothing visible depends on it, because a single line
        // does not wrap; here the difference is a control that shows its text on
        // the first frame and one that shows every word on a line of its own.
        // Unconstrained is the honest reading of "I do not know": the text keeps
        // its hard lines, the second frame wraps it properly, and the wrong
        // answer is wrong in the direction nobody sees.
        return Paragraph.UNCONSTRAINED;
    }

    @Override
    public boolean copy() {
        if (!edit.hasSelection() || host == null) {
            return false;
        }
        return host.clipboard().text(edit.selectedText());
    }

    @Override
    public boolean cut() {
        return copy() && apply(edit.insert(""), EditHistory.Kind.OTHER, true);
    }

    @Override
    public boolean paste() {
        if (host == null) {
            return false;
        }
        var pasted = host.clipboard().text();
        if (pasted.isEmpty()) {
            return false;
        }
        // **Newlines survive**, which is the one place this differs from
        // `text-input`: a multi-line control is exactly where a pasted paragraph
        // belongs, and flattening it would be the control refusing what it is
        // for. Carriage returns are normalised, because a document pasted from
        // Windows is one document.
        var normalised = pasted.replace("\r\n", "\n").replace('\r', '\n');
        var room = room();
        var insertion = room < 0 ? normalised : clip(normalised, room);
        return !insertion.isEmpty() && apply(edit.insert(insertion), EditHistory.Kind.OTHER, true);
    }

    @Override
    public boolean undo() {
        return history.canUndo() && adopt(history.undo(edit));
    }

    @Override
    public boolean redo() {
        return history.canRedo() && adopt(history.redo(edit));
    }

    // --- the lines ------------------------------------------------------------

    /// The **visual** lines the text wrapped into, at the width the last frame
    /// measured.
    private List<TextLine> lines() {
        return paragraph == null ? List.of() : paragraph.layout(contentWidth()).lines();
    }

    /// Which visual line `offset` is on — the last one that starts at or before
    /// it, which is what puts a caret at a wrap on the line it is about to type
    /// into.
    private static int lineIndex(List<TextLine> lines, int offset) {
        var index = 0;
        for (var i = 0; i < lines.size(); i++) {
            if (lines.get(i).start() <= offset) {
                index = i;
            }
        }
        return index;
    }

    private int visualLineStart(int offset) {
        var layout = lines();
        return layout.isEmpty() ? 0 : layout.get(lineIndex(layout, offset)).start();
    }

    private int visualLineEnd(int offset) {
        var layout = lines();
        return layout.isEmpty() ? edit.length() : layout.get(lineIndex(layout, offset)).end();
    }

    /// How far this control can be scrolled: the text's height less what it
    /// shows.
    private double maximumScroll() {
        if (paragraph == null) {
            return 0;
        }
        var lineHeight = paragraph.font().lineHeight();
        return Math.max(0, lines().size() * lineHeight - widget().maxRows() * lineHeight);
    }

    // --- the edit --------------------------------------------------------------

    private boolean apply(TextEdit next, EditHistory.Kind kind, boolean filtered) {
        if (next.equals(edit)) {
            return false;
        }
        if (filtered && !accepts(next.text())) {
            return false;
        }
        var before = edit;
        setState(() -> edit = next);
        if (!before.text().equals(next.text())) {
            history.record(before, next, kind);
            widget().report(next.text());
        }
        // Every operation but a vertical move abandons the column, which is why
        // it is cleared here rather than in each of them — and why `moveLine`
        // sets it back *after* calling this.
        preferredColumn = Double.NaN;
        solid();
        return true;
    }

    private boolean adopt(TextEdit restored) {
        if (restored.equals(edit)) {
            return false;
        }
        var changed = !restored.text().equals(edit.text());
        setState(() -> edit = restored);
        if (changed) {
            widget().report(restored.text());
        }
        solid();
        return true;
    }

    private boolean accepts(String candidate) {
        var maximum = widget().maxLength();
        return maximum < 0 || candidate.length() <= maximum;
    }

    private int room() {
        var maximum = widget().maxLength();
        if (maximum < 0) {
            return -1;
        }
        return Math.max(0, maximum - edit.length() + (edit.end() - edit.start()));
    }

    private static String clip(String text, int room) {
        if (text.length() <= room) {
            return text;
        }
        if (room <= 0) {
            return "";
        }
        var end = text.offsetByCodePoints(0, text.codePointCount(0, Math.min(room, text.length())));
        return text.substring(0, Math.min(end, room));
    }

    // --- the blink -------------------------------------------------------------

    private void solid() {
        if (!focused) {
            return;
        }
        if (!caretShown) {
            setState(() -> caretShown = true);
        }
        schedule();
    }

    private void schedule() {
        stopBlinking();
        if (host == null || !focused) {
            return;
        }
        blink = host.after(BLINK, () -> {
            blink = null;
            if (!focused) {
                return;
            }
            setState(() -> caretShown = !caretShown);
            schedule();
        });
    }

    private void stopBlinking() {
        if (blink != null) {
            blink.cancel();
            blink = null;
        }
    }
}
