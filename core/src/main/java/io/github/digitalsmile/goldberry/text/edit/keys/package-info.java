/// The one key map every editor in the toolkit reads.
///
/// [EditKeys] is the table, [EditCommand] is what it produces, and
/// [EditSurface] is the only thing it needs to know about the editor asking.
/// Nothing here touches a string, a caret or a font: it is the keyboard half of
/// editing, on its own, so that the three editors can share it without sharing a
/// text model ([ADR-0376]).
@org.jspecify.annotations.NullMarked
package io.github.digitalsmile.goldberry.text.edit.keys;
