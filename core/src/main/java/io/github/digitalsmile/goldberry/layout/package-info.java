/// The flexbox vocabulary, owned by the toolkit.
///
/// `docs/ARCHITECTURE.md` §3.1 says raw foreign memory never leaves `:natives`.
/// The rule this package exists for is the other half of that: **no `:natives`
/// type appears in an application-facing signature**. `paint.Box` and
/// `css.ComputedStyle` carried thirteen of the layout engine's own types each,
/// and a `Box` is what every custom widget returns — so writing a widget meant
/// reading the bindings (ADR-0279).
///
/// Everything here is a plain value: a number and a unit, or a name. None of it
/// touches foreign memory, and none of it carries a wire format — the C
/// enumerators stay in `:natives`, checked against the compiled library by the
/// layout probe, and the translation between the two vocabularies happens in one
/// package-private file beside the node that needs it.
///
/// `@NullMarked`, which puts this package under NullAway: inside a marked package
/// every type is non-null unless it says `@Nullable`, and the build fails on a
/// violation (`docs/testing.md` §2).
@NullMarked
package io.github.digitalsmile.goldberry.layout;

import org.jspecify.annotations.NullMarked;
