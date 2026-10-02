/// The `slider` — a continuous value on a track, placed by direct manipulation,
/// with optional tick marks, a value label and a scale.
///
/// [dev.goldberry.widgets.controls.slider.Slider] is the widget. The track,
/// groove, fill, thumb, ticks, value label and the marked spans in the groove
/// are parts, invisible outside this package; the curve between value and
/// position is [dev.goldberry.widgets.controls.Scale], which the package above
/// keeps for every control that needs one.
///
/// Every reference is non-null unless it says `@Nullable`.
///
/// Read more: [Values and progress](https://goldberry.dev/docs/components/values.html#slider).
@NullMarked
package dev.goldberry.widgets.controls.slider;

import org.jspecify.annotations.NullMarked;
