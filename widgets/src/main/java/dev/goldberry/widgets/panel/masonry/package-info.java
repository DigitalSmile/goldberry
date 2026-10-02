/// The `masonry`: cards in columns, each card placed under the column that is
/// currently shortest.
///
/// [dev.goldberry.widgets.panel.masonry.Masonry] is the widget. It reads each
/// card's height from the previous frame and counts its columns from its own
/// width, or takes a fixed count. The wall and its columns are parts.
///
/// Every reference is non-null unless it says `@Nullable`.
///
/// Read more: [Masonry](https://goldberry.dev/docs/layout/masonry.html#masonry).
@NullMarked
package dev.goldberry.widgets.panel.masonry;

import org.jspecify.annotations.NullMarked;
