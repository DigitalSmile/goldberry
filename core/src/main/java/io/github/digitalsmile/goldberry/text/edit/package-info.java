/// Editing text: what a caret is, where it is, and what a key does to it.
///
/// **The half of the text stack that was in the catalogue.** `TextEdit` and
/// `EditHistory` — a string with a caret and an anchor, and an undo stack that
/// coalesces a typing run — were `:widgets`' own, inside `form.textinput`, even
/// though neither has ever named a widget, a box or an element. That put the
/// rules of text editing in the module that draws text fields, so an application
/// editing text *anywhere else* — a sticky on a board, a label on a shape — had
/// either to grow its own or to reach into a control's package (ADR-0285).
///
/// They are here now, beside the shaping and the layout they are arithmetic
/// over, with the two things they were missing: [TextGeometry], which is where a
/// caret **is** on a wrapped paragraph, and [Editor], which is the whole of a
/// text editor as a thing an application drives from a `canvas`.
///
/// `@NullMarked`, which puts this package under NullAway.
@NullMarked
package io.github.digitalsmile.goldberry.text.edit;

import org.jspecify.annotations.NullMarked;
