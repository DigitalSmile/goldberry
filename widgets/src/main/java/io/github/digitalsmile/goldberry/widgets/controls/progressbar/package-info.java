/// `docs/core-widgets.md` §3's `progress` — how far along something is, determinate
/// or indeterminate.
///
/// [io.github.digitalsmile.goldberry.widgets.controls.progressbar.Progress] only
/// reports: nothing in it is focusable or takes a pointer. The indeterminate form is
/// the same widget matching `:indeterminate`, and the fill is a part.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.widgets.controls.progressbar;

import org.jspecify.annotations.NullMarked;
