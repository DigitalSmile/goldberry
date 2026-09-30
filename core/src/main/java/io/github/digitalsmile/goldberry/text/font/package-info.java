/// The font chain: a typeface, the same face at a size, the fallback list a
/// paragraph is shaped against, the faces an application ships, and which
/// characters a face covers.
///
/// Separate from the paragraph because an application picks a font source and does
/// not lay out a line by hand (ADR-0172). A face is opened once and shared by every
/// size (ADR-0044), and a window keeps each face and size it draws with, so a
/// widget tree rebuilt every frame does not parse a font every frame.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.text.font;

import org.jspecify.annotations.NullMarked;
