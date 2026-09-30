/// The events a widget is handed: a pointer event, a key going down or up, text the
/// platform has finished translating, and the composition an input method has in
/// progress.
///
/// Keys and text are kept apart (§7.1) because one character can take several keys
/// — a dead key, a compose sequence, an IME conversion — so a widget that wants
/// what was typed never reasons about what was pressed. Positions are logical
/// pixels; the window's scale is applied when the frame is rasterized (ADR-0031).
///
/// Exported as one of input's parts, split by the role each plays (ADR-0172).
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.input.event;

import org.jspecify.annotations.NullMarked;
