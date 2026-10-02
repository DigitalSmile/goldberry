/// The `tree`: a hierarchical list over a node model whose children are fetched
/// when a node first expands.
///
/// [dev.goldberry.widgets.panel.tree.Tree] flattens the visible
/// nodes into rows and keeps expansion by node id across rebuilds. Each
/// [dev.goldberry.widgets.panel.tree.TreeNode] is one node, and
/// [dev.goldberry.widgets.panel.tree.Checkable] says whether rows
/// carry a checkbox; selecting and checking are two separate values. The
/// row, indent, chevron, check and label are parts.
///
/// Every reference is non-null unless it says `@Nullable`.
///
/// Read more: [Collections](https://goldberry.dev/docs/components/collections.html#tree).
@NullMarked
package dev.goldberry.widgets.panel.tree;

import org.jspecify.annotations.NullMarked;
