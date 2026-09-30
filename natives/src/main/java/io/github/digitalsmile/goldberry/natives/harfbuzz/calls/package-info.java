/// The downcall holders for HarfBuzz: its buffer, its blob, face and font, the
/// shaper with its tag lookups, and the version query — one static final handle per
/// function.
///
/// **Not exported** (ADR-0173). The package-private binding class in
/// `…natives.harfbuzz` is their only caller.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.natives.harfbuzz.calls;

import org.jspecify.annotations.NullMarked;
