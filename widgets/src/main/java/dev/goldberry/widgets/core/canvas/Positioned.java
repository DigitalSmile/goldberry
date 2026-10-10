package dev.goldberry.widgets.core.canvas;

import java.util.Objects;

import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.widget.Widget;

/// A real widget placed on a `canvas` at a rectangle the painter chose.
///
/// ```java
/// new Canvas(week::paint).overlay(size -> week.layout(size).blocks().stream()
///         .map(block -> new Positioned(
///                 new Pressable(block.title(), () -> open(block.event())).keyed(block.event().id()),
///                 block.rect()))
///         .toList())
/// ```
///
/// What [Canvas#overlay] hands back, one per widget. [#at] is in the painter's
/// coordinates, measured from the corner of the canvas's content box, which is
/// where the painter draws from and where `PointerEvent.content()` is measured
/// from: a block painted at `(x, y, w, h)` is covered exactly by a widget
/// positioned at the same rectangle.
///
/// The widget fills its rectangle: it is stretched across it and grown down
/// it, whatever its own stylesheet says about its size.
///
/// Read more: [Canvas, images and QR codes](https://goldberry.dev/docs/components/drawing.html#canvas).
///
/// @param widget what to place; give it a key so its state follows it from one
///               layout to the next
/// @param at     where, in the painter's coordinates
public record Positioned(Widget widget, LogicalRect at) {

    public Positioned {
        Objects.requireNonNull(widget, "widget");
        Objects.requireNonNull(at, "at");
    }
}
