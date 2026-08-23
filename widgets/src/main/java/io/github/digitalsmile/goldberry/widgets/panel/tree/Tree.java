package io.github.digitalsmile.goldberry.widgets.panel.tree;

import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;

import java.util.List;
import java.util.function.Consumer;

/// A hierarchical list — `docs/core-widgets.md` §3's `tree`.
///
/// ```java
/// new Tree(List.of(
///         TreeNode.of("europe", "Europe",
///                 TreeNode.leaf("no", "Norway"),
///                 TreeNode.leaf("se", "Sweden"))),
///         chosen, this::pick)
/// ```
///
/// ## Controlled, like every other value in this toolkit
///
/// It **reads** which node is selected and reports what the user asked for; it
/// selects nothing itself ([ADR-0063]). Expansion is the one exception and is not
/// an exception at all: which branches are open is not a value an application
/// models, it is a view state belonging to the thing on screen — the same
/// distinction `scroll` draws for its offset.
///
/// ## What it is made of
///
/// ```
/// tree                 this node. Stateful, styles nothing, holds what is open
/// └── tree-row × n     one per *visible* node, flattened depth-first
///     ├── tree-indent  the gutter: depth × 20
///     ├── tree-chevron `>` or `v`, and empty on a leaf
///     └── tree-label   the words
/// ```
///
/// Stateful and unstyled for [io.github.digitalsmile.goldberry.widgets.core.scroll.Scroll]'s
/// reason: a stateful widget that also carried the CSS type would put two `tree`
/// nodes in the cascade, one inside the other, and every rule would apply twice.
///
/// ## Selecting a parent
///
/// §3 gives `select tree=` `checkable="leaf|any|cascade"` and makes **leaf-only**
/// the default, "because 'Europe' is usually a heading and not an answer". That
/// default is here as [#leafOnly], and a parent in a leaf-only tree is still a
/// row — navigable, openable, and not an answer.
///
/// The `cascade` and `any` forms, and the checkbox per node §3 asks a standalone
/// `tree` for, are not built
/// ([ADR-0184](../../../../../../../../book/src/adr/0184-a-tree-is-a-list-that-remembers-what-is-open.md)).
///
/// @param roots      the top-level nodes
/// @param selected   the id of the chosen node, or null
/// @param onSelect   what the user asked to choose
/// @param leafOnly   whether a node with children may be chosen — see above
/// @param attributes `id` and `class`, exactly as on the primitives
public record Tree(List<TreeNode> roots, String selected, Consumer<String> onSelect,
        boolean leafOnly, Attributes attributes)
        implements Widget.Stateful, Attributed<Tree> {

    public Tree {
        roots = List.copyOf(roots == null ? List.of() : roots);
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    /// A leaf-only tree, which is §3's default.
    public Tree(List<TreeNode> roots, String selected, Consumer<String> onSelect) {
        this(roots, selected, onSelect, true, Attributes.NONE);
    }

    /// This tree letting a node with children be chosen — §3's `checkable="any"`,
    /// in the half of it that is a selection rule rather than a checkbox.
    public Tree anyNode(boolean value) {
        return new Tree(roots, selected, onSelect, !value, attributes);
    }

    @Override
    public Tree withAttributes(Attributes value) {
        return new Tree(roots, selected, onSelect, leafOnly, value);
    }

    @Override
    public Object key() {
        return attributes.key();
    }

    @Override
    public State<?> createState() {
        return new TreeState();
    }
}
