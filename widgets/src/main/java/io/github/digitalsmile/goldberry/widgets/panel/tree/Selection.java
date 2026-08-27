package io.github.digitalsmile.goldberry.widgets.panel.tree;

/// How many rows of a [Tree] may be chosen at once — `docs/core-widgets.md` §3's
/// "`list`'s selection models: none / single / multi (Ctrl/Shift semantics)".
///
/// **Defined here rather than inherited**, for the reason the node model was
/// (ADR-0184): §3 says a tree shares `list`'s models and `list` is not built, so
/// `list` will have to agree with what shipped here. The shape is the one every
/// desktop list has, which is what makes that a small promise to make on `list`'s
/// behalf.
public enum Selection {

    /// Nothing is an answer. The rows still navigate and still open, and a click
    /// on one opens it rather than choosing it.
    ///
    /// For a tree that is a *view* — a structure being browsed rather than
    /// picked from — and for one whose real answer is its checkboxes
    /// ([Checkable]), where a selection highlight would be a second thing
    /// claiming to be the choice.
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
    /// row, over the flattened visible list, replacing what was there.
    ///
    /// The anchor is the last row chosen *without* `Shift`, so a run of shifted
    /// presses sweeps a range back and forth from one end rather than growing
    /// from wherever it last stopped — which is what every file manager does and
    /// what makes an over-shot range recoverable without starting again.
    ///
    /// **What is reported is the whole new set**, not the row that was pressed.
    /// A `Shift` range is computed over rows only the tree can see, so an id on
    /// its own would be an answer the application could not turn into a
    /// selection.
    MULTIPLE
}
