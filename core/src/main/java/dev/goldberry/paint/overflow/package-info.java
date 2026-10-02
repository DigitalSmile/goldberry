/// Reporting overflow: a box that did not fit the box it was laid out in, and the
/// log that says so once.
///
/// Overflow is CSS's behaviour, not an error, but a control pushed off the edge of
/// a window looks exactly like one that was never built, so it is recorded.
/// Separate from `paint.tree` because only finding an overrun needs the tree;
/// naming the box, phrasing the message and not repeating it sixty times a second
/// need nothing but the two rectangles.
///
/// Every type here is non-null unless it says `@Nullable`.
///
/// Read more: [How layout works](https://goldberry.dev/docs/layout/index.html#the-box-model).
@NullMarked
package dev.goldberry.paint.overflow;

import org.jspecify.annotations.NullMarked;
