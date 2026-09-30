/// What every widget carries and no widget decides: `id`, `class` and the
/// reconciler's key, and a value that can come from a property through §9's
/// `bind=`.
///
/// The attributes markup fills in, as chainable interfaces a widget implements
/// rather than a base class it extends — a record cannot extend one. Exported as
/// one part of the widget layer, split by what each part does (ADR-0172).
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.widget.attr;

import org.jspecify.annotations.NullMarked;
