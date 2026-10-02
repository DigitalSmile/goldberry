/// The machinery a model runs on, woven or not: the interface the weaver adds to a
/// model class, the per-field listener slots its rewritten writes fire, and the
/// reflective binding an unwoven jar falls back to.
///
/// An application's front door is
/// [dev.goldberry.bind.runtime.Models], which turns a model into
/// the registries `bind.registry` holds and never says which of the two
/// implementations it picked. The rest is public because woven bytecode in
/// another module has to name it, not because it is an API.
///
/// Null-marked: every reference is non-null unless annotated `@Nullable`.
///
/// Read more:
/// [Model weaving](https://goldberry.dev/docs/weaving.html#you-probably-do-not-need-to-run-any-of-this).
@NullMarked
package dev.goldberry.bind.runtime;

import org.jspecify.annotations.NullMarked;
