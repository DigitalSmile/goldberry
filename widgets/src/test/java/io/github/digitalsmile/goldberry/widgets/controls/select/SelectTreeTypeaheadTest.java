package io.github.digitalsmile.goldberry.widgets.controls.select;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.panel.tree.Tree;
import io.github.digitalsmile.goldberry.widgets.panel.tree.TreeNode;

/// A `select tree=#true`'s open list takes a typeahead: the tree's own, over its
/// visible rows, reached through the list rather than stopped by it ([ADR-0368]).
class SelectTreeTypeaheadTest {

    private static final List<TreeNode> REALMS = List.of(
            TreeNode.leaf("arnor", "Arnor"),
            TreeNode.of("gondor", "Gondor", TreeNode.leaf("minas-tirith", "Minas Tirith")),
            TreeNode.leaf("mordor", "Mordor"),
            TreeNode.leaf("rohan", "Rohan"));

    @Test
    @DisplayName("a letter reaching a row in the open list asks the host for the first visible match")
    void typesThroughTheList() {
        var host = new TestHost();
        var tree = new ElementTree(new SelectList(List.of(new Tree(REALMS, null, null))), host);
        var router = new PointerRouter();
        router.focusRoot(tree.root());
        router.moveFocus(1);
        host.forgetFocusRequests();

        router.textInput("m");

        assertEquals(List.of("tree-mordor"), host.focusRequests(), "Minas Tirith is inside a closed branch");
    }

    @Test
    @DisplayName("and the list does not swallow the letter on its way down")
    void listDoesNotCapture() {
        var list = new SelectList(List.of(new Tree(REALMS, null, null)));

        assertEquals(null, list.onTypeahead(), "a tree panel has no list-level typeahead to take the letters");
    }
}
