/// Where markup's names are resolved: the registries an application fills in with
/// the actions a `press=` may call and the properties a `bind=` may read.
///
/// Markup names and these resolve, so a KDL file never reaches a Java method or an
/// object graph — and a document reloaded at run time re-resolves against the same
/// registries, keeping its handlers and its values. A binding path is dotted
/// identifiers and nothing else.
///
/// Exported because an application fills these in, and because the weaver writes
/// call sites into an application's own classes that have to name them.
///
/// Null-marked: every reference is non-null unless annotated `@Nullable`.
///
/// Read more:
/// [The four registries](https://goldberry.dev/docs/guide/markup.html#the-four-registries).
@NullMarked
package dev.goldberry.bind.registry;

import org.jspecify.annotations.NullMarked;
