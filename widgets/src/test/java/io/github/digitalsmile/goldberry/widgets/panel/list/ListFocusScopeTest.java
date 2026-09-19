package io.github.digitalsmile.goldberry.widgets.panel.list;

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

/// Two unnamed lists over the same identities, in one window — [ADR-0437].
///
/// `ListTest` drives a row's handler and asserts what the widget **asked** the
/// host for, which is the right level for everything else it tests and is
/// exactly the wrong level for this: the whole question is what a name *resolves
/// to*, and both lists ask for the same one. So this is end to end — a real
/// [PointerRouter] over a real element tree, a key pressed on a row, and an
/// assertion about which element the focus is on afterwards.
class ListFocusScopeTest {

    private static final List<String> NORDICS = List.of("Norway", "Sweden", "Finland", "Denmark", "Iceland");

    /// A [TestHost] whose [#focus] reaches a real router, as a window's does.
    ///
    /// The base class records the request and answers true, which is what a test
    /// of a widget's *intent* wants. Here the request has to actually be resolved
    /// against a tree, and the recording is kept because the first thing to show
    /// is that both lists ask for the same name.
    private static final class Routed extends TestHost {

        private PointerRouter router;

        @Override
        public boolean focus(String id, boolean fromKeyboard) {
            super.focus(id, fromKeyboard);
            return router != null && router.focusById(id, fromKeyboard);
        }
    }

    private final Routed host = new Routed();
    private ElementTree tree;
    private PointerRouter router;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        // Neither list is given an `id`, which is the case the prefix in
        // `ListState#rowId` cannot settle: both name their rows `list-<item>`.
        tree = new ElementTree(new Column(ListView.of(NORDICS), ListView.of(NORDICS)), host);
        router = new PointerRouter();
        router.focusRoot(tree.root());
        host.router = router;
    }

    /// The two `list` boxes, top to bottom.
    private List<Element> lists() {
        var found = new ArrayList<Element>();
        collect(tree.root(), element -> element.widget() instanceof ListBox, found);
        assertEquals(2, found.size(), "the window should hold exactly two lists");
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

    /// The row called `id` **inside** `list` — the only way to name one of two
    /// identically named rows, and the notion the router was missing.
    private static Element row(Element list, String id) {
        var found = new ArrayList<Element>();
        collect(list, element -> id.equals(element.id()), found);
        assertEquals(1, found.size(), "expected one row called \"" + id + "\" in this list");
        return found.getFirst();
    }

    @Test
    @DisplayName("both lists name their rows the same thing")
    void theNamesReallyDoCollide() {
        var rows = lists().stream()
                .map(list -> {
                    var found = new ArrayList<Element>();
                    collect(list, element -> element.widget() instanceof ListRow, found);
                    return found.stream().map(Element::id).toList();
                })
                .toList();
        assertEquals(rows.getFirst(), rows.getLast(), "the case this is about does not arise");
        assertEquals(
                List.of("list-Norway", "list-Sweden", "list-Finland", "list-Denmark", "list-Iceland"), rows.getFirst());
    }

    @Test
    @DisplayName("End in the second list reaches the second list's last row")
    void endStaysInTheListItWasPressedIn() {
        var second = lists().get(1);
        router.focus(row(second, "list-Norway"), true);

        assertTrue(router.keyPressed(Key.END, Modifiers.NONE, false), "the row declined End");

        assertEquals(List.of("list-Iceland"), host.focusRequests(), "it asked for something other than the last row");
        assertSame(
                row(second, "list-Iceland"), router.focused(), "End in the second list moved the focus into the first");
    }

    @Test
    @DisplayName("Home in the second list reaches the second list's first row")
    void homeStaysInTheListItWasPressedIn() {
        var second = lists().get(1);
        router.focus(row(second, "list-Denmark"), true);

        assertTrue(router.keyPressed(Key.HOME, Modifiers.NONE, false));

        assertSame(row(second, "list-Norway"), router.focused());
    }

    @Test
    @DisplayName("and the first list is not simply always losing")
    void theFirstListKeepsItsOwnKeys() {
        // The complement, because a rule that always preferred the *later* list
        // would pass the two above and be just as wrong.
        var first = lists().getFirst();
        router.focus(row(first, "list-Norway"), true);

        assertTrue(router.keyPressed(Key.END, Modifiers.NONE, false));

        assertSame(row(first, "list-Iceland"), router.focused());
    }

    @Test
    @DisplayName("a name from outside every list still resolves in document order")
    void anOutsideCallerIsUnchanged() {
        // `host.focus` is published and its callers are applications, which ask
        // with the focus wherever the user left it. Nothing about that reading
        // changes: a duplicated name asked for from outside any list is the
        // first one, exactly as it was ([ADR-0437]).
        assertTrue(host.focus("list-Sweden", false));
        assertSame(row(lists().getFirst(), "list-Sweden"), router.focused());
    }
}
