/// `docs/core-widgets.md` §7's `tour` — a guided sequence of popovers over real
/// widgets, dimming the window around each target and scrolling it into view.
///
/// A [io.github.digitalsmile.goldberry.widgets.overlay.tour.Tour] is a list of
/// [io.github.digitalsmile.goldberry.widgets.overlay.tour.Stop]s, each naming its
/// target by id with a title and a body. Starting one needs a `Host`, so
/// [io.github.digitalsmile.goldberry.widgets.overlay.tour.Tours] is the call that
/// does it, as a `menu` is opened (ADR-0106). The veil, the ring around the target
/// and the card are parts.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.widgets.overlay.tour;

import org.jspecify.annotations.NullMarked;
