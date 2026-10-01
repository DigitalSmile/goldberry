/// `docs/core-widgets.md` §5's `carousel` — one child visible at a time out of a
/// list, with previous and next controls and a dot indicator.
///
/// [dev.goldberry.widgets.panel.carousel.Carousel] builds only the
/// current slide. It does not advance on its own unless `interval` is set, and then
/// pauses on hover, on focus inside and under reduced motion. The viewport, the
/// controls and the dots are parts.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.widgets.panel.carousel;

import org.jspecify.annotations.NullMarked;
