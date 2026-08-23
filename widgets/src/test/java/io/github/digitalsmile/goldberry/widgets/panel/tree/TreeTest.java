package io.github.digitalsmile.goldberry.widgets.panel.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.panel.Described;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// `tree` — `docs/core-widgets.md` §3's hierarchical list ([ADR-0184]).
///
/// What is here is the **model over time**: what is open, what a lazy node
/// fetched, and what the keyboard does to both. The drawing is `TreeGoldenTest`'s.
class TreeTest {

    private final TestHost host = new TestHost();
    private final List<String> chosen = new ArrayList<>();

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    /// `europe( no se )  asia( jp )`
    private static List<TreeNode> world() {
        return List.of(
                TreeNode.of("europe", "Europe",
                        TreeNode.leaf("no", "Norway"),
                        TreeNode.leaf("se", "Sweden")),
                TreeNode.of("asia", "Asia",
                        TreeNode.leaf("jp", "Japan")));
    }

    private ElementTree tree(Tree widget) {
        return new ElementTree(widget, host);
    }

    private ElementTree world(String selected) {
        return tree(new Tree(world(), selected, chosen::add));
    }

    /// The rows that are actually visible, top to bottom.
    private static List<String> rows(ElementTree tree) {
        return Described.of(tree, TreeRow.class).stream()
                .map(row -> row.node().id()).toList();
    }

    private static TreeRow row(ElementTree tree, String id) {
        return Described.of(tree, TreeRow.class).stream()
                .filter(r -> r.node().id().equals(id))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "no row for \"" + id + "\"; showing " + rows(tree)));
    }

    private static void press(TreeRow row, Key key) {
        row.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, key, Modifiers.NONE, false, null));
    }

    @Nested
    @DisplayName("what is showing")
    class Visible {

        @Test
        @DisplayName("only the roots, until something is opened")
        void closedByDefault() {
            assertEquals(List.of("europe", "asia"), rows(world(null)));
        }

        @Test
        @DisplayName("opening a branch shows its children under it, in order")
        void openingShowsChildren() {
            var tree = world(null);

            press(row(tree, "europe"), Key.RIGHT);
            tree.flush();

            assertEquals(List.of("europe", "no", "se", "asia"), rows(tree),
                    "the children go under their parent, not at the end");
        }

        @Test
        @DisplayName("a chevron is drawn on a node that has children and not on a leaf")
        void chevrons() {
            var tree = world(null);
            press(row(tree, "europe"), Key.RIGHT);
            tree.flush();

            assertTrue(row(tree, "europe").node().mayHaveChildren());
            assertFalse(row(tree, "no").node().mayHaveChildren());
        }

        @Test
        @DisplayName("the indent is the depth, so a child sits one level in")
        void indent() {
            var tree = world(null);
            press(row(tree, "europe"), Key.RIGHT);
            tree.flush();

            assertEquals(0, row(tree, "europe").depth());
            assertEquals(1, row(tree, "no").depth());
        }
    }

    @Nested
    @DisplayName("the keyboard §3 says has to be right")
    class Keyboard {

        @Test
        @DisplayName("Right opens a closed branch")
        void rightOpens() {
            var tree = world(null);

            press(row(tree, "europe"), Key.RIGHT);
            tree.flush();

            assertTrue(row(tree, "europe").expanded());
        }

        /// §3: "`Right` … moves to the first child" once it is open. The first
        /// child is literally the next row, because the rows are flattened depth
        /// first — so the key falls through to the scope rather than being
        /// handled twice.
        @Test
        @DisplayName("Right on an open branch leaves the move to the scope")
        void rightOnOpenFallsThrough() {
            var tree = world(null);
            press(row(tree, "europe"), Key.RIGHT);
            tree.flush();

            var event = new KeyEvent(KeyEvent.Kind.PRESSED, Key.RIGHT, Modifiers.NONE, false, null);
            row(tree, "europe").onKey(event);

            assertFalse(event.isConsumed(),
                    "the row swallowed the arrow that was going to move to the child");
            assertEquals(List.of("europe", "no", "se", "asia"), rows(tree),
                    "and it must not have closed it either");
        }

        @Test
        @DisplayName("Left closes an open branch")
        void leftCloses() {
            var tree = world(null);
            press(row(tree, "europe"), Key.RIGHT);
            tree.flush();

            press(row(tree, "europe"), Key.LEFT);
            tree.flush();

            assertEquals(List.of("europe", "asia"), rows(tree));
        }

        /// §3: "`Left` … moves to the parent" when there is nothing to close. By
        /// **id** through the host, because a row cannot reach another row's
        /// element.
        @Test
        @DisplayName("Left on a closed child asks for the parent by name")
        void leftOnLeafMovesOut() {
            var tree = world(null);
            press(row(tree, "europe"), Key.RIGHT);
            tree.flush();

            press(row(tree, "no"), Key.LEFT);

            assertEquals(List.of("tree-europe"), host.focusRequests(),
                    "it did not ask to move to the parent");
        }

        @Test
        @DisplayName("Left at the top does nothing rather than throwing")
        void leftAtTheTop() {
            var tree = world(null);

            press(row(tree, "europe"), Key.LEFT);
            tree.flush();

            assertEquals(List.of("europe", "asia"), rows(tree));
            assertEquals(List.of(), host.focusRequests());
        }
    }

    @Nested
    @DisplayName("choosing a node")
    class Choosing {

        /// §3's default, and its reason: "'Europe' is usually a heading and not
        /// an answer".
        @Test
        @DisplayName("a leaf is an answer and a parent is not")
        void leafOnly() {
            var tree = world(null);
            press(row(tree, "europe"), Key.RIGHT);
            tree.flush();

            assertFalse(row(tree, "europe").selectable());
            assertTrue(row(tree, "no").selectable());

            press(row(tree, "europe"), Key.ENTER);
            press(row(tree, "no"), Key.ENTER);

            assertEquals(List.of("no"), chosen, "a heading was taken for an answer");
        }

        @Test
        @DisplayName("checkable any makes a parent an answer too")
        void anyNode() {
            var tree = tree(new Tree(world(), null, chosen::add).anyNode(true));

            assertTrue(row(tree, "europe").selectable());
            press(row(tree, "europe"), Key.ENTER);

            assertEquals(List.of("europe"), chosen);
        }

        /// Controlled, like every other value in this toolkit: it reports and
        /// changes nothing itself.
        @Test
        @DisplayName("it reports and selects nothing itself")
        void controlled() {
            var tree = world(null);
            press(row(tree, "europe"), Key.RIGHT);
            tree.flush();

            press(row(tree, "no"), Key.ENTER);
            tree.flush();

            assertEquals(List.of("no"), chosen);
            assertFalse(row(tree, "no").selected(),
                    "the tree selected a value the application never accepted");
        }

        @Test
        @DisplayName("the selected row is the one the model names")
        void selectedFollowsTheModel() {
            var tree = world("no");
            press(row(tree, "europe"), Key.RIGHT);
            tree.flush();

            assertTrue(row(tree, "no").selected());
            assertFalse(row(tree, "se").selected());
        }
    }

    @Nested
    @DisplayName("lazy children")
    class Lazy {

        @Test
        @DisplayName("a supplier runs when the node first opens, and never again")
        void fetchedOnce() {
            var runs = new int[1];
            var tree = tree(new Tree(List.of(TreeNode.lazy("root", "Root", () -> {
                runs[0]++;
                return List.of(TreeNode.leaf("a", "A"));
            })), null, chosen::add));

            assertEquals(0, runs[0], "it fetched before anybody asked");
            assertEquals(List.of("root"), rows(tree));

            press(row(tree, "root"), Key.RIGHT);
            tree.flush();
            assertEquals(1, runs[0]);
            assertEquals(List.of("root", "a"), rows(tree));

            // Closed and opened again: *first* is a promise the cache keeps.
            press(row(tree, "root"), Key.LEFT);
            tree.flush();
            press(row(tree, "root"), Key.RIGHT);
            tree.flush();

            assertEquals(1, runs[0], "it went back to the supplier");
        }

        /// The reason `mayHaveChildren` exists apart from `children`: a directory
        /// tree would have to stat the whole disk to draw its first row.
        @Test
        @DisplayName("a lazy node draws a chevron before anyone knows what is in it")
        void chevronBeforeTheFetch() {
            var tree = tree(new Tree(
                    List.of(TreeNode.lazy("root", "Root", List::of)), null, chosen::add));

            assertTrue(row(tree, "root").node().mayHaveChildren());
        }
    }

    @Nested
    @DisplayName("the model")
    class Model {

        /// §3: expansion is retained "by node **id**, not by index — a tree that
        /// collapsed itself when its model reordered would be the same defect
        /// list keys exist to prevent".
        @Test
        @DisplayName("a branch stays open when the model reorders under it")
        void expansionSurvivesAReorder() {
            var tree = world(null);
            press(row(tree, "europe"), Key.RIGHT);
            tree.flush();
            assertEquals(List.of("europe", "no", "se", "asia"), rows(tree));

            // The same nodes, the other way round -- an application re-sorting.
            var reordered = List.of(world().get(1), world().getFirst());
            tree.update(new Tree(reordered, null, chosen::add));
            tree.flush();

            assertEquals(List.of("asia", "europe", "no", "se"), rows(tree),
                    "Europe closed itself because its index moved");
        }

        @Test
        @DisplayName("a node with no id cannot be built, because expansion is by id")
        void idIsRequired() {
            assertThrows(NullPointerException.class, () -> TreeNode.leaf(null, "x"));
            assertThrows(IllegalArgumentException.class, () -> TreeNode.leaf("  ", "x"));
        }
    }
}
