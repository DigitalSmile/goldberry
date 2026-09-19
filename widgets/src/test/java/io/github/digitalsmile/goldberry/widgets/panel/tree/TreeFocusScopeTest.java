package io.github.digitalsmile.goldberry.widgets.panel.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.core.Column;

/// Two trees over the same node ids, in one window — [ADR-0437].
///
/// **Worse than `list`'s case before the router had the notion**, and settled by
/// the same change. A list prefixes its rows with its own `id` and a tree never
/// did, so two trees sharing a node id answered each other's keys whether or not
/// either was named — there was no spelling of a tree that avoided it. Nothing
/// in `tree` had to change: a tree says it is a focus scope because it needed
/// arrow keys, and that is the boundary the name is now resolved in.
class TreeFocusScopeTest {

    /// A [TestHost] whose [#focus] reaches a real router, as a window's does.
    private static final class Routed extends TestHost {

        private PointerRouter router;

        @Override
        public boolean focus(String id, boolean fromKeyboard) {
            super.focus(id, fromKeyboard);
            return router != null && router.focusById(id, fromKeyboard);
        }
    }

    private final Routed host = new Routed();
    private final List<String> chosen = new ArrayList<>();
    private ElementTree tree;
    private PointerRouter router;

    /// `europe( no se )  asia( jp )` — `TreeTest`'s model, closed.
    private List<TreeNode> world() {
        return List.of(
                TreeNode.of("europe", "Europe", TreeNode.leaf("no", "Norway"), TreeNode.leaf("se", "Sweden")),
                TreeNode.of("asia", "Asia", TreeNode.leaf("jp", "Japan")));
    }

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        tree = new ElementTree(
                new Column(
                        new Tree(world(), (String) null, chosen::add), new Tree(world(), (String) null, chosen::add)),
                host);
        router = new PointerRouter();
        router.focusRoot(tree.root());
        host.router = router;
    }

    /// The two `tree` boxes, top to bottom.
    private List<Element> trees() {
        var found = new ArrayList<Element>();
        collect(tree.root(), element -> element.widget() instanceof TreeBox, found);
        assertEquals(2, found.size(), "the window should hold exactly two trees");
        return found;
    }

    private static void collect(Element from, Predicate<Element> wanted, List<Element> into) {
        if (wanted.test(from)) {
            into.add(from);
        }
        for (var child : from.children()) {
            collect(child, wanted, into);
        }
    }

    private static Element row(Element within, String id) {
        var found = new ArrayList<Element>();
        collect(within, element -> id.equals(element.id()), found);
        assertEquals(1, found.size(), "expected one row called \"" + id + "\" in this tree");
        return found.getFirst();
    }

    @Test
    @DisplayName("both trees name their rows the same thing")
    void theNamesReallyDoCollide() {
        var rows = trees().stream()
                .map(each -> {
                    var found = new ArrayList<Element>();
                    collect(each, element -> element.widget() instanceof TreeRow, found);
                    return found.stream().map(Element::id).toList();
                })
                .toList();
        assertEquals(rows.getFirst(), rows.getLast(), "the case this is about does not arise");
        assertEquals(List.of("tree-europe", "tree-asia"), rows.getFirst());
    }

    @Test
    @DisplayName("End in the second tree reaches the second tree's last row")
    void endStaysInTheTreeItWasPressedIn() {
        var second = trees().get(1);
        router.focus(row(second, "tree-europe"), true);

        assertTrue(router.keyPressed(Key.END, Modifiers.NONE, false), "the row declined End");

        assertEquals(List.of("tree-asia"), host.focusRequests());
        assertSame(row(second, "tree-asia"), router.focused(), "End in the second tree moved the focus into the first");
    }

    @Test
    @DisplayName("Home in the second tree reaches the second tree's first row")
    void homeStaysInTheTreeItWasPressedIn() {
        var second = trees().get(1);
        router.focus(row(second, "tree-asia"), true);

        assertTrue(router.keyPressed(Key.HOME, Modifiers.NONE, false));

        assertSame(row(second, "tree-europe"), router.focused());
    }

    @Test
    @DisplayName("and the first tree is not simply always losing")
    void theFirstTreeKeepsItsOwnKeys() {
        var first = trees().getFirst();
        router.focus(row(first, "tree-europe"), true);

        assertTrue(router.keyPressed(Key.END, Modifiers.NONE, false));

        assertSame(row(first, "tree-asia"), router.focused());
    }
}
