/// `docs/core-widgets.md` §1's `canvas` — an immediate-mode drawing surface, the
/// escape hatch for anything the catalog does not draw, and the base the data widgets
/// build on.
///
/// [dev.goldberry.widgets.core.canvas.Canvas] is the widget, drawn
/// by the painter it is handed, and
/// [dev.goldberry.widgets.core.canvas.Input] is what it does with
/// pointer, keyboard and text input, in the painter's coordinates.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.widgets.core.canvas;

import org.jspecify.annotations.NullMarked;
