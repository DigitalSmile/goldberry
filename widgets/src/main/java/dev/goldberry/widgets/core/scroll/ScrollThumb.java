package dev.goldberry.widgets.core.scroll;

import java.util.List;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.value.Transform;
import dev.goldberry.layout.Length;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The draggable part of a [ScrollBar] — the pill-shaped thumb.
///
/// ## Its length is a measurement, not a style
///
/// How long a thumb is says what proportion of the document is on screen, and
/// that is the one thing about it a stylesheet cannot know. So the length and the
/// travel are written here through [Styled#restyle], which exists for exactly
/// this: a widget may write there only what a stylesheet could not have written.
///
/// Everything else — the colour, the radius, the width, what `:hover` does — is
/// the stylesheet's and is not touched.
///
/// ## Length in pixels, travel in a transform
///
/// The length has to be a real size, because the thumb is a box and a box's
/// height is Yoga's. The *position* is a `translate`, for the reason the content
/// it mirrors is translated: movement stays off layout properties, and a thumb
/// that moved by changing its margin would re-run Yoga on every wheel notch to
/// shift a 6px rectangle.
///
/// @param vertical whether this thumb runs down a bar rather than along one
/// @param length   how long it is, in logical pixels
/// @param offset   how far along its track, in logical pixels
/// @param dragging whether the pointer is holding it — the accent colour,
///                 spelled as a class because `:active` is the router's and the
///                 thumb is dragged by the bar rather than pressed itself
record ScrollThumb(boolean vertical, double length, double offset, boolean dragging)
        implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "scroll-thumb";
    }

    @Override
    public java.util.Set<String> classes() {
        // Two classes rather than a modifier on the bar, so a stylesheet can say
        // `scroll-thumb.dragging` without a descendant selector.
        return dragging
                ? java.util.Set.of(vertical ? "vertical" : "horizontal", "dragging")
                : java.util.Set.of(vertical ? "vertical" : "horizontal");
    }

    @Override
    public ComputedStyle restyle(ComputedStyle resolved) {
        var sized = vertical
                ? resolved.height(Length.points((float) length))
                : resolved.width(Length.points((float) length));
        if (offset == 0) {
            return sized;
        }
        return sized.transform(Transform.of(new Transform.Function.Translate(
                Transform.Length.px(vertical ? 0 : offset), Transform.Length.px(vertical ? offset : 0))));
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style);
    }
}
