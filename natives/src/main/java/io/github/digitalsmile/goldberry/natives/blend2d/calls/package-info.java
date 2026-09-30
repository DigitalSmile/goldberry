/// The downcall holders for Blend2D: one record per object — context, font,
/// gradient, image, path, and the runtime query — with one static final handle per
/// function.
///
/// **Not exported** (ADR-0173). The package-private binding classes in
/// `…natives.blend2d` are their only callers.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.natives.blend2d.calls;

import org.jspecify.annotations.NullMarked;
