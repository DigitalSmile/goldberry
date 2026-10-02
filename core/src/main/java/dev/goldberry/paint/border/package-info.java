/// Border styles: where the dashes of a `dashed` side and the dots of a
/// `dotted` one fall.
///
/// A styled side is drawn along the line down its middle, from corner to
/// corner, with its pattern stretched so that every corner is inked.
/// `BorderPattern` works out the line and the pattern; the painter in `paint`
/// strokes and fills them. Nothing here touches a frame, so the geometry is
/// tested on its numbers alone.
///
/// Every type here is non-null unless it says `@Nullable`.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#border-outline-and-shadow).
@NullMarked
package dev.goldberry.paint.border;

import org.jspecify.annotations.NullMarked;
