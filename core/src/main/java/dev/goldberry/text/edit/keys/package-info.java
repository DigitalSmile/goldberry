/// The one key map every editor in the toolkit reads.
///
/// [EditKeys] is the table, [EditCommand] is what it produces, and [EditSurface]
/// is the only thing it needs to know about the editor asking. Nothing here
/// touches a string, a caret or a font: it is the keyboard half of editing on its
/// own, so that `text-input`, `text-area` and the canvas `Editor` share one map
/// without sharing a text model.
///
/// Read more: [Input and focus](https://goldberry.dev/docs/guide/input.html#keys-and-text-are-different-events).
@org.jspecify.annotations.NullMarked
package dev.goldberry.text.edit.keys;
