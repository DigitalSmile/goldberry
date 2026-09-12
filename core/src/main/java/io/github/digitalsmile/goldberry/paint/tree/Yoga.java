package io.github.digitalsmile.goldberry.paint.tree;

import io.github.digitalsmile.goldberry.layout.Align;
import io.github.digitalsmile.goldberry.layout.FlexDirection;
import io.github.digitalsmile.goldberry.layout.Insets;
import io.github.digitalsmile.goldberry.layout.Justify;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.layout.Measure;
import io.github.digitalsmile.goldberry.layout.MeasureMode;
import io.github.digitalsmile.goldberry.layout.Overflow;
import io.github.digitalsmile.goldberry.layout.Position;
import io.github.digitalsmile.goldberry.layout.Wrap;
import io.github.digitalsmile.goldberry.natives.yoga.ComputedLayout;
import io.github.digitalsmile.goldberry.natives.yoga.measure.MeasureFunction;
import io.github.digitalsmile.goldberry.natives.yoga.style.PositionType;
import io.github.digitalsmile.goldberry.natives.yoga.style.StyleLength;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;

/// Where the toolkit's flexbox vocabulary meets the layout engine's.
///
/// ## Why there is exactly one of these
///
/// `:core` speaks [io.github.digitalsmile.goldberry.layout] everywhere — a `Box`
/// carries it, a `ComputedStyle` resolves to it, a widget writes it. Yoga speaks
/// its own, and that vocabulary belongs in `:natives` where the layout probe can
/// check its enumerators against the compiled library.
///
/// The two meet only where a `YogaNode` is actually called, and that is
/// [RenderObject] — whose Yoga-touching members were already package-private
/// before any of this. So the translation is one file in one package, and nothing
/// else in the toolkit needs to know Yoga exists (ADR-0279).
///
/// ## Why it is a `switch` and not an ordinal
///
/// The two enumerations happen to agree on several values today. They are not
/// obliged to, they are not alphabetical, and the engine's numbering is a
/// property of a pinned C header that a bump could reorder — Yoga has inserted a
/// constant into the middle of an enum before. An ordinal cast would be a
/// coincidence relied upon; an exhaustive `switch` is a claim the compiler checks
/// on both sides.
///
/// `Align.CENTER` is 2 and `Justify.CENTER` is 1 in those headers, which is the
/// concrete version of the same point.
///
/// ## Why half of this file is fully qualified
///
/// Every type here collides by simple name with its own counterpart — `Insets`
/// with `Insets`, `Align` with `Align`, and so on down the list — which is not an
/// accident: the two vocabularies describe the same six concepts, so they were
/// always going to pick the same words. Java has no import alias, so one side has
/// to be spelled out. The toolkit's side is imported because it is what the rest
/// of `:core` reads; the engine's is spelled out because it appears **only in
/// this file**, and spelling it out is a reminder of that.
final class Yoga {

    private Yoga() {}

    /// A toolkit [Length] as the engine's.
    ///
    /// [Length#UNDEFINED] reaches Yoga as `YGUndefined`, which is a NaN —
    /// the engine's convention, and the reason a caller never produces one.
    static StyleLength length(Length length) {
        return switch (length) {
            case Length.Points points -> StyleLength.points(points.value());
            case Length.Percent percent -> StyleLength.percent(percent.value());
            case Length.Keyword.AUTO -> StyleLength.AUTO;
            case Length.Keyword.UNDEFINED -> StyleLength.UNDEFINED;
        };
    }

    /// A laid-out rectangle as the toolkit's own geometry.
    ///
    /// The one translation that goes the other way, and the only one that
    /// **deletes** a type rather than mirroring it: `render.model.LogicalRect` is
    /// already the toolkit's rectangle — `input.hit.HitTest.Region` has always
    /// returned one — so a `ComputedLayout` mirror would have been a second
    /// four-float rectangle kept alike by hand (ADR-0279).
    static LogicalRect rect(ComputedLayout layout) {
        return LogicalRect.of(layout.left(), layout.top(), layout.width(), layout.height());
    }

    /// A toolkit [Measure] as the engine's callback.
    ///
    /// The one translation that wraps rather than converts: it is called *by* the
    /// engine, in the middle of a layout pass, so what crosses is an adapter and
    /// the two `MeasureMode` vocabularies are translated per call.
    static MeasureFunction measure(Measure measure) {
        return (width, widthMode, height, heightMode) -> {
            var measured = measure.measure(width, mode(widthMode), height, mode(heightMode));
            return new io.github.digitalsmile.goldberry.natives.yoga.measure.MeasuredSize(
                    measured.width(), measured.height());
        };
    }

    /// The engine's measure mode as the toolkit's — the one arrow that points
    /// inward, because this value arrives from a callback.
    private static MeasureMode mode(io.github.digitalsmile.goldberry.natives.yoga.measure.MeasureMode mode) {
        return switch (mode) {
            case UNDEFINED -> MeasureMode.UNDEFINED;
            case EXACTLY -> MeasureMode.EXACTLY;
            case AT_MOST -> MeasureMode.AT_MOST;
        };
    }

    /// Four edges, translated edge by edge.
    static io.github.digitalsmile.goldberry.natives.yoga.Insets insets(Insets insets) {
        return new io.github.digitalsmile.goldberry.natives.yoga.Insets(
                length(insets.top()), length(insets.right()), length(insets.bottom()), length(insets.left()));
    }

    static io.github.digitalsmile.goldberry.natives.yoga.style.FlexDirection direction(FlexDirection direction) {
        return switch (direction) {
            case COLUMN -> io.github.digitalsmile.goldberry.natives.yoga.style.FlexDirection.COLUMN;
            case COLUMN_REVERSE -> io.github.digitalsmile.goldberry.natives.yoga.style.FlexDirection.COLUMN_REVERSE;
            case ROW -> io.github.digitalsmile.goldberry.natives.yoga.style.FlexDirection.ROW;
            case ROW_REVERSE -> io.github.digitalsmile.goldberry.natives.yoga.style.FlexDirection.ROW_REVERSE;
        };
    }

    static io.github.digitalsmile.goldberry.natives.yoga.style.Justify justify(Justify justify) {
        return switch (justify) {
            case FLEX_START -> io.github.digitalsmile.goldberry.natives.yoga.style.Justify.FLEX_START;
            case CENTER -> io.github.digitalsmile.goldberry.natives.yoga.style.Justify.CENTER;
            case FLEX_END -> io.github.digitalsmile.goldberry.natives.yoga.style.Justify.FLEX_END;
            case SPACE_BETWEEN -> io.github.digitalsmile.goldberry.natives.yoga.style.Justify.SPACE_BETWEEN;
            case SPACE_AROUND -> io.github.digitalsmile.goldberry.natives.yoga.style.Justify.SPACE_AROUND;
            case SPACE_EVENLY -> io.github.digitalsmile.goldberry.natives.yoga.style.Justify.SPACE_EVENLY;
        };
    }

    static io.github.digitalsmile.goldberry.natives.yoga.style.Align align(Align align) {
        return switch (align) {
            case AUTO -> io.github.digitalsmile.goldberry.natives.yoga.style.Align.AUTO;
            case FLEX_START -> io.github.digitalsmile.goldberry.natives.yoga.style.Align.FLEX_START;
            case CENTER -> io.github.digitalsmile.goldberry.natives.yoga.style.Align.CENTER;
            case FLEX_END -> io.github.digitalsmile.goldberry.natives.yoga.style.Align.FLEX_END;
            case STRETCH -> io.github.digitalsmile.goldberry.natives.yoga.style.Align.STRETCH;
            case BASELINE -> io.github.digitalsmile.goldberry.natives.yoga.style.Align.BASELINE;
            case SPACE_BETWEEN -> io.github.digitalsmile.goldberry.natives.yoga.style.Align.SPACE_BETWEEN;
            case SPACE_AROUND -> io.github.digitalsmile.goldberry.natives.yoga.style.Align.SPACE_AROUND;
            case SPACE_EVENLY -> io.github.digitalsmile.goldberry.natives.yoga.style.Align.SPACE_EVENLY;
        };
    }

    static io.github.digitalsmile.goldberry.natives.yoga.style.Wrap wrap(Wrap wrap) {
        return switch (wrap) {
            case NO_WRAP -> io.github.digitalsmile.goldberry.natives.yoga.style.Wrap.NO_WRAP;
            case WRAP -> io.github.digitalsmile.goldberry.natives.yoga.style.Wrap.WRAP;
            case WRAP_REVERSE -> io.github.digitalsmile.goldberry.natives.yoga.style.Wrap.WRAP_REVERSE;
        };
    }

    static PositionType position(Position position) {
        return switch (position) {
            case STATIC -> PositionType.STATIC;
            case RELATIVE -> PositionType.RELATIVE;
            case ABSOLUTE -> PositionType.ABSOLUTE;
        };
    }

    static io.github.digitalsmile.goldberry.natives.yoga.style.Overflow overflow(Overflow overflow) {
        return switch (overflow) {
            case VISIBLE -> io.github.digitalsmile.goldberry.natives.yoga.style.Overflow.VISIBLE;
            case HIDDEN -> io.github.digitalsmile.goldberry.natives.yoga.style.Overflow.HIDDEN;
            case SCROLL -> io.github.digitalsmile.goldberry.natives.yoga.style.Overflow.SCROLL;
        };
    }
}
