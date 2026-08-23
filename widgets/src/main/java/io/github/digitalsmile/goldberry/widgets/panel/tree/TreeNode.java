package io.github.digitalsmile.goldberry.widgets.panel.tree;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/// One node of a [Tree] — `docs/core-widgets.md` §3's "observable node model with
/// a children supplier".
///
/// ```java
/// TreeNode.of("europe", "Europe",
///         TreeNode.leaf("no", "Norway"),
///         TreeNode.leaf("se", "Sweden"))
/// ```
///
/// ## The id is load-bearing
///
/// §3: "expansion state is retained across rebuilds by node **id**, not by index
/// — a tree that collapsed itself when its model reordered would be the same
/// defect list keys exist to prevent". So an id is required rather than derived,
/// and two nodes sharing one is an application bug this cannot see: what it would
/// look like is two rows expanding together.
///
/// ## Three kinds of node, and the middle one is why this is not a list
///
/// - **A leaf** has no children and draws no chevron.
/// - **A parent** carries its children, which is every node an application
///   already has in memory.
/// - **A lazy parent** carries a [Supplier] instead, fetched when it first
///   expands and never again. §3 asks for exactly this — "lazy, so a node's
///   children are fetched when it first expands" — and it is the reason
///   [#mayHaveChildren()] exists apart from [#children()]: a node that has not
///   been opened must draw a chevron **before** anyone knows whether it has
///   anything in it, or a directory tree would have to stat the whole disk to
///   draw its first row.
///
/// @param id       this node's identity, unique within the tree
/// @param label    what the row reads
/// @param children the children it already has, empty for a leaf and ignored when
///                 `supplier` is set
/// @param supplier where to get the children the first time it expands, or null
public record TreeNode(String id, String label, List<TreeNode> children,
        Supplier<List<TreeNode>> supplier) {

    public TreeNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        children = List.copyOf(children == null ? List.of() : children);
        if (id.isBlank()) {
            throw new IllegalArgumentException(
                    "a tree node's id is what its expansion is remembered by, and \"" + id
                            + "\" cannot tell two nodes apart");
        }
    }

    /// A node with nothing under it.
    public static TreeNode leaf(String id, String label) {
        return new TreeNode(id, label, List.of(), null);
    }

    /// A node carrying the children it already has.
    public static TreeNode of(String id, String label, TreeNode... children) {
        return new TreeNode(id, label, List.of(children), null);
    }

    /// A node whose children are fetched the first time it opens.
    ///
    /// It draws a chevron before anything has been fetched — see the class note.
    public static TreeNode lazy(String id, String label, Supplier<List<TreeNode>> supplier) {
        return new TreeNode(id, label, List.of(),
                Objects.requireNonNull(supplier, "supplier"));
    }

    /// Whether a chevron is drawn — §3's "nodes that have **or may have**
    /// children".
    ///
    /// True for a lazy node whose supplier has never run, which is the whole
    /// point: the alternative is fetching everything to find out what to draw.
    /// A lazy node that turns out to be empty loses its chevron on the frame
    /// after it is opened, which is what every file manager does.
    public boolean mayHaveChildren() {
        return supplier != null || !children.isEmpty();
    }

    /// Whether this node fetches its children rather than carrying them.
    public boolean isLazy() {
        return supplier != null;
    }
}
