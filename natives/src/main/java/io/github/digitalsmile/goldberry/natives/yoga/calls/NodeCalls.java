package io.github.digitalsmile.goldberry.natives.yoga.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import io.github.digitalsmile.goldberry.natives.Downcalls;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/// Yoga's `YGNode` — the tree, its lifecycle, and the layout pass.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See
/// [io.github.digitalsmile.goldberry.natives.calls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record NodeCalls(
        NodeNew nodeNew,
        NodeNewWithConfig nodeNewWithConfig,
        NodeFree nodeFree,
        NodeInsertChild nodeInsertChild,
        NodeRemoveChild nodeRemoveChild,
        NodeRemoveAllChildren nodeRemoveAllChildren,
        NodeGetChildCount nodeGetChildCount,
        NodeSetMeasureFunc nodeSetMeasureFunc,
        NodeHasMeasureFunc nodeHasMeasureFunc,
        NodeMarkDirty nodeMarkDirty,
        NodeIsDirty nodeIsDirty,
        NodeGetHasNewLayout nodeGetHasNewLayout,
        NodeSetHasNewLayout nodeSetHasNewLayout,
        NodeCalculateLayout nodeCalculateLayout) {

    /// Binds every function above, failing if the library exports none of them.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static NodeCalls bind(SymbolLookup lookup) {
        return new NodeCalls(
                new NodeNew(lookup),
                new NodeNewWithConfig(lookup),
                new NodeFree(lookup),
                new NodeInsertChild(lookup),
                new NodeRemoveChild(lookup),
                new NodeRemoveAllChildren(lookup),
                new NodeGetChildCount(lookup),
                new NodeSetMeasureFunc(lookup),
                new NodeHasMeasureFunc(lookup),
                new NodeMarkDirty(lookup),
                new NodeIsDirty(lookup),
                new NodeGetHasNewLayout(lookup),
                new NodeSetHasNewLayout(lookup),
                new NodeCalculateLayout(lookup));
    }

    /// Allocates a node under Yoga’s default config.
    ///
    /// `void* YGNodeNew(void)`
    ///
    /// @return the new `YGNodeRef`
    public static final class NodeNew {

        private static final MethodHandle FD_YGNodeNew =
                Downcalls.link(FunctionDescriptor.of(ADDRESS));

        private final MemorySegment address;

        NodeNew(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeNew");
        }

        public MemorySegment call() {
            try {
                return (MemorySegment) FD_YGNodeNew.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeNew", t);
            }
        }
    }

    /// Allocates a node under a config of the caller’s.
    ///
    /// `void* YGNodeNewWithConfig(void*)`
    ///
    /// @param config the config the node and its subtree are laid out under
    /// @return the new `YGNodeRef`
    public static final class NodeNewWithConfig {

        private static final MethodHandle FD_YGNodeNewWithConfig =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS));

        private final MemorySegment address;

        NodeNewWithConfig(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeNewWithConfig");
        }

        public MemorySegment call(MemorySegment config) {
            try {
                return (MemorySegment) FD_YGNodeNewWithConfig.invokeExact(address, config);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeNewWithConfig", t);
            }
        }
    }

    /// Releases one node.
    ///
    /// One node and not its subtree: freeing a tree is [YogaNode]’s job, which
    /// does it child-first and marks each Java wrapper dead on the way
    /// (ADR-0029).
    ///
    /// `void YGNodeFree(void*)`
    ///
    /// @param node the node to release; its children are not freed
    public static final class NodeFree {

        private static final MethodHandle FD_YGNodeFree =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        NodeFree(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeFree");
        }

        public void call(MemorySegment node) {
            try {
                FD_YGNodeFree.invokeExact(address, node);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeFree", t);
            }
        }
    }

    /// Inserts a child at an index.
    ///
    /// `void YGNodeInsertChild(void*, void*, int64_t)`
    ///
    /// @param node the parent
    /// @param child the node to insert; it must have no parent
    /// @param index where among the existing children
    public static final class NodeInsertChild {

        private static final MethodHandle FD_YGNodeInsertChild =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_LONG));

        private final MemorySegment address;

        NodeInsertChild(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeInsertChild");
        }

        public void call(MemorySegment node, MemorySegment child, long index) {
            try {
                FD_YGNodeInsertChild.invokeExact(address, node, child, index);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeInsertChild", t);
            }
        }
    }

    /// Detaches a child, which becomes parentless rather than freed.
    ///
    /// `void YGNodeRemoveChild(void*, void*)`
    ///
    /// @param node the parent
    /// @param child the child to detach
    public static final class NodeRemoveChild {

        private static final MethodHandle FD_YGNodeRemoveChild =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));

        private final MemorySegment address;

        NodeRemoveChild(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeRemoveChild");
        }

        public void call(MemorySegment node, MemorySegment child) {
            try {
                FD_YGNodeRemoveChild.invokeExact(address, node, child);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeRemoveChild", t);
            }
        }
    }

    /// Detaches every child at once.
    ///
    /// `void YGNodeRemoveAllChildren(void*)`
    ///
    /// @param node the parent to empty
    public static final class NodeRemoveAllChildren {

        private static final MethodHandle FD_YGNodeRemoveAllChildren =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        NodeRemoveAllChildren(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeRemoveAllChildren");
        }

        public void call(MemorySegment node) {
            try {
                FD_YGNodeRemoveAllChildren.invokeExact(address, node);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeRemoveAllChildren", t);
            }
        }
    }

    /// How many children the node has.
    ///
    /// `size_t`, which is 8 bytes on every target Goldberry builds for — the
    /// "size_t" scalar row in the layout table is what says so.
    ///
    /// `int64_t YGNodeGetChildCount(void*)`
    ///
    /// @param node the parent to count
    /// @return the child count
    public static final class NodeGetChildCount {

        private static final MethodHandle FD_YGNodeGetChildCount =
                Downcalls.link(FunctionDescriptor.of(JAVA_LONG, ADDRESS));

        private final MemorySegment address;

        NodeGetChildCount(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeGetChildCount");
        }

        public long call(MemorySegment node) {
            try {
                return (long) FD_YGNodeGetChildCount.invokeExact(address, node);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeGetChildCount", t);
            }
        }
    }

    /// Gives a leaf a callback that measures its own content.
    ///
    /// How text gets a size: Yoga cannot measure a paragraph, so it asks
    /// (ADR-0017).
    ///
    /// `void YGNodeSetMeasureFunc(void*, void*)`
    ///
    /// @param node a **leaf**; Yoga aborts if it has children
    /// @param measureFunc an upcall stub, or NULL to clear it
    public static final class NodeSetMeasureFunc {

        private static final MethodHandle FD_YGNodeSetMeasureFunc =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));

        private final MemorySegment address;

        NodeSetMeasureFunc(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeSetMeasureFunc");
        }

        public void call(MemorySegment node, MemorySegment measureFunc) {
            try {
                FD_YGNodeSetMeasureFunc.invokeExact(address, node, measureFunc);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeSetMeasureFunc", t);
            }
        }
    }

    /// Whether the node measures itself.
    ///
    /// `_Bool YGNodeHasMeasureFunc(void*)`
    ///
    /// @return true if a measure function is set
    public static final class NodeHasMeasureFunc {

        private static final MethodHandle FD_YGNodeHasMeasureFunc =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        NodeHasMeasureFunc(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeHasMeasureFunc");
        }

        public boolean call(MemorySegment node) {
            try {
                return (boolean) FD_YGNodeHasMeasureFunc.invokeExact(address, node);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeHasMeasureFunc", t);
            }
        }
    }

    /// Tells Yoga a measured leaf’s content changed.
    ///
    /// Only meaningful for a node that measures itself — every other node is
    /// dirtied by its own style being set.
    ///
    /// `void YGNodeMarkDirty(void*)`
    ///
    /// @param node a node with a measure function; Yoga aborts otherwise
    public static final class NodeMarkDirty {

        private static final MethodHandle FD_YGNodeMarkDirty =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        NodeMarkDirty(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeMarkDirty");
        }

        public void call(MemorySegment node) {
            try {
                FD_YGNodeMarkDirty.invokeExact(address, node);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeMarkDirty", t);
            }
        }
    }

    /// Whether the node needs laying out again.
    ///
    /// `_Bool YGNodeIsDirty(void*)`
    ///
    /// @return true if dirty
    public static final class NodeIsDirty {

        private static final MethodHandle FD_YGNodeIsDirty =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        NodeIsDirty(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeIsDirty");
        }

        public boolean call(MemorySegment node) {
            try {
                return (boolean) FD_YGNodeIsDirty.invokeExact(address, node);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeIsDirty", t);
            }
        }
    }

    /// Whether the last layout pass moved or resized this node.
    ///
    /// Yoga sets this and never clears it; the caller clears it after reading,
    /// which is what makes it a per-pass flag rather than a permanent one.
    ///
    /// `_Bool YGNodeGetHasNewLayout(void*)`
    ///
    /// @return true if this node’s layout changed
    public static final class NodeGetHasNewLayout {

        private static final MethodHandle FD_YGNodeGetHasNewLayout =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        NodeGetHasNewLayout(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeGetHasNewLayout");
        }

        public boolean call(MemorySegment node) {
            try {
                return (boolean) FD_YGNodeGetHasNewLayout.invokeExact(address, node);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeGetHasNewLayout", t);
            }
        }
    }

    /// Sets or clears the "layout changed" flag.
    ///
    /// `void YGNodeSetHasNewLayout(void*, _Bool)`
    ///
    /// @param hasNewLayout normally false, to clear the flag after reading it
    public static final class NodeSetHasNewLayout {

        private static final MethodHandle FD_YGNodeSetHasNewLayout =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_BOOLEAN));

        private final MemorySegment address;

        NodeSetHasNewLayout(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeSetHasNewLayout");
        }

        public void call(MemorySegment node, boolean hasNewLayout) {
            try {
                FD_YGNodeSetHasNewLayout.invokeExact(address, node, hasNewLayout);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeSetHasNewLayout", t);
            }
        }
    }

    /// Runs the layout pass over a subtree.
    ///
    /// Incremental: a node whose style has not been set since the last pass is
    /// not recomputed, which is why every setter here is guarded by a comparison
    /// (ADR-0069).
    ///
    /// `void YGNodeCalculateLayout(void*, float, float, int)`
    ///
    /// @param node the root to lay out
    /// @param availableWidth the space offered, or `YGUndefined` (NaN) for unbounded
    /// @param availableHeight the space offered, or `YGUndefined` (NaN) for unbounded
    /// @param ownerDirection a `YGDirection` — which way the parent reads
    public static final class NodeCalculateLayout {

        private static final MethodHandle FD_YGNodeCalculateLayout =
                Downcalls.link(FunctionDescriptor.ofVoid(
                        ADDRESS, JAVA_FLOAT, JAVA_FLOAT, JAVA_INT));

        private final MemorySegment address;

        NodeCalculateLayout(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeCalculateLayout");
        }

        public void call(
                MemorySegment node, float availableWidth, float availableHeight,
                int ownerDirection) {
            try {
                FD_YGNodeCalculateLayout.invokeExact(
                        address, node, availableWidth, availableHeight, ownerDirection);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeCalculateLayout", t);
            }
        }
    }
}
