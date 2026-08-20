package io.github.digitalsmile.goldberry.natives.yoga;

import io.github.digitalsmile.goldberry.natives.Downcalls;
import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;

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

    // Yoga's whole surface is seven signatures, and the invocation helpers at
    // the bottom of this file are one per signature -- so the shape a symbol was
    // bound with is named exactly once, by the helper that calls it, rather than
    // twice (ADR-0161). What each field holds is an address, not a handle.

    private static final class Holder {
        private static final Yoga INSTANCE = new Yoga(NativeLibrary.get().lookup());
    }

    // --- config ------------------------------------------------------------
    private final MemorySegment configNew;
    private final MemorySegment configFree;
    private final MemorySegment configSetPointScaleFactor;
    private final MemorySegment configGetPointScaleFactor;
    private final MemorySegment configSetUseWebDefaults;
    private final MemorySegment configGetUseWebDefaults;

    // --- node lifecycle and tree -------------------------------------------
    private final MemorySegment nodeNew;
    private final MemorySegment nodeNewWithConfig;
    private final MemorySegment nodeFree;
    private final MemorySegment nodeInsertChild;
    private final MemorySegment nodeRemoveChild;
    private final MemorySegment nodeRemoveAllChildren;
    private final MemorySegment nodeGetChildCount;
    private final MemorySegment nodeSetMeasureFunc;
    private final MemorySegment nodeHasMeasureFunc;
    private final MemorySegment nodeMarkDirty;
    private final MemorySegment nodeIsDirty;
    private final MemorySegment nodeGetHasNewLayout;
    private final MemorySegment nodeSetHasNewLayout;
    private final MemorySegment nodeCalculateLayout;

    // --- style: enum-valued ------------------------------------------------
    private final MemorySegment styleSetDirection;
    private final MemorySegment styleSetFlexDirection;
    private final MemorySegment styleSetJustifyContent;
    private final MemorySegment styleSetAlignContent;
    private final MemorySegment styleSetAlignItems;
    private final MemorySegment styleSetAlignSelf;
    private final MemorySegment styleSetPositionType;
    private final MemorySegment styleSetFlexWrap;
    private final MemorySegment styleSetOverflow;
    private final MemorySegment styleSetDisplay;

    // --- style: plain floats -----------------------------------------------
    private final MemorySegment styleSetFlexGrow;
    private final MemorySegment styleSetFlexShrink;
    private final MemorySegment styleSetAspectRatio;
    private final MemorySegment styleSetBorder;

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
    private final MemorySegment layoutGetLeft;
    private final MemorySegment layoutGetTop;
    private final MemorySegment layoutGetWidth;
    private final MemorySegment layoutGetHeight;
    private final MemorySegment layoutGetMargin;
    private final MemorySegment layoutGetBorder;
    private final MemorySegment layoutGetPadding;
    private final MemorySegment layoutGetDirection;
    private final MemorySegment layoutGetHadOverflow;

    private Yoga(SymbolLookup lookup) {
        this.configNew = Downcalls.symbol(lookup, "YGConfigNew");
        this.configFree = Downcalls.symbol(lookup, "YGConfigFree");
        this.configSetPointScaleFactor =
                Downcalls.symbol(lookup, "YGConfigSetPointScaleFactor");
        this.configGetPointScaleFactor =
                Downcalls.symbol(lookup, "YGConfigGetPointScaleFactor");
        this.configSetUseWebDefaults = Downcalls.symbol(lookup, "YGConfigSetUseWebDefaults");
        this.configGetUseWebDefaults =
                Downcalls.symbol(lookup, "YGConfigGetUseWebDefaults");

        this.nodeNew = Downcalls.symbol(lookup, "YGNodeNew");
        this.nodeNewWithConfig = Downcalls.symbol(lookup, "YGNodeNewWithConfig");
        this.nodeFree = Downcalls.symbol(lookup, "YGNodeFree");
        // size_t, which is 8 bytes on every target Goldberry builds for -- the
        // "size_t" scalar row in the layout table is what says so.
        this.nodeInsertChild = Downcalls.symbol(lookup, "YGNodeInsertChild");
        this.nodeRemoveChild = Downcalls.symbol(lookup, "YGNodeRemoveChild");
        this.nodeRemoveAllChildren = Downcalls.symbol(lookup, "YGNodeRemoveAllChildren");
        this.nodeGetChildCount = Downcalls.symbol(lookup, "YGNodeGetChildCount");
        this.nodeSetMeasureFunc = Downcalls.symbol(lookup, "YGNodeSetMeasureFunc");
        this.nodeHasMeasureFunc = Downcalls.symbol(lookup, "YGNodeHasMeasureFunc");
        this.nodeMarkDirty = Downcalls.symbol(lookup, "YGNodeMarkDirty");
        this.nodeIsDirty = Downcalls.symbol(lookup, "YGNodeIsDirty");
        this.nodeGetHasNewLayout = Downcalls.symbol(lookup, "YGNodeGetHasNewLayout");
        this.nodeSetHasNewLayout = Downcalls.symbol(lookup, "YGNodeSetHasNewLayout");
        this.nodeCalculateLayout = Downcalls.symbol(lookup, "YGNodeCalculateLayout");

        this.styleSetDirection = Downcalls.symbol(lookup, "YGNodeStyleSetDirection");
        this.styleSetFlexDirection = Downcalls.symbol(lookup, "YGNodeStyleSetFlexDirection");
        this.styleSetJustifyContent = Downcalls.symbol(lookup, "YGNodeStyleSetJustifyContent");
        this.styleSetAlignContent = Downcalls.symbol(lookup, "YGNodeStyleSetAlignContent");
        this.styleSetAlignItems = Downcalls.symbol(lookup, "YGNodeStyleSetAlignItems");
        this.styleSetAlignSelf = Downcalls.symbol(lookup, "YGNodeStyleSetAlignSelf");
        this.styleSetPositionType = Downcalls.symbol(lookup, "YGNodeStyleSetPositionType");
        this.styleSetFlexWrap = Downcalls.symbol(lookup, "YGNodeStyleSetFlexWrap");
        this.styleSetOverflow = Downcalls.symbol(lookup, "YGNodeStyleSetOverflow");
        this.styleSetDisplay = Downcalls.symbol(lookup, "YGNodeStyleSetDisplay");

        this.styleSetFlexGrow = Downcalls.symbol(lookup, "YGNodeStyleSetFlexGrow");
        this.styleSetFlexShrink = Downcalls.symbol(lookup, "YGNodeStyleSetFlexShrink");
        this.styleSetAspectRatio = Downcalls.symbol(lookup, "YGNodeStyleSetAspectRatio");
        // Border is points-only: there is no percent or auto function for it,
        // which matches CSS -- a percentage border-width is not a thing.
        this.styleSetBorder = Downcalls.symbol(lookup, "YGNodeStyleSetBorder");

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

        this.layoutGetLeft = Downcalls.symbol(lookup, "YGNodeLayoutGetLeft");
        this.layoutGetTop = Downcalls.symbol(lookup, "YGNodeLayoutGetTop");
        this.layoutGetWidth = Downcalls.symbol(lookup, "YGNodeLayoutGetWidth");
        this.layoutGetHeight = Downcalls.symbol(lookup, "YGNodeLayoutGetHeight");
        this.layoutGetMargin = Downcalls.symbol(lookup, "YGNodeLayoutGetMargin");
        this.layoutGetBorder = Downcalls.symbol(lookup, "YGNodeLayoutGetBorder");
        this.layoutGetPadding = Downcalls.symbol(lookup, "YGNodeLayoutGetPadding");
        this.layoutGetDirection = Downcalls.symbol(lookup, "YGNodeLayoutGetDirection");
        this.layoutGetHadOverflow = Downcalls.symbol(lookup, "YGNodeLayoutGetHadOverflow");
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
            Downcalls.VOID__PTR_BOOL.invokeExact(configSetUseWebDefaults, config, useWebDefaults);
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
            node = (MemorySegment) Downcalls.PTR__PTR.invokeExact(nodeNewWithConfig, config);
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
            Downcalls.VOID__PTR_PTR_LONG.invokeExact(nodeInsertChild, node, child, index);
        } catch (Throwable t) {
            throw failure("YGNodeInsertChild", t);
        }
    }

    void nodeRemoveChild(MemorySegment node, MemorySegment child) {
        try {
            Downcalls.VOID__PTR_PTR.invokeExact(nodeRemoveChild, node, child);
        } catch (Throwable t) {
            throw failure("YGNodeRemoveChild", t);
        }
    }

    void nodeRemoveAllChildren(MemorySegment node) {
        call(nodeRemoveAllChildren, "YGNodeRemoveAllChildren", node);
    }

    long nodeChildCount(MemorySegment node) {
        try {
            return (long) Downcalls.LONG__PTR.invokeExact(nodeGetChildCount, node);
        } catch (Throwable t) {
            throw failure("YGNodeGetChildCount", t);
        }
    }

    /// Attaches a `YGMeasureFunc`, or clears it when `stub` is
    /// [MemorySegment#NULL].
    void nodeMeasureFunc(MemorySegment node, MemorySegment stub) {
        try {
            Downcalls.VOID__PTR_PTR.invokeExact(nodeSetMeasureFunc, node, stub);
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
            Downcalls.VOID__PTR_BOOL.invokeExact(nodeSetHasNewLayout, node, hasNewLayout);
        } catch (Throwable t) {
            throw failure("YGNodeSetHasNewLayout", t);
        }
    }

    void nodeCalculateLayout(
            MemorySegment node, float availableWidth, float availableHeight, Direction ownerDirection) {
        try {
            Downcalls.VOID__PTR_FLOAT_FLOAT_INT.invokeExact(nodeCalculateLayout,
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
            value = (int) Downcalls.INT__PTR.invokeExact(layoutGetDirection, node);
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
    private record LengthCalls(
            String symbol, MemorySegment points, MemorySegment percent, MemorySegment auto) {
    }

    /// The same, for a property keyed by an [Edge] or a [Gutter]. The C shape is
    /// identical — `(node, int, float)` — so one record serves both.
    private record KeyedLengthCalls(
            String symbol, MemorySegment points, MemorySegment percent, MemorySegment auto) {
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
    private static MemorySegment requireAuto(MemorySegment auto, String property) {
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
                Downcalls.symbol(lookup, prefix),
                Downcalls.symbol(lookup, prefix + "Percent"),
                hasAuto ? Downcalls.symbol(lookup, prefix + "Auto") : null);
    }

    private static KeyedLengthCalls keyedLengths(SymbolLookup lookup, String property, boolean hasAuto) {
        var prefix = "YGNodeStyleSet" + property;
        return new KeyedLengthCalls(
                prefix,
                Downcalls.symbol(lookup, prefix),
                Downcalls.symbol(lookup, prefix + "Percent"),
                hasAuto ? Downcalls.symbol(lookup, prefix + "Auto") : null);
    }

    // --- invocation helpers ------------------------------------------------
    //
    // One per signature, and every Yoga call goes through one: `invokeExact` on
    // a constant handle, never `invokeWithArguments`, so nothing here boxes and
    // both compilers can lower it into a direct call to the stub (ADR-0161).
    // That matters here in a way it does not for SDL -- a layout pass touches
    // every node in the tree, and these are the calls it makes.

    private static void call(MemorySegment function, String name, MemorySegment node) {
        try {
            Downcalls.VOID__PTR.invokeExact(function, node);
        } catch (Throwable t) {
            throw failure(name, t);
        }
    }

    private static void call(MemorySegment function, String name, MemorySegment node, float value) {
        try {
            Downcalls.VOID__PTR_FLOAT.invokeExact(function, node, value);
        } catch (Throwable t) {
            throw failure(name, t);
        }
    }

    private static void callEnum(
            MemorySegment function, String name, MemorySegment node, YogaEnum value) {
        try {
            Downcalls.VOID__PTR_INT.invokeExact(function, node, value.nativeValue());
        } catch (Throwable t) {
            throw failure(name, t);
        }
    }

    private static void callKeyed(
            MemorySegment function, String name, MemorySegment node, int key, float value) {
        try {
            Downcalls.VOID__PTR_INT_FLOAT.invokeExact(function, node, key, value);
        } catch (Throwable t) {
            throw failure(name, t);
        }
    }

    private static void callKeyedVoid(
            MemorySegment function, String name, MemorySegment node, int key) {
        try {
            Downcalls.VOID__PTR_INT.invokeExact(function, node, key);
        } catch (Throwable t) {
            throw failure(name, t);
        }
    }

    private static float getFloat(MemorySegment function, String name, MemorySegment node) {
        try {
            return (float) Downcalls.FLOAT__PTR.invokeExact(function, node);
        } catch (Throwable t) {
            throw failure(name, t);
        }
    }

    private static float getFloatKeyed(
            MemorySegment function, String name, MemorySegment node, Edge edge) {
        try {
            return (float) Downcalls.FLOAT__PTR_INT.invokeExact(function, node, edge.nativeValue());
        } catch (Throwable t) {
            throw failure(name, t);
        }
    }

    private static boolean getBoolean(MemorySegment function, String name, MemorySegment node) {
        try {
            return (boolean) Downcalls.BOOL__PTR.invokeExact(function, node);
        } catch (Throwable t) {
            throw failure(name, t);
        }
    }

    private static MemorySegment pointer(MemorySegment function, String name) {
        MemorySegment result;
        try {
            result = (MemorySegment) Downcalls.PTR__VOID.invokeExact(function);
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
    /// caught here is a broken binding — a [Downcalls] constant that does not
    /// match the C prototype — not a Yoga error, and the message says so rather
    /// than blaming the caller.
    private static IllegalStateException failure(String name, Throwable cause) {
        return new IllegalStateException(name + "() failed", cause);
    }
}
