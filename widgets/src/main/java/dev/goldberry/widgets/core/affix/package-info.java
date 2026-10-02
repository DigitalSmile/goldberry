/// The `affix` widget: a child pinned to an edge of the nearest `scroll` once it
/// would have scrolled past, such as a section header.
///
/// [dev.goldberry.widgets.core.affix.Affix] is the widget and
/// [dev.goldberry.widgets.core.affix.Edge] the side it pins to. It
/// leaves a same-sized hole in layout when it detaches, so nothing below it jumps;
/// the slot and the pinned content are parts. Annotated `@NullMarked`: every type
/// here is non-null unless it says `@Nullable`.
///
/// Read more: [Affix](https://goldberry.dev/docs/layout/affix.html#affix).
@NullMarked
package dev.goldberry.widgets.core.affix;

import org.jspecify.annotations.NullMarked;
