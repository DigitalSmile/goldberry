/// `docs/core-widgets.md` §3's `slider` — a continuous value on a track, placed by
/// direct manipulation, with optional tick marks, a value label and a scale.
///
/// [dev.goldberry.widgets.controls.slider.Slider] is the widget.
/// The track, groove, fill, thumb, ticks, value label and the marked spans in the
/// groove are parts, invisible outside this package; the curve between value and
/// position is [dev.goldberry.widgets.controls.Scale], which the
/// package above keeps for every control that needs one.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.widgets.controls.slider;

import org.jspecify.annotations.NullMarked;
