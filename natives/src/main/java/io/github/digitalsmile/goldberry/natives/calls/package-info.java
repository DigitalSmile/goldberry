/// The downcall holders for `libgoldberry`'s own exports — the shim's functions,
/// which belong to none of the wrapped libraries. One record, one static final
/// handle per function.
///
/// **Not exported**, like every `…calls` package in this module: a `call` takes and
/// returns raw addresses (ADR-0173). The smallest example of the shape every holder
/// has.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.natives.calls;

import org.jspecify.annotations.NullMarked;
