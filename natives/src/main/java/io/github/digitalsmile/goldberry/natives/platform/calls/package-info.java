/// The downcall holder for `goldberry_platform_capabilities`, the one function that
/// reports what this build of the platform layer can do.
///
/// **Not exported** (ADR-0173). The capability query in `…natives.platform` is the
/// one caller.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.natives.platform.calls;

import org.jspecify.annotations.NullMarked;
