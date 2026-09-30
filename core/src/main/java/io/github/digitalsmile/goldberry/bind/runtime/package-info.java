/// The machinery a model runs on, woven or not: the interface the weaver adds to a
/// model class, the per-field listener slots its rewritten writes fire, and the
/// reflective binding an unwoven jar falls back to.
///
/// An application's front door is
/// [io.github.digitalsmile.goldberry.bind.runtime.Models], which turns a model into
/// the registries `bind.registry` holds and never says which of the two
/// implementations it picked (ADR-0155). The rest is public because woven bytecode
/// in another module has to name it, not because it is an API (ADR-0125, ADR-0172).
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.bind.runtime;

import org.jspecify.annotations.NullMarked;
