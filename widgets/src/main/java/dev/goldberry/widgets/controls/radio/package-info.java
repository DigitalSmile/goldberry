/// The `radio-group` and its `radio` — a set of options of which exactly one is
/// chosen.
///
/// [dev.goldberry.widgets.controls.radio.RadioGroup] holds the value and the
/// invariant; each [dev.goldberry.widgets.controls.radio.Radio] owns only its
/// value and label and is told the rest by the group on every build. The
/// indicator and its dot are parts.
///
/// Every reference is non-null unless it says `@Nullable`.
///
/// Read more: [Choices](https://goldberry.dev/docs/components/choices.html#radio-group).
@NullMarked
package dev.goldberry.widgets.controls.radio;

import org.jspecify.annotations.NullMarked;
