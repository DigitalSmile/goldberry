/// `docs/core-widgets.md` §7's `message` — an inline banner about the region it sits
/// in, with a kind, an icon, text, optional actions and an optional dismiss.
///
/// [io.github.digitalsmile.goldberry.widgets.overlay.message.Message] is the one
/// member of the overlay group that never floats: it is a child in a layout and lasts
/// as long as the condition it describes, which is what makes it not a `toast`. The
/// icon, body, actions and dismiss are parts.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.widgets.overlay.message;

import org.jspecify.annotations.NullMarked;
