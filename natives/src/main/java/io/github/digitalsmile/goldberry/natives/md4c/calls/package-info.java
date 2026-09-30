/// The downcall holders for the Markdown functions `libgoldberry` exports. None is
/// md4c's own: `md_parse` is a callback parser, and what crosses here instead is one
/// encoded buffer per document (ADR-0294).
///
/// **Not exported** (ADR-0173). The parser in `…natives.md4c` is the one caller.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.natives.md4c.calls;

import org.jspecify.annotations.NullMarked;
