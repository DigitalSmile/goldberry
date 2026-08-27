package io.github.digitalsmile.goldberry.widgets.panel.tree;

/// Whether a [Tree]'s rows carry a checkbox, and what checking one means —
/// `docs/core-widgets.md` §3's `checkable="none|leaf|any|cascade"`.
///
/// ## This is not the same question as "may this row be chosen"
///
/// §3 spends the word `checkable` twice on two different axes, and it is worth
/// saying which one this is. On `select tree=` it means **which nodes are an
/// answer** — "leaf-only by default, because 'Europe' is usually a heading and
/// not an answer" — and that rule shipped with ADR-0184 as `Tree.anyNode`. On a
/// standalone `tree` it means "adds a checkbox per node", which is this, and a
/// checkbox is a *second* value beside the selection rather than a rendering of
/// it: a file manager where the highlighted row and the ticked rows are the same
/// thing is a file manager that cannot copy six files
/// ([ADR-0210](../../../../../../../../book/src/adr/0210-a-tree-checks-and-selects-two-different-things.md)).
///
/// The disagreement between the two readings is recorded in `ARCHITECTURE.md`
/// §17.1 rather than resolved by picking one, because both sentences are in the
/// design documents and both describe something real.
public enum Checkable {

    /// No checkbox anywhere — the **default**, and what every `tree` in the
    /// toolkit was before there was a choice.
    NONE,

    /// A checkbox on the rows that have no children.
    ///
    /// For a chooser over things rather than over the shape they are filed in: a
    /// tag picker ticks tags and not the groups they sit in. A parent is still a
    /// row, still opens, and has no box.
    LEAF,

    /// A checkbox on every row, each one independent of the others.
    ///
    /// A parent's box says something about the *parent* — "include this folder"
    /// — rather than about what is in it, so checking it changes exactly one
    /// value. The right model when a branch is itself a thing that can be
    /// chosen, and the wrong one when it is a summary of its children, which is
    /// what [#CASCADE] is for.
    ANY,

    /// A checkbox on every row, propagating **down** and summarising **up**.
    ///
    /// §3's "one place the tri-state checkbox is not a decoration": checking a
    /// folder checks everything in it, and a folder with some of its contents
    /// checked draws the mixed state — which is a horizontal bar and deliberately
    /// not a greyed tick, because "some of these are on" and "all of these are
    /// on" have to be distinguishable at a glance.
    ///
    /// A parent's state is **derived** from what is under it rather than stored,
    /// so it cannot drift out of step with its own children. A node whose
    /// children are not known yet — a lazy branch nobody has opened — has nothing
    /// to derive from and reads its own membership, which is the only answer
    /// available without fetching a model the user has not asked for.
    CASCADE
}
