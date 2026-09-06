package io.github.digitalsmile.goldberry.widgets.form.codeinput;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;

/// What a [CodeInput] holds: the code, and whether it has the keyboard.
///
/// Shorter than `text-input`'s state by everything a caret needs. There is no
/// blink timer, because there is no caret to blink — the focus ring on the active
/// box is what says where the next character goes, and §2.2 asks for it to be
/// instant. So a window with a focused code field asks for no frames at all,
/// which is §1.7's idle loop holding for one more control.
///
/// ## Text input follows this field's focus
///
/// `text-input`'s arrangement, unchanged: SDL delivers no committed text until a
/// window asks, and asking is what raises an on-screen keyboard, so it is turned
/// on when focus arrives here and off when it leaves.
final class CodeInputState extends State<CodeInput> implements CodeEditor {

    private CodeEdit edit = CodeEdit.empty(CodeEdit.DEFAULT_LENGTH);

    /// The window, captured in `build` and used only from a handler — which is
    /// what [BuildContext#host()] allows.
    private @Nullable Host host;

    private boolean focused;

    /// The value the widget last offered, so a *change* to it can be told from a
    /// value that has simply always been there.
    ///
    /// `text-input`'s [#follow] in full, and it is needed here for the same
    /// reason: without it an unbound field — whose `value` is a constant the
    /// widget was built with — would be reset to that constant by every rebuild,
    /// which is every keystroke.
    private String lastOffered = "";

    /// Whether the last edit left every box filled.
    ///
    /// What makes `complete` fire on the edit that filled the last box rather
    /// than on every keystroke into a full field. A `Backspace` sets it back, so
    /// a code corrected and retyped completes twice, which is right: it is two
    /// codes.
    private boolean completed;

    @Override
    protected void initState() {
        super.initState();
        var input = widget();
        lastOffered = offered(input);
        edit = CodeEdit.empty(input.length()).type(lastOffered, input.type());
        completed = edit.isComplete();
    }

    @Override
    public Widget build(BuildContext context) {
        host = context.host().orElse(null);
        var input = widget();
        // Before `follow`, because a length that changed is a different set of
        // boxes for the same code and `follow` compares against what is held.
        edit = edit.resized(input.length());
        follow(input);
        return new CodeField(
                edit, input.mask(), focused && !input.disabled(), input.disabled(), input.attributes(), this);
    }

    /// Takes a value the **application** changed, and ignores the echo of the
    /// user's own keystroke.
    ///
    /// In `build` rather than in `didUpdateWidget`, because a `bind=` value
    /// changing does not replace the widget: the property fires, the element is
    /// marked for build, and the widget is the same object it was ([ADR-0062]).
    ///
    /// The offered value goes through [CodeEdit#withValue], so a `bind=` carrying
    /// a letter into a `digits` field leaves the boxes empty rather than drawing
    /// something nobody could have typed.
    private void follow(CodeInput input) {
        var value = offered(input);
        if (value.equals(lastOffered)) {
            return;
        }
        lastOffered = value;
        if (value.equals(edit.value())) {
            return;
        }
        edit = edit.withValue(value, input.type());
        // A code the application set is a code nobody typed, so the next fill of
        // the last box is still the first one worth reporting.
        completed = edit.isComplete();
    }

    /// The value the widget says it holds — its binding if it has one, its
    /// literal otherwise.
    private static String offered(CodeInput input) {
        var resolved = input.resolved();
        return resolved == null ? "" : resolved;
    }

    // --- CodeEditor -----------------------------------------------------------

    @Override
    public boolean type(String text) {
        if (widget().disabled()) {
            return false;
        }
        return adopt(edit.type(text, widget().type()));
    }

    @Override
    public boolean backspace() {
        if (widget().disabled()) {
            return false;
        }
        return adopt(edit.backspace());
    }

    @Override
    public boolean clear() {
        if (widget().disabled()) {
            return false;
        }
        return adopt(edit.cleared());
    }

    @Override
    public boolean paste() {
        if (widget().disabled() || host == null) {
            return false;
        }
        var pasted = host.clipboard().text();
        if (pasted.isEmpty()) {
            return false;
        }
        // Nothing is flattened or trimmed on the way in, unlike `text-input`'s
        // paste: `CodeType` drops every character that is not a box's, so the
        // spaces in a `Your code is 123 456` are gone before they reach the edit
        // and there is no second rule to keep in step with the first.
        return adopt(edit.type(pasted, widget().type()));
    }

    @Override
    public void focusChanged(boolean gained, boolean fromKeyboard) {
        if (focused == gained) {
            return;
        }
        setState(() -> focused = gained);
        if (host != null) {
            host.textInput(gained);
        }
    }

    /// Takes `next` if it differs, tells the application, and asks for a frame.
    ///
    /// @return whether anything changed — which is what decides whether the key
    ///         event that caused it is consumed
    private boolean adopt(CodeEdit next) {
        if (next.equals(edit)) {
            return false;
        }
        setState(() -> edit = next);
        var onChange = widget().onChange();
        if (onChange != null) {
            onChange.accept(next.value());
        }
        // After `change`, so an application that reads its model in `complete`
        // reads the code that completed it rather than the one before.
        var full = next.isComplete();
        if (full && !completed) {
            var onComplete = widget().onComplete();
            if (onComplete != null) {
                onComplete.accept(next.value());
            }
        }
        completed = full;
        return true;
    }

    @Override
    protected void dispose() {
        if (focused && host != null) {
            // The window would otherwise keep an on-screen keyboard up for a
            // field that has gone away.
            host.textInput(false);
        }
        super.dispose();
    }
}
