package io.github.digitalsmile.goldberry.natives.yoga.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import io.github.digitalsmile.goldberry.natives.Downcalls;

/// Yoga's style setters — everything a stylesheet decides.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record StyleCalls(
        StyleSetDirection styleSetDirection,
        StyleSetFlexDirection styleSetFlexDirection,
        StyleSetJustifyContent styleSetJustifyContent,
        StyleSetAlignContent styleSetAlignContent,
        StyleSetAlignItems styleSetAlignItems,
        StyleSetAlignSelf styleSetAlignSelf,
        StyleSetPositionType styleSetPositionType,
        StyleSetFlexWrap styleSetFlexWrap,
        StyleSetOverflow styleSetOverflow,
        StyleSetDisplay styleSetDisplay,
        StyleSetFlexGrow styleSetFlexGrow,
        StyleSetFlexShrink styleSetFlexShrink,
        StyleSetAspectRatio styleSetAspectRatio,
        StyleSetBorder styleSetBorder) {

    /// Binds every function above, failing if the library exports none of them.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static StyleCalls bind(SymbolLookup lookup) {
        return new StyleCalls(
                new StyleSetDirection(lookup),
                new StyleSetFlexDirection(lookup),
                new StyleSetJustifyContent(lookup),
                new StyleSetAlignContent(lookup),
                new StyleSetAlignItems(lookup),
                new StyleSetAlignSelf(lookup),
                new StyleSetPositionType(lookup),
                new StyleSetFlexWrap(lookup),
                new StyleSetOverflow(lookup),
                new StyleSetDisplay(lookup),
                new StyleSetFlexGrow(lookup),
                new StyleSetFlexShrink(lookup),
                new StyleSetAspectRatio(lookup),
                new StyleSetBorder(lookup));
    }

    /// Sets which way the node reads — LTR, RTL, or inherit.
    ///
    /// `void YGNodeStyleSetDirection(void*, int)`
    public static final class StyleSetDirection {

        private static final MethodHandle FD_YGNodeStyleSetDirection =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        StyleSetDirection(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeStyleSetDirection");
        }

        /// Calls `YGNodeStyleSetDirection`.
        ///
        /// @param direction a `YGDirection`
        public void call(MemorySegment node, int direction) {
            try {
                FD_YGNodeStyleSetDirection.invokeExact(address, node, direction);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeStyleSetDirection", t);
            }
        }
    }

    /// Sets the main axis — CSS’s `flex-direction`.
    ///
    /// `void YGNodeStyleSetFlexDirection(void*, int)`
    public static final class StyleSetFlexDirection {

        private static final MethodHandle FD_YGNodeStyleSetFlexDirection =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        StyleSetFlexDirection(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeStyleSetFlexDirection");
        }

        /// Calls `YGNodeStyleSetFlexDirection`.
        ///
        /// @param flexDirection a `YGFlexDirection`
        public void call(MemorySegment node, int flexDirection) {
            try {
                FD_YGNodeStyleSetFlexDirection.invokeExact(address, node, flexDirection);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeStyleSetFlexDirection", t);
            }
        }
    }

    /// Sets alignment along the main axis — CSS’s `justify-content`.
    ///
    /// `void YGNodeStyleSetJustifyContent(void*, int)`
    public static final class StyleSetJustifyContent {

        private static final MethodHandle FD_YGNodeStyleSetJustifyContent =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        StyleSetJustifyContent(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeStyleSetJustifyContent");
        }

        /// Calls `YGNodeStyleSetJustifyContent`.
        ///
        /// @param justify a `YGJustify`
        public void call(MemorySegment node, int justify) {
            try {
                FD_YGNodeStyleSetJustifyContent.invokeExact(address, node, justify);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeStyleSetJustifyContent", t);
            }
        }
    }

    /// Sets how wrapped lines are distributed — CSS’s `align-content`.
    ///
    /// `void YGNodeStyleSetAlignContent(void*, int)`
    public static final class StyleSetAlignContent {

        private static final MethodHandle FD_YGNodeStyleSetAlignContent =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        StyleSetAlignContent(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeStyleSetAlignContent");
        }

        /// Calls `YGNodeStyleSetAlignContent`.
        ///
        /// @param align a `YGAlign`
        public void call(MemorySegment node, int align) {
            try {
                FD_YGNodeStyleSetAlignContent.invokeExact(address, node, align);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeStyleSetAlignContent", t);
            }
        }
    }

    /// Sets alignment across the main axis — CSS’s `align-items`.
    ///
    /// `void YGNodeStyleSetAlignItems(void*, int)`
    public static final class StyleSetAlignItems {

        private static final MethodHandle FD_YGNodeStyleSetAlignItems =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        StyleSetAlignItems(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeStyleSetAlignItems");
        }

        /// Calls `YGNodeStyleSetAlignItems`.
        ///
        /// @param align a `YGAlign`
        public void call(MemorySegment node, int align) {
            try {
                FD_YGNodeStyleSetAlignItems.invokeExact(address, node, align);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeStyleSetAlignItems", t);
            }
        }
    }

    /// Overrides the parent’s `align-items` for one child — CSS’s `align-self`.
    ///
    /// `void YGNodeStyleSetAlignSelf(void*, int)`
    public static final class StyleSetAlignSelf {

        private static final MethodHandle FD_YGNodeStyleSetAlignSelf =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        StyleSetAlignSelf(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeStyleSetAlignSelf");
        }

        /// Calls `YGNodeStyleSetAlignSelf`.
        ///
        /// @param align a `YGAlign`
        public void call(MemorySegment node, int align) {
            try {
                FD_YGNodeStyleSetAlignSelf.invokeExact(address, node, align);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeStyleSetAlignSelf", t);
            }
        }
    }

    /// Sets whether the node is in flow — CSS’s `position`.
    ///
    /// `void YGNodeStyleSetPositionType(void*, int)`
    public static final class StyleSetPositionType {

        private static final MethodHandle FD_YGNodeStyleSetPositionType =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        StyleSetPositionType(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeStyleSetPositionType");
        }

        /// Calls `YGNodeStyleSetPositionType`.
        ///
        /// @param positionType a `YGPositionType`
        public void call(MemorySegment node, int positionType) {
            try {
                FD_YGNodeStyleSetPositionType.invokeExact(address, node, positionType);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeStyleSetPositionType", t);
            }
        }
    }

    /// Sets whether children wrap onto new lines — CSS’s `flex-wrap`.
    ///
    /// `void YGNodeStyleSetFlexWrap(void*, int)`
    public static final class StyleSetFlexWrap {

        private static final MethodHandle FD_YGNodeStyleSetFlexWrap =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        StyleSetFlexWrap(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeStyleSetFlexWrap");
        }

        /// Calls `YGNodeStyleSetFlexWrap`.
        ///
        /// @param wrap a `YGWrap`
        public void call(MemorySegment node, int wrap) {
            try {
                FD_YGNodeStyleSetFlexWrap.invokeExact(address, node, wrap);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeStyleSetFlexWrap", t);
            }
        }
    }

    /// Sets what happens to content that does not fit — CSS’s `overflow`.
    ///
    /// `void YGNodeStyleSetOverflow(void*, int)`
    public static final class StyleSetOverflow {

        private static final MethodHandle FD_YGNodeStyleSetOverflow =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        StyleSetOverflow(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeStyleSetOverflow");
        }

        /// Calls `YGNodeStyleSetOverflow`.
        ///
        /// @param overflow a `YGOverflow`
        public void call(MemorySegment node, int overflow) {
            try {
                FD_YGNodeStyleSetOverflow.invokeExact(address, node, overflow);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeStyleSetOverflow", t);
            }
        }
    }

    /// Sets whether the node lays out at all — CSS’s `display`.
    ///
    /// `void YGNodeStyleSetDisplay(void*, int)`
    public static final class StyleSetDisplay {

        private static final MethodHandle FD_YGNodeStyleSetDisplay =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        StyleSetDisplay(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeStyleSetDisplay");
        }

        /// Calls `YGNodeStyleSetDisplay`.
        ///
        /// @param display a `YGDisplay`
        public void call(MemorySegment node, int display) {
            try {
                FD_YGNodeStyleSetDisplay.invokeExact(address, node, display);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeStyleSetDisplay", t);
            }
        }
    }

    /// Sets how eagerly the node takes leftover space — CSS’s `flex-grow`.
    ///
    /// `void YGNodeStyleSetFlexGrow(void*, float)`
    public static final class StyleSetFlexGrow {

        private static final MethodHandle FD_YGNodeStyleSetFlexGrow =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_FLOAT));

        private final MemorySegment address;

        StyleSetFlexGrow(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeStyleSetFlexGrow");
        }

        /// Calls `YGNodeStyleSetFlexGrow`.
        ///
        /// @param flexGrow a share of the free space, 0 for none
        public void call(MemorySegment node, float flexGrow) {
            try {
                FD_YGNodeStyleSetFlexGrow.invokeExact(address, node, flexGrow);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeStyleSetFlexGrow", t);
            }
        }
    }

    /// Sets how readily the node gives up space — CSS’s `flex-shrink`.
    ///
    /// `void YGNodeStyleSetFlexShrink(void*, float)`
    public static final class StyleSetFlexShrink {

        private static final MethodHandle FD_YGNodeStyleSetFlexShrink =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_FLOAT));

        private final MemorySegment address;

        StyleSetFlexShrink(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeStyleSetFlexShrink");
        }

        /// Calls `YGNodeStyleSetFlexShrink`.
        ///
        /// @param flexShrink a share of the overflow, 0 to refuse to shrink
        public void call(MemorySegment node, float flexShrink) {
            try {
                FD_YGNodeStyleSetFlexShrink.invokeExact(address, node, flexShrink);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeStyleSetFlexShrink", t);
            }
        }
    }

    /// Ties one axis to the other — CSS’s `aspect-ratio`.
    ///
    /// `void YGNodeStyleSetAspectRatio(void*, float)`
    public static final class StyleSetAspectRatio {

        private static final MethodHandle FD_YGNodeStyleSetAspectRatio =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_FLOAT));

        private final MemorySegment address;

        StyleSetAspectRatio(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeStyleSetAspectRatio");
        }

        /// Calls `YGNodeStyleSetAspectRatio`.
        ///
        /// @param aspectRatio width divided by height, or NaN to unset
        public void call(MemorySegment node, float aspectRatio) {
            try {
                FD_YGNodeStyleSetAspectRatio.invokeExact(address, node, aspectRatio);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeStyleSetAspectRatio", t);
            }
        }
    }

    /// Sets a border width on one edge.
    ///
    /// Points-only: there is no percent or auto function for it, which matches
    /// CSS — a percentage `border-width` is not a thing.
    ///
    /// `void YGNodeStyleSetBorder(void*, int, float)`
    public static final class StyleSetBorder {

        private static final MethodHandle FD_YGNodeStyleSetBorder =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, JAVA_FLOAT));

        private final MemorySegment address;

        StyleSetBorder(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeStyleSetBorder");
        }

        /// Calls `YGNodeStyleSetBorder`.
        ///
        /// @param edge a `YGEdge`
        /// @param border in points
        public void call(MemorySegment node, int edge, float border) {
            try {
                FD_YGNodeStyleSetBorder.invokeExact(address, node, edge, border);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeStyleSetBorder", t);
            }
        }
    }

    // --- the length dispatch ------------------------------------------------
    //
    // Eleven properties, each set through two or three C functions that differ
    // only in their address. A holder per function would need eleven record
    // types to group them by property, so these are holders per *shape*: the
    // handle is still a constant read inside `call`, which is what ADR-0173
    // requires, and the symbol travels on the instance so a failure names the
    // function that failed rather than the shape.

    /// A points or percent setter — `YGNodeStyleSetWidth`, `…WidthPercent`, and
    /// the nine other properties shaped like them.
    ///
    /// `void f(void*, float)`
    public static final class SetLength {

        private static final MethodHandle FD_YGNodeStyleSetLength =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_FLOAT));

        private final String symbol;
        private final MemorySegment address;

        SetLength(SymbolLookup lookup, String symbol) {
            this.symbol = symbol;
            this.address = Downcalls.symbol(lookup, symbol);
        }

        /// @param node  the node to style
        /// @param value points, or a percentage, depending which of the two this is
        public void call(MemorySegment node, float value) {
            try {
                FD_YGNodeStyleSetLength.invokeExact(address, node, value);
            } catch (Throwable t) {
                throw Downcalls.failure(symbol, t);
            }
        }
    }

    /// An `auto` setter, which takes the node and nothing else.
    ///
    /// `void f(void*)`
    public static final class SetAuto {

        private static final MethodHandle FD_YGNodeStyleSetAuto = Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final String symbol;
        private final MemorySegment address;

        SetAuto(SymbolLookup lookup, String symbol) {
            this.symbol = symbol;
            this.address = Downcalls.symbol(lookup, symbol);
        }

        /// @param node the node to style
        public void call(MemorySegment node) {
            try {
                FD_YGNodeStyleSetAuto.invokeExact(address, node);
            } catch (Throwable t) {
                throw Downcalls.failure(symbol, t);
            }
        }
    }

    /// The same as [StyleCalls.SetLength], for a property keyed by an edge or a
    /// gutter — `YGNodeStyleSetMargin`, `YGNodeStyleSetGap`.
    ///
    /// `void f(void*, int, float)`
    public static final class SetKeyedLength {

        private static final MethodHandle FD_YGNodeStyleSetKeyedLength =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, JAVA_FLOAT));

        private final String symbol;
        private final MemorySegment address;

        SetKeyedLength(SymbolLookup lookup, String symbol) {
            this.symbol = symbol;
            this.address = Downcalls.symbol(lookup, symbol);
        }

        /// @param node  the node to style
        /// @param key   a `YGEdge` or a `YGGutter`
        /// @param value points, or a percentage, depending which of the two this is
        public void call(MemorySegment node, int key, float value) {
            try {
                FD_YGNodeStyleSetKeyedLength.invokeExact(address, node, key, value);
            } catch (Throwable t) {
                throw Downcalls.failure(symbol, t);
            }
        }
    }

    /// A keyed `auto` setter, which takes the edge but no value.
    ///
    /// `void f(void*, int)`
    public static final class SetKeyedAuto {

        private static final MethodHandle FD_YGNodeStyleSetKeyedAuto =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));

        private final String symbol;
        private final MemorySegment address;

        SetKeyedAuto(SymbolLookup lookup, String symbol) {
            this.symbol = symbol;
            this.address = Downcalls.symbol(lookup, symbol);
        }

        /// @param node the node to style
        /// @param key  a `YGEdge` or a `YGGutter`
        public void call(MemorySegment node, int key) {
            try {
                FD_YGNodeStyleSetKeyedAuto.invokeExact(address, node, key);
            } catch (Throwable t) {
                throw Downcalls.failure(symbol, t);
            }
        }
    }

    /// The two or three functions a length-valued property is set through.
    ///
    /// `auto` is null for the properties Yoga has no `*Auto` function for. There
    /// is no undefined variant: Yoga's way to unset a property is to pass
    /// `YGUndefined` -- a NaN -- to the points function.
    public record LengthCalls(SetLength points, SetLength percent, SetAuto auto) {

        /// Binds `YGNodeStyleSet<property>`, its `Percent` twin, and its `Auto`
        /// one when Yoga has it.
        ///
        /// The names are mechanical, so they are composed rather than written out
        /// three times. Composing one that does not exist is not a silent
        /// failure: the lookup throws, naming the missing symbol.
        ///
        /// @param lookup   the loaded `libgoldberry`
        /// @param property the name between `YGNodeStyleSet` and the suffix
        /// @param hasAuto  whether Yoga exports an `Auto` variant for it
        public static LengthCalls bind(SymbolLookup lookup, String property, boolean hasAuto) {
            var prefix = "YGNodeStyleSet" + property;
            return new LengthCalls(
                    new SetLength(lookup, prefix),
                    new SetLength(lookup, prefix + "Percent"),
                    hasAuto ? new SetAuto(lookup, prefix + "Auto") : null);
        }
    }

    /// The same, for a property keyed by an edge or a gutter. The C shape is
    /// identical -- `(node, int, float)` -- so one record serves both.
    public record KeyedLengthCalls(SetKeyedLength points, SetKeyedLength percent, SetKeyedAuto auto) {

        /// @param lookup   the loaded `libgoldberry`
        /// @param property the name between `YGNodeStyleSet` and the suffix
        /// @param hasAuto  whether Yoga exports an `Auto` variant for it
        public static KeyedLengthCalls bind(SymbolLookup lookup, String property, boolean hasAuto) {
            var prefix = "YGNodeStyleSet" + property;
            return new KeyedLengthCalls(
                    new SetKeyedLength(lookup, prefix),
                    new SetKeyedLength(lookup, prefix + "Percent"),
                    hasAuto ? new SetKeyedAuto(lookup, prefix + "Auto") : null);
        }
    }
}
