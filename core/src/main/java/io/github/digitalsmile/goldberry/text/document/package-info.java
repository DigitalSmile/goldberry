/// Long text being edited: a document shaped, and re-shaped, one hard line at a
/// time, and its visual lines at one width (ADR-0388).
///
/// A `text.Paragraph` shapes its whole string at once, which is right for a label
/// and wrong for a document somebody is typing into. Exported for `text.edit`'s
/// reason: an application editing text on a `canvas` needs it, and `text-area` is
/// only the first caller. Visual lines are computed on demand, so a document of ten
/// thousand lines does not allocate a record per line per frame.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.text.document;

import org.jspecify.annotations.NullMarked;
