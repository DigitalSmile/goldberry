package io.github.digitalsmile.goldberry.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// [Styled#isHidden()] — kept, and not used ([ADR-0366]).
class HiddenSubtreeTest {

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

    private record Page(String name, boolean hidden, List<Widget> children) implements Widget.Leaf, Styled {

        @Override
        public String cssType() {
            return "page";
        }

        @Override
        public String id() {
            return name;
        }

        @Override
        public Object key() {
            return name;
        }

        @Override
        public boolean isHidden() {
            return hidden;
        }
    }

    private record Root(List<Widget> children) implements Widget.Leaf, Styled {

        @Override
        public String cssType() {
            return "root";
        }
    }

    private static Widget pages(boolean secondShown) {
        return new Root(List.of(
                new Page("one", secondShown, List.of(new Item("a"), new Item("b"))),
                new Page("two", !secondShown, List.of(new Item("c")))));
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

    private static List<String> tabOrder(PointerRouter router, ElementTree tree) {
        var seen = new ArrayList<String>();
        router.focus(null, false);
        for (var i = 0; i < 5; i++) {
            router.moveFocus(1);
            var focused = router.focused();
            if (focused == null || seen.contains(focused.id())) {
                break;
            }
            seen.add(focused.id());
        }
        return seen;
    }

    @Test
    @DisplayName("a hidden page's controls are not Tab stops, and its elements are still mounted")
    void notTraversed() {
        var tree = new ElementTree(pages(false));
        var router = new PointerRouter();
        router.focusRoot(tree.root());

        assertEquals(List.of("a", "b"), tabOrder(router, tree));
        assertTrue(find(tree.root(), "c").isMounted(), "the hidden page kept its element");
    }

    @Test
    @DisplayName("focus inside a page that becomes hidden is let go, and the element is the same one")
    void focusIsReleased() {
        var tree = new ElementTree(pages(false));
        var router = new PointerRouter();
        router.focusRoot(tree.root());
        var b = find(tree.root(), "b");
        router.focus(b, true);

        tree.update(pages(true));
        tree.flush();
        router.refocus();

        assertNull(router.focused());
        assertSame(b, find(tree.root(), "b"), "hiding kept the element rather than rebuilding it");
        assertEquals(List.of("c"), tabOrder(router, tree));
    }

    @Test
    @DisplayName("a hidden node refuses a direct focus too")
    void refusesDirectFocus() {
        var tree = new ElementTree(pages(false));
        var router = new PointerRouter();
        router.focusRoot(tree.root());

        assertFalse(router.focusById("c", true));
    }
}
