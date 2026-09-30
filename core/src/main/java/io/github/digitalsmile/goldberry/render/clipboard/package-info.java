/// The system clipboard as a backend SPI, and `text/uri-list`, the format files
/// travel in (ADR-0496).
///
/// Each backend provides a
/// [io.github.digitalsmile.goldberry.render.clipboard.Clipboard], and a widget
/// reaches it through its host rather than naming a backend.
///
/// Marked for NullAway from its first commit (`docs/testing.md` §2).
@NullMarked
package io.github.digitalsmile.goldberry.render.clipboard;

import org.jspecify.annotations.NullMarked;
