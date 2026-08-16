package io.github.digitalsmile.goldberry.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ElementTreeTest {

    // --- fixtures ----------------------------------------------------------

    /// A leaf with a label. The bottom of every tree here.
    private record Label(String text, Object key) implements Widget.Leaf, Styled {
        Label(String text) {
            this(text, null);
        }
    }

    private record Row(List<Widget> children, Object key) implements Widget.Leaf, Styled {
        Row(Widget... kids) {
            this(List.of(kids), null);
        }

        @Override
        public List<Widget> children() {
            return children;
        }
    }

    /// Composition only: no CSS type of its own.
    private record Wrapper(Widget child) implements Widget.Stateless {
        @Override
        public Widget build(BuildContext context) {
            return child;
        }
    }

    private record Counter(String label, Object key) implements Widget.Stateful {
        Counter(String label) {
            this(label, null);
        }

        @Override
        public State<?> createState() {
            return new CounterState();
        }
    }

    private static final class CounterState extends State<Counter> {
        int clicks;
        int builds;
        int initialised;
        int disposed;
        final List<String> updatedFrom = new ArrayList<>();

        @Override
        protected void initState() {
            initialised++;
        }

        @Override
        protected void didUpdateWidget(Counter previous) {
            updatedFrom.add(previous.label());
        }

        @Override
        protected void dispose() {
            disposed++;
        }

        @Override
        public Widget build(BuildContext context) {
            builds++;
            return new Label(widget().label() + ":" + clicks);
        }

        void click() {
            setState(() -> clicks++);
        }
    }

    private static CounterState stateOf(Element element) {
        return (CounterState) element.state().orElseThrow();
    }

    private static String labelOf(Element element) {
        return ((Label) element.widget()).text();
    }

    @Nested
    @DisplayName("building")
    class Building {

        @Test
        @DisplayName("the tree is built on construction")
        void builtEagerly() {
            var tree = new ElementTree(new Row(new Label("a"), new Label("b")));

            assertEquals(2, tree.root().children().size());
            assertEquals("a", labelOf(tree.root().children().get(0)));
            assertFalse(tree.needsBuild());
        }

        @Test
        @DisplayName("a stateless widget contributes an element but builds through")
        void statelessComposes() {
            var tree = new ElementTree(new Wrapper(new Label("inner")));

            // The wrapper is a node in the element tree -- it has to be, or there
            // would be nowhere to hang its rebuild -- and its child is what it
            // described.
            assertEquals(1, tree.root().children().size());
            assertEquals("inner", labelOf(tree.root().children().getFirst()));
        }

        @Test
        @DisplayName("a stateful widget gets its state once, mounted before the first build")
        void statefulMounts() {
            var tree = new ElementTree(new Counter("c"));
            var state = stateOf(tree.root());

            assertEquals(1, state.initialised);
            assertEquals(1, state.builds);
            assertTrue(state.isMounted());
        }
    }

    @Nested
    @DisplayName("setState and the rebuild schedule")
    class Rebuilding {

        @Test
        @DisplayName("setState mutates immediately but defers the rebuild")
        void deferredRebuild() {
            var tree = new ElementTree(new Counter("c"));
            var state = stateOf(tree.root());

            state.click();

            // The mutation is visible at once -- code after setState sees the new
            // value, which is what everyone expects.
            assertEquals(1, state.clicks);
            // The build is not.
            assertEquals(1, state.builds);
            assertTrue(tree.needsBuild());

            tree.flush();
            assertEquals(2, state.builds);
            assertEquals("c:1", labelOf(tree.root().children().getFirst()));
        }

        @Test
        @DisplayName("many setState calls in one frame cost one build")
        void coalescing() {
            var tree = new ElementTree(new Counter("c"));
            var state = stateOf(tree.root());

            for (var i = 0; i < 10; i++) {
                state.click();
            }
            assertEquals(1, tree.flush());

            assertEquals(10, state.clicks);
            assertEquals(2, state.builds);
        }

        @Test
        @DisplayName("a flush with nothing dirty does nothing")
        void emptyFlush() {
            var tree = new ElementTree(new Counter("c"));
            assertEquals(0, tree.flush());
        }

        @Test
        @DisplayName("a setState during a build settles in the same flush")
        void setStateDuringBuild() {
            var settles = new Widget.Stateful() {
                @Override
                public State<?> createState() {
                    return new State<Widget.Stateful>() {
                        int builds;

                        @Override
                        public Widget build(BuildContext context) {
                            builds++;
                            if (builds < 3) {
                                setState(() -> { });
                            }
                            return new Label("settled after " + builds);
                        }
                    };
                }
            };

            var tree = new ElementTree(settles);
            tree.flush();

            // Legal, and it has to converge before the frame is painted.
            assertFalse(tree.needsBuild());
            assertEquals("settled after 3", labelOf(tree.root().children().getFirst()));
        }

        @Test
        @DisplayName("a build that never settles gives up rather than spinning")
        void runawayBuild() {
            var runaway = new Widget.Stateful() {
                @Override
                public State<?> createState() {
                    return new State<Widget.Stateful>() {
                        @Override
                        public Widget build(BuildContext context) {
                            setState(() -> { });
                            return new Label("never done");
                        }
                    };
                }
            };

            var tree = new ElementTree(runaway);
            // A frozen window with nothing in the log is a bad way to report an
            // application bug; this returns and warns.
            tree.flush();
            assertFalse(tree.needsBuild());
        }
    }

    @Nested
    @DisplayName("reconciliation")
    class Reconciliation {

        @Test
        @DisplayName("the same type at the same position keeps its element and state")
        void updatesInPlace() {
            var tree = new ElementTree(new Counter("first"));
            var element = tree.root();
            var state = stateOf(element);
            state.click();
            tree.flush();

            element.update(new Counter("second"));

            assertSame(state, stateOf(tree.root()), "state must survive a rebuild");
            assertEquals(1, state.clicks);
            assertEquals(1, state.initialised, "initState must not run again");
            assertEquals(List.of("first"), state.updatedFrom);
        }

        @Test
        @DisplayName("a different type at the same position replaces the element")
        void replacesOnTypeChange() {
            var tree = new ElementTree(new Row(new Counter("c")));
            var before = tree.root().children().getFirst();
            var state = stateOf(before);

            tree.root().update(new Row(new Label("plain")));

            assertNotSame(before, tree.root().children().getFirst());
            assertEquals(1, state.disposed, "the replaced element's state must be disposed");
            assertFalse(before.isMounted());
        }

        @Test
        @DisplayName("a different key at the same position replaces the element too")
        void replacesOnKeyChange() {
            var tree = new ElementTree(new Row(List.of(new Counter("c", "one")), null));
            var before = tree.root().children().getFirst();

            tree.root().update(new Row(List.of(new Counter("c", "two")), null));

            // The author said these are different things even though they look
            // alike, and the framework has to believe them.
            assertNotSame(before, tree.root().children().getFirst());
        }

        @Test
        @DisplayName("keyed children keep their state when reordered")
        void reorderingKeepsState() {
            var tree = new ElementTree(new Row(
                    List.of(new Counter("a", "a"), new Counter("b", "b")), null));

            var stateA = stateOf(tree.root().children().get(0));
            var stateB = stateOf(tree.root().children().get(1));
            stateA.click();
            stateA.click();
            stateB.click();
            tree.flush();

            tree.root().update(new Row(
                    List.of(new Counter("b", "b"), new Counter("a", "a")), null));

            // This is the case keys exist for: without them, position matching
            // would hand A's element to B and silently swap their counters.
            assertSame(stateB, stateOf(tree.root().children().get(0)));
            assertSame(stateA, stateOf(tree.root().children().get(1)));
            assertEquals(1, stateB.clicks);
            assertEquals(2, stateA.clicks);
        }

        @Test
        @DisplayName("removed children are unmounted and disposed, deepest included")
        void removalDisposes() {
            var tree = new ElementTree(new Row(new Row(new Counter("deep"))));
            var deep = tree.root().children().getFirst().children().getFirst();
            var state = stateOf(deep);

            tree.root().update(new Row());

            assertEquals(1, state.disposed);
            assertFalse(deep.isMounted());
            assertTrue(tree.root().children().isEmpty());
        }

        @Test
        @DisplayName("an unkeyed child does not steal an element a key claimed")
        void unkeyedDoesNotStealKeyed() {
            var tree = new ElementTree(new Row(
                    List.of(new Counter("keyed", "k"), new Counter("plain")), null));
            var keyedState = stateOf(tree.root().children().get(0));
            keyedState.click();
            tree.flush();

            // The keyed one moves to the end; an unkeyed description now sits at
            // position 0. It must create a new element rather than adopt the
            // keyed one.
            tree.root().update(new Row(
                    List.of(new Counter("plain"), new Counter("keyed", "k")), null));

            assertSame(keyedState, stateOf(tree.root().children().get(1)));
            assertEquals(0, stateOf(tree.root().children().get(0)).clicks);
        }
    }

    @Nested
    @DisplayName("lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("setState after dispose is a noisy error, not a silent leak")
        void setStateAfterDispose() {
            var tree = new ElementTree(new Row(new Counter("c")));
            var state = stateOf(tree.root().children().getFirst());
            tree.root().update(new Row());

            // A callback that outlived its widget is a bug worth hearing about.
            assertThrows(IllegalStateException.class, state::click);
        }

        @Test
        @DisplayName("unmounting the tree disposes every state")
        void unmountAll() {
            var tree = new ElementTree(new Row(new Counter("a"), new Counter("b")));
            var a = stateOf(tree.root().children().get(0));
            var b = stateOf(tree.root().children().get(1));

            tree.unmount();

            assertEquals(1, a.disposed);
            assertEquals(1, b.disposed);
        }
    }

    @Nested
    @DisplayName("BuildContext")
    class Context {

        @Test
        @DisplayName("findAncestor walks up to the nearest enclosing widget")
        void findAncestor() {
            var found = new ArrayList<Row>();
            var probe = new Widget.Stateless() {
                @Override
                public Widget build(BuildContext context) {
                    context.findAncestor(Row.class).ifPresent(found::add);
                    return new Label("probe");
                }
            };

            new ElementTree(new Row(new Wrapper(probe)));

            // How a radio finds its group and a field finds its form.
            assertEquals(1, found.size());
        }

        @Test
        @DisplayName("depth counts from the root")
        void depth() {
            var tree = new ElementTree(new Row(new Row(new Label("leaf"))));
            assertEquals(0, tree.root().depth());
            assertEquals(2, tree.root().children().getFirst().children().getFirst().depth());
        }
    }

    @Nested
    @DisplayName("as a StyleElement")
    class AsStyleElement {

        @Test
        @DisplayName("a styled widget's type is its kebab-case name")
        void typeName() {
            record TextInput(Object key) implements Widget.Leaf, Styled {
            }

            var tree = new ElementTree(new TextInput(null));
            // One name in one place: `text-input` in CSS, in KDL and in Java.
            assertEquals("text-input", tree.root().type());
        }

        @Test
        @DisplayName("classes and id come from the widget")
        void classesAndId() {
            record Button(String id, Set<String> classes) implements Widget.Leaf, Styled {
            }

            var tree = new ElementTree(new Button("apply", Set.of("primary")));

            assertEquals("apply", tree.root().id());
            assertEquals(Set.of("primary"), tree.root().classes());
        }

        @Test
        @DisplayName("pseudo-classes live on the element and survive a rebuild")
        void pseudoClassesSurviveRebuild() {
            var tree = new ElementTree(new Counter("c"));
            var element = tree.root();

            assertTrue(element.setPseudoClass(
                    io.github.digitalsmile.goldberry.css.Selector.PseudoClass.HOVER, true));
            element.update(new Counter("c2"));

            // A button does not stop being hovered because its parent
            // re-described it.
            assertTrue(element.hasState(
                    io.github.digitalsmile.goldberry.css.Selector.PseudoClass.HOVER));
        }

        @Test
        @DisplayName("the cascade can walk from a leaf up through composition wrappers")
        void ancestorChain() {
            record Panel(Object key) implements Widget.Leaf, Styled {
                @Override
                public List<Widget> children() {
                    return List.of(new Wrapper(new Label("deep")));
                }
            }

            var tree = new ElementTree(new Panel(null));
            var label = tree.root().children().getFirst().children().getFirst();

            assertEquals("label", label.type());
            // The wrapper is in the chain and matches nothing -- a widget that is
            // not Styled has no CSS type at all -- so a descendant selector still
            // reaches the panel, exactly as an unstyled <div> behaves. Deriving a
            // name here would make every private composition class selectable.
            assertNull(label.parent().type());
            assertTrue(label.parent().classes().isEmpty());
            assertSame(tree.root(), label.parent().parent());
        }
    }
}
