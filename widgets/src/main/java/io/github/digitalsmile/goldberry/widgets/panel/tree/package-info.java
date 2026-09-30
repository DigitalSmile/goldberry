/// `docs/core-widgets.md` §10's `tree` — a hierarchical list over a node model whose
/// children are fetched when a node first expands.
///
/// [io.github.digitalsmile.goldberry.widgets.panel.tree.Tree] flattens the visible
/// nodes into rows and keeps expansion by node id across rebuilds. Each
/// [io.github.digitalsmile.goldberry.widgets.panel.tree.TreeNode] is one node, and
/// [io.github.digitalsmile.goldberry.widgets.panel.tree.Checkable] says whether rows
/// carry a checkbox; selecting and checking are two separate values (ADR-0210). The
/// row, indent, chevron, check and label are parts.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.widgets.panel.tree;

import org.jspecify.annotations.NullMarked;
