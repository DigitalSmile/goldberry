package io.github.digitalsmile.goldberry.widgets.panel.list;

/// How many rows may be chosen at once — `docs/core-widgets.md` §10's
/// "selection models: none / single / multi (Ctrl/Shift semantics)".
///
/// **Defined here now, and it was defined in `tree` first.** §3 gives a tree
/// "`list`'s selection models" and `list` was not built, so ADR-0184's rule
/// applied again: the widget that needs a model first defines it and writes down
/// that the other will have to agree ([ADR-0210]). This is that debt being paid
/// the way it was promised — the definition moved to the widget the specification
/// names it after, and `tree` imports it. Nothing about the shape changed, which
/// is the evidence that the promise was a small one to make.
public enum Selection {

    /// Nothing is an answer. The rows still navigate and still open, and a click
    /// on one opens it rather than choosing it.
    ///
    /// For a list that is a *view* — a set of things being browsed rather than
    /// picked from — and for one whose real answer is something else on the row,
    /// where a selection highlight would be a second thing claiming to be the
    /// choice.
    NONE,

    /// One row at a time — the **default**, and what `select tree=` needs.
    ///
    /// Modifiers are ignored: `Ctrl` and `Shift` mean "and also" and "through
    /// to", and a control that can only hold one has nothing to say to either.
    SINGLE,

    /// Several rows, with the modifiers every desktop list uses.
    ///
    /// A plain click or `Enter` **replaces** the selection with the row.
    /// `Ctrl` **toggles** that row and leaves the rest — which is also the only
    /// way to take one out. `Shift` selects **through** from the anchor to the
    /// row, over the rows as they are on screen, replacing what was there.
    ///
    /// The anchor is the last row chosen *without* `Shift`, so a run of shifted
    /// presses sweeps a range back and forth from one end rather than growing
    /// from wherever it last stopped — which is what every file manager does and
    /// what makes an over-shot range recoverable without starting again.
    ///
    /// **What is reported is the whole new set**, not the row that was pressed.
    /// A `Shift` range is computed over rows only the list can see, so an id on
    /// its own would be an answer the application could not turn into a
    /// selection.
    MULTIPLE
}
