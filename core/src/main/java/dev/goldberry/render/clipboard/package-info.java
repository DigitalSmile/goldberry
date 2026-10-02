/// The system clipboard as a backend SPI, the primary selection where the
/// platform has one, and `text/uri-list`, the format files travel in.
///
/// Each backend provides a
/// [dev.goldberry.render.clipboard.Clipboard], and a widget
/// reaches it through its host rather than naming a backend. Exported to every
/// module.
///
/// Null-marked: a parameter or return is non-null unless it says `@Nullable`.
///
/// Read more: [Text, fonts and icons](https://goldberry.dev/docs/guide/text.html#the-clipboard).
@NullMarked
package dev.goldberry.render.clipboard;

import org.jspecify.annotations.NullMarked;
