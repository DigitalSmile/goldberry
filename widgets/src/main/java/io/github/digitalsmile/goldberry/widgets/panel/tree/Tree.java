package io.github.digitalsmile.goldberry.widgets.panel.tree;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.markup.Bound;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;
import io.github.digitalsmile.goldberry.widgets.panel.list.Selection;

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
/// It **reads** which nodes are selected and which are checked, and reports what
/// the user asked for; it changes neither itself ([ADR-0063]). Expansion is the
/// one exception and is not an exception at all: which branches are open is not a
/// value an application models, it is a view state belonging to the thing on
/// screen — the same distinction `scroll` draws for its offset.
///
/// ## What it is made of
///
/// ```
/// tree                 this node. Stateful, styles nothing, holds what is open
/// └── tree-row × n     one per *visible* node, flattened depth-first
///     ├── tree-indent  the gutter: depth × 20
///     ├── tree-chevron `>` or `v`, and empty on a leaf
///     ├── tree-check   the box, when `checkable` gives this row one
///     └── tree-label   the words
/// ```
///
/// Stateful and unstyled for [io.github.digitalsmile.goldberry.widgets.core.scroll.Scroll]'s
/// reason: a stateful widget that also carried the CSS type would put two `tree`
/// nodes in the cascade, one inside the other, and every rule would apply twice.
///
/// ## Selecting and checking are two different values
///
/// §3 asks for both and they are not two renderings of one thing: the selection
/// is where the reader *is* and the checks are what they have *marked*. A file
/// manager where those were the same could not copy six files, because opening
/// the seventh folder would clear the list
/// (ADR-0210).
/// So [#selected] and [#checked] are separate sets reported through separate
/// callbacks, and a tree may have either, both, or neither.
///
/// §3 gives `select tree=` `checkable="leaf|any"` and makes **leaf-only** the
/// default, "because 'Europe' is usually a heading and not an answer". That is a
/// third thing again — a rule about which rows are an *answer* — and it is
/// [#leafOnly].
///
/// @param roots      the top-level nodes
/// @param selected   the ids of the chosen nodes; empty for none
/// @param onSelect   the selection the user asked for, whole
/// @param selection  how many rows may be chosen at once
/// @param leafOnly   whether a node with children may be chosen — see above
/// @param checkable  whether the rows carry a checkbox, and what one means
/// @param checked    the ids of the ticked nodes; empty for none
/// @param onCheck    the checked set the user asked for, whole
/// @param attributes `id` and `class`, exactly as on the primitives
@Markup("tree")
public record Tree(
        List<TreeNode> roots,
        Set<String> selected,
        Consumer<Set<String>> onSelect,
        Selection selection,
        boolean leafOnly,
        Checkable checkable,
        Set<String> checked,
        Consumer<Set<String>> onCheck,
        Attributes attributes)
        implements Widget.Stateful, Attributed<Tree> {

    public Tree {
        roots = List.copyOf(roots == null ? List.of() : roots);
        // A LinkedHashSet copy rather than Set.copyOf, because the order a caller
        // gave is the order a diagnostic prints and the order a test asserts --
        // and Set.copyOf's is a hash order that changes between runs.
        selected = unmodifiableOrdered(selected);
        checked = unmodifiableOrdered(checked);
        selection = selection == null ? Selection.SINGLE : selection;
        checkable = checkable == null ? Checkable.NONE : checkable;
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    private static Set<String> unmodifiableOrdered(Set<String> values) {
        return values == null || values.isEmpty()
                ? Set.of()
                : java.util.Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }

    /// A single-selection, leaf-only tree with no checkboxes — §3's defaults, and
    /// the shape `select tree=` needs.
    ///
    /// **The single-selection form is a `String` and not a set of one**, because
    /// that is what a caller with one selection has: `select` holds a value, an
    /// application holds a field, and asking either to wrap it in a set to hand it
    /// over and unwrap it to read it back would be ceremony in the common case for
    /// the benefit of the rare one. What crosses inside is a set either way.
    public Tree(List<TreeNode> roots, String selected, Consumer<String> onSelect) {
        this(
                roots,
                selected == null ? Set.of() : Set.of(selected),
                onSelect == null
                        ? null
                        : chosen -> onSelect.accept(
                                chosen.isEmpty() ? null : chosen.iterator().next()),
                Selection.SINGLE,
                true,
                Checkable.NONE,
                Set.of(),
                null,
                Attributes.NONE);
    }

    /// The one chosen id, or null — the single-selection reading of [#selected].
    ///
    /// Null rather than empty for the same reason the constructor above takes a
    /// `String`: a caller in single-selection mode has a value or has none.
    public String selectedOne() {
        return selected.isEmpty() ? null : selected.iterator().next();
    }

    /// This tree letting a node with children be chosen — §3's `checkable="any"`,
    /// in the half of it that is a selection rule rather than a checkbox.
    public Tree anyNode(boolean value) {
        return new Tree(roots, selected, onSelect, selection, !value, checkable, checked, onCheck, attributes);
    }

    /// This tree with a different selection model — §3's "`list`'s selection
    /// models".
    public Tree selection(Selection value) {
        return new Tree(roots, selected, onSelect, value, leafOnly, checkable, checked, onCheck, attributes);
    }

    /// This tree reporting a **set** rather than one id, which is what
    /// [Selection#MULTIPLE] needs.
    ///
    /// Separate from [#selection] rather than taken with it, because the two are
    /// different facts — an application may hold a set and still show one at a
    /// time — and because a single method taking both would have to decide what a
    /// null callback means.
    public Tree onSelect(Consumer<Set<String>> value) {
        return new Tree(roots, selected, value, selection, leafOnly, checkable, checked, onCheck, attributes);
    }

    /// This tree with `values` selected.
    public Tree selected(Set<String> values) {
        return new Tree(roots, values, onSelect, selection, leafOnly, checkable, checked, onCheck, attributes);
    }

    /// This tree with a checkbox on its rows — §3's `checkable=`.
    public Tree checkable(Checkable value) {
        return new Tree(roots, selected, onSelect, selection, leafOnly, value, checked, onCheck, attributes);
    }

    /// This tree with `values` ticked, and `onCheck` told what the user asked for.
    ///
    /// The two together, because a checkbox nobody is listening to is a control
    /// that cannot change and a listener with no value has nothing to draw —
    /// ADR-0063's loop needs both ends or neither.
    public Tree checked(Set<String> values, Consumer<Set<String>> onCheck) {
        return new Tree(roots, selected, onSelect, selection, leafOnly, checkable, values, onCheck, attributes);
    }

    @Override
    public Tree withAttributes(Attributes value) {
        return new Tree(roots, selected, onSelect, selection, leafOnly, checkable, checked, onCheck, value);
    }

    @Override
    public Object key() {
        return attributes.key();
    }

    @Override
    public State<?> createState() {
        return new TreeState();
    }

    /// Builds a `tree` from markup: a [Bound] over the `Tree` a model's `bind=`
    /// value holds, since a node's children are suppliers a document cannot write
    /// (ADR-0367).
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Bound(wiring.bound(node), Tree.class, Attributes.of(node));
    }
}
