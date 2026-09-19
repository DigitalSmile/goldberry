package io.github.digitalsmile.goldberry.widgets.panel.tree;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.panel.list.Selection;

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

    /// How long a typeahead lasts before the next letter starts a new one.
    ///
    /// A second, which is `select`'s figure and the interval every desktop list
    /// uses ([ADR-0141]): long enough to type "no" and reach Norway rather than
    /// Oman, short enough that coming back a moment later starts again.
    private static final long TYPEAHEAD_MILLIS = 1000;

    /// The typeahead so far, and when it was last added to.
    private String typed = "";
    private long typedAt;

    /// The rows the last build flattened, in the order the keyboard walks them.
    ///
    /// **Read only by the handlers**, never by a build, which is what makes it
    /// safe to be a field: `Home`, `End` and the typeahead all need to know about
    /// rows the focused one cannot see, and by the time one of them runs this is
    /// the list that is on screen ([ADR-0209]).
    private final List<TreeNode> visible = new ArrayList<>();

    /// The parent of each visible node, by id — for `*`, which needs the
    /// siblings of a row and therefore the row's parent.
    private final Map<String, String> parents = new LinkedHashMap<>();

    @Override
    public Widget build(BuildContext context) {
        host = context.host().orElse(null);
        var tree = widget();
        var rows = new ArrayList<Widget>();
        visible.clear();
        parents.clear();
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
        // **Nothing is an answer in a `NONE` tree**, which is a rule about the
        // selection and not about the node — so it is asked here rather than
        // folded into `leafOnly`, which is a rule about the node.
        var selectable = widget().selection() != Selection.NONE && (!widget().leafOnly() || !node.mayHaveChildren());
        visible.add(node);
        if (parent != null) {
            parents.put(node.id(), parent);
        }
        rows.add(new TreeRow(
                node,
                depth,
                isOpen,
                selectable,
                widget().selected().contains(node.id()),
                checkStateOf(node),
                () -> toggle(node),
                modifiers -> select(node, modifiers),
                () -> moveOut(parent),
                this::moveToEnd,
                () -> expandSiblings(node),
                text -> typeahead(node, text),
                () -> check(node)));
        if (!isOpen) {
            return;
        }
        for (var child : childrenOf(node)) {
            flatten(child, depth + 1, node.id(), rows);
        }
    }

    /// §3's `Home` and `End`: the first and last **visible** rows.
    ///
    /// Visible in the tree's sense — the flattened list — rather than the
    /// viewport's, which is what every tree means by it and what `Ctrl+End` means
    /// in every document.
    private void moveToEnd(int direction) {
        if (visible.isEmpty()) {
            return;
        }
        focusRow(direction < 0 ? visible.getFirst() : visible.getLast());
    }

    /// §3's `*`: "expands every sibling".
    ///
    /// **Every sibling and not every descendant**, which is the reading that
    /// makes it useful on a big tree: `*` on a folder opens that whole level so
    /// the reader can see across it, and a key that opened everything underneath
    /// would be a key that hangs a lazy tree by fetching its entire model.
    ///
    /// A lazy sibling's supplier runs here, exactly as it does for one opened by
    /// hand — which is the one place this key costs anything, and the one place it
    /// is doing the work the user asked for.
    private void expandSiblings(TreeNode node) {
        var siblings = siblingsOf(node);
        if (siblings.isEmpty()) {
            return;
        }
        setState(() -> {
            for (var sibling : siblings) {
                if (!sibling.mayHaveChildren() || expanded.contains(sibling.id())) {
                    continue;
                }
                expanded.add(sibling.id());
                if (sibling.isLazy() && !fetched.containsKey(sibling.id())) {
                    var children = sibling.supplier().get();
                    fetched.put(sibling.id(), List.copyOf(children == null ? List.of() : children));
                }
            }
        });
    }

    /// The nodes at `node`'s own level — its parent's children, or the roots.
    private List<TreeNode> siblingsOf(TreeNode node) {
        var parent = parents.get(node.id());
        if (parent == null) {
            return widget().roots();
        }
        for (var candidate : visible) {
            if (candidate.id().equals(parent)) {
                return childrenOf(candidate);
            }
        }
        return List.of();
    }

    /// §3's type-to-select: "matches across **visible rows only**".
    ///
    /// Which is the rule that makes it cheap and the rule that makes it honest —
    /// a search that opened branches to find a match would be a search, and a
    /// tree with a lazy model cannot have one without fetching everything.
    ///
    /// The three cases are `select`'s, and the middle one is why this is not a
    /// string concatenation: the **same letter again** asks for the next row
    /// beginning with it rather than searching for "dd". Wrapping, because the
    /// rows are a cycle to a reader pressing one key repeatedly.
    ///
    /// It moves the **focus** and does not select. A tree reports what the user
    /// asked for and selects nothing itself ([ADR-0063]), and typing is a way of
    /// getting somewhere rather than a way of choosing — `Enter` is still what
    /// chooses, which is the same split `select`'s open list draws.
    private void typeahead(TreeNode from, String text) {
        if (visible.isEmpty()) {
            return;
        }
        var now = now();
        var stale = now - typedAt > TYPEAHEAD_MILLIS;
        typedAt = now;
        if (stale || (text.length() == 1 && typed.equals(text))) {
            typed = text;
        } else {
            typed = typed + text;
        }

        // **By id, not by `indexOf`.** A `TreeNode` is a record, so equality is
        // over every component — and the id is the whole model here (ADR-0184),
        // which is what the rest of this class already keys on.
        var here = indexOf(from.id());
        // A repeated single letter steps on from where the focus is; a longer
        // prefix starts from the current row, because "no" then "nor" must not
        // skip Norway for having matched it once already.
        var start = typed.length() == 1 ? here + 1 : Math.max(0, here);
        var match = matching(start);
        if (match == null && start > 0) {
            match = matching(0);
        }
        if (match != null && !match.id().equals(from.id())) {
            focusRow(match);
        }
    }

    /// Where a node sits in the flattened list, or -1.
    private int indexOf(String id) {
        for (var i = 0; i < visible.size(); i++) {
            if (visible.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /// The first visible row from `start` onwards whose label begins with what
    /// has been typed, or null.
    private TreeNode matching(int start) {
        var wanted = typed.toLowerCase(java.util.Locale.ROOT);
        for (var i = Math.max(0, start); i < visible.size(); i++) {
            var node = visible.get(i);
            if (node.label().toLowerCase(java.util.Locale.ROOT).startsWith(wanted)) {
                return node;
            }
        }
        return null;
    }

    /// Puts the keyboard on a row, by id.
    ///
    /// Through the host's focus-by-name for [#moveOut]'s reason: a row cannot
    /// reach another row's element, and an id is the one handle both ends agree
    /// on ([ADR-0176]).
    ///
    /// **Unprefixed, and two trees no longer collide anyway.** A `list` prefixes
    /// its rows with its own id and a tree never did, so two trees sharing a node
    /// id answered each other's keys whether or not either was named. The router
    /// resolves a name inside the composite the keyboard is in before the window,
    /// and a tree is a composite — which settles the named case and the unnamed
    /// one together, and is why nothing here had to change ([ADR-0437]).
    private void focusRow(TreeNode node) {
        if (host != null) {
            host.focus("tree-" + node.id(), true);
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

    /// The row a `Shift` range runs **from** — the last one chosen without it.
    ///
    /// State, because it is exactly what a modifier cannot carry: `Shift` says
    /// "through to here" and the *here it started from* is a fact about what the
    /// user did two gestures ago. Kept as an id for [#moveOut]'s reason, and
    /// allowed to go stale — a row that has left the tree simply fails to be
    /// found and the range starts at the pressed row, which is what a reader who
    /// has re-sorted the model underneath their own selection means anyway.
    private String anchor;

    /// Asks for a selection. It does **not** select — the value is the
    /// application's ([ADR-0063]).
    ///
    /// **What goes out is the whole set**, even in single-selection mode where it
    /// always holds one. A `Shift` range is computed over the flattened rows,
    /// which only this class can see, so an id on its own would be an answer the
    /// application could not turn back into a selection (ADR-0210). The
    /// three-argument [Tree] constructor unwraps it again for the callers that
    /// have one value.
    private void select(TreeNode node, io.github.digitalsmile.goldberry.input.key.Modifiers modifiers) {

        var tree = widget();
        if (tree.onSelect() == null || tree.selection() == Selection.NONE) {
            return;
        }
        if (tree.selection() == Selection.SINGLE) {
            // Modifiers are ignored rather than refused: `Ctrl` and `Shift` mean
            // "and also" and "through to", and a control holding one row has
            // nothing to say to either. Reporting the row is the whole answer.
            anchor = node.id();
            tree.onSelect().accept(Set.of(node.id()));
            return;
        }
        var next = new LinkedHashSet<String>();
        if (modifiers.shift()) {
            // **Replaces rather than adds.** A shifted press is "the range from
            // there to here", and a reader who over-shoots presses `Shift` again
            // one row back and gets the range they meant -- which is only true if
            // the second press is not adding to the first. The anchor is
            // deliberately not moved, so a run of them sweeps from one end.
            next.addAll(rangeTo(node));
        } else if (modifiers.control()) {
            // The only way to take a row *out* of a selection, which is why the
            // toggle lives on `Ctrl` and not on a plain press.
            next.addAll(tree.selected());
            if (!next.remove(node.id())) {
                next.add(node.id());
            }
            anchor = node.id();
        } else {
            next.add(node.id());
            anchor = node.id();
        }
        tree.onSelect().accept(java.util.Collections.unmodifiableSet(next));
    }

    /// Every visible row from the anchor through `node`, in the order they are on
    /// screen.
    ///
    /// Over the **flattened visible list**, which is what a reader sweeping a
    /// range means: the rows between the two on screen, not the nodes between
    /// them in a model whose closed branches nobody can see.
    private List<String> rangeTo(TreeNode node) {
        var to = indexOf(node.id());
        var from = anchor == null ? to : indexOf(anchor);
        if (to < 0) {
            return List.of();
        }
        if (from < 0) {
            // The anchor has left the tree -- a branch closed under it, or the
            // model dropped it. The pressed row is the range.
            from = to;
        }
        var out = new ArrayList<String>(Math.abs(to - from) + 1);
        for (var i = Math.min(from, to); i <= Math.max(from, to); i++) {
            out.add(visible.get(i).id());
        }
        return out;
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

    // --- §3's `checkable` ---------------------------------------------------

    /// The state of `node`'s checkbox, or null when this tree gives it none.
    ///
    /// Null and not `UNCHECKED`: "there is no box here" and "the box is empty"
    /// are different rows, and a row that drew an empty box where §3 asked for
    /// none would put a control on every heading in a `LEAF` tree.
    private io.github.digitalsmile.goldberry.widgets.controls.checkbox.Checkbox.Value checkStateOf(TreeNode node) {

        return switch (widget().checkable()) {
            case NONE -> null;
            case LEAF -> node.mayHaveChildren() ? null : membership(node);
            case ANY -> membership(node);
            case CASCADE -> cascadeState(node);
        };
    }

    /// The plain reading: is this id in the set the application handed down.
    private io.github.digitalsmile.goldberry.widgets.controls.checkbox.Checkbox.Value membership(TreeNode node) {

        return io.github.digitalsmile.goldberry.widgets.controls.checkbox.Checkbox.Value.of(
                widget().checked().contains(node.id()));
    }

    /// §3's "propagates down and shows `indeterminate` upward".
    ///
    /// **Derived rather than stored.** A parent's box is a statement about what is
    /// under it, so computing it from the children is the only way it cannot drift
    /// out of step with them — a stored parent bit would go stale the moment one
    /// child was unticked, and the resulting row would claim "all of these" while
    /// showing one that is not.
    ///
    /// A node whose children are **not known** — a lazy branch nobody has opened —
    /// has nothing to derive from and reads its own membership. That is the only
    /// answer available without fetching a model the user has not asked for, and
    /// it is the honest one: what was ticked was the branch.
    private io.github.digitalsmile.goldberry.widgets.controls.checkbox.Checkbox.Value cascadeState(TreeNode node) {

        var children = knownChildrenOf(node);
        if (children.isEmpty()) {
            return membership(node);
        }
        var all = true;
        var none = true;
        for (var child : children) {
            var state = cascadeState(child);
            all &= state == io.github.digitalsmile.goldberry.widgets.controls.checkbox.Checkbox.Value.CHECKED;
            none &= state == io.github.digitalsmile.goldberry.widgets.controls.checkbox.Checkbox.Value.UNCHECKED;
            if (!all && !none) {
                // Mixed already, and nothing further down can change that.
                return io.github.digitalsmile.goldberry.widgets.controls.checkbox.Checkbox.Value.MIXED;
            }
        }
        return all
                ? io.github.digitalsmile.goldberry.widgets.controls.checkbox.Checkbox.Value.CHECKED
                : io.github.digitalsmile.goldberry.widgets.controls.checkbox.Checkbox.Value.UNCHECKED;
    }

    /// A node's children **as far as anybody knows** — what it carries, or what
    /// its supplier has already answered, and empty for a lazy node nobody has
    /// opened.
    ///
    /// Different from [#childrenOf] in exactly one case and it is the case that
    /// matters: `childrenOf` returns empty for an unfetched lazy node too, but a
    /// caller reading it cannot tell that from a branch that is genuinely empty.
    /// This one is named for what it means so that [#cascadeState]'s fallback
    /// reads as a decision rather than as an accident.
    private List<TreeNode> knownChildrenOf(TreeNode node) {
        if (!node.isLazy()) {
            return node.children();
        }
        return fetched.getOrDefault(node.id(), List.of());
    }

    /// Asks for a different set of ticks. It does **not** tick — the set is the
    /// application's, exactly as the selection is ([ADR-0063]).
    ///
    /// The mixed state goes to **checked**, not to unchecked: a reader clicking a
    /// partially ticked folder is asking for all of it, which is the only reading
    /// of that click that is ever what was meant. `Checkbox.Value.toggled()` has
    /// said so since it shipped and this is its second caller.
    private void check(@Nullable TreeNode node) {
        var tree = widget();
        var state = checkStateOf(node);
        if (state == null || tree.onCheck() == null) {
            return;
        }
        var wanted =
                state.toggled() == io.github.digitalsmile.goldberry.widgets.controls.checkbox.Checkbox.Value.CHECKED;
        var next = new LinkedHashSet<>(tree.checked());
        if (tree.checkable() == Checkable.CASCADE) {
            // **Down through everything known.** The parent goes in too, so a
            // branch whose children arrive later is still ticked -- and so that
            // the set an application reads names the folder the user actually
            // clicked rather than only its contents.
            applyDeep(node, wanted, next);
        } else if (wanted) {
            next.add(node.id());
        } else {
            next.remove(node.id());
        }
        tree.onCheck().accept(java.util.Collections.unmodifiableSet(next));
    }

    /// Adds or removes `node` and every descendant anybody knows about.
    private void applyDeep(TreeNode node, boolean wanted, java.util.Set<String> into) {
        if (wanted) {
            into.add(node.id());
        } else {
            into.remove(node.id());
        }
        for (var child : knownChildrenOf(node)) {
            applyDeep(child, wanted, into);
        }
    }

    /// How long ago the previous keystroke arrived, on the window's clock.
    ///
    /// The **host's** and not `System.nanoTime()`, which is what this used to
    /// read. A typeahead measured against the real clock is the one behaviour in
    /// this catalog a test cannot drive: asserting that a gap longer than the
    /// window starts a fresh search meant sleeping for it and hoping, so nothing
    /// asserted it. Against a `Clock.virtual()` it is `advance(600)` and a
    /// keystroke (`docs/testing.md` §0.1).
    ///
    /// Falls back to the system clock when there is no host, which is a widget
    /// built and driven outside a window. Typeahead still works there; it is
    /// simply not drivable, and there is nothing else to read.
    private long now() {
        return (long)
                (host == null
                        ? io.github.digitalsmile.goldberry.motion.Clock.system().nowMillis()
                        : host.clock().nowMillis());
    }
}
