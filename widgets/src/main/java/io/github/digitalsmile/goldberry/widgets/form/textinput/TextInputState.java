package io.github.digitalsmile.goldberry.widgets.form.textinput;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.backend.EventLoop;
import io.github.digitalsmile.goldberry.input.Extent;
import io.github.digitalsmile.goldberry.text.Paragraph;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.time.Duration;

/// What a [TextInput] holds: the text, the history, the blink and how far it has
/// scrolled.
///
/// ## The caret blinks on a timer, not on the frame clock
///
/// A spinner draws itself from [io.github.digitalsmile.goldberry.widget.Paints.Context#nowMillis]
/// and says [io.github.digitalsmile.goldberry.widget.Paints#isAnimating], which
/// asks for a frame every frame — right for something that moves continuously,
/// and badly wrong for a caret. A caret changes **twice a second**, so animating
/// it would run the frame loop at the display's rate for the whole time a field
/// has focus, which is most of the time a form is open. §1.7's "the frame loop is
/// fully idle when no animation is active" would then be false for every window
/// with a focused field in it.
///
/// So the blink is a one-shot timer, rescheduled — the arrangement `carousel`
/// uses ([ADR-0165]) — and it produces two frames a second instead of a hundred
/// and twenty.
///
/// **It restarts on every edit and every caret move.** A caret that blinked out
/// while somebody was typing would be a caret they cannot find, so any change
/// makes it solid again and the next dark half is [#BLINK] later.
///
/// ## Text input follows this field's focus
///
/// SDL delivers no committed text until a window asks, and asking is what raises
/// an on-screen keyboard, so it is turned on when focus arrives here and off when
/// it leaves. A window whose fields are all unfocused has it off.
final class TextInputState extends State<TextInput> implements TextEditor {

    /// How long each half of the blink lasts.
    ///
    /// 530 ms, which is the Windows default and within a few tens of a
    /// millisecond of every other platform's. Not a token: §8's subset has no
    /// property for it, and §1.7's motion durations are about things moving from
    /// one place to another, which a caret does not do.
    private static final Duration BLINK = Duration.ofMillis(530);

    private TextEdit edit = TextEdit.EMPTY;
    private final EditHistory history = new EditHistory();

    /// The window, captured in `build` and used only from a handler — which is
    /// what [BuildContext#host()] allows.
    private Host host;

    private boolean focused;
    private boolean caretShown = true;
    private EventLoop.Timer blink;

    /// How far the content has been scrolled left, in logical pixels.
    ///
    /// Not `setState`: it is computed during `render` from the caret's position
    /// and applied in the same frame, so marking the element dirty for it would
    /// be asking for a frame in order to draw the frame being drawn (ADR-0119).
    private double scrollOffset;

    /// The last frame's size, from [io.github.digitalsmile.goldberry.input.Measured].
    private Extent bounds = Extent.NONE;

    /// The last frame's shaped text and the mask it was shaped from, kept so the
    /// pointer — which arrives outside a render pass — can turn an x into an
    /// offset.
    private Paragraph paragraph;
    private Mask mask = Mask.of("", false);
    private double leftPadding;

    @Override
    protected void initState() {
        super.initState();
        lastOffered = text(widget());
        edit = TextEdit.of(lastOffered);
    }

    /// The value the widget last offered, so a *change* to it can be told from a
    /// value that has simply always been there.
    ///
    /// Set in [#initState] and compared on every build. Without it an unbound
    /// field — whose `value` is a constant the widget was built with — would be
    /// reset to that constant by every rebuild, which is every keystroke.
    private String lastOffered;

    /// Takes a value the **application** changed, and ignores the echo of the
    /// user's own keystroke.
    ///
    /// In `build` rather than in `didUpdateWidget`, because a `bind=` value
    /// changing does not replace the widget: the property fires, the element is
    /// marked for build, and the widget is the same object it was
    /// ([ADR-0062]). `didUpdateWidget` would therefore miss the case this exists
    /// for entirely.
    ///
    /// Two tests, and both are needed. The value must have *changed* since the
    /// last build — or a constant `value=` would overwrite the field forever —
    /// and it must differ from what the field holds, or the round trip through
    /// an application's own `change` handler would reset the caret to the end on
    /// every letter.
    private void follow() {
        var offered = text(widget());
        if (offered.equals(lastOffered)) {
            return;
        }
        lastOffered = offered;
        if (offered.equals(edit.text())) {
            return;
        }
        edit = edit.withText(offered);
        // Not an undo step: undoing your way back into a value the application
        // set is not an undo.
        history.clear();
    }

    @Override
    public Widget build(BuildContext context) {
        host = context.host().orElse(null);
        follow();
        var input = widget();
        mask = Mask.of(edit.text(), input.password());

        var showPlaceholder = edit.isEmpty() && !input.placeholder().isEmpty();
        return new TextField(
                showPlaceholder ? input.placeholder() : mask.display(),
                showPlaceholder,
                mask.displayed(edit),
                focused && !input.disabled(),
                caretShown,
                input.disabled(),
                input.readOnly(),
                input.attributes(),
                this);
    }

    @Override
    protected void dispose() {
        stopBlinking();
        if (focused && host != null) {
            // The window would otherwise keep an on-screen keyboard up for a
            // field that has gone away.
            host.textInput(false);
        }
        super.dispose();
    }

    /// The text the widget says it holds — its binding if it has one, its literal
    /// otherwise.
    private static String text(TextInput input) {
        var resolved = input.resolved();
        return resolved == null ? "" : resolved;
    }

    /// What the field holds, unmasked.
    ///
    /// For a test, and package-private because that is the only honest caller:
    /// what a `password` field holds is exactly what nothing outside it should be
    /// able to ask for, which is why [#copy()] refuses too.
    String heldText() {
        return edit.text();
    }

    // --- TextEditor -----------------------------------------------------------

    @Override
    public boolean move(Motion motion, boolean byWord, boolean extend) {
        // A row of bullets has no words in it, so `Ctrl+Left` in a masked field
        // goes to the end it was heading for. Stepping by real words would move
        // the caret by an amount that says how long they are.
        var masked = widget().password();
        var next = switch (motion) {
            case LEFT -> !byWord ? edit.left(extend)
                    : masked ? edit.toStart(extend) : edit.wordLeft(extend);
            case RIGHT -> !byWord ? edit.right(extend)
                    : masked ? edit.toEnd(extend) : edit.wordRight(extend);
            case START -> edit.toStart(extend);
            case END -> edit.toEnd(extend);
        };
        return apply(next, EditHistory.Kind.OTHER, false);
    }

    @Override
    public boolean selectAll() {
        return apply(edit.selectAll(), EditHistory.Kind.OTHER, false);
    }

    @Override
    public boolean deleteBefore(boolean byWord) {
        var words = byWord && !widget().password();
        return apply(words ? edit.deleteWordBefore() : edit.backspace(),
                EditHistory.Kind.DELETING, true);
    }

    @Override
    public boolean deleteAfter(boolean byWord) {
        var words = byWord && !widget().password();
        return apply(words ? edit.deleteWordAfter() : edit.delete(),
                EditHistory.Kind.DELETING, true);
    }

    /// Replaces the edit, recording it in the history and telling the model.
    ///
    /// @param filtered whether the new text has to pass the field's filter and
    ///                 its maximum length — true for anything that changes it,
    ///                 false for a caret move, which no filter has an opinion
    ///                 about
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
        solid();
        return true;
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
    public void pointerAt(double x, boolean extend, int clickCount) {
        if (paragraph == null) {
            return;
        }
        // Into the content's own coordinates: past the padding, and back by
        // however far the field has scrolled.
        var contentX = x - leftPadding + scrollOffset;
        var displayOffset = paragraph.offsetAt(0, mask.display().length(), contentX);
        var offset = mask.real(displayOffset);

        var next = switch (Math.min(clickCount, 3)) {
            // A triple-click is "select the line", and a single-line field has
            // one line -- so it is select-all, which is also what it looks like.
            case 3 -> edit.selectAll();
            // A masked field has no words to select: every word() call over
            // bullets would select the whole run, which is what select-all
            // already does and is not what a double-click means.
            case 2 -> widget().password() ? edit.selectAll() : edit.wordAt(offset);
            default -> edit.caretTo(offset, extend);
        };
        apply(next, EditHistory.Kind.OTHER, false);
    }

    @Override
    public void focusChanged(boolean gained, boolean fromKeyboard) {
        // `setState` and not a bare assignment: the cascade's `:focus` is the
        // router's and repaints on its own, but the caret and the highlight are
        // *described* by this widget, so the tree has to be rebuilt for either to
        // appear. A focused field that never rebuilt would have no caret in it.
        setState(() -> focused = gained);
        if (host != null) {
            host.textInput(gained && !widget().disabled() && !widget().readOnly());
        }
        if (gained) {
            // A field reached by Tab selects everything, which is what lets a
            // keyboard user replace a value without reaching for Ctrl+A -- and a
            // field reached by a click does not, because the click has already
            // said where the caret goes.
            if (fromKeyboard) {
                apply(edit.selectAll(), EditHistory.Kind.OTHER, false);
            }
            solid();
        } else {
            stopBlinking();
            // Losing focus is a boundary a user believes in, although nothing
            // about the text changed.
            history.endRun();
            setState(() -> caretShown = true);
        }
    }

    @Override
    public void measured(Extent extent) {
        bounds = extent;
    }

    @Override
    public double laidOut(Paragraph shaped, double padding) {
        paragraph = shaped;
        leftPadding = padding;

        var display = mask.display();
        var caretAt = paragraph.widthBetween(0, Math.clamp(mask.display(edit.caret()), 0, display.length()));
        var textWidth = paragraph.widthBetween(0, display.length());
        // The last frame's width, less the padding on both sides. Zero before
        // anything has been measured, which reads as "no room" and leaves the
        // offset alone rather than snapping it to the caret.
        var room = bounds.width() - 2 * padding;
        if (room <= 0) {
            return scrollOffset;
        }

        // Move as little as possible: only when the caret has left the window.
        // A caret at the very end needs its own width of room, or the field
        // scrolls one pixel short of showing it.
        var offset = Math.max(scrollOffset, caretAt - room + 1);
        offset = Math.min(offset, caretAt);
        // And never leave a gap at the end: a field that has been scrolled and
        // then had its text deleted should come back rather than show a blank.
        offset = Math.clamp(offset, 0, Math.max(0, textWidth - room));
        scrollOffset = offset;
        return offset;
    }

    @Override
    public boolean copy() {
        if (!edit.hasSelection() || widget().password() || host == null) {
            // §4: a password field has no clipboard-out. The selection is still
            // real -- it can be replaced or deleted -- it just cannot leave.
            return false;
        }
        return host.clipboard().text(edit.selectedText());
    }

    @Override
    public boolean cut() {
        if (!copy()) {
            return false;
        }
        return apply(edit.insert(""), EditHistory.Kind.OTHER, true);
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
        // Newlines and tabs become spaces rather than being refused: a single
        // line cannot hold them, and a paste that silently did nothing because
        // the copied cell had a trailing newline is the worse outcome.
        var flattened = pasted.replaceAll("\\s*\\R\\s*", " ").replace('\t', ' ');
        var room = room();
        var insertion = room < 0 ? flattened : clip(flattened, room);
        if (insertion.isEmpty()) {
            return false;
        }
        return apply(edit.insert(insertion), EditHistory.Kind.OTHER, true);
    }

    @Override
    public boolean undo() {
        if (!history.canUndo()) {
            return false;
        }
        var restored = history.undo(edit);
        return adopt(restored);
    }

    @Override
    public boolean redo() {
        if (!history.canRedo()) {
            return false;
        }
        return adopt(history.redo(edit));
    }

    /// Takes a state back off the history, without recording it again.
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

    // --- limits ---------------------------------------------------------------

    /// Whether the field's filter will have `candidate`.
    private boolean accepts(String candidate) {
        var maximum = widget().maxLength();
        if (maximum >= 0 && candidate.length() > maximum) {
            return false;
        }
        return widget().filter().accepts(candidate);
    }

    /// How many more characters will fit, or -1 for no limit.
    private int room() {
        var maximum = widget().maxLength();
        if (maximum < 0) {
            return -1;
        }
        // What the selection would free up counts as room: typing over a full
        // field's selection must work.
        return Math.max(0, maximum - edit.length() + (edit.end() - edit.start()));
    }

    /// `text` cut to at most `room` characters, never through a cluster.
    private static String clip(String text, int room) {
        if (text.length() <= room) {
            return text;
        }
        if (room <= 0) {
            return "";
        }
        // offsetByCodePoints from the front rather than a substring, so a limit
        // that falls inside a surrogate pair drops the whole character rather
        // than leaving half of one.
        var end = text.offsetByCodePoints(0, text.codePointCount(0, Math.min(room, text.length())));
        return text.substring(0, Math.min(end, room));
    }

    // --- the blink ------------------------------------------------------------

    /// Makes the caret solid and starts the next dark half a full interval away.
    ///
    /// Called from every edit and every caret move, which is what keeps a caret
    /// visible while somebody types.
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
