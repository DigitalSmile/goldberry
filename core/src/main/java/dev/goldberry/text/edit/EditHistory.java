package dev.goldberry.text.edit;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/// The undo and redo stacks of one edited text.
///
/// ```java
/// var history = new EditHistory();
///
/// var next = edit.insert(typed);
/// history.record(edit, next, EditHistory.Kind.TYPING);   // after the edit, with both ends
/// edit = next;
///
/// edit = history.undo(edit);                             // the state before the last run
/// ```
///
/// It is a stack of [TextEdit] values rather than a log of inverse operations:
/// undoing hands back a state that already existed, so nothing has to know how
/// to reverse a word delete.
///
/// Consecutive changes of the same [Kind] fold into one undo entry, so a user who
/// types "Goldberry" and presses `Ctrl+Z` gets the word back and not one letter.
/// What decides "consecutive" is that the state a change starts from is exactly
/// the state the last one ended at. That one test covers several rules: a caret
/// move or a click breaks the run, because the next keystroke starts from a state
/// the last one did not leave; typing after deleting starts a new entry, because
/// the kinds differ; a value arriving from the model breaks it, because the text
/// no longer matches. No timer is involved, so a long typed run is one undo
/// however long it took. A paste, a cut and a replaced selection are
/// [Kind#OTHER] and never fold: each is one deliberate act, and one `Ctrl+Z`.
///
/// The stack holds [#DEPTH] entries and drops the oldest, so a field that lives
/// as long as its window cannot grow without bound. Confined to the UI thread,
/// like the state that holds it.
///
/// Read more: [Selection and editing](https://goldberry.dev/docs/guide/text.html#selection-and-editing).
public final class EditHistory {

    /// How many undo steps are kept.
    ///
    /// Generous rather than measured: an entry is a string the history already
    /// shares with its neighbours, so a limit too high costs little and one too
    /// low loses somebody's work.
    public static final int DEPTH = 200;

    /// What kind of change produced a state — the first half of "can these fold
    /// together".
    public enum Kind {

        /// Text was typed in. Folds with more typing.
        TYPING,

        /// Text was deleted with `Backspace` or `Delete`, by character or by
        /// word. Folds with more deleting.
        DELETING,

        /// Everything else: a paste, a cut, a replaced selection, a value set
        /// from outside. Never folds — one deliberate act, one `Ctrl+Z`.
        OTHER
    }

    private final Deque<TextEdit> past = new ArrayDeque<>();
    private final Deque<TextEdit> future = new ArrayDeque<>();

    /// The state the last recorded change ended at, or null if nothing has been
    /// recorded. Compared against the next change's starting state to decide
    /// whether the two are one run.
    private @Nullable TextEdit lastAfter;
    private Kind lastKind = Kind.OTHER;

    /// An empty history, for a field that has just been mounted.
    public EditHistory() {}

    /// Records that `before` became `after`.
    ///
    /// Called after the edit, with both ends of it: the state to restore is
    /// `before`, and whether this continues the last run is a question about
    /// `before` alone. A change that changed nothing is ignored, so a `Backspace`
    /// at the start of a field does not silently consume the next `Ctrl+Z`.
    /// Recording anything clears the redo stack: once something new is typed,
    /// the future that was undone is no longer reachable.
    public void record(TextEdit before, TextEdit after, Kind kind) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        Objects.requireNonNull(kind, "kind");
        if (before.equals(after)) {
            return;
        }
        future.clear();
        if (foldsInto(before, kind)) {
            // The run continues: the entry already on the stack is the state to
            // go back to, and this change only moves where the run has got to.
            lastAfter = after;
            return;
        }
        past.push(before);
        while (past.size() > DEPTH) {
            past.removeLast();
        }
        lastKind = kind;
        lastAfter = after;
    }

    private boolean foldsInto(TextEdit before, Kind kind) {
        return kind != Kind.OTHER && kind == lastKind && !past.isEmpty() && before.equals(lastAfter);
    }

    /// Whether there is anything to undo.
    public boolean canUndo() {
        return !past.isEmpty();
    }

    /// Whether there is anything to redo.
    public boolean canRedo() {
        return !future.isEmpty();
    }

    /// The state before the last change, with `current` kept for [#redo].
    ///
    /// @return the state to restore, or `current` unchanged when there is nothing
    ///         to undo — so a caller can assign the result unconditionally rather
    ///         than unwrap an `Optional` it has already tested for
    public TextEdit undo(TextEdit current) {
        Objects.requireNonNull(current, "current");
        if (past.isEmpty()) {
            return current;
        }
        future.push(current);
        var restored = past.pop();
        // The run is over either way: the next keystroke must not fold into an
        // entry that has been popped, and after a redo the state it would compare
        // against is no longer where the field is.
        endRun();
        return restored;
    }

    /// The state undone by the last [#undo], with `current` pushed back onto the
    /// undo stack.
    ///
    /// @return the state to restore, or `current` when there is nothing to redo
    public TextEdit redo(TextEdit current) {
        Objects.requireNonNull(current, "current");
        if (future.isEmpty()) {
            return current;
        }
        past.push(current);
        var restored = future.pop();
        endRun();
        return restored;
    }

    /// Ends the current run, so the next change starts a new undo entry even if
    /// it would otherwise have folded.
    ///
    /// The escape hatch for the cases the coalescing rule cannot see for itself —
    /// a field losing focus, which is a place a user thinks of as a boundary
    /// although nothing about the text changed.
    public void endRun() {
        lastAfter = null;
        lastKind = Kind.OTHER;
    }

    /// Forgets everything.
    ///
    /// What a field does when it is given a new value to hold rather than a new
    /// value to edit — undoing your way back into somebody else's data is not an
    /// undo.
    public void clear() {
        past.clear();
        future.clear();
        endRun();
    }

    /// How many undo steps are available. For tests and for a diagnostic.
    public int depth() {
        return past.size();
    }

    @Override
    public String toString() {
        return "EditHistory[" + past.size() + " back, " + future.size() + " forward]";
    }
}
