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

/// Yoga's config, node, style and layout functions, one holder each.
///
/// Except for the length setters, which are the one place a function is
/// chosen at **run time**: `width: 50%` and `width: 50px` are
/// `YGNodeStyleSetWidthPercent` and `YGNodeStyleSetWidth`, and which one is
/// called depends on the value. Those are [LengthCalls] and
/// [KeyedLengthCalls] below -- a holder per *shape* rather than per function,
/// each carrying the symbol it was bound to so a failure still names it.
///
/// See [io.github.digitalsmile.goldberry.natives.calls] for why the holders
/// live in a package of their own.
public record YogaCalls(
        ConfigNew configNew,
        ConfigFree configFree,
        ConfigSetPointScaleFactor configSetPointScaleFactor,
        ConfigGetPointScaleFactor configGetPointScaleFactor,
        ConfigSetUseWebDefaults configSetUseWebDefaults,
        ConfigGetUseWebDefaults configGetUseWebDefaults,
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
        NodeCalculateLayout nodeCalculateLayout,
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
        StyleSetBorder styleSetBorder,
        LayoutGetLeft layoutGetLeft,
        LayoutGetTop layoutGetTop,
        LayoutGetWidth layoutGetWidth,
        LayoutGetHeight layoutGetHeight,
        LayoutGetMargin layoutGetMargin,
        LayoutGetBorder layoutGetBorder,
        LayoutGetPadding layoutGetPadding,
        LayoutGetDirection layoutGetDirection,
        LayoutGetHadOverflow layoutGetHadOverflow) {

    /// Binds every function above.
    public static YogaCalls bind(SymbolLookup lookup) {
        return new YogaCalls(
                new ConfigNew(lookup),
                new ConfigFree(lookup),
                new ConfigSetPointScaleFactor(lookup),
                new ConfigGetPointScaleFactor(lookup),
                new ConfigSetUseWebDefaults(lookup),
                new ConfigGetUseWebDefaults(lookup),
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
                new NodeCalculateLayout(lookup),
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
                new StyleSetBorder(lookup),
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

    /// `void* YGConfigNew(void)`
    public static final class ConfigNew {

        private static final MethodHandle FD_YGConfigNew =
                Downcalls.link(FunctionDescriptor.of(ADDRESS));

        private final MemorySegment address;

        ConfigNew(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGConfigNew");
        }

        public MemorySegment call() {
            try {
                return (MemorySegment) FD_YGConfigNew.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("YGConfigNew", t);
            }
        }
    }

    /// `void YGConfigFree(void*)`
    public static final class ConfigFree {

        private static final MethodHandle FD_YGConfigFree =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        ConfigFree(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGConfigFree");
        }

        public void call(MemorySegment a1) {
            try {
                FD_YGConfigFree.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("YGConfigFree", t);
            }
        }
    }

    /// `void YGConfigSetPointScaleFactor(void*, float)`
    public static final class ConfigSetPointScaleFactor {

        private static final MethodHandle FD_YGConfigSetPointScaleFactor =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_FLOAT));

        private final MemorySegment address;

        ConfigSetPointScaleFactor(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGConfigSetPointScaleFactor");
        }

        public void call(MemorySegment a1, float a2) {
            try {
                FD_YGConfigSetPointScaleFactor.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("YGConfigSetPointScaleFactor", t);
            }
        }
    }

    /// `float YGConfigGetPointScaleFactor(void*)`
    public static final class ConfigGetPointScaleFactor {

        private static final MethodHandle FD_YGConfigGetPointScaleFactor =
                Downcalls.link(FunctionDescriptor.of(JAVA_FLOAT, ADDRESS));

        private final MemorySegment address;

        ConfigGetPointScaleFactor(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGConfigGetPointScaleFactor");
        }

        public float call(MemorySegment a1) {
            try {
                return (float) FD_YGConfigGetPointScaleFactor.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("YGConfigGetPointScaleFactor", t);
            }
        }
    }

    /// `void YGConfigSetUseWebDefaults(void*, _Bool)`
    public static final class ConfigSetUseWebDefaults {

        private static final MethodHandle FD_YGConfigSetUseWebDefaults =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_BOOLEAN));

        private final MemorySegment address;

        ConfigSetUseWebDefaults(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGConfigSetUseWebDefaults");
        }

        public void call(MemorySegment a1, boolean a2) {
            try {
                FD_YGConfigSetUseWebDefaults.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("YGConfigSetUseWebDefaults", t);
            }
        }
    }

    /// `_Bool YGConfigGetUseWebDefaults(void*)`
    public static final class ConfigGetUseWebDefaults {

        private static final MethodHandle FD_YGConfigGetUseWebDefaults =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        ConfigGetUseWebDefaults(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGConfigGetUseWebDefaults");
        }

        public boolean call(MemorySegment a1) {
            try {
                return (boolean) FD_YGConfigGetUseWebDefaults.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("YGConfigGetUseWebDefaults", t);
            }
        }
    }

    /// `void* YGNodeNew(void)`
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

    /// `void* YGNodeNewWithConfig(void*)`
    public static final class NodeNewWithConfig {

        private static final MethodHandle FD_YGNodeNewWithConfig =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS));

        private final MemorySegment address;

        NodeNewWithConfig(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeNewWithConfig");
        }

        public MemorySegment call(MemorySegment a1) {
            try {
                return (MemorySegment) FD_YGNodeNewWithConfig.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeNewWithConfig", t);
            }
        }
    }

    /// `void YGNodeFree(void*)`
    public static final class NodeFree {

        private static final MethodHandle FD_YGNodeFree =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        NodeFree(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeFree");
        }

        public void call(MemorySegment a1) {
            try {
                FD_YGNodeFree.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeFree", t);
            }
        }
    }

    /// `void YGNodeInsertChild(void*, void*, int64_t)`
    public static final class NodeInsertChild {

        private static final MethodHandle FD_YGNodeInsertChild =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_LONG));

        private final MemorySegment address;

        NodeInsertChild(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeInsertChild");
        }

        public void call(MemorySegment a1, MemorySegment a2, long a3) {
            try {
                FD_YGNodeInsertChild.invokeExact(address, a1, a2, a3);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeInsertChild", t);
            }
        }
    }

    /// `void YGNodeRemoveChild(void*, void*)`
    public static final class NodeRemoveChild {

        private static final MethodHandle FD_YGNodeRemoveChild =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));

        private final MemorySegment address;

        NodeRemoveChild(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeRemoveChild");
        }

        public void call(MemorySegment a1, MemorySegment a2) {
            try {
                FD_YGNodeRemoveChild.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeRemoveChild", t);
            }
        }
    }

    /// `void YGNodeRemoveAllChildren(void*)`
    public static final class NodeRemoveAllChildren {

        private static final MethodHandle FD_YGNodeRemoveAllChildren =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        NodeRemoveAllChildren(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeRemoveAllChildren");
        }

        public void call(MemorySegment a1) {
            try {
                FD_YGNodeRemoveAllChildren.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeRemoveAllChildren", t);
            }
        }
    }

    /// `int64_t YGNodeGetChildCount(void*)`
    public static final class NodeGetChildCount {

        private static final MethodHandle FD_YGNodeGetChildCount =
                Downcalls.link(FunctionDescriptor.of(JAVA_LONG, ADDRESS));

        private final MemorySegment address;

        NodeGetChildCount(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeGetChildCount");
        }

        public long call(MemorySegment a1) {
            try {
                return (long) FD_YGNodeGetChildCount.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeGetChildCount", t);
            }
        }
    }

    /// `void YGNodeSetMeasureFunc(void*, void*)`
    public static final class NodeSetMeasureFunc {

        private static final MethodHandle FD_YGNodeSetMeasureFunc =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));

        private final MemorySegment address;

        NodeSetMeasureFunc(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeSetMeasureFunc");
        }

        public void call(MemorySegment a1, MemorySegment a2) {
            try {
                FD_YGNodeSetMeasureFunc.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeSetMeasureFunc", t);
            }
        }
    }

    /// `_Bool YGNodeHasMeasureFunc(void*)`
    public static final class NodeHasMeasureFunc {

        private static final MethodHandle FD_YGNodeHasMeasureFunc =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        NodeHasMeasureFunc(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeHasMeasureFunc");
        }

        public boolean call(MemorySegment a1) {
            try {
                return (boolean) FD_YGNodeHasMeasureFunc.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeHasMeasureFunc", t);
            }
        }
    }

    /// `void YGNodeMarkDirty(void*)`
    public static final class NodeMarkDirty {

        private static final MethodHandle FD_YGNodeMarkDirty =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        NodeMarkDirty(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeMarkDirty");
        }

        public void call(MemorySegment a1) {
            try {
                FD_YGNodeMarkDirty.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeMarkDirty", t);
            }
        }
    }

    /// `_Bool YGNodeIsDirty(void*)`
    public static final class NodeIsDirty {

        private static final MethodHandle FD_YGNodeIsDirty =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        NodeIsDirty(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeIsDirty");
        }

        public boolean call(MemorySegment a1) {
            try {
                return (boolean) FD_YGNodeIsDirty.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeIsDirty", t);
            }
        }
    }

    /// `_Bool YGNodeGetHasNewLayout(void*)`
    public static final class NodeGetHasNewLayout {

        private static final MethodHandle FD_YGNodeGetHasNewLayout =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        NodeGetHasNewLayout(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeGetHasNewLayout");
        }

        public boolean call(MemorySegment a1) {
            try {
                return (boolean) FD_YGNodeGetHasNewLayout.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeGetHasNewLayout", t);
            }
        }
    }

    /// `void YGNodeSetHasNewLayout(void*, _Bool)`
    public static final class NodeSetHasNewLayout {

        private static final MethodHandle FD_YGNodeSetHasNewLayout =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_BOOLEAN));

        private final MemorySegment address;

        NodeSetHasNewLayout(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeSetHasNewLayout");
        }

        public void call(MemorySegment a1, boolean a2) {
            try {
                FD_YGNodeSetHasNewLayout.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeSetHasNewLayout", t);
            }
        }
    }

    /// `void YGNodeCalculateLayout(void*, float, float, int)`
    public static final class NodeCalculateLayout {

        private static final MethodHandle FD_YGNodeCalculateLayout =
                Downcalls.link(FunctionDescriptor.ofVoid(
                        ADDRESS, JAVA_FLOAT, JAVA_FLOAT, JAVA_INT));

        private final MemorySegment address;

        NodeCalculateLayout(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeCalculateLayout");
        }

        public void call(MemorySegment a1, float a2, float a3, int a4) {
            try {
                FD_YGNodeCalculateLayout.invokeExact(address, a1, a2, a3, a4);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeCalculateLayout", t);
            }
        }
    }

    /// `void YGNodeStyleSetDirection(void*, int)`
    public static final class StyleSetDirection {

        private static final MethodHandle FD_YGNodeStyleSetDirection =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        StyleSetDirection(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeStyleSetDirection");
        }

        public void call(MemorySegment a1, int a2) {
            try {
                FD_YGNodeStyleSetDirection.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeStyleSetDirection", t);
            }
        }
    }

    /// `void YGNodeStyleSetFlexDirection(void*, int)`
    public static final class StyleSetFlexDirection {

        private static final MethodHandle FD_YGNodeStyleSetFlexDirection =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        StyleSetFlexDirection(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeStyleSetFlexDirection");
        }

        public void call(MemorySegment a1, int a2) {
            try {
                FD_YGNodeStyleSetFlexDirection.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeStyleSetFlexDirection", t);
            }
        }
    }

    /// `void YGNodeStyleSetJustifyContent(void*, int)`
    public static final class StyleSetJustifyContent {

        private static final MethodHandle FD_YGNodeStyleSetJustifyContent =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        StyleSetJustifyContent(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeStyleSetJustifyContent");
        }

        public void call(MemorySegment a1, int a2) {
            try {
                FD_YGNodeStyleSetJustifyContent.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeStyleSetJustifyContent", t);
            }
        }
    }

    /// `void YGNodeStyleSetAlignContent(void*, int)`
    public static final class StyleSetAlignContent {

        private static final MethodHandle FD_YGNodeStyleSetAlignContent =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        StyleSetAlignContent(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeStyleSetAlignContent");
        }

        public void call(MemorySegment a1, int a2) {
            try {
                FD_YGNodeStyleSetAlignContent.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeStyleSetAlignContent", t);
            }
        }
    }

    /// `void YGNodeStyleSetAlignItems(void*, int)`
    public static final class StyleSetAlignItems {

        private static final MethodHandle FD_YGNodeStyleSetAlignItems =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        StyleSetAlignItems(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeStyleSetAlignItems");
        }

        public void call(MemorySegment a1, int a2) {
            try {
                FD_YGNodeStyleSetAlignItems.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeStyleSetAlignItems", t);
            }
        }
    }

    /// `void YGNodeStyleSetAlignSelf(void*, int)`
    public static final class StyleSetAlignSelf {

        private static final MethodHandle FD_YGNodeStyleSetAlignSelf =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        StyleSetAlignSelf(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeStyleSetAlignSelf");
        }

        public void call(MemorySegment a1, int a2) {
            try {
                FD_YGNodeStyleSetAlignSelf.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeStyleSetAlignSelf", t);
            }
        }
    }

    /// `void YGNodeStyleSetPositionType(void*, int)`
    public static final class StyleSetPositionType {

        private static final MethodHandle FD_YGNodeStyleSetPositionType =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        StyleSetPositionType(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeStyleSetPositionType");
        }

        public void call(MemorySegment a1, int a2) {
            try {
                FD_YGNodeStyleSetPositionType.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeStyleSetPositionType", t);
            }
        }
    }

    /// `void YGNodeStyleSetFlexWrap(void*, int)`
    public static final class StyleSetFlexWrap {

        private static final MethodHandle FD_YGNodeStyleSetFlexWrap =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        StyleSetFlexWrap(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeStyleSetFlexWrap");
        }

        public void call(MemorySegment a1, int a2) {
            try {
                FD_YGNodeStyleSetFlexWrap.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeStyleSetFlexWrap", t);
            }
        }
    }

    /// `void YGNodeStyleSetOverflow(void*, int)`
    public static final class StyleSetOverflow {

        private static final MethodHandle FD_YGNodeStyleSetOverflow =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        StyleSetOverflow(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeStyleSetOverflow");
        }

        public void call(MemorySegment a1, int a2) {
            try {
                FD_YGNodeStyleSetOverflow.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeStyleSetOverflow", t);
            }
        }
    }

    /// `void YGNodeStyleSetDisplay(void*, int)`
    public static final class StyleSetDisplay {

        private static final MethodHandle FD_YGNodeStyleSetDisplay =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        StyleSetDisplay(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeStyleSetDisplay");
        }

        public void call(MemorySegment a1, int a2) {
            try {
                FD_YGNodeStyleSetDisplay.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeStyleSetDisplay", t);
            }
        }
    }

    /// `void YGNodeStyleSetFlexGrow(void*, float)`
    public static final class StyleSetFlexGrow {

        private static final MethodHandle FD_YGNodeStyleSetFlexGrow =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_FLOAT));

        private final MemorySegment address;

        StyleSetFlexGrow(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeStyleSetFlexGrow");
        }

        public void call(MemorySegment a1, float a2) {
            try {
                FD_YGNodeStyleSetFlexGrow.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeStyleSetFlexGrow", t);
            }
        }
    }

    /// `void YGNodeStyleSetFlexShrink(void*, float)`
    public static final class StyleSetFlexShrink {

        private static final MethodHandle FD_YGNodeStyleSetFlexShrink =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_FLOAT));

        private final MemorySegment address;

        StyleSetFlexShrink(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeStyleSetFlexShrink");
        }

        public void call(MemorySegment a1, float a2) {
            try {
                FD_YGNodeStyleSetFlexShrink.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeStyleSetFlexShrink", t);
            }
        }
    }

    /// `void YGNodeStyleSetAspectRatio(void*, float)`
    public static final class StyleSetAspectRatio {

        private static final MethodHandle FD_YGNodeStyleSetAspectRatio =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_FLOAT));

        private final MemorySegment address;

        StyleSetAspectRatio(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeStyleSetAspectRatio");
        }

        public void call(MemorySegment a1, float a2) {
            try {
                FD_YGNodeStyleSetAspectRatio.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeStyleSetAspectRatio", t);
            }
        }
    }

    /// `void YGNodeStyleSetBorder(void*, int, float)`
    public static final class StyleSetBorder {

        private static final MethodHandle FD_YGNodeStyleSetBorder =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, JAVA_FLOAT));

        private final MemorySegment address;

        StyleSetBorder(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeStyleSetBorder");
        }

        public void call(MemorySegment a1, int a2, float a3) {
            try {
                FD_YGNodeStyleSetBorder.invokeExact(address, a1, a2, a3);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeStyleSetBorder", t);
            }
        }
    }

    /// `float YGNodeLayoutGetLeft(void*)`
    public static final class LayoutGetLeft {

        private static final MethodHandle FD_YGNodeLayoutGetLeft =
                Downcalls.link(FunctionDescriptor.of(JAVA_FLOAT, ADDRESS));

        private final MemorySegment address;

        LayoutGetLeft(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeLayoutGetLeft");
        }

        public float call(MemorySegment a1) {
            try {
                return (float) FD_YGNodeLayoutGetLeft.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeLayoutGetLeft", t);
            }
        }
    }

    /// `float YGNodeLayoutGetTop(void*)`
    public static final class LayoutGetTop {

        private static final MethodHandle FD_YGNodeLayoutGetTop =
                Downcalls.link(FunctionDescriptor.of(JAVA_FLOAT, ADDRESS));

        private final MemorySegment address;

        LayoutGetTop(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeLayoutGetTop");
        }

        public float call(MemorySegment a1) {
            try {
                return (float) FD_YGNodeLayoutGetTop.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeLayoutGetTop", t);
            }
        }
    }

    /// `float YGNodeLayoutGetWidth(void*)`
    public static final class LayoutGetWidth {

        private static final MethodHandle FD_YGNodeLayoutGetWidth =
                Downcalls.link(FunctionDescriptor.of(JAVA_FLOAT, ADDRESS));

        private final MemorySegment address;

        LayoutGetWidth(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeLayoutGetWidth");
        }

        public float call(MemorySegment a1) {
            try {
                return (float) FD_YGNodeLayoutGetWidth.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeLayoutGetWidth", t);
            }
        }
    }

    /// `float YGNodeLayoutGetHeight(void*)`
    public static final class LayoutGetHeight {

        private static final MethodHandle FD_YGNodeLayoutGetHeight =
                Downcalls.link(FunctionDescriptor.of(JAVA_FLOAT, ADDRESS));

        private final MemorySegment address;

        LayoutGetHeight(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeLayoutGetHeight");
        }

        public float call(MemorySegment a1) {
            try {
                return (float) FD_YGNodeLayoutGetHeight.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeLayoutGetHeight", t);
            }
        }
    }

    /// `float YGNodeLayoutGetMargin(void*, int)`
    public static final class LayoutGetMargin {

        private static final MethodHandle FD_YGNodeLayoutGetMargin =
                Downcalls.link(FunctionDescriptor.of(JAVA_FLOAT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        LayoutGetMargin(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeLayoutGetMargin");
        }

        public float call(MemorySegment a1, int a2) {
            try {
                return (float) FD_YGNodeLayoutGetMargin.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeLayoutGetMargin", t);
            }
        }
    }

    /// `float YGNodeLayoutGetBorder(void*, int)`
    public static final class LayoutGetBorder {

        private static final MethodHandle FD_YGNodeLayoutGetBorder =
                Downcalls.link(FunctionDescriptor.of(JAVA_FLOAT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        LayoutGetBorder(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeLayoutGetBorder");
        }

        public float call(MemorySegment a1, int a2) {
            try {
                return (float) FD_YGNodeLayoutGetBorder.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeLayoutGetBorder", t);
            }
        }
    }

    /// `float YGNodeLayoutGetPadding(void*, int)`
    public static final class LayoutGetPadding {

        private static final MethodHandle FD_YGNodeLayoutGetPadding =
                Downcalls.link(FunctionDescriptor.of(JAVA_FLOAT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        LayoutGetPadding(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeLayoutGetPadding");
        }

        public float call(MemorySegment a1, int a2) {
            try {
                return (float) FD_YGNodeLayoutGetPadding.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeLayoutGetPadding", t);
            }
        }
    }

    /// `int YGNodeLayoutGetDirection(void*)`
    public static final class LayoutGetDirection {

        private static final MethodHandle FD_YGNodeLayoutGetDirection =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        LayoutGetDirection(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeLayoutGetDirection");
        }

        public int call(MemorySegment a1) {
            try {
                return (int) FD_YGNodeLayoutGetDirection.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeLayoutGetDirection", t);
            }
        }
    }

    /// `_Bool YGNodeLayoutGetHadOverflow(void*)`
    public static final class LayoutGetHadOverflow {

        private static final MethodHandle FD_YGNodeLayoutGetHadOverflow =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        LayoutGetHadOverflow(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "YGNodeLayoutGetHadOverflow");
        }

        public boolean call(MemorySegment a1) {
            try {
                return (boolean) FD_YGNodeLayoutGetHadOverflow.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("YGNodeLayoutGetHadOverflow", t);
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

    /// `void f(void*, float)` -- a points or percent setter.
    public static final class SetLength {

        private static final MethodHandle FD_YGNodeStyleSetLength =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_FLOAT));

        private final String symbol;
        private final MemorySegment address;

        SetLength(SymbolLookup lookup, String symbol) {
            this.symbol = symbol;
            this.address = Downcalls.symbol(lookup, symbol);
        }

        public void call(MemorySegment a1, float a2) {
            try {
                FD_YGNodeStyleSetLength.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure(symbol, t);
            }
        }
    }

    /// `void f(void*)` -- an `auto` setter, which takes the node and nothing else.
    public static final class SetAuto {

        private static final MethodHandle FD_YGNodeStyleSetAuto =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final String symbol;
        private final MemorySegment address;

        SetAuto(SymbolLookup lookup, String symbol) {
            this.symbol = symbol;
            this.address = Downcalls.symbol(lookup, symbol);
        }

        public void call(MemorySegment a1) {
            try {
                FD_YGNodeStyleSetAuto.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure(symbol, t);
            }
        }
    }

    /// `void f(void*, int, float)` -- the same, for a property keyed by an edge
    /// or a gutter.
    public static final class SetKeyedLength {

        private static final MethodHandle FD_YGNodeStyleSetKeyedLength =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, JAVA_FLOAT));

        private final String symbol;
        private final MemorySegment address;

        SetKeyedLength(SymbolLookup lookup, String symbol) {
            this.symbol = symbol;
            this.address = Downcalls.symbol(lookup, symbol);
        }

        public void call(MemorySegment a1, int a2, float a3) {
            try {
                FD_YGNodeStyleSetKeyedLength.invokeExact(address, a1, a2, a3);
            } catch (Throwable t) {
                throw Downcalls.failure(symbol, t);
            }
        }
    }

    /// `void f(void*, int)` -- a keyed `auto` setter, which takes the edge but no
    /// value.
    public static final class SetKeyedAuto {

        private static final MethodHandle FD_YGNodeStyleSetKeyedAuto =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));

        private final String symbol;
        private final MemorySegment address;

        SetKeyedAuto(SymbolLookup lookup, String symbol) {
            this.symbol = symbol;
            this.address = Downcalls.symbol(lookup, symbol);
        }

        public void call(MemorySegment a1, int a2) {
            try {
                FD_YGNodeStyleSetKeyedAuto.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure(symbol, t);
            }
        }
    }

    /// The functions a length-valued property is set through.
    ///
    /// `auto` is null for the properties Yoga has no `*Auto` function for. There
    /// is no undefined variant: Yoga's way to unset a property is to pass
    /// `YGUndefined` -- a NaN -- to the points function.
    public record LengthCalls(SetLength points, SetLength percent, SetAuto auto) {

        /// Binds `YGNodeStyleSet<property>`, its `Percent` twin, and its `Auto`
        /// one when Yoga has it.
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
    public record KeyedLengthCalls(
            SetKeyedLength points, SetKeyedLength percent, SetKeyedAuto auto) {

        public static KeyedLengthCalls bind(
                SymbolLookup lookup, String property, boolean hasAuto) {
            var prefix = "YGNodeStyleSet" + property;
            return new KeyedLengthCalls(
                    new SetKeyedLength(lookup, prefix),
                    new SetKeyedLength(lookup, prefix + "Percent"),
                    hasAuto ? new SetKeyedAuto(lookup, prefix + "Auto") : null);
        }
    }
}
