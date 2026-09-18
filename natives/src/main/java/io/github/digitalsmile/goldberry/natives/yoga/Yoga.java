package io.github.digitalsmile.goldberry.natives.yoga;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;

import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import io.github.digitalsmile.goldberry.natives.yoga.calls.ConfigCalls;
import io.github.digitalsmile.goldberry.natives.yoga.calls.LayoutCalls;
import io.github.digitalsmile.goldberry.natives.yoga.calls.NodeCalls;
import io.github.digitalsmile.goldberry.natives.yoga.calls.StyleCalls;
import io.github.digitalsmile.goldberry.natives.yoga.style.Align;
import io.github.digitalsmile.goldberry.natives.yoga.style.Direction;
import io.github.digitalsmile.goldberry.natives.yoga.style.Display;
import io.github.digitalsmile.goldberry.natives.yoga.style.Edge;
import io.github.digitalsmile.goldberry.natives.yoga.style.FlexDirection;
import io.github.digitalsmile.goldberry.natives.yoga.style.Gutter;
import io.github.digitalsmile.goldberry.natives.yoga.style.Justify;
import io.github.digitalsmile.goldberry.natives.yoga.style.Overflow;
import io.github.digitalsmile.goldberry.natives.yoga.style.PositionType;
import io.github.digitalsmile.goldberry.natives.yoga.style.StyleLength;
import io.github.digitalsmile.goldberry.natives.yoga.style.Wrap;
import io.github.digitalsmile.goldberry.natives.yoga.style.YogaEnum;

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

    // What this class holds is four `…Calls` records -- the config, the tree, the
    // style and the results -- each one holder per Yoga function,
    // each keeping its own address and naming its own handle. The invocation
    // helpers that used to be at the bottom of this file -- one per signature,
    // so that a constant was read by the method that called it (ADR-0161) --
    // are the holders' `call` methods now (ADR-0173).

    private static final class Holder {
        private static final Yoga INSTANCE = new Yoga(NativeLibrary.get().lookup());
    }

    /// The two or three functions each length-valued property is set through.
    ///
    /// Held per property rather than in [YogaCalls] beside the rest, because
    /// which of them is called depends on the *value* -- see
    /// [#applyLength]. Everything else is a function this class names.
    private final StyleCalls.LengthCalls width;
    private final StyleCalls.LengthCalls height;
    private final StyleCalls.LengthCalls minWidth;
    private final StyleCalls.LengthCalls minHeight;
    private final StyleCalls.LengthCalls maxWidth;
    private final StyleCalls.LengthCalls maxHeight;
    private final StyleCalls.LengthCalls flexBasis;
    private final StyleCalls.KeyedLengthCalls position;
    private final StyleCalls.KeyedLengthCalls margin;
    private final StyleCalls.KeyedLengthCalls padding;
    private final StyleCalls.KeyedLengthCalls gap;

    private final ConfigCalls configCalls;
    private final NodeCalls nodeCalls;
    private final StyleCalls styleCalls;
    private final LayoutCalls layoutCalls;

    private Yoga(SymbolLookup lookup) {
        this.configCalls = ConfigCalls.bind(lookup);
        this.nodeCalls = NodeCalls.bind(lookup);
        this.styleCalls = StyleCalls.bind(lookup);
        this.layoutCalls = LayoutCalls.bind(lookup);

        this.width = StyleCalls.LengthCalls.bind(lookup, "Width", true);
        this.height = StyleCalls.LengthCalls.bind(lookup, "Height", true);
        // No YGNodeStyleSetMinWidthAuto or MaxWidthAuto exists in Yoga, so a
        // caller asking for `auto` on a bound is refused by name rather than
        // silently dropped. See applyLength.
        this.minWidth = StyleCalls.LengthCalls.bind(lookup, "MinWidth", false);
        this.minHeight = StyleCalls.LengthCalls.bind(lookup, "MinHeight", false);
        this.maxWidth = StyleCalls.LengthCalls.bind(lookup, "MaxWidth", false);
        this.maxHeight = StyleCalls.LengthCalls.bind(lookup, "MaxHeight", false);
        this.flexBasis = StyleCalls.LengthCalls.bind(lookup, "FlexBasis", true);

        // Inset has no `auto` in Yoga 3.1 -- CSS's `inset: auto` has no
        // equivalent to bind to.
        this.position = StyleCalls.KeyedLengthCalls.bind(lookup, "Position", false);
        this.margin = StyleCalls.KeyedLengthCalls.bind(lookup, "Margin", true);
        this.padding = StyleCalls.KeyedLengthCalls.bind(lookup, "Padding", false);
        this.gap = StyleCalls.KeyedLengthCalls.bind(lookup, "Gap", false);
    }

    static Yoga get() {
        return Holder.INSTANCE;
    }

    // --- config ------------------------------------------------------------

    MemorySegment configNew() {
        return configCalls.configNew().call();
    }

    void configFree(MemorySegment config) {
        configCalls.configFree().call(config);
    }

    void configPointScaleFactor(MemorySegment config, float factor) {
        configCalls.configSetPointScaleFactor().call(config, factor);
    }

    float configPointScaleFactor(MemorySegment config) {
        return configCalls.configGetPointScaleFactor().call(config);
    }

    void configUseWebDefaults(MemorySegment config, boolean useWebDefaults) {
        configCalls.configSetUseWebDefaults().call(config, useWebDefaults);
    }

    boolean configUseWebDefaults(MemorySegment config) {
        return configCalls.configGetUseWebDefaults().call(config);
    }

    // --- node lifecycle and tree -------------------------------------------

    MemorySegment nodeNew() {
        return nodeCalls.nodeNew().call();
    }

    MemorySegment nodeNew(MemorySegment config) {
        MemorySegment node;
        node = nodeCalls.nodeNewWithConfig().call(config);
        return requireNonNull(node, "YGNodeNewWithConfig");
    }

    /// Frees one node. Never recursive: [YogaNode] knows which Java wrappers
    /// refer to which pointers and frees them one at a time so that each wrapper
    /// can be marked dead as its pointer goes.
    void nodeFree(MemorySegment node) {
        nodeCalls.nodeFree().call(node);
    }

    void nodeInsertChild(MemorySegment node, MemorySegment child, long index) {
        nodeCalls.nodeInsertChild().call(node, child, index);
    }

    void nodeRemoveChild(MemorySegment node, MemorySegment child) {
        nodeCalls.nodeRemoveChild().call(node, child);
    }

    void nodeRemoveAllChildren(MemorySegment node) {
        nodeCalls.nodeRemoveAllChildren().call(node);
    }

    long nodeChildCount(MemorySegment node) {
        return nodeCalls.nodeGetChildCount().call(node);
    }

    /// Attaches a `YGMeasureFunc`, or clears it when `stub` is
    /// [MemorySegment#NULL].
    void nodeMeasureFunc(MemorySegment node, MemorySegment stub) {
        nodeCalls.nodeSetMeasureFunc().call(node, stub);
    }

    boolean nodeHasMeasureFunc(MemorySegment node) {
        return nodeCalls.nodeHasMeasureFunc().call(node);
    }

    void nodeMarkDirty(MemorySegment node) {
        nodeCalls.nodeMarkDirty().call(node);
    }

    boolean nodeIsDirty(MemorySegment node) {
        return nodeCalls.nodeIsDirty().call(node);
    }

    boolean nodeHasNewLayout(MemorySegment node) {
        return nodeCalls.nodeGetHasNewLayout().call(node);
    }

    void nodeHasNewLayout(MemorySegment node, boolean hasNewLayout) {
        nodeCalls.nodeSetHasNewLayout().call(node, hasNewLayout);
    }

    void nodeCalculateLayout(
            MemorySegment node, float availableWidth, float availableHeight, Direction ownerDirection) {
        nodeCalls.nodeCalculateLayout().call(node, availableWidth, availableHeight, ownerDirection.nativeValue());
    }

    // --- style -------------------------------------------------------------

    void styleDirection(MemorySegment node, Direction value) {
        styleCalls.styleSetDirection().call(node, value.nativeValue());
    }

    void styleFlexDirection(MemorySegment node, FlexDirection value) {
        styleCalls.styleSetFlexDirection().call(node, value.nativeValue());
    }

    void styleJustifyContent(MemorySegment node, Justify value) {
        styleCalls.styleSetJustifyContent().call(node, value.nativeValue());
    }

    void styleAlignContent(MemorySegment node, Align value) {
        styleCalls.styleSetAlignContent().call(node, value.nativeValue());
    }

    void styleAlignItems(MemorySegment node, Align value) {
        styleCalls.styleSetAlignItems().call(node, value.nativeValue());
    }

    void styleAlignSelf(MemorySegment node, Align value) {
        styleCalls.styleSetAlignSelf().call(node, value.nativeValue());
    }

    void stylePositionType(MemorySegment node, PositionType value) {
        styleCalls.styleSetPositionType().call(node, value.nativeValue());
    }

    void styleFlexWrap(MemorySegment node, Wrap value) {
        styleCalls.styleSetFlexWrap().call(node, value.nativeValue());
    }

    void styleOverflow(MemorySegment node, Overflow value) {
        styleCalls.styleSetOverflow().call(node, value.nativeValue());
    }

    void styleDisplay(MemorySegment node, Display value) {
        styleCalls.styleSetDisplay().call(node, value.nativeValue());
    }

    void styleFlexGrow(MemorySegment node, float value) {
        styleCalls.styleSetFlexGrow().call(node, value);
    }

    void styleFlexShrink(MemorySegment node, float value) {
        styleCalls.styleSetFlexShrink().call(node, value);
    }

    void styleAspectRatio(MemorySegment node, float value) {
        styleCalls.styleSetAspectRatio().call(node, value);
    }

    /// Border is points-only: there is no percent or auto function for it, which
    /// matches CSS -- a percentage `border-width` is not a thing.
    void styleBorder(MemorySegment node, Edge edge, float value) {
        styleCalls.styleSetBorder().call(node, edge.nativeValue(), value);
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
                layoutCalls.layoutGetLeft().call(node),
                layoutCalls.layoutGetTop().call(node),
                layoutCalls.layoutGetWidth().call(node),
                layoutCalls.layoutGetHeight().call(node));
    }

    float layoutMargin(MemorySegment node, Edge edge) {
        return layoutCalls.layoutGetMargin().call(node, edge.nativeValue());
    }

    float layoutBorder(MemorySegment node, Edge edge) {
        return layoutCalls.layoutGetBorder().call(node, edge.nativeValue());
    }

    float layoutPadding(MemorySegment node, Edge edge) {
        return layoutCalls.layoutGetPadding().call(node, edge.nativeValue());
    }

    Direction layoutDirection(MemorySegment node) {
        int value;
        value = layoutCalls.layoutGetDirection().call(node);
        // Converted outside the try: a value Yoga does not define is a
        // diagnostic worth keeping, and wrapping it as a failed downcall would
        // bury it.
        return Direction.of(value);
    }

    boolean layoutHadOverflow(MemorySegment node) {
        return layoutCalls.layoutGetHadOverflow().call(node);
    }

    // --- the length dispatch -----------------------------------------------

    private static void applyLength(
            StyleCalls.LengthCalls calls, String property, MemorySegment node, StyleLength length) {
        switch (length) {
            case StyleLength.Points(var value) -> calls.points().call(node, value);
            case StyleLength.Percent(var value) -> calls.percent().call(node, value);
            case StyleLength.Keyword keyword -> {
                switch (keyword) {
                    // The auto function takes the node and nothing else.
                    case AUTO -> requireAuto(calls.auto(), property).call(node);
                    case UNDEFINED -> calls.points().call(node, Float.NaN);
                }
            }
        }
    }

    private static void applyKeyedLength(
            StyleCalls.KeyedLengthCalls calls, String property, MemorySegment node, int key, StyleLength length) {
        switch (length) {
            case StyleLength.Points(var value) -> calls.points().call(node, key, value);
            case StyleLength.Percent(var value) -> calls.percent().call(node, key, value);
            case StyleLength.Keyword keyword -> {
                switch (keyword) {
                    // The auto function takes the edge but no value.
                    case AUTO -> requireAuto(calls.auto(), property).call(node, key);
                    case UNDEFINED -> calls.points().call(node, key, Float.NaN);
                }
            }
        }
    }

    /// Yoga exports no `*Auto` function for every property, and a property that
    /// has none cannot be told `auto` at all. Refusing by name beats dropping the
    /// value, which would read as a stylesheet that has no effect.
    private static <T> T requireAuto(T auto, String property) {
        if (auto == null) {
            throw new IllegalArgumentException("Yoga has no `auto` for " + property + " — it exports no setter for it,"
                    + " so there is nothing to translate the value into");
        }
        return auto;
    }

    // --- invocation helpers ------------------------------------------------
    //
    // One per signature, and every Yoga call goes through one: `invokeExact` on
    // a constant handle, never `invokeWithArguments`, so nothing here boxes and
    // both compilers can lower it into a direct call to the stub (ADR-0161).
    // That matters here in a way it does not for SDL -- a layout pass touches
    // every node in the tree, and these are the calls it makes.

    private static MemorySegment requireNonNull(MemorySegment pointer, String name) {
        if (MemorySegment.NULL.equals(pointer)) {
            throw new IllegalStateException(name + "() returned NULL — allocation failed");
        }
        return pointer;
    }
}
