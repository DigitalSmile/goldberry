/// What a widget says to the cascade and to the painter: [Styled] names it to a
/// stylesheet, [Paints] turns its resolved style into a box, and [Corner] names
/// where an overlay pins.
///
/// A custom widget that draws implements both interfaces beside `Widget.Leaf`.
/// The renderer reads the widget's own pseudo-classes through `Styled` before
/// the cascade runs, and asks `Paints` for a box afterwards with a context that
/// shapes text and resolves design tokens for that node.
///
/// The package is null-marked: a parameter or return is non-null unless annotated
/// `@Nullable`.
///
/// Read more:
/// [A name for the cascade](https://goldberry.dev/docs/guide/writing-a-widget.html#styled-a-name-for-the-cascade).
@NullMarked
package dev.goldberry.widget.style;

import org.jspecify.annotations.NullMarked;
