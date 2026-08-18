package io.github.digitalsmile.goldberry.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.bind.Property;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// What an element does with a binding: when it subscribes, when it rebuilds, and
/// — the half that leaks if it is wrong — when it lets go.
///
/// The other half of `:widgets`' `BindingTest`, which is about reading `bind=`
/// off markup. The two were one file until [ADR-0092] moved `text` out of
/// `:core`, and the seam turned out to be a real one: everything here is a fact
/// about [Element] and nothing here is a fact about `text`.
///
/// Written against a local [Bound] widget for that reason, and for the reason
/// `DragOriginTest` and `GestureAnchorTest` are: `:core` has no widgets of its
/// own, and a test of the element tree should not need one.
class BindingLifecycleTest {

    /// A leaf that follows a property — the smallest thing [Element] can
    /// subscribe to.
    private record Bound(Observable<?> source) implements Widget.Leaf {

        @Override
        public Observable<?> binding() {
            return source;
        }

        /// What the property says right now, which is what a rebuild has to have
        /// picked up.
        String resolved() {
            var value = source.get();
            return value == null ? "" : String.valueOf(value);
        }
    }

    /// A leaf that follows nothing, for the case where a bound child is replaced
    /// by one that is not.
    private record Plain() implements Widget.Leaf {
    }

    /// A container that re-describes its children, which is what a rebuild is.
    private record Parent(List<Widget> children) implements Widget.Leaf {

        Parent(Widget... kids) {
            this(List.of(kids));
        }

        @Override
        public List<Widget> children() {
            return children;
        }
    }

    @Test
    @DisplayName("a change marks the bound element as needing a build")
    void changeDirtiesTheElement() {
        var name = Property.of("Ada");
        var tree = new ElementTree(new Bound(name));
        tree.flush();
        assertFalse(tree.needsBuild());

        name.set("Grace");

        assertTrue(tree.needsBuild(), "the element did not hear the change");
        assertEquals(1, tree.flush());
        assertEquals("Grace", ((Bound) tree.root().widget()).resolved());
    }

    @Test
    @DisplayName("a value set twice in one frame costs one build")
    void changesCoalesce() {
        var name = Property.of("a");
        var tree = new ElementTree(new Bound(name));
        tree.flush();

        name.set("b");
        name.set("c");
        name.set("d");

        // The same bargain setState makes (ADR-0052): the value changes
        // immediately, the rebuild happens once.
        assertEquals(1, tree.flush());
        assertEquals("d", ((Bound) tree.root().widget()).resolved());
    }

    @Test
    @DisplayName("one element subscribes once, however many times it is rebuilt")
    void rebuildDoesNotAccumulateListeners() {
        var name = Property.of("Ada");
        var tree = new ElementTree(new Parent(new Bound(name)));

        assertEquals(1, name.listenerCount());
        for (var i = 0; i < 5; i++) {
            // A parent re-describing its child with an equal widget is the
            // commonest thing that happens in this tree. A listener per rebuild
            // would be a leak that grows with the frame count.
            tree.root().update(new Parent(new Bound(name)));
        }

        assertEquals(1, name.listenerCount());
    }

    @Test
    @DisplayName("a widget that names a different property follows the new one")
    void followsTheNewProperty() {
        var first = Property.of("a");
        var second = Property.of("b");
        var tree = new ElementTree(new Parent(new Bound(first)));

        tree.root().update(new Parent(new Bound(second)));

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
        var tree = new ElementTree(new Bound(name));
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
        var tree = new ElementTree(new Parent(new Bound(name)));
        assertEquals(1, name.listenerCount());

        tree.root().update(new Parent(new Plain()));

        assertEquals(0, name.listenerCount());
    }

    @Test
    @DisplayName("two widgets on one property both rebuild")
    void oneValueManyReaders() {
        var name = Property.of("Ada");
        var tree = new ElementTree(new Parent(new Bound(name), new Bound(name)));
        tree.flush();

        name.set("Grace");

        assertEquals(2, tree.flush(), "both bound elements should have rebuilt");
    }
}
