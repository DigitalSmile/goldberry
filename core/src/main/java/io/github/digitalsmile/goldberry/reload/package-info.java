/// Hot reload: stylesheets and markup watched on disk and re-applied while the
/// application runs (§1, §8, ADR-0051).
///
/// The parsing half has no threads in it, and the watching is layered on top. A
/// file that is broken, half-written or unchanged leaves the last thing that parsed
/// in force — a stylesheet saved mid-edit is expected to be broken, so a failed
/// parse is reported rather than tearing the window down.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.reload;

import org.jspecify.annotations.NullMarked;
