/// Hot reload: stylesheets and markup watched on disk and re-applied while the
/// application runs.
///
/// The parsing half has no threads in it, and the watching is layered on top. A
/// file that is broken, half-written or unchanged leaves the last thing that parsed
/// in force — a stylesheet saved mid-edit is expected to be broken, so a failed
/// parse is reported rather than tearing the window down.
///
/// Null-marked: a parameter or return is non-null unless it says `@Nullable`.
///
/// Read more: [Markup](https://goldberry.dev/docs/guide/markup.html#hot-reload).
@NullMarked
package dev.goldberry.reload;

import org.jspecify.annotations.NullMarked;
