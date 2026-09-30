/// The downcall holders for libwebp's own functions, bound directly with no C glue:
/// the decoder, and the encoder and animation reader that followed it (ADR-0329,
/// ADR-0385).
///
/// **Not exported** (ADR-0173). The decoder in `…natives.webp` is the one caller.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.natives.webp.calls;

import org.jspecify.annotations.NullMarked;
