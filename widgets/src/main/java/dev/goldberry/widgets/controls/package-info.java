/// The controls group: one package per control, and here the one type they
/// share.
///
/// [dev.goldberry.widgets.controls.Scale] is the curve between a value and a
/// position along a track or round a dial, which `slider` and `knob` both use.
/// Every control lives in its own sub-package with its parts, so a part is
/// styleable from CSS and not constructible from outside the control that owns
/// it.
///
/// Every reference is non-null unless it says `@Nullable`.
///
/// Read more: [The catalogue](https://goldberry.dev/docs/components/index.html#parts-are-not-widgets).
@NullMarked
package dev.goldberry.widgets.controls;

import org.jspecify.annotations.NullMarked;
