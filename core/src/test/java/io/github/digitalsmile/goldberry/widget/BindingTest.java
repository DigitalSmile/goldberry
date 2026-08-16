package io.github.digitalsmile.goldberry.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.bind.Bindings;
import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// `bind=` from markup to the element tree (§9, ADR-0062).
class BindingTest {

    private static Widget inflate(String markup, Bindings bindings) {
        return Widgets.inflater(bindings).inflate(KdlParser.parse(markup).getFirst());
    }

    @Nested
    @DisplayName("inflating")
    class Inflating {

        @Test
        @DisplayName("a bound text is the same value however it was built")
        void parity() {
            var name = Property.of("Ada");
            var bindings = Bindings.strict().bind("user.name", name);

            var fromMarkup = inflate("text id=\"who\" bind=\"user.name\"", bindings);
            var fromJava = new Widgets.Text(
                    "", name, new Widgets.Attributes("who", java.util.Set.of(), "who"));

            // The parity invariant of §11, extended to the attribute: markup and
            // Java produce the same widget, and the property is the same object
            // rather than a copy of its value.
            assertEquals(fromJava, fromMarkup);
            assertSame(name, ((Widgets.Text) fromMarkup).binding());
        }

        @Test
        @DisplayName("a bound text shows what the property holds, not its argument")
        void boundValueWins() {
            var bindings = Bindings.strict().bind("user.name", Property.of("Ada"));

            var text = (Widgets.Text) inflate("text bind=\"user.name\" \"placeholder\"", bindings);

            assertEquals("Ada", text.resolved());
        }

        @Test
        @DisplayName("an unbound path leaves the argument as what is drawn")
        void lenientFallsBackToTheLiteral() {
            // Markup-first: the screen is laid out before the model exists, and a
            // designer needs to see something (ADR-0051).
            var text = (Widgets.Text) inflate("text bind=\"user.name\" \"Name here\"", Bindings.lenient());

            assertEquals("Name here", text.resolved());
            assertEquals(null, text.binding(), "nothing to follow, so nothing is subscribed to");
        }

        @Test
        @DisplayName("a null value reads as nothing rather than as the word null")
        void nullReadsAsEmpty() {
            var bindings = Bindings.strict().bind("user.name", Property.of(null));

            assertEquals("", ((Widgets.Text) inflate("text bind=\"user.name\"", bindings)).resolved());
        }

        @Test
        @DisplayName("an expression in bind= fails at inflation, with the text quoted")
        void expressionFailsLoudly() {
            var bindings = Bindings.strict().bind("prefs.frost", Property.of(true));

            var thrown = assertThrows(IllegalArgumentException.class,
                    () -> inflate("text bind=\"!prefs.frost\"", bindings));

            assertTrue(thrown.getMessage().contains("!prefs.frost"), thrown.getMessage());
        }
    }

    @Nested
    @DisplayName("in the element tree")
    class InTheTree {

        @Test
        @DisplayName("a change marks the bound element as needing a build")
        void changeDirtiesTheElement() {
            var name = Property.of("Ada");
            var tree = new ElementTree(new Widgets.Text("", name, Widgets.Attributes.NONE));
            tree.flush();
            assertFalse(tree.needsBuild());

            name.set("Grace");

            assertTrue(tree.needsBuild(), "the element did not hear the change");
            assertEquals(1, tree.flush());
            assertEquals("Grace", ((Widgets.Text) tree.root().widget()).resolved());
        }

        @Test
        @DisplayName("a value set twice in one frame costs one build")
        void changesCoalesce() {
            var name = Property.of("a");
            var tree = new ElementTree(new Widgets.Text("", name, Widgets.Attributes.NONE));
            tree.flush();

            name.set("b");
            name.set("c");
            name.set("d");

            // The same bargain setState makes (ADR-0052): the value changes
            // immediately, the rebuild happens once.
            assertEquals(1, tree.flush());
            assertEquals("d", ((Widgets.Text) tree.root().widget()).resolved());
        }

        @Test
        @DisplayName("one element subscribes once, however many times it is rebuilt")
        void rebuildDoesNotAccumulateListeners() {
            var name = Property.of("Ada");
            var parent = new Parent(new Widgets.Text("", name, Widgets.Attributes.NONE));
            var tree = new ElementTree(parent);

            assertEquals(1, name.listenerCount());
            for (var i = 0; i < 5; i++) {
                // A parent re-describing its child with an equal widget is the
                // commonest thing that happens in this tree. A listener per
                // rebuild would be a leak that grows with the frame count.
                tree.root().update(new Parent(new Widgets.Text("", name, Widgets.Attributes.NONE)));
            }

            assertEquals(1, name.listenerCount());
        }

        @Test
        @DisplayName("a widget that names a different property follows the new one")
        void followsTheNewProperty() {
            var first = Property.of("a");
            var second = Property.of("b");
            var tree = new ElementTree(new Parent(new Widgets.Text("", first, Widgets.Attributes.NONE)));

            tree.root().update(new Parent(new Widgets.Text("", second, Widgets.Attributes.NONE)));

            assertEquals(0, first.listenerCount(), "still listening to the property it no longer names");
            assertEquals(1, second.listenerCount());

            tree.flush();
            second.set("c");
            assertTrue(tree.needsBuild());
        }

        @Test
        @DisplayName("an unmounted element lets go of the property")
        void unmountUnsubscribes() {
            var name = Property.of("Ada");
            var tree = new ElementTree(new Widgets.Text("", name, Widgets.Attributes.NONE));
            assertEquals(1, name.listenerCount());

            tree.unmount();

            // A property outlives the tree -- it is the application's -- so a
            // listener left behind keeps the whole subtree alive and rebuilds
            // something nobody can see.
            assertEquals(0, name.listenerCount());
            name.set("Grace");
            assertFalse(tree.needsBuild());
        }

        @Test
        @DisplayName("a child dropped from the tree lets go too")
        void replacedChildUnsubscribes() {
            var name = Property.of("Ada");
            var tree = new ElementTree(new Parent(new Widgets.Text("", name, Widgets.Attributes.NONE)));
            assertEquals(1, name.listenerCount());

            tree.root().update(new Parent(new Widgets.Spacer()));

            assertEquals(0, name.listenerCount());
        }

        @Test
        @DisplayName("two widgets on one property both rebuild")
        void oneValueManyReaders() {
            var name = Property.of("Ada");
            var tree = new ElementTree(new Widgets.Row(
                    List.of(new Widgets.Text("", name, Widgets.Attributes.NONE),
                            new Widgets.Text("", name, Widgets.Attributes.NONE)),
                    Widgets.Attributes.NONE));
            tree.flush();

            name.set("Grace");

            assertEquals(2, tree.flush(), "both bound elements should have rebuilt");
        }
    }

    /// A container that re-describes its child, which is what a rebuild is.
    private record Parent(Widget child) implements Widget.Leaf {

        @Override
        public List<Widget> children() {
            return List.of(child);
        }
    }
}
