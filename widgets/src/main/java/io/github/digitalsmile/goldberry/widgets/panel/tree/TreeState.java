package io.github.digitalsmile.goldberry.widgets.panel.tree;

import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// What is open, and what a lazy node turned out to hold.
///
/// ## Both are keyed by node id, and §3 says why
///
/// "Expansion state is retained across rebuilds by node **id**, not by index — a
/// tree that collapsed itself when its model reordered would be the same defect
/// list keys exist to prevent." So this holds a set of ids rather than a set of
/// nodes or a parallel structure: a model rebuilt with its branches in a
/// different order, or with a node inserted at the top, leaves every open branch
/// open.
///
/// The fetched children are keyed the same way and for the same reason, with one
/// more: §3 asks for them to be fetched "when it first expands", and *first* is a
/// promise this map is what keeps. A node closed and reopened does not go back to
/// the supplier.
final class TreeState extends State<Tree> {

    /// The ids of every branch currently open.
    private final Set<String> expanded = new LinkedHashSet<>();

    /// What a lazy node's supplier answered, the one time it ran.
    private final Map<String, List<TreeNode>> fetched = new LinkedHashMap<>();

    /// The window, for `Left`'s move to the parent — captured in `build`, which
    /// is the only place a widget is handed one ([ADR-0140]).
    private io.github.digitalsmile.goldberry.Host host;

    @Override
    public Widget build(BuildContext context) {
        host = context.host().orElse(null);
        var tree = widget();
        var rows = new ArrayList<Widget>();
        // Depth first, which is the order the rows are read in and therefore the
        // order the keyboard moves through. A parent is followed by its children
        // rather than by its sibling, which is what makes `Right` on an open row
        // fall through to "the next row" and land on the first child.
        for (var root : tree.roots()) {
            flatten(root, 0, null, rows);
        }
        return new TreeBox(rows, tree.attributes());
    }

    /// Adds `node` and, if it is open, everything under it.
    ///
    /// @param parent the id of the row `Left` moves out to, or null at the top
    private void flatten(TreeNode node, int depth, String parent, List<Widget> rows) {
        var isOpen = expanded.contains(node.id());
        var selectable = !widget().leafOnly() || !node.mayHaveChildren();
        rows.add(new TreeRow(node, depth, isOpen, selectable,
                node.id().equals(widget().selected()),
                () -> toggle(node),
                () -> select(node),
                () -> moveOut(parent)));
        if (!isOpen) {
            return;
        }
        for (var child : childrenOf(node)) {
            flatten(child, depth + 1, node.id(), rows);
        }
    }

    /// A node's children: what it carries, or what its supplier answered once.
    private List<TreeNode> childrenOf(TreeNode node) {
        if (!node.isLazy()) {
            return node.children();
        }
        return fetched.getOrDefault(node.id(), List.of());
    }

    /// Opens or closes a branch, running a lazy node's supplier the first time.
    ///
    /// The fetch happens **here** rather than in `build`, which is the whole of
    /// "lazy": a build that fetched would fetch for every node on every frame,
    /// and a supplier that reads a disk or a network must run when the user asks
    /// and not when the frame does.
    private void toggle(TreeNode node) {
        if (!node.mayHaveChildren()) {
            return;
        }
        setState(() -> {
            if (!expanded.remove(node.id())) {
                expanded.add(node.id());
                if (node.isLazy() && !fetched.containsKey(node.id())) {
                    var children = node.supplier().get();
                    fetched.put(node.id(), List.copyOf(children == null ? List.of() : children));
                }
            }
        });
    }

    /// Asks for a node. It does **not** select it — the value is the
    /// application's ([ADR-0063]).
    private void select(TreeNode node) {
        var onSelect = widget().onSelect();
        if (onSelect != null) {
            onSelect.accept(node.id());
        }
    }

    /// §3's `Left` on a closed row: "moves to the parent".
    ///
    /// By **id**, through the host's focus-by-name, because a row cannot reach
    /// another row's element — the same door `dialog` opened and the same reason
    /// ([ADR-0176]). A root row has no parent and the key does nothing, which is
    /// what every tree does at the top level.
    private void moveOut(String parent) {
        if (parent == null || host == null) {
            return;
        }
        host.focus("tree-" + parent, true);
    }

    /// Whether a branch is open — for a test, and for nothing else.
    boolean isExpanded(String id) {
        return expanded.contains(id);
    }
}
