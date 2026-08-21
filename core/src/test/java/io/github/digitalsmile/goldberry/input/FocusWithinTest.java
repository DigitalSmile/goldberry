package io.github.digitalsmile.goldberry.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// `:focus-within`, as a notification — what a container is told when the
/// keyboard arrives somewhere under it.
///
/// The rule under test is the one that makes it usable: **only the boundary**.
/// A container is told when its subtree gains or loses focus and told nothing
/// about a move that stayed inside, so a `field` with one control and a `field`
/// with three behave the same and neither has to filter anything out.
class FocusWithinTest {

    private final List<String> log = new ArrayList<>();

    /// A node that records both focus questions it can be asked.
    private final class Node implements Widget.Leaf, Styled, Handles {

        private final String name;
        private final boolean focusable;
        private final List<Widget> children;

        Node(String name, boolean focusable, Widget... children) {
            this.name = name;
            this.focusable = focusable;
            this.children = List.of(children);
        }

        @Override
        public List<Widget> children() {
            return children;
        }

        @Override
        public String cssType() {
            return name;
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public boolean isFocusable() {
            return focusable;
        }

        @Override
        public void onFocusChanged(boolean focused, boolean fromKeyboard) {
            log.add(name + (focused ? " focused" : " blurred"));
        }

        @Override
        public void onFocusWithin(boolean within, boolean fromKeyboard) {
            log.add(name + (within ? " entered" : " left"));
        }
    }

    private ElementTree tree;

    /// `form > field-one > (input-a, input-b)` and `form > field-two > input-c`.
    private ElementTree twoFields() {
        tree = new ElementTree(new Node("form", false,
                new Node("field-one", false,
                        new Node("input-a", true),
                        new Node("input-b", true)),
                new Node("field-two", false,
                        new Node("input-c", true))));
        return tree;
    }

    private Element find(String type) {
        return find(tree.root(), type);
    }

    private static Element find(Element element, String type) {
        if (element.widget() instanceof Styled styled && styled.cssType().equals(type)) {
            return element;
        }
        for (var child : element.children()) {
            var found = find(child, type);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Nested
    @DisplayName("crossing the boundary")
    class Boundary {

        @Test
        @DisplayName("every ancestor of the newly focused node is told")
        void entersOnce() {
            var router = new PointerRouter();
            twoFields();

            router.focus(find("input-a"), true);

            assertEquals(List.of("input-a focused", "input-a entered", "field-one entered",
                    "form entered"), log);
        }

        @Test
        @DisplayName("leaving tells the ancestors that stopped containing it")
        void leaves() {
            var router = new PointerRouter();
            twoFields();
            router.focus(find("input-a"), true);
            log.clear();

            router.focus(null, true);

            assertEquals(List.of("input-a blurred", "input-a left", "field-one left",
                    "form left"), log);
        }

        @Test
        @DisplayName("a widget that is focused is inside its own subtree")
        void aFocusedNodeIsWithinItself() {
            var router = new PointerRouter();
            twoFields();

            router.focus(find("input-a"), true);

            // Told twice, about two different questions -- `:focus` and
            // `:focus-within` are both true of a focused node in CSS for exactly
            // this reason.
            assertTrue(log.contains("input-a focused"));
            assertTrue(log.contains("input-a entered"));
        }
    }

    @Nested
    @DisplayName("staying inside")
    class Inside {

        @Test
        @DisplayName("a move between two controls in one field tells that field nothing")
        void movingWithinAFieldIsSilent() {
            var router = new PointerRouter();
            twoFields();
            router.focus(find("input-a"), true);
            log.clear();

            router.focus(find("input-b"), true);

            // The field's subtree held the keyboard throughout. A field told
            // "left" and then "entered" here would validate on a move that never
            // crossed its boundary.
            assertEquals(List.of("input-a blurred", "input-b focused",
                    "input-a left", "input-b entered"), log);
        }

        @Test
        @DisplayName("a move between two fields tells both of them, and not the form")
        void movingBetweenFields() {
            var router = new PointerRouter();
            twoFields();
            router.focus(find("input-a"), true);
            log.clear();

            router.focus(find("input-c"), true);

            assertEquals(List.of("input-a blurred", "input-c focused",
                    "input-a left", "field-one left",
                    "input-c entered", "field-two entered"), log);
            assertTrue(log.stream().noneMatch(entry -> entry.startsWith("form ")),
                    "the form never stopped containing the focus");
        }
    }

    @Nested
    @DisplayName("the ordinary cases")
    class Ordinary {

        @Test
        @DisplayName("focusing the same node again reports nothing")
        void sameNodeIsSilent() {
            var router = new PointerRouter();
            twoFields();
            router.focus(find("input-a"), true);
            log.clear();

            router.focus(find("input-a"), true);

            assertTrue(log.isEmpty());
        }

        @Test
        @DisplayName("a container is told about a pointer focus as well as a keyboard one")
        void carriesTheSource() {
            var router = new PointerRouter();
            twoFields();

            router.focus(find("input-a"), false);

            // The `fromKeyboard` flag rides along so a container can tell a click
            // from a Tab, which is the same distinction `:focus-visible` draws —
            // but *both* are reported, because a field validates on blur however
            // the user left it.
            assertTrue(log.contains("field-one entered"));
        }
    }
}
