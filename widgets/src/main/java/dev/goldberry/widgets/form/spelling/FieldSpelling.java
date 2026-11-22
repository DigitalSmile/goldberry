package dev.goldberry.widgets.form.spelling;

import java.util.Objects;
import java.util.concurrent.Executor;

import org.jspecify.annotations.Nullable;

import dev.goldberry.bind.Subscription;
import dev.goldberry.text.spell.SpellCheck;
import dev.goldberry.text.spell.SpellCheckers;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.attr.Spelling;

/// One field state's spell checking: the [SpellCheck] its `spellcheck` and
/// `lang` call for, kept from build to build and made again when they change or
/// when the application installs another checker.
///
/// ```java
/// private final FieldSpelling spelling = new FieldSpelling(this::recheck);
///
/// public Widget build(BuildContext context) {
///     var check = spelling.update(context, !widget().password());
///     ...
/// }
///
/// protected void dispose() {
///     spelling.close();
/// }
/// ```
///
/// `changed` runs on the UI thread when a check lands or the checkers change,
/// and the state builds again.
public final class FieldSpelling implements AutoCloseable {

    private final Runnable changed;
    private @Nullable SpellCheck check;
    private @Nullable Subscription checked;
    private @Nullable Subscription registry;
    private @Nullable Executor ui;

    /// @param changed what to do when there is something new to draw
    public FieldSpelling(Runnable changed) {
        this.changed = Objects.requireNonNull(changed, "changed");
    }

    /// The check for the field at `context`, or null when it does not check:
    /// nothing in the tree says `spellcheck=true`, `allowed` is false (a
    /// password), or no checker answers its language.
    public @Nullable SpellCheck update(BuildContext context, boolean allowed) {
        var spelling = Spelling.of(context);
        if (!allowed || !spelling.checks()) {
            close();
            return null;
        }
        var threads = SpellCheck.Threads.application();
        if (registry == null) {
            var hop = threads.ui();
            ui = hop;
            registry = SpellCheckers.onChange(() -> hop.execute(changed));
        }
        var checker = SpellCheckers.forField(spelling.lang()).orElse(null);
        if (checker == null) {
            dropCheck();
            return null;
        }
        var current = check;
        if (current != null && current.checker() == checker) {
            return current;
        }
        dropCheck();
        var made = new SpellCheck(checker, SpellCheckers.personal(checker.locale()), threads);
        checked = made.onChecked(changed);
        check = made;
        return made;
    }

    /// The check the last [#update] made, or null.
    public @Nullable SpellCheck current() {
        return check;
    }

    /// Stops checking and stops listening. Idempotent; the next [#update] starts
    /// again.
    @Override
    public void close() {
        dropCheck();
        var listening = registry;
        registry = null;
        ui = null;
        if (listening != null) {
            listening.close();
        }
    }

    private void dropCheck() {
        var subscription = checked;
        checked = null;
        if (subscription != null) {
            subscription.close();
        }
        var previous = check;
        check = null;
        if (previous != null) {
            previous.close();
        }
    }

    @Override
    public String toString() {
        return "FieldSpelling[" + (check == null ? "off" : check.checker().locale()) + (ui == null ? "" : ", listening")
                + "]";
    }
}
