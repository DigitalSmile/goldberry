/// Tours: a guided sequence of popovers over real widgets, dimming the window
/// around each target and scrolling it into view.
///
/// A [dev.goldberry.widgets.overlay.tour.Tour] is a list of
/// [dev.goldberry.widgets.overlay.tour.Stop]s, each naming its
/// target by id with a title and a body. Starting one needs a `Host`, so
/// [dev.goldberry.widgets.overlay.tour.Tours] is the call that
/// does it, as a `menu` is opened by one. The veil, the ring around the target
/// and the card are parts.
///
/// Null-marked: every reference is non-null unless it says `@Nullable`.
///
/// Read more: [Overlays](https://goldberry.dev/docs/components/overlays.html#tours).
@NullMarked
package dev.goldberry.widgets.overlay.tour;

import org.jspecify.annotations.NullMarked;
