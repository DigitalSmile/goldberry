/// The events a widget is handed: a pointer event, a key going down or up, text the
/// platform has finished translating, and the composition an input method has in
/// progress.
///
/// Keys and text are kept apart because one character can take several keys
/// — a dead key, a compose sequence, an IME conversion — so a widget that wants
/// what was typed never reasons about what was pressed. Positions are logical
/// pixels; the window's scale is applied when the frame is rasterized.
///
/// Exported to applications as one of input's parts, split by the role each
/// plays.
///
/// `@NullMarked`, which puts this package under NullAway.
///
/// Read more: [Input and focus](https://goldberry.dev/docs/guide/input.html#kinds).
@NullMarked
package dev.goldberry.input.event;

import org.jspecify.annotations.NullMarked;
