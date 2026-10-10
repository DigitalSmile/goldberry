/// The application's own cursors: a picture for a shape, at the sizes it was
/// drawn at, and the hot spot of each.
///
/// What [dev.goldberry.Application#cursors()] answers with. The shapes stay
/// CSS's, so `cursor: grab` in a stylesheet and the toolkit's own `pointer`
/// over a link draw the application's pictures without either naming one.
/// Exported to every module.
///
/// Every type here is non-null unless it says `@Nullable`.
///
/// Read more: [Input and focus](https://goldberry.dev/docs/guide/input.html#the-cursor).
@NullMarked
package dev.goldberry.input.cursor;

import org.jspecify.annotations.NullMarked;
