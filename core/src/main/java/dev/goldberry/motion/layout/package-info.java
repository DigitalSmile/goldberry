/// Layout animation's bookkeeping: where each named box was laid out and drawn,
/// and the move that carries it from one to the other.
///
/// Pure arithmetic over rectangles and a clock. The render tree walks its nodes
/// and asks; nothing here knows what a node is. Not exported: an application
/// asks for layout animation with `view-transition-name`.
///
/// Read more: [The design system](https://goldberry.dev/docs/guide/design-system.html#motion).
@org.jspecify.annotations.NullMarked
package dev.goldberry.motion.layout;
