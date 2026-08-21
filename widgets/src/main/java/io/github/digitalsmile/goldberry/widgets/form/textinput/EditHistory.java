package io.github.digitalsmile.goldberry.widgets.form.textinput;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/// The undo and redo stacks of one field.
///
/// `docs/core-widgets.md` §4 asks a `text-input` for an "undo/redo stack", and
/// [TextEdit] being a value is what makes it a stack of **states** rather than a
/// log of inverse operations: undoing is handing back a value that already
/// existed, so nothing has to know how to reverse a word delete.
///
/// ## Coalescing, and why it needs no timer
///
/// A user who types "Goldberry" and presses `Ctrl+Z` expects one word back, not
/// one letter. So consecutive changes of the same [Kind] fold into a single undo
/// entry — and what decides "consecutive" is that the state the new change starts
/// from is exactly the state the last one ended at.
///
/// That one test does the work of several rules:
///
/// - **A caret move breaks the run**, because moving the caret produces a state
///   the next keystroke starts from that is not the one the last keystroke left.
///   Nothing here mentions the caret; it falls out.
/// - **A click breaks it**, for the same reason.
/// - **Typing after deleting starts a new entry**, because the kinds differ.
/// - **A value arriving from the model breaks it**, because the text will not
///   match.
///
/// Every editor that coalesces on a *timer* has the bug where thinking for two
/// seconds mid-word splits the undo; this has the opposite and better failure,
/// where a long typed run is one undo however long it took.
///
/// A **paste, a cut and a replaced selection never coalesce**: they are
/// [Kind#OTHER], and each is one thing the user did deliberately and expects one
/// `Ctrl+Z` to reverse.
///
/// ## Bounded
///
/// [#DEPTH] entries, oldest dropped. A field is not a document, and an unbounded
/// stack on a widget that lives as long as its window is a leak that only shows
/// up on the machine of whoever leaves the application open all week.
///
/// Confined to the UI thread, like the state that holds it.
public final class EditHistory {

    /// How many undo steps a field keeps.
    ///
    /// A round number rather than a measured one, and generous: the entries are
    /// strings that already exist elsewhere in the field's own history, so the
    /// cost of the limit being too high is small and the cost of it being too low
    /// is somebody losing work.
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
    private TextEdit lastAfter;
    private Kind lastKind = Kind.OTHER;

    /// An empty history, for a field that has just been mounted.
    public EditHistory() {
    }

    /// Records that `before` became `after`.
    ///
    /// Called *after* the edit, with both ends of it. Coalescing needs both: the
    /// state to restore is `before`, and whether this continues the last run is a
    /// question about `before` and nothing else.
    ///
    /// A change that changed nothing is ignored, so a `Backspace` at the start of
    /// a field does not silently consume the next `Ctrl+Z`.
    ///
    /// Recording anything **clears the redo stack**, which is the universal rule:
    /// once you have typed something new, the future you undid your way out of is
    /// not reachable any more.
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
        return kind != Kind.OTHER
                && kind == lastKind
                && !past.isEmpty()
                && before.equals(lastAfter);
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
