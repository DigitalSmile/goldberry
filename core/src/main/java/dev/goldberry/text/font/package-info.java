/// The font chain: a typeface, the same face at a size, the book a window keeps
/// them in, the faces an application ships, and which characters a face covers.
///
/// An application picks a face and a size and does not lay out a line by hand,
/// so these types stand apart from the paragraph. A face is opened once and
/// shared by every size over it, and a window keeps each face and size it draws
/// with in one `Fonts` book, so a widget tree rebuilt every frame does not parse
/// a font every frame. A font and a face hold native memory: each is confined to
/// the thread that created it and must be closed, and the book that opened them
/// closes them.
///
/// The package is null-marked: a parameter or return is non-null unless annotated
/// `@Nullable`.
///
/// Read more:
/// [Faces, fonts and the book](https://goldberry.dev/docs/guide/text.html#faces-fonts-and-the-book).
@NullMarked
package dev.goldberry.text.font;

import org.jspecify.annotations.NullMarked;
