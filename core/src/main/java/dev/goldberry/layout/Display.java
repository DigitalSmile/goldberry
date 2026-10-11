package dev.goldberry.layout;

/// Whether a node takes part in layout, and as what — CSS's `display`.
///
/// Every box is a flex container here, so the values are the three that mean
/// something when that is so.
public enum Display {

    /// A flex box, which every box is. The default.
    FLEX,

    /// No box at all: the node and everything under it are not laid out,
    /// painted, hit or reachable by the keyboard. Its elements stay mounted and
    /// keep their state.
    NONE,

    /// No box of its own: its children are laid out as its parent's. Whatever
    /// the node's widget draws itself, its text or its icon, goes with the box.
    CONTENTS
}
