package io.github.digitalsmile.goldberry.input;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// A focus name is resolved **inside the composite the keyboard is in** before
/// the window — [ADR-0437].
///
/// In `:core` and built from bare widgets rather than from `list`, for
/// [FocusTrapTest]'s reason: the rule is the router's and holds for whatever
/// declares itself a scope next. `list` is the widget that needed it and
/// `ListFocusScopeTest` is where it is shown needing it.
///
/// Every case here rests on a duplicated id, which is the only thing this
/// changes: a name that names one node resolves to that node exactly as before.
class FocusNameScopeTest {

    /// A focusable leaf, named.
    private record Item(String name) implements Widget.Leaf, Styled, Handles {

        @Override
        public String cssType() {
            return "item";
        }

        @Override
        public String id() {
            return name;
        }

        @Override
        public boolean isFocusable() {
            return true;
        }
    }

    /// A composite: one Tab stop, and therefore one namespace.
    ///
    /// The component is `body` rather than `children` because a leaf's
    /// `children()` is already a method and a record component of that name
    /// would be overriding it by accident.
    private record Group(String name, List<Widget> body) implements Widget.Leaf, Styled, Handles {

        Group(String name, Widget... kids) {
            this(name, List.of(kids));
        }

        @Override
        public List<Widget> children() {
            return body;
        }

        @Override
        public String cssType() {
            return "group";
        }

        @Override
        public String id() {
            return name;
        }

        @Override
        public FocusScope focusScope() {
            return FocusScope.VERTICAL;
        }
    }

    /// Scenery that is not a composite — a panel, a form, a window.
    private record Panel(String name, List<Widget> body) implements Widget.Leaf, Styled, Handles {

        Panel(String name, Widget... kids) {
            this(name, List.of(kids));
        }

        @Override
        public List<Widget> children() {
            return body;
        }

        @Override
        public String cssType() {
            return "panel";
        }

        @Override
        public String id() {
            return name;
        }
    }

    private PointerRouter router;
    private ElementTree tree;

    /// ```
    /// window( one( row other ) two( row other ) elsewhere )
    /// ```
    ///
    /// Two composites holding the **same two names** — the bare shape of two
    /// unnamed lists over items with the same identity — and one stop belonging
    /// to neither.
    @BeforeEach
    void setUp() {
        tree = new ElementTree(new Panel(
                "window",
                new Group("one", new Item("row"), new Item("other")),
                new Group("two", new Item("row"), new Item("other")),
                new Item("elsewhere")));
        router = new PointerRouter();
        router.focusRoot(tree.root());
    }

    private Element byId(String id) {
        return find(tree.root(), id);
    }

    /// The node called `id` inside the subtree called `scope` — which is the
    /// only way to name either of the two `row`s from a test, and is itself the
    /// thing the router had no notion of.
    private Element byId(String scope, String id) {
        return find(byId(scope), id);
    }

    private static Element find(Element from, String id) {
        if (id.equals(from.id())) {
            return from;
        }
        for (var child : from.children()) {
            var found = find(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Nested
    @DisplayName("a name the keyboard's composite holds")
    class Inside {

        @Test
        @DisplayName("resolves inside that composite, not in document order")
        void nearestCompositeAnswers() {
            router.focus(byId("two", "other"), true);
            assertTrue(router.focusById("row", true));
            assertSame(
                    byId("two", "row"), router.focused(), "the second composite's key moved the focus into the first");
        }

        @Test
        @DisplayName("still resolves in document order with the focus nowhere")
        void noFocusMeansNoSubtree() {
            router.focus(null, false);
            assertTrue(router.focusById("row", false));
            assertSame(
                    byId("one", "row"),
                    router.focused(),
                    "with nothing focused there is no subtree for a name to be relative to");
        }

        @Test
        @DisplayName("resolves in document order for a caller outside every composite")
        void outsideEveryComposite() {
            router.focus(byId("elsewhere"), true);
            assertTrue(router.focusById("row", true));
            assertSame(byId("one", "row"), router.focused());
        }
    }

    @Nested
    @DisplayName("a name the keyboard's composite does not hold")
    class Outside {

        @Test
        @DisplayName("resolves in the window, exactly as it always did")
        void fallsBackToTheWindow() {
            router.focus(byId("two", "other"), true);
            assertTrue(router.focusById("elsewhere", true));
            assertSame(byId("elsewhere"), router.focused());
        }

        @Test
        @DisplayName("is refused when nothing anywhere is called it")
        void refusesANameNobodyHas() {
            router.focus(byId("two", "other"), true);
            assertFalse(router.focusById("nobody", true));
            assertSame(byId("two", "other"), router.focused(), "a refused name moved the focus anyway");
        }
    }

    @Nested
    @DisplayName("composites inside composites")
    class NestedComposites {

        /// ```
        /// window( one( row ) two( row inner( leaf ) ) )
        /// ```
        private void nest() {
            tree = new ElementTree(new Panel(
                    "window",
                    new Group("one", new Item("row")),
                    new Group("two", new Item("row"), new Group("inner", new Item("row"), new Item("leaf")))));
            router = new PointerRouter();
            router.focusRoot(tree.root());
        }

        @Test
        @DisplayName("the innermost one answers first")
        void innermostFirst() {
            nest();
            router.focus(byId("inner", "leaf"), true);
            assertTrue(router.focusById("row", true));
            assertSame(byId("inner", "row"), router.focused());
        }

        @Test
        @DisplayName("and the search walks outwards before it reaches the window")
        void outwardsBeforeTheWindow() {
            tree = new ElementTree(new Panel(
                    "window",
                    new Group("one", new Item("row")),
                    new Group("two", new Item("row"), new Group("inner", new Item("leaf")))));
            router = new PointerRouter();
            router.focusRoot(tree.root());

            router.focus(byId("inner", "leaf"), true);
            assertTrue(router.focusById("row", true));
            assertSame(
                    byId("two", "row"),
                    router.focused(),
                    "a name the inner composite does not hold jumped past the one containing it");
        }
    }
}
