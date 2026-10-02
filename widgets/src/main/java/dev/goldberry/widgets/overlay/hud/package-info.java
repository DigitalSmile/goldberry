/// The HUD: what the frame loop is doing, drawn in a corner of the window it
/// is doing it to.
///
/// [dev.goldberry.widgets.overlay.hud.Hud] is the widget and
/// [dev.goldberry.widgets.overlay.hud.Reading] is one number it can show: the
/// rate, the display's refresh, the frames that were late, the paint time and
/// its stages, and a composited window's present. Each reading is a mean over
/// the last sixty frames, judged against a share of one display frame. The
/// rows and the caption are parts.
///
/// Null-marked: every reference is non-null unless it says `@Nullable`.
///
/// Read more: [Overlays](https://goldberry.dev/docs/components/overlays.html#hud).
@NullMarked
package dev.goldberry.widgets.overlay.hud;

import org.jspecify.annotations.NullMarked;
