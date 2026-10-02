/// What every widget carries and no widget decides: `id`, `class` and the
/// reconciler's key, and a value that can come from a property through `bind=`.
///
/// The attributes markup fills in, as chainable interfaces a widget implements
/// rather than a base class it extends, because a record cannot extend one.
/// Exported as one part of the widget layer, split by what each part does.
///
/// The package is null-marked: a parameter or return is non-null unless annotated
/// `@Nullable`.
///
/// Read more:
/// [Attributes and binding](https://goldberry.dev/docs/guide/writing-a-widget.html#attributes-and-binding).
@NullMarked
package dev.goldberry.widget.attr;

import org.jspecify.annotations.NullMarked;
