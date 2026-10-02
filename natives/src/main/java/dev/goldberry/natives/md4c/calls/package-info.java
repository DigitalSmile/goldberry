/// The downcall holders for the Markdown functions `libgoldberry` exports. None is
/// md4c's own: `md_parse` is a callback parser, and what crosses here instead is one
/// encoded buffer per document.
///
/// **Not exported**, like every `…calls` package. The parser in `…natives.md4c` is
/// the one caller.
///
/// Marked for NullAway, so a parameter that may be null says so on its signature.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@NullMarked
package dev.goldberry.natives.md4c.calls;

import org.jspecify.annotations.NullMarked;
