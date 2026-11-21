/// Dragging out of the application: what a drag carries to another
/// application, and what the source hears when it ends.
///
/// A widget that is `draggable` and also has a `dragOut` offer becomes the
/// platform's own drag once the press has travelled the drag threshold. The
/// operating system then draws the picture under the pointer, and another
/// application (a file manager, a mail client, a browser) can take a
/// [DragOffer]'s files, text, links, image or bytes. Over the application's
/// own windows the drag is still an in-app drag: `DropTarget`s light up,
/// hear `whileOver` and are handed the original payload. The source hears one
/// [DragOutcome] when the drag ends, wherever that was.
///
/// The data is asked for when a target takes it, not when the drag starts:
/// each [DragOffer] entry is a supplier, run on the UI thread.
///
/// `@NullMarked`, which puts this package under NullAway.
///
/// Read more: [Input and focus](https://goldberry.dev/docs/guide/input.html#dropped-files-and-text).
@NullMarked
package dev.goldberry.input.dragout;

import org.jspecify.annotations.NullMarked;
