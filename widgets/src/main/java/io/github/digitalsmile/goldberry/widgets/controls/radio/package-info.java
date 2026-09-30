/// `docs/core-widgets.md` §3's `radio-group` and its `radio` — a set of options of
/// which exactly one is chosen.
///
/// [io.github.digitalsmile.goldberry.widgets.controls.radio.RadioGroup] holds the
/// value and the invariant; each
/// [io.github.digitalsmile.goldberry.widgets.controls.radio.Radio] owns only its
/// value and label and is told the rest by the group on every build. The indicator
/// and its dot are parts.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.widgets.controls.radio;

import org.jspecify.annotations.NullMarked;
