package io.github.digitalsmile.goldberry.widgets.panel.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.controls.checkbox.Checkbox;
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

    /// §3's three remaining keys, which ADR-0184 shipped without and ADR-0209
    /// finished. All three need to know about rows the focused one cannot see, so
    /// all three are the tree's rather than the row's.
    @Nested
    @DisplayName("the rest of §3's keyboard")
    class RestOfTheKeyboard {

        private static void type(TreeRow row, String text) {
            row.onText(new io.github.digitalsmile.goldberry.input.event.TextEvent(text, null));
        }

        @Test
        @DisplayName("Home and End go to the first and last VISIBLE rows")
        void homeAndEnd() {
            var tree = world(null);
            press(row(tree, "europe"), Key.RIGHT);
            tree.flush();
            // europe, no, se, asia
            press(row(tree, "se"), Key.END);
            assertEquals(List.of("tree-asia"), host.focusRequests(),
                    "End went somewhere other than the last row on screen");

            host.forgetFocusRequests();
            press(row(tree, "se"), Key.HOME);
            assertEquals(List.of("tree-europe"), host.focusRequests());
        }

        /// The distinction that matters: `End` is the last row **showing**, not
        /// the last node in the model. `Japan` exists the whole time and is not
        /// where `End` goes until `Asia` is open.
        @Test
        @DisplayName("End follows what is open, not what the model holds")
        void endIsAboutVisibility() {
            var tree = world(null);

            press(row(tree, "europe"), Key.END);
            assertEquals(List.of("tree-asia"), host.focusRequests(),
                    "with everything closed the last visible row is Asia");

            host.forgetFocusRequests();
            press(row(tree, "asia"), Key.RIGHT);
            tree.flush();
            press(row(tree, "asia"), Key.END);

            assertEquals(List.of("tree-jp"), host.focusRequests(),
                    "and once Asia is open it is Japan");
        }

        /// §3's `*`: "expands every sibling". A character rather than a key,
        /// because the key `*` sits on differs by layout.
        @Test
        @DisplayName("* opens every sibling at that level")
        void starOpensSiblings() {
            var tree = world(null);

            type(row(tree, "europe"), "*");
            tree.flush();

            assertEquals(List.of("europe", "no", "se", "asia", "jp"), rows(tree),
                    "both roots should have opened, not just the one that was pressed");
        }

        /// **Siblings, not descendants**, which is the reading that keeps it from
        /// hanging a lazy tree by fetching the whole model.
        @Test
        @DisplayName("* does not open the grandchildren")
        void starIsOneLevel() {
            var nested = List.of(TreeNode.of("a", "A",
                    TreeNode.of("b", "B", TreeNode.leaf("c", "C"))));
            var tree = tree(new Tree(nested, null, chosen::add));

            type(row(tree, "a"), "*");
            tree.flush();

            assertEquals(List.of("a", "b"), rows(tree),
                    "C is a grandchild and * is one level");
        }

        @Test
        @DisplayName("a lazy sibling opened by * fetches, exactly as one opened by hand does")
        void starFetches() {
            var runs = new int[1];
            var nodes = List.of(
                    TreeNode.lazy("one", "One", () -> {
                        runs[0]++;
                        return List.of(TreeNode.leaf("x", "X"));
                    }),
                    TreeNode.leaf("two", "Two"));
            var tree = tree(new Tree(nodes, null, chosen::add));

            type(row(tree, "two"), "*");
            tree.flush();

            assertEquals(1, runs[0]);
            assertEquals(List.of("one", "x", "two"), rows(tree));
        }

        /// §3: type-to-select "matches across visible rows only".
        @Test
        @DisplayName("typing moves the focus to the next row that starts with it")
        void typeToSelect() {
            var tree = world(null);
            press(row(tree, "europe"), Key.RIGHT);
            tree.flush();

            type(row(tree, "europe"), "s");

            assertEquals(List.of("tree-se"), host.focusRequests(), "Sweden starts with s");
        }

        @Test
        @DisplayName("it does not reach into a branch that is closed")
        void typeToSelectIsVisibleOnly() {
            var tree = world(null);

            // Sweden is in the model and is not on screen. A search that opened
            // Europe to find it would be a search, and a lazy tree cannot have
            // one without fetching everything.
            type(row(tree, "europe"), "s");

            assertEquals(List.of(), host.focusRequests());
        }

        /// The same letter again is "the next one", not a search for "ss" — which
        /// is `select`'s rule and every desktop list's.
        @Test
        @DisplayName("the same letter twice steps on rather than searching for a double")
        void repeatedLetterCycles() {
            var nodes = List.of(
                    TreeNode.leaf("s1", "Sweden"),
                    TreeNode.leaf("s2", "Spain"),
                    TreeNode.leaf("n1", "Norway"));
            var tree = tree(new Tree(nodes, null, chosen::add));

            type(row(tree, "s1"), "s");
            assertEquals(List.of("tree-s2"), host.focusRequests(), "from Sweden to Spain");

            host.forgetFocusRequests();
            type(row(tree, "s2"), "s");
            assertEquals(List.of("tree-s1"), host.focusRequests(), "and round again");
        }

        @Test
        @DisplayName("a longer prefix narrows rather than stepping on")
        void aPrefixNarrows() {
            var nodes = List.of(
                    TreeNode.leaf("no", "Norway"),
                    TreeNode.leaf("nl", "Netherlands"));
            var tree = tree(new Tree(nodes, null, chosen::add));

            type(row(tree, "no"), "n");
            assertEquals(List.of("tree-nl"), host.focusRequests(), "n steps on to the next n");

            host.forgetFocusRequests();
            // "ne" is a prefix rather than a repeat, so it matches from where the
            // focus is -- and Netherlands is the answer whether or not "n"
            // already reached it.
            type(row(tree, "nl"), "e");
            assertEquals(List.of(), host.focusRequests(),
                    "\"ne\" already matches the focused row, so nothing moves");
        }

        /// Typing moves the keyboard; it does not choose. `Enter` is what
        /// chooses, which is the same split `select`'s open list draws.
        @Test
        @DisplayName("typing moves the focus and selects nothing")
        void typingDoesNotChoose() {
            var tree = world(null);
            press(row(tree, "europe"), Key.RIGHT);
            tree.flush();

            type(row(tree, "europe"), "s");

            assertEquals(List.of(), chosen);
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

    /// §3's `checkable="none|leaf|any|cascade"` — the checkbox per node, which is
    /// a **second value** beside the selection rather than a rendering of it
    /// ([ADR-0210]).
    @Nested
    @DisplayName("the checkbox §3 asks for")
    class Checking {

        private final List<java.util.Set<String>> ticked = new ArrayList<>();

        private ElementTree checkable(Checkable mode, java.util.Set<String> checked) {
            return tree(new Tree(world(), null, chosen::add)
                    .checkable(mode)
                    .checked(checked, ticked::add));
        }

        private static Checkbox.Value check(ElementTree tree, String id) {
            return row(tree, id).check();
        }

        private static void clickTheBox(ElementTree tree, String id) {
            var row = row(tree, id);
            for (var child : row.children()) {
                if (child instanceof TreeRow.TreeCheck box) {
                    box.onPointer(new io.github.digitalsmile.goldberry.input.event.PointerEvent(
                            io.github.digitalsmile.goldberry.input.event.PointerEvent.Kind.CLICKED,
                            0, 0,
                            io.github.digitalsmile.goldberry.input.event.PointerEvent.Button.PRIMARY,
                            1, null));
                    return;
                }
            }
            throw new AssertionError("no checkbox on row \"" + id + "\"");
        }

        @Test
        @DisplayName("none is the default, so no tree in the toolkit grew a checkbox")
        void noneByDefault() {
            var tree = world(null);

            assertNull(check(tree, "europe"), "a box appeared where nobody asked for one");
            assertNull(check(tree, "asia"));
        }

        /// Null and not `UNCHECKED`: "there is no box here" and "the box is
        /// empty" are different rows.
        @Test
        @DisplayName("leaf puts a box on the leaves and none on the branches")
        void leafOnly() {
            var tree = checkable(Checkable.LEAF, java.util.Set.of());
            press(row(tree, "europe"), Key.RIGHT);
            tree.flush();

            assertNull(check(tree, "europe"), "a branch has no box in a leaf tree");
            assertEquals(Checkbox.Value.UNCHECKED, check(tree, "no"));
        }

        @Test
        @DisplayName("any puts a box on every row, and they are independent")
        void anyIsIndependent() {
            var tree = checkable(Checkable.ANY, java.util.Set.of());
            press(row(tree, "europe"), Key.RIGHT);
            tree.flush();

            assertEquals(Checkbox.Value.UNCHECKED, check(tree, "europe"));
            clickTheBox(tree, "europe");

            assertEquals(List.of(java.util.Set.of("europe")), ticked,
                    "a parent's box in an `any` tree is about the parent alone");
        }

        // --- cascade ---------------------------------------------------------

        @Test
        @DisplayName("cascade ticks everything under the branch, and the branch")
        void cascadeGoesDown() {
            var tree = checkable(Checkable.CASCADE, java.util.Set.of());
            press(row(tree, "europe"), Key.RIGHT);
            tree.flush();

            clickTheBox(tree, "europe");

            assertEquals(List.of(java.util.Set.of("europe", "no", "se")), ticked);
        }

        /// §3's "shows `indeterminate` upward", and the reason it is a bar and
        /// not a greyed tick: "some of these are on" has to be distinguishable
        /// from "all of these are on" at a glance.
        @Test
        @DisplayName("a branch with some of its children ticked draws the mixed state")
        void cascadeSummarisesUp() {
            var tree = checkable(Checkable.CASCADE, java.util.Set.of("no"));
            press(row(tree, "europe"), Key.RIGHT);
            tree.flush();

            assertEquals(Checkbox.Value.MIXED, check(tree, "europe"));
            assertEquals(Checkbox.Value.CHECKED, check(tree, "no"));
            assertEquals(Checkbox.Value.UNCHECKED, check(tree, "se"));
        }

        @Test
        @DisplayName("and reads CHECKED once all of them are")
        void cascadeIsCheckedWhenAllAre() {
            var tree = checkable(Checkable.CASCADE, java.util.Set.of("no", "se"));
            press(row(tree, "europe"), Key.RIGHT);
            tree.flush();

            assertEquals(Checkbox.Value.CHECKED, check(tree, "europe"),
                    "the branch is derived from its children, not from its own membership");
        }

        /// **Derived and not stored**: a parent bit kept in the set would go stale
        /// the moment one child was unticked, and the row would claim "all of
        /// these" while showing one that is not.
        @Test
        @DisplayName("a branch in the set still reads MIXED when a child is not")
        void derivedBeatsStored() {
            var tree = checkable(Checkable.CASCADE, java.util.Set.of("europe", "no"));
            press(row(tree, "europe"), Key.RIGHT);
            tree.flush();

            assertEquals(Checkbox.Value.MIXED, check(tree, "europe"));
        }

        /// `Checkbox.Value.toggled()`'s rule, and this is its second caller: a
        /// reader clicking a partly ticked folder is asking for all of it.
        @Test
        @DisplayName("clicking a mixed branch asks for all of it, not for none")
        void mixedGoesToChecked() {
            var tree = checkable(Checkable.CASCADE, java.util.Set.of("no"));
            press(row(tree, "europe"), Key.RIGHT);
            tree.flush();

            clickTheBox(tree, "europe");

            assertEquals(List.of(java.util.Set.of("no", "europe", "se")), ticked);
        }

        @Test
        @DisplayName("unticking a branch takes everything under it out")
        void cascadeClearsDown() {
            var tree = checkable(Checkable.CASCADE, java.util.Set.of("europe", "no", "se"));
            press(row(tree, "europe"), Key.RIGHT);
            tree.flush();

            clickTheBox(tree, "europe");

            assertEquals(List.of(java.util.Set.of()), ticked);
        }

        /// A lazy branch nobody has opened has nothing to derive from, and
        /// fetching one to draw a checkbox would be fetching a model the user
        /// has not asked for.
        @Test
        @DisplayName("an unopened lazy branch reads its own membership")
        void cascadeFallsBackForALazyBranch() {
            var runs = new int[1];
            var tree = tree(new Tree(
                    List.of(TreeNode.lazy("root", "Root", () -> {
                        runs[0]++;
                        return List.of(TreeNode.leaf("a", "A"));
                    })), null, chosen::add)
                    .checkable(Checkable.CASCADE)
                    .checked(java.util.Set.of("root"), ticked::add));

            assertEquals(Checkbox.Value.CHECKED, check(tree, "root"));
            assertEquals(0, runs[0], "drawing a checkbox fetched the model");
        }

        // --- the two values stay apart ---------------------------------------

        /// The whole point of ADR-0210: ticking is not choosing. A click that did
        /// both would make the box unusable in a single-selection tree, because
        /// every tick would move the highlight.
        @Test
        @DisplayName("ticking a row does not select it")
        void tickingIsNotChoosing() {
            var tree = checkable(Checkable.ANY, java.util.Set.of());
            press(row(tree, "europe"), Key.RIGHT);
            tree.flush();

            clickTheBox(tree, "no");

            assertEquals(List.of(java.util.Set.of("no")), ticked);
            assertEquals(List.of(), chosen, "the tick was taken for a choice");
        }

        @Test
        @DisplayName("Space ticks the box, and Enter still chooses")
        void spaceTicks() {
            var tree = checkable(Checkable.ANY, java.util.Set.of());
            press(row(tree, "europe"), Key.RIGHT);
            tree.flush();

            press(row(tree, "no"), Key.SPACE);
            assertEquals(List.of(java.util.Set.of("no")), ticked);
            assertEquals(List.of(), chosen);

            press(row(tree, "no"), Key.ENTER);
            assertEquals(List.of("no"), chosen, "Enter stopped choosing");
        }

        @Test
        @DisplayName("Space on a row with no box is left for whatever else wants it")
        void spaceWithNoBox() {
            var tree = world(null);
            var event = new KeyEvent(
                    KeyEvent.Kind.PRESSED, Key.SPACE, Modifiers.NONE, false, null);

            row(tree, "europe").onKey(event);

            assertFalse(event.isConsumed());
        }

        /// Controlled, like the selection: it reports and ticks nothing itself.
        @Test
        @DisplayName("it reports and ticks nothing itself")
        void controlled() {
            var tree = checkable(Checkable.ANY, java.util.Set.of());
            press(row(tree, "europe"), Key.RIGHT);
            tree.flush();

            clickTheBox(tree, "no");
            tree.flush();

            assertEquals(Checkbox.Value.UNCHECKED, check(tree, "no"),
                    "the tree ticked a box the application never accepted");
        }
    }

    /// §3's "`list`'s selection models: none / single / multi (Ctrl/Shift
    /// semantics)" — defined here because `list` is not built ([ADR-0210]).
    @Nested
    @DisplayName("the selection models")
    class SelectionModels {

        private final List<java.util.Set<String>> sets = new ArrayList<>();

        /// A tree of five visible rows once Europe is open:
        /// `europe, no, se, asia, jp`.
        private ElementTree multi() {
            var built = tree(new Tree(world(), java.util.Set.of(), sets::add,
                    Selection.MULTIPLE, false, Checkable.NONE, java.util.Set.of(), null,
                    io.github.digitalsmile.goldberry.widget.attr.Attributes.NONE));
            press(row(built, "europe"), Key.RIGHT);
            press(row(built, "asia"), Key.RIGHT);
            built.flush();
            return built;
        }

        private static void activate(ElementTree tree, String id, Modifiers modifiers) {
            row(tree, id).onPointer(new io.github.digitalsmile.goldberry.input.event.PointerEvent(
                    io.github.digitalsmile.goldberry.input.event.PointerEvent.Kind.CLICKED,
                    0, 0,
                    io.github.digitalsmile.goldberry.input.event.PointerEvent.Button.PRIMARY,
                    1, Float.NaN, Float.NaN, modifiers, null));
        }

        private static final Modifiers CTRL = new Modifiers(false, true, false, false);
        private static final Modifiers SHIFT = new Modifiers(true, false, false, false);

        @Test
        @DisplayName("none makes no row an answer, and a click opens instead")
        void noneSelectsNothing() {
            var tree = tree(new Tree(world(), null, chosen::add)
                    .selection(Selection.NONE));

            assertFalse(row(tree, "europe").selectable());
            activate(tree, "europe", Modifiers.NONE);
            tree.flush();

            assertEquals(List.of(), chosen);
            assertEquals(List.of("europe", "no", "se", "asia"), rows(tree),
                    "a row that cannot be chosen should still open");
        }

        @Test
        @DisplayName("single reports one, and ignores the modifiers")
        void singleIgnoresModifiers() {
            var tree = world(null);
            press(row(tree, "europe"), Key.RIGHT);
            tree.flush();

            activate(tree, "no", CTRL);
            activate(tree, "se", SHIFT);

            assertEquals(List.of("no", "se"), chosen,
                    "a control holding one row has nothing to say to Ctrl or Shift");
        }

        @Test
        @DisplayName("a plain press replaces the selection")
        void plainReplaces() {
            var tree = multi();

            activate(tree, "no", Modifiers.NONE);
            activate(tree, "se", Modifiers.NONE);

            assertEquals(List.of(java.util.Set.of("no"), java.util.Set.of("se")), sets);
        }

        /// The only way to take a row *out*, which is why the toggle is on
        /// `Ctrl` and not on a plain press.
        ///
        /// **The answer is applied back between the two presses**, which is what
        /// an application does and what makes this a test of the loop rather than
        /// of one call: the tree is controlled, so what `Ctrl` toggles against is
        /// the set the model currently holds and never one the widget remembered.
        @Test
        @DisplayName("Ctrl adds a row, and Ctrl again takes it out")
        void ctrlToggles() {
            var built = tree(multiple(java.util.Set.of("no")));
            press(row(built, "europe"), Key.RIGHT);
            built.flush();

            activate(built, "se", CTRL);
            assertEquals(java.util.Set.of("no", "se"), sets.getLast(),
                    "Ctrl replaced instead of adding");

            built.update(multiple(sets.getLast()));
            built.flush();
            sets.clear();
            activate(built, "no", CTRL);

            assertEquals(java.util.Set.of("se"), sets.getLast(),
                    "Ctrl on a row already in the selection should take it out");
        }

        /// The same tree, with whatever the application currently holds.
        private Tree multiple(java.util.Set<String> selected) {
            return new Tree(world(), selected, sets::add,
                    Selection.MULTIPLE, false, Checkable.NONE, java.util.Set.of(), null,
                    io.github.digitalsmile.goldberry.widget.attr.Attributes.NONE);
        }

        /// Over the **flattened visible list**, which is what a reader sweeping
        /// a range means: the rows between the two on screen.
        @Test
        @DisplayName("Shift selects through, over the rows that are showing")
        void shiftSelectsARange() {
            var tree = multi();

            activate(tree, "europe", Modifiers.NONE);
            sets.clear();
            activate(tree, "asia", SHIFT);

            assertEquals(java.util.Set.of("europe", "no", "se", "asia"), sets.getLast(),
                    "the range should include the children showing between them");
        }

        @Test
        @DisplayName("a range runs the same way backwards")
        void shiftIsSymmetric() {
            var tree = multi();

            activate(tree, "asia", Modifiers.NONE);
            sets.clear();
            activate(tree, "no", SHIFT);

            assertEquals(java.util.Set.of("no", "se", "asia"), sets.getLast());
        }

        /// The anchor does not move, so an over-shot range is recoverable by
        /// pressing `Shift` again one row back rather than by starting over.
        @Test
        @DisplayName("a run of shifted presses sweeps from the same anchor")
        void shiftKeepsItsAnchor() {
            var tree = multi();

            activate(tree, "no", Modifiers.NONE);
            activate(tree, "jp", SHIFT);
            sets.clear();
            activate(tree, "se", SHIFT);

            assertEquals(java.util.Set.of("no", "se"), sets.getLast(),
                    "the second range grew from where the first stopped");
        }

        @Test
        @DisplayName("a shifted range replaces rather than adding to what was there")
        void shiftReplaces() {
            var built = tree(new Tree(world(), java.util.Set.of("jp"), sets::add,
                    Selection.MULTIPLE, false, Checkable.NONE, java.util.Set.of(), null,
                    io.github.digitalsmile.goldberry.widget.attr.Attributes.NONE));
            press(row(built, "europe"), Key.RIGHT);
            built.flush();

            activate(built, "no", Modifiers.NONE);
            sets.clear();
            activate(built, "se", SHIFT);

            assertEquals(java.util.Set.of("no", "se"), sets.getLast());
        }

        @Test
        @DisplayName("Ctrl+Enter and Shift+Enter are the keyboard's halves of the same gestures")
        void theKeyboardHasThemToo() {
            var tree = multi();

            row(tree, "no").onKey(new KeyEvent(
                    KeyEvent.Kind.PRESSED, Key.ENTER, Modifiers.NONE, false, null));
            sets.clear();
            row(tree, "se").onKey(new KeyEvent(
                    KeyEvent.Kind.PRESSED, Key.ENTER, SHIFT, false, null));
            assertEquals(java.util.Set.of("no", "se"), sets.getLast(),
                    "Shift+Enter should sweep from the row Enter chose");

            // Applied back, for `ctrlToggles`'s reason: `Ctrl` adds to what the
            // application holds, and here that is what the range just reported.
            tree.update(multiple(sets.getLast()));
            tree.flush();
            press(row(tree, "europe"), Key.RIGHT);
            press(row(tree, "asia"), Key.RIGHT);
            tree.flush();
            sets.clear();
            row(tree, "jp").onKey(new KeyEvent(
                    KeyEvent.Kind.PRESSED, Key.ENTER, CTRL, false, null));

            assertEquals(java.util.Set.of("no", "se", "jp"), sets.getLast(),
                    "Ctrl+Enter should add to what the range chose");
        }

        /// `Alt+Enter` is nobody's here, so an application's accelerator on it
        /// still reaches the window.
        @Test
        @DisplayName("Alt+Enter is left alone")
        void altEnterFallsThrough() {
            var tree = multi();
            var event = new KeyEvent(KeyEvent.Kind.PRESSED, Key.ENTER,
                    new Modifiers(false, false, true, false), false, null);

            row(tree, "no").onKey(event);

            assertFalse(event.isConsumed());
            assertEquals(List.of(), sets);
        }

        @Test
        @DisplayName("the selected rows are the ones the model names, all of them")
        void selectedFollowsTheModel() {
            var built = tree(new Tree(world(), java.util.Set.of("no", "jp"), sets::add,
                    Selection.MULTIPLE, false, Checkable.NONE, java.util.Set.of(), null,
                    io.github.digitalsmile.goldberry.widget.attr.Attributes.NONE));
            press(row(built, "europe"), Key.RIGHT);
            press(row(built, "asia"), Key.RIGHT);
            built.flush();

            assertTrue(row(built, "no").selected());
            assertTrue(row(built, "jp").selected());
            assertFalse(row(built, "se").selected());
        }

        /// The three-argument constructor is the single-selection door and it
        /// unwraps the set again, so `select tree=` and every existing caller see
        /// the id they always saw.
        @Test
        @DisplayName("the single-selection constructor still reports one id")
        void theConvenienceFormUnwraps() {
            var tree = world(null);
            press(row(tree, "europe"), Key.RIGHT);
            tree.flush();

            activate(tree, "no", Modifiers.NONE);

            assertEquals(List.of("no"), chosen);
            assertEquals("no", new Tree(world(), "no", chosen::add).selectedOne());
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

    /// Found by running the application: none of these branches would open,
    /// because nothing handled a **click** at all. The keyboard tests above all
    /// passed ([ADR-0185]).
    @Nested
    @DisplayName("the pointer")
    class Pointer {

        private static void click(TreeRow row) {
            row.onPointer(new io.github.digitalsmile.goldberry.input.event.PointerEvent(
                    io.github.digitalsmile.goldberry.input.event.PointerEvent.Kind.CLICKED, 0, 0,
                    io.github.digitalsmile.goldberry.input.event.PointerEvent.Button.PRIMARY,
                    1, null));
        }

        private static TreeRow.TreeChevron chevronOf(TreeRow row) {
            return (TreeRow.TreeChevron) row.children().get(1);
        }

        /// A leaf-only tree makes a parent unselectable, so a click on it had
        /// nothing to do and did nothing — and the chevron had no handler either,
        /// which left no way at all to open a branch with a mouse.
        @Test
        @DisplayName("clicking a branch that is not an answer opens it")
        void clickingAHeadingOpens() {
            var tree = world(null);

            click(row(tree, "europe"));
            tree.flush();

            assertEquals(List.of("europe", "no", "se", "asia"), rows(tree));
            assertEquals(List.of(), chosen, "opening a branch reported a choice");
        }

        @Test
        @DisplayName("clicking the chevron opens, and does not also choose")
        void chevronOpens() {
            var tree = tree(new Tree(world(), null, chosen::add).anyNode(true));

            var event = new io.github.digitalsmile.goldberry.input.event.PointerEvent(
                    io.github.digitalsmile.goldberry.input.event.PointerEvent.Kind.CLICKED, 0, 0,
                    io.github.digitalsmile.goldberry.input.event.PointerEvent.Button.PRIMARY,
                    1, null);
            chevronOf(row(tree, "europe")).onPointer(event);
            tree.flush();

            assertTrue(event.isConsumed(), "the click went on to select the row it opened");
            assertEquals(List.of("europe", "no", "se", "asia"), rows(tree));
            assertEquals(List.of(), chosen);
        }

        @Test
        @DisplayName("clicking a leaf chooses it")
        void clickingALeafChooses() {
            var tree = world(null);
            click(row(tree, "europe"));
            tree.flush();

            click(row(tree, "no"));

            assertEquals(List.of("no"), chosen);
        }

        @Test
        @DisplayName("a leaf has no chevron to click")
        void aLeafHasNoChevron() {
            var tree = world(null);
            click(row(tree, "europe"));
            tree.flush();

            assertFalse(chevronOf(row(tree, "no")).present());
        }
    }

}
