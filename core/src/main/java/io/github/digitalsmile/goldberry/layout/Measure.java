package io.github.digitalsmile.goldberry.layout;

/// Measures a leaf that the layout engine cannot size for itself.
///
/// A paragraph is the case the toolkit actually has: flexbox can place a box but
/// it cannot know how tall a wrapped line of text comes out, so the engine calls
/// back and asks.
///
/// ## Why the toolkit owns this interface
///
/// It is the return type of `Paragraph.measureFunction()`, which is read by the
/// render tree — so before ADR-0279 an application reading that signature read a
/// `:natives` callback type. Nothing about the question is native: it is four
/// numbers in and two out.
///
/// Implementations run on the layout engine's calling thread, in the middle of a
/// layout pass, so this is the wrong place for anything slow or reentrant.
@FunctionalInterface
public interface Measure {

    /// @param width      the constraint across, meaningless when `widthMode` is
    ///                   [MeasureMode#UNDEFINED]
    /// @param widthMode  how to read `width`
    /// @param height     the constraint down
    /// @param heightMode how to read `height`
    MeasuredSize measure(float width, MeasureMode widthMode, float height, MeasureMode heightMode);
}
