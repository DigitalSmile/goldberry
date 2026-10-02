/// The `canvas` widget — an immediate-mode drawing surface, the escape hatch for
/// anything the catalogue does not draw, and the base the data widgets build on.
///
/// [dev.goldberry.widgets.core.canvas.Canvas] is the widget, drawn
/// by the painter it is handed, and
/// [dev.goldberry.widgets.core.canvas.Input] is what it does with
/// pointer, keyboard and text input, in the painter's coordinates.
///
/// Null-marked: every reference is non-null unless annotated otherwise.
///
/// Read more: [Canvas, images and QR codes](https://goldberry.dev/docs/components/drawing.html#canvas).
@NullMarked
package dev.goldberry.widgets.core.canvas;

import org.jspecify.annotations.NullMarked;
