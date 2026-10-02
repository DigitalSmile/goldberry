/// A modifier key tapped on its own — pressed and released with nothing in
/// between — which is how a menu bar opens from the keyboard.
///
/// A tap is a gesture over two events, not an accelerator, so it is detected at
/// the window from raw keycodes rather than looked up in a table. Exported to
/// applications as one of input's parts, split by the role each plays.
///
/// `@NullMarked`, which puts this package under NullAway.
///
/// Read more: [Input and focus](https://goldberry.dev/docs/guide/input.html#accelerators).
@NullMarked
package dev.goldberry.input.tap;

import org.jspecify.annotations.NullMarked;
