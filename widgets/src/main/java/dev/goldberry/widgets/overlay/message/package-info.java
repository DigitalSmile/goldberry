/// Messages: an inline banner about the region it sits in, with a kind, an
/// icon, text, optional actions and an optional dismiss.
///
/// [dev.goldberry.widgets.overlay.message.Message] is the one
/// member of the overlay group that never floats: it is a child in a layout and lasts
/// as long as the condition it describes, which is what makes it not a `toast`. The
/// icon, body, actions and dismiss are parts.
///
/// Null-marked: every reference is non-null unless it says `@Nullable`.
///
/// Read more: [Overlays](https://goldberry.dev/docs/components/overlays.html#message).
@NullMarked
package dev.goldberry.widgets.overlay.message;

import org.jspecify.annotations.NullMarked;
