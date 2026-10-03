package dev.goldberry.example.ui.collections;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.panel.list.Selection;
import dev.goldberry.widgets.panel.tree.Checkable;
import dev.goldberry.widgets.panel.tree.Tree;
import dev.goldberry.widgets.panel.tree.TreeNode;
import dev.goldberry.widgets.text.Text;

/// A `tree` with cascading checkboxes, and a branch that fetches its children the
/// first time it opens.
///
/// Read more: [`tree`](https://goldberry.dev/docs/components/collections.html#tree).
record RealmsCard() implements Widget.Stateful {

    /// Deep enough that a tri-state box has something to say: a branch with
    /// branches under it.
    static final List<TreeNode> LANDS = List.of(
            TreeNode.of(
                    "eriador",
                    "Eriador",
                    TreeNode.of(
                            "shire",
                            "The Shire",
                            TreeNode.leaf("hobbiton", "Hobbiton"),
                            TreeNode.leaf("buckland", "Buckland"),
                            TreeNode.leaf("tuckborough", "Tuckborough")),
                    TreeNode.of(
                            "angle",
                            "The Angle",
                            TreeNode.leaf("bree", "Bree"),
                            TreeNode.leaf("weathertop", "Weathertop"))),
            TreeNode.of(
                    "wilderland",
                    "Wilderland",
                    TreeNode.leaf("lorien", "Lothlórien"),
                    TreeNode.leaf("fangorn", "Fangorn"),
                    TreeNode.lazy(
                            "erebor",
                            "Erebor",
                            () -> List.of(TreeNode.leaf("dale", "Dale"), TreeNode.leaf("esgaroth", "Esgaroth")))),
            TreeNode.of(
                    "south",
                    "The South Kingdoms",
                    TreeNode.leaf("edoras", "Edoras"),
                    TreeNode.leaf("minas-tirith", "Minas Tirith")));

    private static final ShowcaseCard CARD = new ShowcaseCard(
            "lands-card",
            "Rows with rows under them",
            "A tree checks and selects separately: tick a branch and its children follow. Right and Left open"
                    + " and close, Space ticks. Erebor fetches its children the first time it opens.",
            DocLink.to("components/collections", "tree"));

    @Override
    public State<?> createState() {
        return new RealmsState();
    }

    /// The selected and the checked ids, two sets the tree reports separately.
    static final class RealmsState extends State<RealmsCard> {

        private Set<String> selected = Set.of("hobbiton");

        /// Insertion-ordered rather than `Set.of`, whose iteration order changes
        /// between runs and would change the caption with it.
        private Set<String> checked = new LinkedHashSet<>(List.of("buckland", "weathertop"));

        private void select(Set<String> values) {
            setState(() -> selected = values);
        }

        private void check(Set<String> values) {
            setState(() -> checked = values);
        }

        @Override
        public Widget build(BuildContext context) {
            return CARD.of(
                    new Tree(
                            LANDS,
                            selected,
                            this::select,
                            Selection.SINGLE,
                            false,
                            Checkable.CASCADE,
                            checked,
                            this::check,
                            Attributes.NONE.id("lands")),
                    new Text(
                            checked.isEmpty()
                                    ? "Nothing ticked"
                                    : "Ticked " + checked.size() + ": " + String.join(", ", checked),
                            Attributes.NONE.id("lands-ticked").classes("caption")));
        }
    }
}
