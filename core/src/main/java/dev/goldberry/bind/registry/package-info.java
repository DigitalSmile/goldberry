/// Where markup's names are resolved: the registries an application fills in with
/// the actions a `press=` may call and the properties a `bind=` may read (§9).
///
/// Markup names and these resolve, so a KDL file never reaches a Java method or an
/// object graph — and a document reloaded at run time re-resolves against the same
/// registries, keeping its handlers and its values (ADR-0051). A binding path is
/// dotted identifiers and nothing else (ADR-0062).
///
/// Exported because an application fills these in, and because the weaver writes
/// call sites into an application's own classes that have to name them (ADR-0172).
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.bind.registry;

import org.jspecify.annotations.NullMarked;
