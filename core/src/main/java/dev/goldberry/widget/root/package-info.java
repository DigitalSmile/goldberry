/// The window's own node: the root the launcher puts above an application's
/// content, with the overlay layer and the tooltip plate that float over it.
///
/// `window-root` and `tooltip` can be styled from a stylesheet and are not
/// written in markup: a document cannot write the node it is the document of,
/// and a tooltip is produced by an attribute rather than by a widget. Exported
/// because a launcher of one's own builds the same root.
///
/// The package is null-marked: a parameter or return is non-null unless annotated
/// `@Nullable`.
///
/// Read more:
/// [Overlays and popups](https://goldberry.dev/docs/guide/windows.html#overlays-and-popups).
@NullMarked
package dev.goldberry.widget.root;

import org.jspecify.annotations.NullMarked;
