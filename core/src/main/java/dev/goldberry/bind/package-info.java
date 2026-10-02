/// The vocabulary an application writes its models in: the
/// [dev.goldberry.bind.Model], [dev.goldberry.bind.Bind] and
/// [dev.goldberry.bind.Action] annotations the build reads, and the
/// [dev.goldberry.bind.Observable], [dev.goldberry.bind.Property] and
/// [dev.goldberry.bind.Subscription] types a bound value is seen through.
///
/// A model is a plain class with plain fields. `@Bind` names a field by the path
/// markup uses, `@Action` names a method, and the build rewires each write into
/// one that also notifies. A widget is handed the read-only half, so data flows
/// down into the tree and events flow back up.
///
/// Null-marked: every reference is non-null unless annotated `@Nullable`.
///
/// Read more: [Values](https://goldberry.dev/docs/applications.html#values) and
/// [The four registries](https://goldberry.dev/docs/guide/markup.html#the-four-registries).
@NullMarked
package dev.goldberry.bind;

import org.jspecify.annotations.NullMarked;
