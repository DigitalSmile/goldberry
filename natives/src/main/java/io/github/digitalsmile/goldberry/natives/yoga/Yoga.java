package io.github.digitalsmile.goldberry.natives.yoga;

import io.github.digitalsmile.goldberry.natives.yoga.calls.YogaCalls;
import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
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

    // Yoga's whole surface is seven signatures, and the invocation helpers at
    // the bottom of this file are one per signature -- so the shape a symbol was
    // bound with is named exactly once, by the helper that calls it, rather than
    // twice (ADR-0161). What each field holds is an address, not a handle.

    private static final class Holder {
        private static final Yoga INSTANCE = new Yoga(NativeLibrary.get().lookup());
    }

    // --- config ------------------------------------------------------------

    // --- node lifecycle and tree -------------------------------------------

    // --- style: enum-valued ------------------------------------------------

    // --- style: plain floats -----------------------------------------------

    // --- style: lengths ----------------------------------------------------
    private final YogaCalls.LengthCalls width;
    private final YogaCalls.LengthCalls height;
    private final YogaCalls.LengthCalls minWidth;
    private final YogaCalls.LengthCalls minHeight;
    private final YogaCalls.LengthCalls maxWidth;
    private final YogaCalls.LengthCalls maxHeight;
    private final YogaCalls.LengthCalls flexBasis;
    private final YogaCalls.KeyedLengthCalls position;
    private final YogaCalls.KeyedLengthCalls margin;
    private final YogaCalls.KeyedLengthCalls padding;
    private final YogaCalls.KeyedLengthCalls gap;

    // --- computed layout ---------------------------------------------------

    private final YogaCalls calls;

    private Yoga(SymbolLookup lookup) {
        this.calls = YogaCalls.bind(lookup);

        // size_t, which is 8 bytes on every target Goldberry builds for -- the
        // "size_t" scalar row in the layout table is what says so.


        // Border is points-only: there is no percent or auto function for it,
        // which matches CSS -- a percentage border-width is not a thing.

        this.width = YogaCalls.LengthCalls.bind(lookup, "Width", true);
        this.height = YogaCalls.LengthCalls.bind(lookup, "Height", true);
        // No YGNodeStyleSetMinWidthAuto or MaxWidthAuto exists in Yoga, so a
        // caller asking for `auto` on a bound is refused by name rather than
        // silently dropped. See applyLength.
        this.minWidth = YogaCalls.LengthCalls.bind(lookup, "MinWidth", false);
        this.minHeight = YogaCalls.LengthCalls.bind(lookup, "MinHeight", false);
        this.maxWidth = YogaCalls.LengthCalls.bind(lookup, "MaxWidth", false);
        this.maxHeight = YogaCalls.LengthCalls.bind(lookup, "MaxHeight", false);
        this.flexBasis = YogaCalls.LengthCalls.bind(lookup, "FlexBasis", true);

        // Inset has no `auto` in Yoga 3.1 -- CSS's `inset: auto` has no
        // equivalent to bind to.
        this.position = YogaCalls.KeyedLengthCalls.bind(lookup, "Position", false);
        this.margin = YogaCalls.KeyedLengthCalls.bind(lookup, "Margin", true);
        this.padding = YogaCalls.KeyedLengthCalls.bind(lookup, "Padding", false);
        this.gap = YogaCalls.KeyedLengthCalls.bind(lookup, "Gap", false);

    }

    static Yoga get() {
        return Holder.INSTANCE;
    }

    // --- config ------------------------------------------------------------

    MemorySegment configNew() {
        return calls.configNew().call();
    }

    void configFree(MemorySegment config) {
        calls.configFree().call(config);
    }

    void configPointScaleFactor(MemorySegment config, float factor) {
        calls.configSetPointScaleFactor().call(config, factor);
    }

    float configPointScaleFactor(MemorySegment config) {
        return calls.configGetPointScaleFactor().call(config);
    }

    void configUseWebDefaults(MemorySegment config, boolean useWebDefaults) {
        calls.configSetUseWebDefaults().call(config, useWebDefaults);
    }

    boolean configUseWebDefaults(MemorySegment config) {
        return calls.configGetUseWebDefaults().call(config);
    }

    // --- node lifecycle and tree -------------------------------------------

    MemorySegment nodeNew() {
        return calls.nodeNew().call();
    }

    MemorySegment nodeNew(MemorySegment config) {
        MemorySegment node;
        node = calls.nodeNewWithConfig().call(config);
        return requireNonNull(node, "YGNodeNewWithConfig");
    }

    /// Frees one node. Never recursive: [YogaNode] knows which Java wrappers
    /// refer to which pointers and frees them one at a time so that each wrapper
    /// can be marked dead as its pointer goes.
    void nodeFree(MemorySegment node) {
        calls.nodeFree().call(node);
    }

    void nodeInsertChild(MemorySegment node, MemorySegment child, long index) {
        calls.nodeInsertChild().call(node, child, index);
    }

    void nodeRemoveChild(MemorySegment node, MemorySegment child) {
        calls.nodeRemoveChild().call(node, child);
    }

    void nodeRemoveAllChildren(MemorySegment node) {
        calls.nodeRemoveAllChildren().call(node);
    }

    long nodeChildCount(MemorySegment node) {
        return calls.nodeGetChildCount().call(node);
    }

    /// Attaches a `YGMeasureFunc`, or clears it when `stub` is
    /// [MemorySegment#NULL].
    void nodeMeasureFunc(MemorySegment node, MemorySegment stub) {
        calls.nodeSetMeasureFunc().call(node, stub);
    }

    boolean nodeHasMeasureFunc(MemorySegment node) {
        return calls.nodeHasMeasureFunc().call(node);
    }

    void nodeMarkDirty(MemorySegment node) {
        calls.nodeMarkDirty().call(node);
    }

    boolean nodeIsDirty(MemorySegment node) {
        return calls.nodeIsDirty().call(node);
    }

    boolean nodeHasNewLayout(MemorySegment node) {
        return calls.nodeGetHasNewLayout().call(node);
    }

    void nodeHasNewLayout(MemorySegment node, boolean hasNewLayout) {
        calls.nodeSetHasNewLayout().call(node, hasNewLayout);
    }

    void nodeCalculateLayout(
            MemorySegment node, float availableWidth, float availableHeight, Direction ownerDirection) {
        calls.nodeCalculateLayout().call(node, availableWidth, availableHeight,
                ownerDirection.nativeValue());
    }

    // --- style -------------------------------------------------------------

    void styleDirection(MemorySegment node, Direction value) {
        calls.styleSetDirection().call(node, value.nativeValue());
    }

    void styleFlexDirection(MemorySegment node, FlexDirection value) {
        calls.styleSetFlexDirection().call(node, value.nativeValue());
    }

    void styleJustifyContent(MemorySegment node, Justify value) {
        calls.styleSetJustifyContent().call(node, value.nativeValue());
    }

    void styleAlignContent(MemorySegment node, Align value) {
        calls.styleSetAlignContent().call(node, value.nativeValue());
    }

    void styleAlignItems(MemorySegment node, Align value) {
        calls.styleSetAlignItems().call(node, value.nativeValue());
    }

    void styleAlignSelf(MemorySegment node, Align value) {
        calls.styleSetAlignSelf().call(node, value.nativeValue());
    }

    void stylePositionType(MemorySegment node, PositionType value) {
        calls.styleSetPositionType().call(node, value.nativeValue());
    }

    void styleFlexWrap(MemorySegment node, Wrap value) {
        calls.styleSetFlexWrap().call(node, value.nativeValue());
    }

    void styleOverflow(MemorySegment node, Overflow value) {
        calls.styleSetOverflow().call(node, value.nativeValue());
    }

    void styleDisplay(MemorySegment node, Display value) {
        calls.styleSetDisplay().call(node, value.nativeValue());
    }

    void styleFlexGrow(MemorySegment node, float value) {
        calls.styleSetFlexGrow().call(node, value);
    }

    void styleFlexShrink(MemorySegment node, float value) {
        calls.styleSetFlexShrink().call(node, value);
    }

    void styleAspectRatio(MemorySegment node, float value) {
        calls.styleSetAspectRatio().call(node, value);
    }

    void styleBorder(MemorySegment node, Edge edge, float value) {
        calls.styleSetBorder().call(node, edge.nativeValue(), value);
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
                calls.layoutGetLeft().call(node),
                calls.layoutGetTop().call(node),
                calls.layoutGetWidth().call(node),
                calls.layoutGetHeight().call(node));
    }

    float layoutMargin(MemorySegment node, Edge edge) {
        return calls.layoutGetMargin().call(node, edge.nativeValue());
    }

    float layoutBorder(MemorySegment node, Edge edge) {
        return calls.layoutGetBorder().call(node, edge.nativeValue());
    }

    float layoutPadding(MemorySegment node, Edge edge) {
        return calls.layoutGetPadding().call(node, edge.nativeValue());
    }

    Direction layoutDirection(MemorySegment node) {
        int value;
        value = calls.layoutGetDirection().call(node);
        // Converted outside the try: a value Yoga does not define is a
        // diagnostic worth keeping, and wrapping it as a failed downcall would
        // bury it.
        return Direction.of(value);
    }

    boolean layoutHadOverflow(MemorySegment node) {
        return calls.layoutGetHadOverflow().call(node);
    }

    // --- the length dispatch -----------------------------------------------

    private static void applyLength(
            YogaCalls.LengthCalls calls, String property, MemorySegment node, StyleLength length) {
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
            YogaCalls.KeyedLengthCalls calls, String property, MemorySegment node, int key,
            StyleLength length) {
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
            throw new IllegalArgumentException(
                    "Yoga has no `auto` for " + property + " — it exports no setter for it,"
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
