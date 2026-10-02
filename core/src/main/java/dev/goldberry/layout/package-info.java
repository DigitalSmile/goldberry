/// The flexbox vocabulary, owned by the toolkit: lengths, insets, limits,
/// directions, alignments and the measure callback a text leaf answers.
///
/// Raw foreign memory never leaves `:natives`, and this package is the other
/// half of that rule: **no `:natives` type appears in an application-facing
/// signature**. A `Box` is what every custom widget returns, so the values it is
/// built from are these rather than the layout engine's own — writing a widget
/// must not mean reading the bindings. Exported to applications.
///
/// Everything here is a plain value: a number and a unit, or a name. None of it
/// touches foreign memory, and none of it carries a wire format — the C
/// enumerators stay in `:natives`, checked against the compiled library by the
/// layout probe, and the translation between the two vocabularies happens in one
/// package-private file beside the node that needs it.
///
/// `@NullMarked`, which puts this package under NullAway.
///
/// Read more: [How layout works](https://goldberry.dev/docs/layout/index.html).
@NullMarked
package dev.goldberry.layout;

import org.jspecify.annotations.NullMarked;
