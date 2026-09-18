package io.github.digitalsmile.goldberry.widgets.form.textarea;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.render.event.EventLoop;
import io.github.digitalsmile.goldberry.input.hit.Extent;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.text.Paragraph;
import io.github.digitalsmile.goldberry.text.TextLine;
import io.github.digitalsmile.goldberry.text.document.DocumentLines;
import io.github.digitalsmile.goldberry.text.document.TextDocument;
import io.github.digitalsmile.goldberry.text.font.Font;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollBar;
import io.github.digitalsmile.goldberry.widgets.form.parts.Composing;
import io.github.digitalsmile.goldberry.widgets.form.parts.MaxLength;
import io.github.digitalsmile.goldberry.text.edit.EditHistory;
import io.github.digitalsmile.goldberry.text.edit.TextEdit;
import io.github.digitalsmile.goldberry.text.flow.TextAlign;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

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

    private @Nullable Host host;
    private boolean focused;
    private boolean caretShown = true;
    private EventLoop.@Nullable Timer blink;

    /// How far the content has been scrolled **up**, in logical pixels. Not
    /// `setState`: it is computed during `render` and applied in the same frame.
    private double scrollOffset;

    /// Whether the caret is worth chasing yet.
    ///
    /// **False until somebody touches this control**, and the reason is what a
    /// `text-area` holding a *document* looks like without it. [TextEdit#of] puts
    /// the caret at the end of the text it is given — right for a field somebody is
    /// about to type into — and [#laidOut] keeps the caret's line in view, so an
    /// area opened on a hundred-line note showed its **last** line and a reader had
    /// to scroll up to find the beginning.
    ///
    /// A press, a key or the focus arriving sets it, which is exactly when the
    /// caret becomes something the reader is looking for. Until then the content
    /// starts where the content starts.
    private boolean caretMatters;

    /// The x a run of `Up`/`Down` is trying to stay at, or `NaN` for "no run in
    /// progress" — which is the arithmetic saying it rather than a second flag,
    /// the same trick `dragX` uses for "this is not a drag".
    private double preferredColumn = Double.NaN;

    private Extent bounds = Extent.NONE;

    /// The text as the last frame shaped it — one paragraph per hard line, so a
    /// keystroke re-shapes one of them ([ADR-0388]).
    ///
    /// Null until the first render, which is the same "nothing has been measured
    /// yet" every other field here starts in.
    private @Nullable TextDocument document;
    private AreaPadding padding = AreaPadding.NONE;

    /// How wide the line-number column was on the last frame, or 0 when there is
    /// none — [TextArea#gutter(boolean)].
    ///
    /// Beside [#padding] rather than folded into it, because the two are not the
    /// same number in the two places they are used: the padding is on *both*
    /// sides and each edge comes off the wrap, and the gutter is on one and comes
    /// off once (`docs/gaps.md` G37, [ADR-0331]).
    private double gutterWidth;

    /// What an input method is composing, or `""` when it is not —
    /// `docs/gaps.md` G16. Beside [#edit] and never in it; see
    /// [io.github.digitalsmile.goldberry.widgets.form.textinput.TextEditor#compose].
    private String preedit = "";

    private int preeditCaret;

    private int preeditClauseStart = -1;

    private int preeditClauseEnd = -1;

    /// The value the widget last offered, so a change to it can be told from a
    /// constant that has always been there — see
    /// [io.github.digitalsmile.goldberry.widgets.form.textinput.TextInput].
    private String lastOffered;

    /// The [TextEdit] the widget last offered through [TextArea#edit(TextEdit)],
    /// so an edit the application *pushed* can be told from the same one being
    /// carried by every rebuild since.
    ///
    /// [#lastOffered]'s rule exactly, and for its reason: a constant `edit=` that
    /// were adopted on every build would put the caret back at the application's
    /// last answer after every keystroke ([ADR-0332]).
    private @Nullable TextEdit lastPushed;

    @Override
    protected void initState() {
        super.initState();
        lastOffered = widget().resolved();
        edit = TextEdit.of(lastOffered);
        // An area built with an edit already on it opens at that caret rather
        // than at the end of its value -- the application has said where.
        lastPushed = widget().edit();
        if (lastPushed != null) {
            edit = lastPushed;
        }
    }

    @Override
    public Widget build(BuildContext context) {
        host = context.host().orElse(null);
        follow();
        adoptPushed();
        var area = widget();
        // A composition ends when the control stops being typed into, and nothing
        // else would clear it: the empty TEXT_EDITING goes to whatever has focus.
        if (!focused || area.disabled() || area.readOnly()) {
            preedit = "";
            preeditClauseStart = -1;
            preeditClauseEnd = -1;
        }

        var shown = edit.text();
        var displayed = edit;
        var composing = Composing.NONE;
        if (!preedit.isEmpty()) {
            // Spliced at the caret, with the caret inside it -- `text-input`'s
            // arrangement, and every native field's (ADR-0292).
            var at = edit.caret();
            shown = new StringBuilder(shown).insert(at, preedit).toString();
            displayed = new TextEdit(shown, at + preeditCaret, at + preeditCaret);
            composing = new Composing(
                    at,
                    at + preedit.length(),
                    preeditClauseStart < 0 ? -1 : at + preeditClauseStart,
                    preeditClauseEnd < 0 ? -1 : at + preeditClauseEnd);
        }

        var showPlaceholder = edit.isEmpty() && preedit.isEmpty() && !area.placeholder().isEmpty();
        return new TextAreaBox(
                showPlaceholder ? area.placeholder() : shown,
                showPlaceholder,
                displayed,
                composing,
                focused && !area.disabled(),
                caretShown,
                area.fill() ? visibleRows() : area.rows(),
                visibleRows(),
                area.fill(),
                area.gutter(),
                area.disabled(),
                area.readOnly(),
                area.attributes(),
                scrollbar(),
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

    /// Takes an edit the **application** computed, and ignores the one it has
    /// already taken.
    ///
    /// After [#follow], so an area that is both bound and pushed to in one frame
    /// ends at the caret the application asked for rather than at the clamp a new
    /// value left behind — which is what every Markdown shortcut is
    /// (`docs/gaps.md` G38, [ADR-0332]).
    ///
    /// Not announced back through [TextArea#reportEdit]: the caller already knows
    /// what it pushed, and an application mirroring the report into its own state
    /// would loop.
    private void adoptPushed() {
        var pushed = widget().edit();
        if (pushed == null || pushed.equals(lastPushed)) {
            return;
        }
        lastPushed = pushed;
        if (pushed.equals(edit)) {
            return;
        }
        var before = edit;
        edit = pushed;
        // The application moved the caret, so the content follows it -- the same
        // rule a keystroke gets (see [#caretMatters]).
        caretMatters = true;
        if (!before.text().equals(pushed.text())) {
            // Recorded, so `Ctrl+Z` undoes a shortcut as it undoes a keystroke --
            // an application's `**` is an edit and belongs in the same history.
            history.record(before, pushed, EditHistory.Kind.OTHER);
        }
        // `onChange` is **not** raised, and [#lastOffered] is left alone. The
        // application computed this text, so telling it back would be an echo --
        // raised from inside `build`, where a `setState` in reply is a rebuild
        // during a rebuild. What a bound model holds stays the application's, and
        // [#follow] keeps comparing against what it last offered.
        preferredColumn = Double.NaN;
        solid();
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
        var shaped = document;
        var layout = lines();
        if (layout.isEmpty() || shaped == null) {
            return false;
        }
        var index = lineIndex(edit.caret());
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

        // The column is in the **painted** space, so it carries the source line's
        // indent — and the target line's comes off it again, because the two lines
        // are not indented by the same amount unless they are the same length
        // ([ADR-0324]).
        var column = Double.isNaN(preferredColumn)
                ? indentOf(layout.get(index)) + shaped.widthBetween(layout.get(index).start(), edit.caret())
                : preferredColumn;
        var line = layout.get(target);
        var offset = shaped.offsetAt(line.start(), line.end(), column - indentOf(line));

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
        // Clears the composition first, for `text-input`'s reason: the empty
        // TEXT_EDITING is not ordered against this one on every platform, and an
        // accepted candidate must not draw twice (ADR-0289).
        var wasComposing = clearPreedit();
        var room = room();
        var insertion = room < 0 ? typed : MaxLength.clip(typed, room);
        if (insertion.isEmpty()) {
            return wasComposing;
        }
        return apply(edit.insert(insertion), EditHistory.Kind.TYPING, true) || wasComposing;
    }

    @Override
    public boolean compose(String text, int caret, int clauseStart, int clauseEnd) {
        var area = widget();
        if (area.disabled() || area.readOnly()) {
            return false;
        }
        var clamped = Math.clamp(caret, 0, text.length());
        var end = clauseStart < 0 ? -1 : Math.clamp(clauseStart + Math.max(0, clauseEnd), 0, text.length());
        if (preedit.equals(text) && preeditCaret == clamped && preeditClauseStart == clauseStart) {
            return !text.isEmpty();
        }
        setState(() -> {
            preedit = text;
            preeditCaret = clamped;
            preeditClauseStart = clauseStart;
            preeditClauseEnd = end;
        });
        solid();
        return true;
    }

    /// Drops any composition. @return whether there was one
    private boolean clearPreedit() {
        if (preedit.isEmpty()) {
            return false;
        }
        setState(() -> {
            preedit = "";
            preeditCaret = 0;
            preeditClauseStart = -1;
            preeditClauseEnd = -1;
        });
        return true;
    }

    /// The caret's **line**, in this control's content coordinates — the whole
    /// control would push a candidate window a long way from the text.
    @Override
    public Optional<LogicalRect> caretArea() {
        var shaped = document;
        if (!focused || shaped == null || widget().disabled() || widget().readOnly()) {
            return Optional.empty();
        }
        var lineHeight = shaped.font().lineHeight();
        var layout = lines();
        if (layout.isEmpty()) {
            return Optional.empty();
        }
        var index = lineIndex(displayCaret());
        var line = layout.get(index);
        return Optional.of(LogicalRect.of(
                // Where the line was *drawn*, not where the paragraph starts: a
                // candidate window under a centred line belongs under the glyphs
                // — and past the gutter, which the glyphs also are.
                (float) (gutterWidth + indentOf(line)),
                (float) (index * lineHeight - scrollOffset),
                (float) Math.max(1, shaped.widthBetween(line.start(), line.end())),
                (float) lineHeight));
    }

    @Override
    public double caretOffset() {
        var shaped = document;
        var layout = lines();
        if (shaped == null || layout.isEmpty()) {
            return 0;
        }
        var at = displayCaret();
        var line = layout.get(lineIndex(at));
        // Relative to [#caretArea]'s left edge, which is the line's own start —
        // so the indent is in the area's origin rather than in this offset, and
        // adding it here would count it twice.
        return shaped.widthBetween(line.start(), Math.clamp(at, line.start(), line.end()));
    }

    /// The caret's offset into what is **drawn** — inside the composition while
    /// there is one.
    private int displayCaret() {
        return preedit.isEmpty() ? edit.caret() : edit.caret() + preeditCaret;
    }

    @Override
    public void pointerAt(double x, double y, boolean extend, int clickCount) {
        var shaped = document;
        var layout = lines();
        if (shaped == null || layout.isEmpty()) {
            return;
        }
        var lineHeight = shaped.font().lineHeight();
        var row = (int) Math.floor((y - padding.top() + scrollOffset) / lineHeight);
        var line = layout.get(Math.clamp(row, 0, layout.size() - 1));
        // The press is where the user pressed, so the line's own indent comes off
        // it — the mirror of what the caret adds ([ADR-0324]).
        // The gutter comes off as well as the padding: a click at the left edge of
        // the *text* is a click one gutter's width in from the left edge of the
        // control ([ADR-0331]).
        var offset = shaped.offsetAt(line.start(), line.end(), x - padding.left() - gutterWidth - indentOf(line));

        var next = switch (Math.min(clickCount, 3)) {
            // A triple-click is "select the line", and here there really is one.
            case 3 -> new TextEdit(edit.text(), line.start(), line.end());
            case 2 -> edit.wordAt(offset);
            default -> edit.caretTo(offset, extend);
        };
        apply(next, EditHistory.Kind.OTHER, false);
    }

    @Override
    public boolean scrollByLines(double lines) {
        var maximum = maximumScroll();
        if (maximum <= 0) {
            // Nothing to scroll. Reported so the wheel is *not* consumed and the
            // page behind this keeps it — a control that swallowed every wheel
            // would trap the scroll the moment the pointer crossed it.
            return false;
        }
        // A line of *this* control's text, which is why the conversion is here
        // and not at the caller: a `mono` area at 13px and a body one at 15px
        // move different distances for the same turn of the wheel, and both of
        // them move a line at a time ([ADR-0314]).
        var lineHeight = document == null ? 0 : document.font().lineHeight();
        if (lineHeight <= 0) {
            return false;
        }
        var next = Math.clamp(scrollOffset + lines * lineHeight, 0, maximum);
        if (next == scrollOffset) {
            return false;
        }
        setState(() -> scrollOffset = next);
        return true;
    }

    @Override
    public void focusChanged(boolean gained, boolean fromKeyboard) {
        if (gained) {
            // From here on the caret is what the reader is looking for, so the
            // content follows it.
            caretMatters = true;
        }
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
        // The height counts too when the area fills: how many lines are on screen is
        // then a fact about the container rather than about `max-rows`, and a pane
        // that got taller has to rebuild before it will show the extra line.
        var changed = Math.abs(extent.width() - bounds.width()) > 0.5f
                || (widget().fill() && Math.abs(extent.height() - bounds.height()) > 0.5f);
        bounds = extent;
        if (changed && isMounted()) {
            setState(() -> { });
        }
    }

    @Override
    public TextDocument shaped(String text, Font font, TextDocument.Shaper shaper) {
        // The previous document is handed back in, which is what makes this
        // incremental: everything but the hard lines the edit touched keeps the
        // paragraph it already had, and keeps its wrap with it ([ADR-0388]).
        var next = TextDocument.of(font, text, document, shaper);
        document = next;
        return next;
    }

    @Override
    public double laidOut(TextDocument shaped, AreaPadding edges, double gutter, TextAlign align) {
        document = shaped;
        padding = edges;
        gutterWidth = gutter;
        textAlign = align;

        var lineHeight = shaped.font().lineHeight();
        var offset = scrollOffset;

        // The caret is only chased once this control has been touched -- see
        // [#caretMatters]. An untouched area shows the top of its value, which is
        // what a reader handed a document expects and what every text box on the
        // web does.
        if (caretMatters) {
            var caretLine = lineIndex(edit.caret());
            var caretTop = caretLine * lineHeight;
            var visible = visibleRows() * lineHeight;

            // Move as little as possible to keep the caret's line in view.
            offset = Math.max(offset, caretTop + lineHeight - visible);
            offset = Math.min(offset, caretTop);
        }
        offset = Math.clamp(offset, 0, maximumScroll());
        scrollOffset = offset;
        // The text moved to follow the caret after the bar was built, so the
        // thumb is where the text was. One rebuild puts it where the text is; the
        // next render lays out the same offset and asks for nothing.
        if (!Double.isNaN(barOffset) && Math.abs(barOffset - offset) > 0.01 && isMounted()) {
            setState(() -> {});
        }
        return offset;
    }

    /// How many lines are on screen at once — [TextArea#maxRows] for an ordinary
    /// area, and what the **measured** height holds for one that fills.
    ///
    /// One method, read by three: the box sizes its parts by it, `laidOut` keeps the
    /// caret inside it and `maximumScroll` stops at it. They disagreeing is a
    /// selection highlight that runs out half way down a pane.
    ///
    /// Before the first measurement a filling area reports [TextArea#maxRows] as
    /// well, which is one frame of a guess and the same bargain every measured
    /// control here makes.
    private int visibleRows() {
        var area = widget();
        if (!area.fill() || document == null || bounds.height() <= 0) {
            return area.maxRows();
        }
        var lineHeight = document.font().lineHeight();
        if (lineHeight <= 0) {
            return area.maxRows();
        }
        var content = bounds.height() - padding.vertical();
        return Math.max(1, (int) Math.floor(content / lineHeight));
    }

    /// How wide the gutter was made on the last frame — what the box insets the
    /// text by, and 0 when there is no gutter.
    double gutter() {
        return gutterWidth;
    }

    /// How far the content has been scrolled up, in logical pixels.
    ///
    /// For the tests, which is where "an area opened on a document shows its first
    /// line" is a number rather than a picture. Package-private: what a control has
    /// scrolled to is nobody else's business.
    double scrolledBy() {
        return scrollOffset;
    }

    /// Where each line sits in [#contentWidth()] — `text-align`, from the last
    /// frame's resolved style ([#laidOut]).
    ///
    /// The last frame's, like the width beside it and for the same reason: `render`
    /// runs before Yoga, and both are read by the hit test and the caret between
    /// frames rather than during one.
    private TextAlign textAlign = TextAlign.START;

    /// How far in `line` was drawn, which is what every x here is measured from.
    ///
    /// [TextAlign#indentOf] and not a rule of its own: the paint indents each line
    /// by that method, so a second implementation here is the drift `docs/gaps.md`
    /// G30 is about ([ADR-0318], [ADR-0324]).
    private double indentOf(TextLine line) {
        return textAlign.indentOf(line.width(), contentWidth());
    }

    @Override
    public double contentWidth() {
        // Both edges, each once. `2 * left` was every shipped stylesheet's answer
        // and nobody else's: `padding: 12px 16px 12px 0` wrapped the text 16
        // pixels wider than its room (`docs/gaps.md` G43, ADR-0350).
        var measured = bounds.width() - padding.horizontal() - gutterWidth;
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
        var insertion = room < 0 ? normalised : MaxLength.clip(normalised, room);
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
    ///
    /// A computed list rather than a built one: a document of ten thousand lines
    /// is asked about three of them per frame, and materialising the rest would
    /// be the cost `docs/gaps.md` G44 is about, moved rather than removed
    /// ([ADR-0388]).
    private List<TextLine> lines() {
        var layout = layout();
        return layout == null ? List.of() : layout;
    }

    /// The same, as the document's own view — null before the first render.
    private @Nullable DocumentLines layout() {
        var shaped = document;
        return shaped == null ? null : shaped.lines(contentWidth());
    }

    /// Which visual line `offset` is on — the last one that starts at or before
    /// it, which is what puts a caret at a wrap on the line it is about to type
    /// into.
    ///
    /// A search within the offset's own hard line rather than a walk over the
    /// document's lines, which is what makes it independent of how long the note
    /// is ([ADR-0388]).
    private int lineIndex(int offset) {
        var layout = layout();
        return layout == null ? 0 : layout.indexOf(offset);
    }

    private int visualLineStart(int offset) {
        var layout = lines();
        return layout.isEmpty() ? 0 : layout.get(lineIndex(offset)).start();
    }

    private int visualLineEnd(int offset) {
        var layout = lines();
        return layout.isEmpty() ? edit.length() : layout.get(lineIndex(offset)).end();
    }

    /// Whether the pointer is holding the scrollbar's thumb.
    private boolean draggingBar;

    /// The offset the scrollbar was last built with, so a render that moved the
    /// text to follow the caret can ask for the rebuild that moves the thumb.
    private double barOffset = Double.NaN;

    /// §4's "scrollbar beyond": `scroll`'s bar over this control's own offset,
    /// or null while the text fits (ADR-0362).
    ///
    /// The viewport is what the content box shows — the rows for an ordinary
    /// area, the measured height for one that fills — so the thumb's length is
    /// the proportion of the text on screen, and its track is the box it is
    /// drawn down.
    private @Nullable ScrollBar scrollbar() {
        var shaped = document;
        if (shaped == null) {
            barOffset = Double.NaN;
            return null;
        }
        var lineHeight = shaped.font().lineHeight();
        var content = lines().size() * lineHeight;
        var viewport = widget().fill() && bounds.height() > 0
                ? bounds.height() - padding.vertical()
                : visibleRows() * lineHeight;
        if (lineHeight <= 0 || content <= viewport + 0.5) {
            barOffset = Double.NaN;
            return null;
        }
        barOffset = scrollOffset;
        return new ScrollBar(
                true, viewport, content, scrollOffset, this::scrollTo, draggingBar, this::dragBar);
    }

    private void scrollTo(double offset) {
        var next = Math.clamp(offset, 0, maximumScroll());
        if (next != scrollOffset) {
            setState(() -> scrollOffset = next);
        }
    }

    private void dragBar(boolean held) {
        setState(() -> draggingBar = held);
    }

    /// How far this control can be scrolled: the text's height less what it
    /// shows.
    private double maximumScroll() {
        var shaped = document;
        if (shaped == null) {
            return 0;
        }
        var lineHeight = shaped.font().lineHeight();
        return Math.max(0, lines().size() * lineHeight - visibleRows() * lineHeight);
    }

    // --- the edit --------------------------------------------------------------

    private boolean apply(TextEdit next, EditHistory.Kind kind, boolean filtered) {
        if (next.equals(edit)) {
            return false;
        }
        if (filtered && !accepts(next.text())) {
            return false;
        }
        // A press, a key or an edit: whichever it was, the caret is now where the
        // reader is working and the content follows it (see [#caretMatters]).
        caretMatters = true;
        var before = edit;
        setState(() -> edit = next);
        if (!before.text().equals(next.text())) {
            history.record(before, next, kind);
            widget().report(next.text());
        }
        // **Every** change, including one that moved only the caret: three of the
        // four things a shortcut needs to know about change no text at all
        // (`docs/gaps.md` G38, [ADR-0332]).
        widget().reportEdit(next);
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
        widget().reportEdit(restored);
        solid();
        return true;
    }

    private boolean accepts(String candidate) {
        var maximum = widget().maxLength();
        return maximum < 0 || candidate.length() <= maximum;
    }

    /// How many more characters will fit, or -1 for no limit — [MaxLength]'s,
    /// along with the clipping, because `text-input` asks the same two questions
    /// and each control used to answer them for itself.
    private int room() {
        return MaxLength.room(widget().maxLength(), edit);
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
