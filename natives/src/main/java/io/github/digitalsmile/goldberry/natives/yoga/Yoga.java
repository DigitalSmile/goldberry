package io.github.digitalsmile.goldberry.natives.yoga;

import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/// Yoga's node, style, layout and config calls.
///
/// Package-private, and deliberately so. [Sdl][io.github.digitalsmile.goldberry.natives.sdl.Sdl]
/// and its siblings are public because `:core` drives SDL directly; nothing
/// above this module drives Yoga directly. What `:core` gets is [YogaNode],
/// which owns a pointer and enforces the rules Yoga only asserts. Keeping this
/// class package-private means there is no second way in — no path that reaches
/// `YGNodeFree` without going through the tree that knows which nodes are still
/// alive.
///
/// Every method takes the raw pointer as a [MemorySegment], which is exactly the
/// type `docs/ARCHITECTURE.md` §3.1 keeps inside this module. Package-private
/// members are not part of an exported package's surface, so the boundary holds
/// even though `natives.yoga` is exported.
///
/// **Yoga's enums cross as `int`.** `YG_ENUM_BEGIN` expands to a plain C `enum`,
/// which the ABI gives `int` width on every target Goldberry builds for. The
/// values themselves are checked against the compiled library through
/// [YogaEnum#all()].
final class Yoga {

    private static final Linker LINKER = Linker.nativeLinker();

    /// `void f(YGNodeRef)`
    private static final FunctionDescriptor NODE_VOID =
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS);

    /// `void f(YGNodeRef, float)`
    private static final FunctionDescriptor NODE_FLOAT =
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_FLOAT);

    /// `void f(YGNodeRef, <enum>)`
    private static final FunctionDescriptor NODE_ENUM =
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT);

    /// `void f(YGNodeRef, <enum>, float)` — the edge- and gutter-keyed setters.
    private static final FunctionDescriptor NODE_KEY_FLOAT = FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT);

    /// `float f(YGNodeConstRef)`
    private static final FunctionDescriptor GET_FLOAT =
            FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, ValueLayout.ADDRESS);

    /// `float f(YGNodeConstRef, <enum>)`
    private static final FunctionDescriptor GET_FLOAT_KEYED = FunctionDescriptor.of(
            ValueLayout.JAVA_FLOAT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT);

    /// `bool f(YGNodeConstRef)` — C's `_Bool`, one byte and not four.
    private static final FunctionDescriptor GET_BOOL =
            FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS);

    private static final class Holder {
        private static final Yoga INSTANCE = new Yoga(NativeLibrary.get().lookup());
    }

    // --- config ------------------------------------------------------------
    private final MethodHandle configNew;
    private final MethodHandle configFree;
    private final MethodHandle configSetPointScaleFactor;
    private final MethodHandle configGetPointScaleFactor;
    private final MethodHandle configSetUseWebDefaults;
    private final MethodHandle configGetUseWebDefaults;

    // --- node lifecycle and tree -------------------------------------------
    private final MethodHandle nodeNew;
    private final MethodHandle nodeNewWithConfig;
    private final MethodHandle nodeFree;
    private final MethodHandle nodeInsertChild;
    private final MethodHandle nodeRemoveChild;
    private final MethodHandle nodeRemoveAllChildren;
    private final MethodHandle nodeGetChildCount;
    private final MethodHandle nodeSetMeasureFunc;
    private final MethodHandle nodeHasMeasureFunc;
    private final MethodHandle nodeMarkDirty;
    private final MethodHandle nodeIsDirty;
    private final MethodHandle nodeGetHasNewLayout;
    private final MethodHandle nodeSetHasNewLayout;
    private final MethodHandle nodeCalculateLayout;

    // --- style: enum-valued ------------------------------------------------
    private final MethodHandle styleSetDirection;
    private final MethodHandle styleSetFlexDirection;
    private final MethodHandle styleSetJustifyContent;
    private final MethodHandle styleSetAlignContent;
    private final MethodHandle styleSetAlignItems;
    private final MethodHandle styleSetAlignSelf;
    private final MethodHandle styleSetPositionType;
    private final MethodHandle styleSetFlexWrap;
    private final MethodHandle styleSetOverflow;
    private final MethodHandle styleSetDisplay;

    // --- style: plain floats -----------------------------------------------
    private final MethodHandle styleSetFlexGrow;
    private final MethodHandle styleSetFlexShrink;
    private final MethodHandle styleSetAspectRatio;
    private final MethodHandle styleSetBorder;

    // --- style: lengths ----------------------------------------------------
    private final LengthCalls width;
    private final LengthCalls height;
    private final LengthCalls minWidth;
    private final LengthCalls minHeight;
    private final LengthCalls maxWidth;
    private final LengthCalls maxHeight;
    private final LengthCalls flexBasis;
    private final KeyedLengthCalls position;
    private final KeyedLengthCalls margin;
    private final KeyedLengthCalls padding;
    private final KeyedLengthCalls gap;

    // --- computed layout ---------------------------------------------------
    private final MethodHandle layoutGetLeft;
    private final MethodHandle layoutGetTop;
    private final MethodHandle layoutGetWidth;
    private final MethodHandle layoutGetHeight;
    private final MethodHandle layoutGetMargin;
    private final MethodHandle layoutGetBorder;
    private final MethodHandle layoutGetPadding;
    private final MethodHandle layoutGetDirection;
    private final MethodHandle layoutGetHadOverflow;

    private Yoga(SymbolLookup lookup) {
        this.configNew = downcall(lookup, "YGConfigNew",
                FunctionDescriptor.of(ValueLayout.ADDRESS));
        this.configFree = downcall(lookup, "YGConfigFree", NODE_VOID);
        this.configSetPointScaleFactor =
                downcall(lookup, "YGConfigSetPointScaleFactor", NODE_FLOAT);
        this.configGetPointScaleFactor =
                downcall(lookup, "YGConfigGetPointScaleFactor", GET_FLOAT);
        this.configSetUseWebDefaults = downcall(lookup, "YGConfigSetUseWebDefaults",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BOOLEAN));
        this.configGetUseWebDefaults =
                downcall(lookup, "YGConfigGetUseWebDefaults", GET_BOOL);

        this.nodeNew = downcall(lookup, "YGNodeNew",
                FunctionDescriptor.of(ValueLayout.ADDRESS));
        this.nodeNewWithConfig = downcall(lookup, "YGNodeNewWithConfig",
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.nodeFree = downcall(lookup, "YGNodeFree", NODE_VOID);
        // size_t, which is 8 bytes on every target Goldberry builds for -- the
        // "size_t" scalar row in the layout table is what says so.
        this.nodeInsertChild = downcall(lookup, "YGNodeInsertChild", FunctionDescriptor.ofVoid(
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        this.nodeRemoveChild = downcall(lookup, "YGNodeRemoveChild",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.nodeRemoveAllChildren = downcall(lookup, "YGNodeRemoveAllChildren", NODE_VOID);
        this.nodeGetChildCount = downcall(lookup, "YGNodeGetChildCount",
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
        this.nodeSetMeasureFunc = downcall(lookup, "YGNodeSetMeasureFunc",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.nodeHasMeasureFunc = downcall(lookup, "YGNodeHasMeasureFunc", GET_BOOL);
        this.nodeMarkDirty = downcall(lookup, "YGNodeMarkDirty", NODE_VOID);
        this.nodeIsDirty = downcall(lookup, "YGNodeIsDirty", GET_BOOL);
        this.nodeGetHasNewLayout = downcall(lookup, "YGNodeGetHasNewLayout", GET_BOOL);
        this.nodeSetHasNewLayout = downcall(lookup, "YGNodeSetHasNewLayout",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BOOLEAN));
        this.nodeCalculateLayout = downcall(lookup, "YGNodeCalculateLayout",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_FLOAT,
                        ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT));

        this.styleSetDirection = downcall(lookup, "YGNodeStyleSetDirection", NODE_ENUM);
        this.styleSetFlexDirection = downcall(lookup, "YGNodeStyleSetFlexDirection", NODE_ENUM);
        this.styleSetJustifyContent = downcall(lookup, "YGNodeStyleSetJustifyContent", NODE_ENUM);
        this.styleSetAlignContent = downcall(lookup, "YGNodeStyleSetAlignContent", NODE_ENUM);
        this.styleSetAlignItems = downcall(lookup, "YGNodeStyleSetAlignItems", NODE_ENUM);
        this.styleSetAlignSelf = downcall(lookup, "YGNodeStyleSetAlignSelf", NODE_ENUM);
        this.styleSetPositionType = downcall(lookup, "YGNodeStyleSetPositionType", NODE_ENUM);
        this.styleSetFlexWrap = downcall(lookup, "YGNodeStyleSetFlexWrap", NODE_ENUM);
        this.styleSetOverflow = downcall(lookup, "YGNodeStyleSetOverflow", NODE_ENUM);
        this.styleSetDisplay = downcall(lookup, "YGNodeStyleSetDisplay", NODE_ENUM);

        this.styleSetFlexGrow = downcall(lookup, "YGNodeStyleSetFlexGrow", NODE_FLOAT);
        this.styleSetFlexShrink = downcall(lookup, "YGNodeStyleSetFlexShrink", NODE_FLOAT);
        this.styleSetAspectRatio = downcall(lookup, "YGNodeStyleSetAspectRatio", NODE_FLOAT);
        // Border is points-only: there is no percent or auto function for it,
        // which matches CSS -- a percentage border-width is not a thing.
        this.styleSetBorder = downcall(lookup, "YGNodeStyleSetBorder", NODE_KEY_FLOAT);

        this.width = lengths(lookup, "Width", true);
        this.height = lengths(lookup, "Height", true);
        // No YGNodeStyleSetMinWidthAuto or MaxWidthAuto exists in Yoga, so a
        // caller asking for `auto` on a bound is refused by name rather than
        // silently dropped. See applyLength.
        this.minWidth = lengths(lookup, "MinWidth", false);
        this.minHeight = lengths(lookup, "MinHeight", false);
        this.maxWidth = lengths(lookup, "MaxWidth", false);
        this.maxHeight = lengths(lookup, "MaxHeight", false);
        this.flexBasis = lengths(lookup, "FlexBasis", true);

        // Inset has no `auto` in Yoga 3.1 -- CSS's `inset: auto` has no
        // equivalent to bind to.
        this.position = keyedLengths(lookup, "Position", false);
        this.margin = keyedLengths(lookup, "Margin", true);
        this.padding = keyedLengths(lookup, "Padding", false);
        this.gap = keyedLengths(lookup, "Gap", false);

        this.layoutGetLeft = downcall(lookup, "YGNodeLayoutGetLeft", GET_FLOAT);
        this.layoutGetTop = downcall(lookup, "YGNodeLayoutGetTop", GET_FLOAT);
        this.layoutGetWidth = downcall(lookup, "YGNodeLayoutGetWidth", GET_FLOAT);
        this.layoutGetHeight = downcall(lookup, "YGNodeLayoutGetHeight", GET_FLOAT);
        this.layoutGetMargin = downcall(lookup, "YGNodeLayoutGetMargin", GET_FLOAT_KEYED);
        this.layoutGetBorder = downcall(lookup, "YGNodeLayoutGetBorder", GET_FLOAT_KEYED);
        this.layoutGetPadding = downcall(lookup, "YGNodeLayoutGetPadding", GET_FLOAT_KEYED);
        this.layoutGetDirection = downcall(lookup, "YGNodeLayoutGetDirection",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.layoutGetHadOverflow = downcall(lookup, "YGNodeLayoutGetHadOverflow", GET_BOOL);
    }

    static Yoga get() {
        return Holder.INSTANCE;
    }

    // --- config ------------------------------------------------------------

    MemorySegment configNew() {
        return pointer(configNew, "YGConfigNew");
    }

    void configFree(MemorySegment config) {
        call(configFree, "YGConfigFree", config);
    }

    void configPointScaleFactor(MemorySegment config, float factor) {
        call(configSetPointScaleFactor, "YGConfigSetPointScaleFactor", config, factor);
    }

    float configPointScaleFactor(MemorySegment config) {
        return getFloat(configGetPointScaleFactor, "YGConfigGetPointScaleFactor", config);
    }

    void configUseWebDefaults(MemorySegment config, boolean useWebDefaults) {
        try {
            configSetUseWebDefaults.invokeExact(config, useWebDefaults);
        } catch (Throwable t) {
            throw failure("YGConfigSetUseWebDefaults", t);
        }
    }

    boolean configUseWebDefaults(MemorySegment config) {
        return getBoolean(configGetUseWebDefaults, "YGConfigGetUseWebDefaults", config);
    }

    // --- node lifecycle and tree -------------------------------------------

    MemorySegment nodeNew() {
        return pointer(nodeNew, "YGNodeNew");
    }

    MemorySegment nodeNew(MemorySegment config) {
        MemorySegment node;
        try {
            node = (MemorySegment) nodeNewWithConfig.invokeExact(config);
        } catch (Throwable t) {
            throw failure("YGNodeNewWithConfig", t);
        }
        return requireNonNull(node, "YGNodeNewWithConfig");
    }

    /// Frees one node. Never recursive: [YogaNode] knows which Java wrappers
    /// refer to which pointers and frees them one at a time so that each wrapper
    /// can be marked dead as its pointer goes.
    void nodeFree(MemorySegment node) {
        call(nodeFree, "YGNodeFree", node);
    }

    void nodeInsertChild(MemorySegment node, MemorySegment child, long index) {
        try {
            nodeInsertChild.invokeExact(node, child, index);
        } catch (Throwable t) {
            throw failure("YGNodeInsertChild", t);
        }
    }

    void nodeRemoveChild(MemorySegment node, MemorySegment child) {
        try {
            nodeRemoveChild.invokeExact(node, child);
        } catch (Throwable t) {
            throw failure("YGNodeRemoveChild", t);
        }
    }

    void nodeRemoveAllChildren(MemorySegment node) {
        call(nodeRemoveAllChildren, "YGNodeRemoveAllChildren", node);
    }

    long nodeChildCount(MemorySegment node) {
        try {
            return (long) nodeGetChildCount.invokeExact(node);
        } catch (Throwable t) {
            throw failure("YGNodeGetChildCount", t);
        }
    }

    /// Attaches a `YGMeasureFunc`, or clears it when `stub` is
    /// [MemorySegment#NULL].
    void nodeMeasureFunc(MemorySegment node, MemorySegment stub) {
        try {
            nodeSetMeasureFunc.invokeExact(node, stub);
        } catch (Throwable t) {
            throw failure("YGNodeSetMeasureFunc", t);
        }
    }

    boolean nodeHasMeasureFunc(MemorySegment node) {
        return getBoolean(nodeHasMeasureFunc, "YGNodeHasMeasureFunc", node);
    }

    void nodeMarkDirty(MemorySegment node) {
        call(nodeMarkDirty, "YGNodeMarkDirty", node);
    }

    boolean nodeIsDirty(MemorySegment node) {
        return getBoolean(nodeIsDirty, "YGNodeIsDirty", node);
    }

    boolean nodeHasNewLayout(MemorySegment node) {
        return getBoolean(nodeGetHasNewLayout, "YGNodeGetHasNewLayout", node);
    }

    void nodeHasNewLayout(MemorySegment node, boolean hasNewLayout) {
        try {
            nodeSetHasNewLayout.invokeExact(node, hasNewLayout);
        } catch (Throwable t) {
            throw failure("YGNodeSetHasNewLayout", t);
        }
    }

    void nodeCalculateLayout(
            MemorySegment node, float availableWidth, float availableHeight, Direction ownerDirection) {
        try {
            nodeCalculateLayout.invokeExact(
                    node, availableWidth, availableHeight, ownerDirection.nativeValue());
        } catch (Throwable t) {
            throw failure("YGNodeCalculateLayout", t);
        }
    }

    // --- style -------------------------------------------------------------

    void styleDirection(MemorySegment node, Direction value) {
        callEnum(styleSetDirection, "YGNodeStyleSetDirection", node, value);
    }

    void styleFlexDirection(MemorySegment node, FlexDirection value) {
        callEnum(styleSetFlexDirection, "YGNodeStyleSetFlexDirection", node, value);
    }

    void styleJustifyContent(MemorySegment node, Justify value) {
        callEnum(styleSetJustifyContent, "YGNodeStyleSetJustifyContent", node, value);
    }

    void styleAlignContent(MemorySegment node, Align value) {
        callEnum(styleSetAlignContent, "YGNodeStyleSetAlignContent", node, value);
    }

    void styleAlignItems(MemorySegment node, Align value) {
        callEnum(styleSetAlignItems, "YGNodeStyleSetAlignItems", node, value);
    }

    void styleAlignSelf(MemorySegment node, Align value) {
        callEnum(styleSetAlignSelf, "YGNodeStyleSetAlignSelf", node, value);
    }

    void stylePositionType(MemorySegment node, PositionType value) {
        callEnum(styleSetPositionType, "YGNodeStyleSetPositionType", node, value);
    }

    void styleFlexWrap(MemorySegment node, Wrap value) {
        callEnum(styleSetFlexWrap, "YGNodeStyleSetFlexWrap", node, value);
    }

    void styleOverflow(MemorySegment node, Overflow value) {
        callEnum(styleSetOverflow, "YGNodeStyleSetOverflow", node, value);
    }

    void styleDisplay(MemorySegment node, Display value) {
        callEnum(styleSetDisplay, "YGNodeStyleSetDisplay", node, value);
    }

    void styleFlexGrow(MemorySegment node, float value) {
        call(styleSetFlexGrow, "YGNodeStyleSetFlexGrow", node, value);
    }

    void styleFlexShrink(MemorySegment node, float value) {
        call(styleSetFlexShrink, "YGNodeStyleSetFlexShrink", node, value);
    }

    void styleAspectRatio(MemorySegment node, float value) {
        call(styleSetAspectRatio, "YGNodeStyleSetAspectRatio", node, value);
    }

    void styleBorder(MemorySegment node, Edge edge, float value) {
        callKeyed(styleSetBorder, "YGNodeStyleSetBorder", node, edge.nativeValue(), value);
    }

    void styleWidth(MemorySegment node, StyleLength value) {
        applyLength(width, "width", node, value);
    }

    void styleHeight(MemorySegment node, StyleLength value) {
        applyLength(height, "height", node, value);
    }

    void styleMinWidth(MemorySegment node, StyleLength value) {
        applyLength(minWidth, "min-width", node, value);
    }

    void styleMinHeight(MemorySegment node, StyleLength value) {
        applyLength(minHeight, "min-height", node, value);
    }

    void styleMaxWidth(MemorySegment node, StyleLength value) {
        applyLength(maxWidth, "max-width", node, value);
    }

    void styleMaxHeight(MemorySegment node, StyleLength value) {
        applyLength(maxHeight, "max-height", node, value);
    }

    void styleFlexBasis(MemorySegment node, StyleLength value) {
        applyLength(flexBasis, "flex-basis", node, value);
    }

    void stylePosition(MemorySegment node, Edge edge, StyleLength value) {
        applyKeyedLength(position, "inset", node, edge.nativeValue(), value);
    }

    void styleMargin(MemorySegment node, Edge edge, StyleLength value) {
        applyKeyedLength(margin, "margin", node, edge.nativeValue(), value);
    }

    void stylePadding(MemorySegment node, Edge edge, StyleLength value) {
        applyKeyedLength(padding, "padding", node, edge.nativeValue(), value);
    }

    void styleGap(MemorySegment node, Gutter gutter, StyleLength value) {
        applyKeyedLength(gap, "gap", node, gutter.nativeValue(), value);
    }

    // --- computed layout ---------------------------------------------------

    ComputedLayout layout(MemorySegment node) {
        return new ComputedLayout(
                getFloat(layoutGetLeft, "YGNodeLayoutGetLeft", node),
                getFloat(layoutGetTop, "YGNodeLayoutGetTop", node),
                getFloat(layoutGetWidth, "YGNodeLayoutGetWidth", node),
                getFloat(layoutGetHeight, "YGNodeLayoutGetHeight", node));
    }

    float layoutMargin(MemorySegment node, Edge edge) {
        return getFloatKeyed(layoutGetMargin, "YGNodeLayoutGetMargin", node, edge);
    }

    float layoutBorder(MemorySegment node, Edge edge) {
        return getFloatKeyed(layoutGetBorder, "YGNodeLayoutGetBorder", node, edge);
    }

    float layoutPadding(MemorySegment node, Edge edge) {
        return getFloatKeyed(layoutGetPadding, "YGNodeLayoutGetPadding", node, edge);
    }

    Direction layoutDirection(MemorySegment node) {
        int value;
        try {
            value = (int) layoutGetDirection.invokeExact(node);
        } catch (Throwable t) {
            throw failure("YGNodeLayoutGetDirection", t);
        }
        // Converted outside the try: a value Yoga does not define is a
        // diagnostic worth keeping, and wrapping it as a failed downcall would
        // bury it.
        return Direction.of(value);
    }

    boolean layoutHadOverflow(MemorySegment node) {
        return getBoolean(layoutGetHadOverflow, "YGNodeLayoutGetHadOverflow", node);
    }

    // --- the length dispatch -----------------------------------------------

    /// The functions a length-valued property is set through.
    ///
    /// `auto` is null for the properties Yoga has no `*Auto` function for. There
    /// is no undefined variant: Yoga's way to unset a property is to pass
    /// `YGUndefined` — a NaN — to the points function.
    ///
    /// `symbol` is the C name the three share as a prefix, kept for the failure
    /// message: composed handles would otherwise report a CSS property name for
    /// what is really a broken binding to a named symbol.
    private record LengthCalls(String symbol, MethodHandle points, MethodHandle percent, MethodHandle auto) {
    }

    /// The same, for a property keyed by an [Edge] or a [Gutter]. The C shape is
    /// identical — `(node, int, float)` — so one record serves both.
    private record KeyedLengthCalls(
            String symbol, MethodHandle points, MethodHandle percent, MethodHandle auto) {
    }

    private static void applyLength(
            LengthCalls calls, String property, MemorySegment node, StyleLength length) {
        switch (length) {
            case StyleLength.Points(var value) -> call(calls.points(), calls.symbol(), node, value);
            case StyleLength.Percent(var value) ->
                    call(calls.percent(), calls.symbol() + "Percent", node, value);
            case StyleLength.Keyword keyword -> {
                switch (keyword) {
                    // The auto function takes the node and nothing else.
                    case AUTO -> call(
                            requireAuto(calls.auto(), property), calls.symbol() + "Auto", node);
                    case UNDEFINED -> call(calls.points(), calls.symbol(), node, Float.NaN);
                }
            }
        }
    }

    private static void applyKeyedLength(
            KeyedLengthCalls calls, String property, MemorySegment node, int key, StyleLength length) {
        switch (length) {
            case StyleLength.Points(var value) ->
                    callKeyed(calls.points(), calls.symbol(), node, key, value);
            case StyleLength.Percent(var value) ->
                    callKeyed(calls.percent(), calls.symbol() + "Percent", node, key, value);
            case StyleLength.Keyword keyword -> {
                switch (keyword) {
                    // The auto function takes the edge but no value.
                    case AUTO -> callKeyedVoid(
                            requireAuto(calls.auto(), property), calls.symbol() + "Auto", node, key);
                    case UNDEFINED -> callKeyed(calls.points(), calls.symbol(), node, key, Float.NaN);
                }
            }
        }
    }

    /// Yoga exports no `*Auto` function for every property, and a property that
    /// has none cannot be told `auto` at all. Refusing by name beats dropping the
    /// value, which would read as a stylesheet that has no effect.
    private static MethodHandle requireAuto(MethodHandle auto, String property) {
        if (auto == null) {
            throw new IllegalArgumentException(
                    "Yoga has no `auto` for " + property + " — it exports no setter for it,"
                            + " so there is nothing to translate the value into");
        }
        return auto;
    }

    // --- binding helpers ---------------------------------------------------

    /// Binds the two or three functions behind a length-valued property.
    ///
    /// The names are mechanical — `YGNodeStyleSetWidth`, `...WidthPercent`,
    /// `...WidthAuto` — so they are composed rather than written out three times.
    /// Composing a name that does not exist is not a silent failure: the lookup
    /// throws, naming the missing symbol.
    private static LengthCalls lengths(SymbolLookup lookup, String property, boolean hasAuto) {
        var prefix = "YGNodeStyleSet" + property;
        return new LengthCalls(
                prefix,
                downcall(lookup, prefix, NODE_FLOAT),
                downcall(lookup, prefix + "Percent", NODE_FLOAT),
                hasAuto ? downcall(lookup, prefix + "Auto", NODE_VOID) : null);
    }

    private static KeyedLengthCalls keyedLengths(SymbolLookup lookup, String property, boolean hasAuto) {
        var prefix = "YGNodeStyleSet" + property;
        return new KeyedLengthCalls(
                prefix,
                downcall(lookup, prefix, NODE_KEY_FLOAT),
                downcall(lookup, prefix + "Percent", NODE_KEY_FLOAT),
                hasAuto ? downcall(lookup, prefix + "Auto", NODE_ENUM) : null);
    }

    // --- invocation helpers ------------------------------------------------
    //
    // invokeExact rather than invokeWithArguments: the handle's type is known
    // statically at each of these call sites, so the JIT can inline through it.
    // That matters here in a way it does not for SDL -- a layout pass touches
    // every node in the tree, and these are the calls it makes.

    private static void call(MethodHandle handle, String name, MemorySegment node) {
        try {
            handle.invokeExact(node);
        } catch (Throwable t) {
            throw failure(name, t);
        }
    }

    private static void call(MethodHandle handle, String name, MemorySegment node, float value) {
        try {
            handle.invokeExact(node, value);
        } catch (Throwable t) {
            throw failure(name, t);
        }
    }

    private static void callEnum(MethodHandle handle, String name, MemorySegment node, YogaEnum value) {
        try {
            handle.invokeExact(node, value.nativeValue());
        } catch (Throwable t) {
            throw failure(name, t);
        }
    }

    private static void callKeyed(
            MethodHandle handle, String name, MemorySegment node, int key, float value) {
        try {
            handle.invokeExact(node, key, value);
        } catch (Throwable t) {
            throw failure(name, t);
        }
    }

    private static void callKeyedVoid(MethodHandle handle, String name, MemorySegment node, int key) {
        try {
            handle.invokeExact(node, key);
        } catch (Throwable t) {
            throw failure(name, t);
        }
    }

    private static float getFloat(MethodHandle handle, String name, MemorySegment node) {
        try {
            return (float) handle.invokeExact(node);
        } catch (Throwable t) {
            throw failure(name, t);
        }
    }

    private static float getFloatKeyed(MethodHandle handle, String name, MemorySegment node, Edge edge) {
        try {
            return (float) handle.invokeExact(node, edge.nativeValue());
        } catch (Throwable t) {
            throw failure(name, t);
        }
    }

    private static boolean getBoolean(MethodHandle handle, String name, MemorySegment node) {
        try {
            return (boolean) handle.invokeExact(node);
        } catch (Throwable t) {
            throw failure(name, t);
        }
    }

    private static MemorySegment pointer(MethodHandle handle, String name) {
        MemorySegment result;
        try {
            result = (MemorySegment) handle.invokeExact();
        } catch (Throwable t) {
            throw failure(name, t);
        }
        return requireNonNull(result, name);
    }

    private static MemorySegment requireNonNull(MemorySegment pointer, String name) {
        if (MemorySegment.NULL.equals(pointer)) {
            throw new IllegalStateException(name + "() returned NULL — allocation failed");
        }
        return pointer;
    }

    /// A failed downcall.
    ///
    /// Yoga has no error channel: its C API returns void almost everywhere and
    /// aborts on a violated precondition rather than reporting one. So anything
    /// caught here is a broken binding — a descriptor that does not match the
    /// signature it was written against — not a Yoga error, and the message says
    /// so rather than blaming the caller.
    private static IllegalStateException failure(String name, Throwable cause) {
        return new IllegalStateException(name + "() failed", cause);
    }

    // Restricted: see GoldberryShim.downcall -- same obligation, same reason.
    @SuppressWarnings("restricted")
    private static MethodHandle downcall(SymbolLookup lookup, String symbol, FunctionDescriptor descriptor) {
        var address = lookup.find(symbol).orElseThrow(() -> new UnsatisfiedLinkError(
                "libgoldberry does not export " + symbol
                        + " — is it listed in natives/src/main/cmake/exports/goldberry.symbols?"));
        return LINKER.downcallHandle(address, descriptor);
    }
}
