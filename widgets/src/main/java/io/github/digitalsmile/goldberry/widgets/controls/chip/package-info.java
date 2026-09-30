/// `docs/core-widgets.md` §3's `chip` — a small rounded label you can choose and take
/// away, such as a filter that is on or a tag on a document.
///
/// Unlike a `badge`, [io.github.digitalsmile.goldberry.widgets.controls.chip.Chip] is
/// a control: it is focusable, carries `:checked` and can be dismissed. Its dot,
/// label and × are parts, styleable and not constructible (ADR-0305).
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.widgets.controls.chip;

import org.jspecify.annotations.NullMarked;
