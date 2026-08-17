package io.github.digitalsmile.goldberry.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.css.Selector.PseudoClass;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// §7.2's "composites are one Tab stop with roving arrow-key focus inside".
///
/// [KeyboardTest] covers traversal over a flat tree; this covers the case that
/// tree cannot express — a group whose members are focusable individually and
/// collectively count as one stop ([ADR-0073]).
///
/// Deliberately in `:core` and built from bare widgets rather than from `radio`,
/// which lives in `:widgets`: the mechanism is the router's and has to hold for
/// the tab list and the menu that will use it next, neither of which will look
/// anything like a radio.
class FocusScopeTest {

    private final List<String> log = new ArrayList<>();

    /// A focusable leaf that records the focus it is given.
    private class Item implements Widget.Leaf, Styled, Handles {
        private final String name;
        private final boolean focusable;

        Item(String name) {
            this(name, true);
        }

        Item(String name, boolean focusable) {
            this.name = name;
            this.focusable = focusable;
        }

        @Override
        public String cssType() {
            return name;
        }

        @Override
        public boolean isFocusable() {
            return focusable;
        }

        @Override
        public void onFocusChanged(boolean focused, boolean fromKeyboard) {
            log.add((focused ? "gained:" : "lost:") + name + (fromKeyboard ? ":keys" : ":mouse"));
        }
    }

    /// A composite: one Tab stop, arrows inside.
    private static class Group implements Widget.Leaf, Styled, Handles {
        private final List<Widget> children;

        Group(Widget... children) {
            this.children = List.of(children);
        }

        @Override
        public List<Widget> children() {
            return children;
        }

        @Override
        public String cssType() {
            return "group";
        }

        @Override
        public boolean focusScope() {
            return true;
        }
    }

    private PointerRouter router;
    private Element before;
    private Element group;
    private Element after;

    @BeforeEach
    void buildTree() {
        // before | group( one two three ) | after
        var tree = new ElementTree(new Item("root", false) {
            @Override
            public List<Widget> children() {
                return List.of(
                        new Item("before"),
                        new Group(new Item("one"), new Item("two"), new Item("three")),
                        new Item("after"));
            }
        });
        router = new PointerRouter();
        router.focusRoot(tree.root());
        before = tree.root().children().get(0);
        group = tree.root().children().get(1);
        after = tree.root().children().get(2);
    }

    private Element option(int index) {
        return group.children().get(index);
    }

    private boolean tab() {
        return router.keyPressed(Key.TAB, Modifiers.NONE, false);
    }

    private boolean arrow(Key key) {
        return router.keyPressed(key, Modifiers.NONE, false);
    }

    @Nested
    @DisplayName("a composite is one Tab stop")
    class OneStop {

        @Test
        @DisplayName("Tab crosses a group of three in one press, not three")
        void oneStopNotThree() {
            tab();
            assertSame(before, router.focused());
            tab();
            assertSame(option(0), router.focused(), "Tab enters the group");
            tab();
            assertSame(after, router.focused(), "and the next Tab leaves it entirely");
        }

        @Test
        @DisplayName("Shift+Tab leaves it in one press too")
        void backwards() {
            router.focus(after, true);
            assertTrue(router.keyPressed(Key.TAB, new Modifiers(true, false, false, false), false));
            assertSame(option(0), router.focused());
        }

        @Test
        @DisplayName("Tab enters at the selected option, not the first")
        void entersAtSelection() {
            // The entry point is derived from `:checked` rather than remembered,
            // which is the whole reason there is no roving-position field to get
            // out of step with the selection.
            option(2).setPseudoClass(PseudoClass.CHECKED, true);

            tab();
            tab();
            assertSame(option(2), router.focused());
        }

        @Test
        @DisplayName("an application setting the value moves where Tab lands, with nothing told")
        void selectionIsTheMemory() {
            option(0).setPseudoClass(PseudoClass.CHECKED, true);
            tab();
            tab();
            assertSame(option(0), router.focused());

            // Focus leaves, and meanwhile the model changes underneath -- which
            // is exactly what a stored roving position would fail to notice.
            router.focus(after, true);
            option(0).setPseudoClass(PseudoClass.CHECKED, false);
            option(1).setPseudoClass(PseudoClass.CHECKED, true);

            router.keyPressed(Key.TAB, new Modifiers(true, false, false, false), false);
            assertSame(option(1), router.focused());
        }

        @Test
        @DisplayName("a group that is itself focusable is still one stop, not two")
        void focusableGroupIsOneStop() {
            // Reachable twice by Tab would be bad enough; the second arrival
            // would also have no arrow keys, because a scope is found strictly
            // upwards from the focused node.
            class FocusableGroup extends Group {
                FocusableGroup(Widget... children) {
                    super(children);
                }

                @Override
                public boolean isFocusable() {
                    return true;
                }
            }
            var tree = new ElementTree(new Item("root", false) {
                @Override
                public List<Widget> children() {
                    return List.of(
                            new FocusableGroup(new Item("one"), new Item("two")),
                            new Item("after"));
                }
            });
            var router = new PointerRouter();
            router.focusRoot(tree.root());

            router.keyPressed(Key.TAB, Modifiers.NONE, false);
            assertEquals("one", router.focused().type());
            router.keyPressed(Key.TAB, Modifiers.NONE, false);
            assertEquals("after", router.focused().type());
        }

        @Test
        @DisplayName("a group with nothing focusable in it is skipped, not stopped on")
        void emptyGroupSkipped() {
            var tree = new ElementTree(new Item("root", false) {
                @Override
                public List<Widget> children() {
                    return List.of(
                            new Item("before"),
                            new Group(new Item("inert", false)),
                            new Item("after"));
                }
            });
            var empty = new PointerRouter();
            empty.focusRoot(tree.root());

            empty.keyPressed(Key.TAB, Modifiers.NONE, false);
            assertEquals("before", empty.focused().type());
            empty.keyPressed(Key.TAB, Modifiers.NONE, false);
            assertEquals("after", empty.focused().type());
        }
    }

    @Nested
    @DisplayName("arrow keys rove inside it")
    class Roving {

        @Test
        @DisplayName("both axes move, because the group's direction is the stylesheet's")
        void bothAxes() {
            router.focus(option(0), true);

            assertTrue(arrow(Key.RIGHT));
            assertSame(option(1), router.focused());
            assertTrue(arrow(Key.DOWN));
            assertSame(option(2), router.focused());
            assertTrue(arrow(Key.LEFT));
            assertSame(option(1), router.focused());
            assertTrue(arrow(Key.UP));
            assertSame(option(0), router.focused());
        }

        @Test
        @DisplayName("it wraps at both ends rather than stopping")
        void wraps() {
            router.focus(option(0), true);
            arrow(Key.UP);
            assertSame(option(2), router.focused());
            arrow(Key.DOWN);
            assertSame(option(0), router.focused());
        }

        @Test
        @DisplayName("Home and End reach the ends in one press")
        void homeAndEnd() {
            router.focus(option(1), true);
            assertTrue(arrow(Key.END));
            assertSame(option(2), router.focused());
            assertTrue(arrow(Key.HOME));
            assertSame(option(0), router.focused());
        }

        @Test
        @DisplayName("an arrow key never leaves the group")
        void confined() {
            router.focus(option(2), true);
            arrow(Key.DOWN);
            assertSame(option(0), router.focused(), "wrapped, rather than reaching `after`");
        }

        @Test
        @DisplayName("an arrow outside a group does nothing and is not consumed")
        void outside() {
            router.focus(before, true);
            assertFalse(arrow(Key.RIGHT));
            assertSame(before, router.focused());
        }

        @Test
        @DisplayName("a widget that consumes an arrow keeps it")
        void consumedFirst() {
            // A slider stepping its value, a text field moving its caret. The
            // focused chain is asked before the router treats the key as
            // traversal, so neither has to know it is inside a group.
            var stepper = new Item("stepper") {
                @Override
                public void onKey(KeyEvent event) {
                    event.consume();
                }
            };
            var tree = new ElementTree(new Group(stepper, new Item("other")));
            var scoped = new PointerRouter();
            scoped.focusRoot(tree.root());
            var first = tree.root().children().get(0);
            scoped.focus(first, true);

            assertTrue(scoped.keyPressed(Key.RIGHT, Modifiers.NONE, false));
            assertSame(first, scoped.focused(), "the stepper kept the key");
        }

        @Test
        @DisplayName("a modified arrow is not traversal")
        void modified() {
            router.focus(option(0), true);
            assertFalse(router.keyPressed(Key.RIGHT, new Modifiers(false, true, false, false), false));
            assertSame(option(0), router.focused());
        }
    }

    @Nested
    @DisplayName("focus is reported to the widget it reaches")
    class Notification {

        @Test
        @DisplayName("gaining and losing focus both arrive, in that order")
        void bothEnds() {
            router.focus(option(0), true);
            log.clear();

            arrow(Key.RIGHT);

            assertEquals(List.of("lost:one:keys", "gained:two:keys"), log);
        }

        @Test
        @DisplayName("a mouse focus says so, which is what stops a click selecting twice")
        void mouseIsDistinguished() {
            // A radio selects on keyboard focus and on click. If a pointer focus
            // looked like a keyboard one, the press that moves focus and the
            // click that follows it would each fire the change.
            router.focus(option(0), false);
            assertEquals(List.of("gained:one:mouse"), log);
        }

        @Test
        @DisplayName("re-focusing the node that already has it says nothing")
        void noRepeat() {
            router.focus(option(0), true);
            log.clear();
            router.focus(option(0), true);
            assertEquals(List.of(), log);
        }
    }
}
