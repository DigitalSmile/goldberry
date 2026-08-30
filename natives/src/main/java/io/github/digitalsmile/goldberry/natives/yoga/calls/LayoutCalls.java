package io.github.digitalsmile.goldberry.natives.yoga.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import io.github.digitalsmile.goldberry.natives.Downcalls;

/// Yoga's layout results — what the last pass computed.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record LayoutCalls(
        LayoutGetLeft layoutGetLeft,
        LayoutGetTop layoutGetTop,
        LayoutGetWidth layoutGetWidth,
        LayoutGetHeight layoutGetHeight,
        LayoutGetMargin layoutGetMargin,
        LayoutGetBorder layoutGetBorder,
        LayoutGetPadding layoutGetPadding,
        LayoutGetDirection layoutGetDirection,
        LayoutGetHadOverflow layoutGetHadOverflow) {

    /// Binds every function above, failing if the library exports none of them.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static LayoutCalls bind(SymbolLookup lookup) {
        return new LayoutCalls(
                new LayoutGetLeft(lookup),
                new LayoutGetTop(lookup),
                new LayoutGetWidth(lookup),
                new LayoutGetHeight(lookup),
                new LayoutGetMargin(lookup),
                new LayoutGetBorder(lookup),
                new LayoutGetPadding(lookup),
                new LayoutGetDirection(lookup),
                new LayoutGetHadOverflow(lookup));
    }

    /// The node’s left edge, relative to its parent.
    ///
    /// `float YGNodeLayoutGetLeft(void*)`
    ///
    /// @return points
    public static final class LayoutGetLeft {

        private static final MethodHandle FD_YGNodeLayoutGetLeft =
                Downcalls.link(FunctionDescriptor.of(JAVA_FLOAT, ADDRESS));

        private final MemorySegment address;

        LayoutGetLeft(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeLayoutGetLeft");
        }

        public float call(MemorySegment node) {
            try {
                return (float) FD_YGNodeLayoutGetLeft.invokeExact(address, node);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeLayoutGetLeft", t);
            }
        }
    }

    /// The node’s top edge, relative to its parent.
    ///
    /// `float YGNodeLayoutGetTop(void*)`
    ///
    /// @return points
    public static final class LayoutGetTop {

        private static final MethodHandle FD_YGNodeLayoutGetTop =
                Downcalls.link(FunctionDescriptor.of(JAVA_FLOAT, ADDRESS));

        private final MemorySegment address;

        LayoutGetTop(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeLayoutGetTop");
        }

        public float call(MemorySegment node) {
            try {
                return (float) FD_YGNodeLayoutGetTop.invokeExact(address, node);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeLayoutGetTop", t);
            }
        }
    }

    /// The node’s computed width.
    ///
    /// `float YGNodeLayoutGetWidth(void*)`
    ///
    /// @return points
    public static final class LayoutGetWidth {

        private static final MethodHandle FD_YGNodeLayoutGetWidth =
                Downcalls.link(FunctionDescriptor.of(JAVA_FLOAT, ADDRESS));

        private final MemorySegment address;

        LayoutGetWidth(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeLayoutGetWidth");
        }

        public float call(MemorySegment node) {
            try {
                return (float) FD_YGNodeLayoutGetWidth.invokeExact(address, node);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeLayoutGetWidth", t);
            }
        }
    }

    /// The node’s computed height.
    ///
    /// `float YGNodeLayoutGetHeight(void*)`
    ///
    /// @return points
    public static final class LayoutGetHeight {

        private static final MethodHandle FD_YGNodeLayoutGetHeight =
                Downcalls.link(FunctionDescriptor.of(JAVA_FLOAT, ADDRESS));

        private final MemorySegment address;

        LayoutGetHeight(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeLayoutGetHeight");
        }

        public float call(MemorySegment node) {
            try {
                return (float) FD_YGNodeLayoutGetHeight.invokeExact(address, node);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeLayoutGetHeight", t);
            }
        }
    }

    /// The margin the pass resolved on one edge.
    ///
    /// Resolved, not declared: a `margin-inline-start` has already become a left
    /// or a right by the time it is readable here, which is why the edge must be
    /// a physical one.
    ///
    /// `float YGNodeLayoutGetMargin(void*, int)`
    ///
    /// @param edge a **physical** `YGEdge` — LEFT or RIGHT, never START or END
    /// @return points
    public static final class LayoutGetMargin {

        private static final MethodHandle FD_YGNodeLayoutGetMargin =
                Downcalls.link(FunctionDescriptor.of(JAVA_FLOAT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        LayoutGetMargin(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeLayoutGetMargin");
        }

        public float call(MemorySegment node, int edge) {
            try {
                return (float) FD_YGNodeLayoutGetMargin.invokeExact(address, node, edge);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeLayoutGetMargin", t);
            }
        }
    }

    /// The border width the pass resolved on one edge.
    ///
    /// `float YGNodeLayoutGetBorder(void*, int)`
    ///
    /// @param edge a physical `YGEdge`
    /// @return points
    public static final class LayoutGetBorder {

        private static final MethodHandle FD_YGNodeLayoutGetBorder =
                Downcalls.link(FunctionDescriptor.of(JAVA_FLOAT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        LayoutGetBorder(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeLayoutGetBorder");
        }

        public float call(MemorySegment node, int edge) {
            try {
                return (float) FD_YGNodeLayoutGetBorder.invokeExact(address, node, edge);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeLayoutGetBorder", t);
            }
        }
    }

    /// The padding the pass resolved on one edge.
    ///
    /// `float YGNodeLayoutGetPadding(void*, int)`
    ///
    /// @param edge a physical `YGEdge`
    /// @return points
    public static final class LayoutGetPadding {

        private static final MethodHandle FD_YGNodeLayoutGetPadding =
                Downcalls.link(FunctionDescriptor.of(JAVA_FLOAT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        LayoutGetPadding(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeLayoutGetPadding");
        }

        public float call(MemorySegment node, int edge) {
            try {
                return (float) FD_YGNodeLayoutGetPadding.invokeExact(address, node, edge);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeLayoutGetPadding", t);
            }
        }
    }

    /// The direction the node was actually laid out in, with `inherit` resolved.
    ///
    /// `int YGNodeLayoutGetDirection(void*)`
    ///
    /// @return a `YGDirection`
    public static final class LayoutGetDirection {

        private static final MethodHandle FD_YGNodeLayoutGetDirection =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        LayoutGetDirection(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeLayoutGetDirection");
        }

        public int call(MemorySegment node) {
            try {
                return (int) FD_YGNodeLayoutGetDirection.invokeExact(address, node);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeLayoutGetDirection", t);
            }
        }
    }

    /// Whether any child overflowed this node in the last pass.
    ///
    /// `_Bool YGNodeLayoutGetHadOverflow(void*)`
    ///
    /// @return true if something did not fit
    public static final class LayoutGetHadOverflow {

        private static final MethodHandle FD_YGNodeLayoutGetHadOverflow =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        LayoutGetHadOverflow(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeLayoutGetHadOverflow");
        }

        public boolean call(MemorySegment node) {
            try {
                return (boolean) FD_YGNodeLayoutGetHadOverflow.invokeExact(address, node);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeLayoutGetHadOverflow", t);
            }
        }
    }
}
