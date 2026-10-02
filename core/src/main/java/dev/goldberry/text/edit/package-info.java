/// Editing text: a string with a caret and a selection, the undo history over it,
/// where a caret is on wrapped text, and a whole editor an application drives
/// from a `canvas`.
///
/// [TextEdit] is the value — the text, the anchor and the caret — and every edit
/// returns a new one. [EditHistory] is the undo and redo stack of those values,
/// with a typing run folded into one step. [TextGeometry] answers where a caret
/// is drawn, what a click landed on and what `Up` means on text that wraps.
/// [Editor] puts the three together with the shared key map and a clipboard, for
/// text no widget owns: a sticky on a board, a label on a shape, a cell in a
/// drawing. `text-input` and `text-area` are built from the same pieces.
///
/// These live beside the shaping and the layout they are arithmetic over, and not
/// in a control's package, so an application editing text anywhere can reach them
/// without reaching into a widget.
///
/// The package is null-marked: a parameter or return is non-null unless annotated
/// `@Nullable`.
///
/// Read more: [Selection and editing](https://goldberry.dev/docs/guide/text.html#selection-and-editing).
@NullMarked
package dev.goldberry.text.edit;

import org.jspecify.annotations.NullMarked;
