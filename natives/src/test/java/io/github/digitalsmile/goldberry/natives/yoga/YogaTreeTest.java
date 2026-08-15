package io.github.digitalsmile.goldberry.natives.yoga;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Who owns a node, and what happens to the ones that break the rules.
///
/// Every case here is something Yoga answers with an `abort()` or with silent
/// corruption — a freed pointer still held by a live Java object, a child whose
/// parent no longer exists, a tree touched from two threads. None of them is
/// catchable once it reaches native code, so the whole point of [YogaNode] is
/// that they are refused before they get there. These tests are the evidence
/// that they are.
class YogaTreeTest {

    @BeforeAll
    static void requireNativeLibrary() {
        NativeLibraryRequirement.enforce();
    }

    @Test
    @DisplayName("Yoga's own child count agrees with the one Java keeps")
    void theTwoViewsOfTheTreeAgree() {
        try (var config = YogaConfig.create();
                var root = YogaNode.create(config)) {

            var first = YogaNode.create(config);
            var second = YogaNode.create(config);
            var inserted = YogaNode.create(config);
            root.addChild(first);
            root.addChild(second);
            root.insertChild(inserted, 1);

            // The Java list exists so that children() can hand back wrappers
            // rather than pointers. That it is not a fiction is this assertion.
            assertEquals(3, root.childCount());
            assertEquals(3L, root.nativeChildCount(), "and Yoga counts the same");
            assertEquals(List.of(first, inserted, second), root.children(), "in the order given");
            assertSame(root, inserted.parent());
            assertNull(root.parent(), "a root has no parent");
        }
    }

    @Test
    @DisplayName("closing a root frees the whole subtree")
    void closingARootClosesItsDescendants() {
        var config = YogaConfig.create();
        var root = YogaNode.create(config);
        var child = YogaNode.create(config);
        var grandchild = YogaNode.create(config);
        root.addChild(child);
        child.addChild(grandchild);

        root.close();

        assertTrue(root.isClosed(), "the root");
        assertTrue(child.isClosed(), "and the child");
        assertTrue(grandchild.isClosed(), "and the one below it");

        // The reference is still here; the memory is not. Reading a layout off
        // it would be a use-after-free, so it is refused.
        var thrown = assertThrows(IllegalStateException.class, grandchild::layout);
        assertTrue(thrown.getMessage().contains("closed"), thrown.getMessage());

        assertDoesNotThrow(root::close, "closing twice does nothing");
        assertDoesNotThrow(config::close, "and the config is free to go once its nodes have");
    }

    @Test
    @DisplayName("a child cannot be closed out from under its parent")
    void aChildIsClosedByItsParent() {
        try (var config = YogaConfig.create();
                var root = YogaNode.create(config)) {

            var child = YogaNode.create(config);
            root.addChild(child);

            // Freeing it here would leave Yoga's own child list pointing at
            // released memory, and the next layout pass would walk into it.
            var thrown = assertThrows(IllegalStateException.class, child::close);

            assertTrue(thrown.getMessage().contains("removeChild"), thrown.getMessage());
            assertFalse(child.isClosed(), "and it is still alive");
        }
    }

    @Test
    @DisplayName("removing a child hands ownership back")
    void removingAChildMakesItARootAgain() {
        try (var config = YogaConfig.create();
                var root = YogaNode.create(config)) {

            var child = YogaNode.create(config);
            child.setWidth(StyleLength.points(40f));
            root.addChild(child);
            root.removeChild(child);

            assertEquals(0, root.childCount());
            assertEquals(0L, root.nativeChildCount());
            assertNull(child.parent(), "a root again");

            // Still alive, still carrying the style it was given, and now the
            // caller's problem.
            child.calculateLayout(YogaNode.UNDEFINED, YogaNode.UNDEFINED);
            assertEquals(40f, child.layout().width());
            child.close();
        }
    }

    @Test
    @DisplayName("removeAllChildren detaches every one of them")
    void removeAllChildrenDetachesEveryone() {
        try (var config = YogaConfig.create();
                var root = YogaNode.create(config)) {

            var first = YogaNode.create(config);
            var second = YogaNode.create(config);
            root.addChild(first);
            root.addChild(second);

            root.removeAllChildren();

            assertEquals(0, root.childCount());
            assertEquals(0L, root.nativeChildCount());
            assertNull(first.parent());
            assertNull(second.parent());
            first.close();
            second.close();
        }
    }

    @Test
    @DisplayName("a node cannot be given two parents")
    void aNodeHasOneParent() {
        try (var config = YogaConfig.create();
                var first = YogaNode.create(config);
                var second = YogaNode.create(config)) {

            var child = YogaNode.create(config);
            first.addChild(child);

            var thrown = assertThrows(IllegalStateException.class, () -> second.addChild(child));

            assertTrue(thrown.getMessage().contains("already has a parent"), thrown.getMessage());
        }
    }

    @Test
    @DisplayName("a node cannot become its own ancestor")
    void cyclesAreRefused() {
        try (var config = YogaConfig.create();
                var root = YogaNode.create(config)) {

            var child = YogaNode.create(config);
            root.addChild(child);

            // A layout pass over a cycle does not terminate -- it recurses until
            // the stack runs out, inside native code.
            assertThrows(IllegalStateException.class, () -> root.addChild(root));
            assertThrows(IllegalStateException.class, () -> child.addChild(root));
        }
    }

    @Test
    @DisplayName("an out-of-range insertion index is refused")
    void insertionIndexIsChecked() {
        try (var config = YogaConfig.create();
                var root = YogaNode.create(config)) {

            var child = YogaNode.create(config);

            assertThrows(IndexOutOfBoundsException.class, () -> root.insertChild(child, 1));
            assertThrows(IndexOutOfBoundsException.class, () -> root.insertChild(child, -1));

            // The boundary case is legal: inserting at the end is appending.
            assertDoesNotThrow(() -> root.insertChild(child, 0));
        }
    }

    @Test
    @DisplayName("a config outlives its nodes, and says so if asked not to")
    void aConfigMayNotBeClosedUnderItsNodes() {
        var config = YogaConfig.create();
        var root = YogaNode.create(config);

        // Freeing the config here leaves every node in the tree pointing at it,
        // and the crash lands inside Yoga on the next pass rather than here.
        var thrown = assertThrows(IllegalStateException.class, config::close);
        assertTrue(thrown.getMessage().contains("live node"), thrown.getMessage());

        root.close();
        assertDoesNotThrow(config::close);
        assertTrue(config.isClosed());
        assertDoesNotThrow(config::close, "closing twice does nothing");
    }

    @Test
    @DisplayName("a closed config cannot make more nodes")
    void aClosedConfigIsUnusable() {
        var config = YogaConfig.create();
        config.close();

        assertThrows(IllegalStateException.class, () -> YogaNode.create(config));
        assertThrows(IllegalStateException.class, config::pointScaleFactor);
    }

    @Test
    @DisplayName("a tree belongs to the thread that built it")
    void nodesAreConfinedToTheirThread() throws InterruptedException {
        try (var root = YogaNode.create()) {
            var fromElsewhere = new AtomicReference<Throwable>();

            // Yoga has no locking at all. A tree touched from two threads does
            // not fail -- it corrupts, which is strictly worse.
            var other = Thread.ofVirtual().start(() -> {
                try {
                    root.setFlexGrow(1f);
                } catch (Throwable t) {
                    fromElsewhere.set(t);
                }
            });
            other.join();

            var thrown = assertInstanceOf(IllegalStateException.class, fromElsewhere.get());
            assertTrue(thrown.getMessage().contains("thread that created it"), thrown.getMessage());
        }
    }

    @Test
    @DisplayName("a node from another thread cannot be grafted into this tree")
    void aTreeMayNotSpanThreads() throws InterruptedException {
        try (var root = YogaNode.create()) {
            var elsewhere = new AtomicReference<YogaNode>();
            var builder = Thread.ofVirtual().start(() -> elsewhere.set(YogaNode.create()));
            builder.join();

            var thrown =
                    assertThrows(IllegalStateException.class, () -> root.addChild(elsewhere.get()));

            assertTrue(thrown.getMessage().contains("may not span threads"), thrown.getMessage());
            // That node stays allocated: closing it would have to happen on the
            // thread that made it, and that thread is gone. One leaked node in a
            // test process is a better trade than the synchronisation it would
            // take to avoid it.
        }
    }
}
