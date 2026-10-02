/// The markup contract: how a widget declares the node it answers to in a KDL
/// document, and how a document is inflated into widgets.
///
/// A widget carries [dev.goldberry.widgets.markup.Markup] with its
/// node name and satisfies
/// [dev.goldberry.widgets.markup.Inflatable]; the build collects
/// both into a generated
/// [dev.goldberry.widgets.markup.WidgetCatalog] per module, which
/// an application finds through `ServiceLoader` without naming it. A
/// document is inflated against
/// [dev.goldberry.widgets.markup.Wiring], the registries its names
/// resolve in; [dev.goldberry.widgets.markup.Named] holds the
/// objects a document may name, such as a `FormController`, and
/// [dev.goldberry.widgets.markup.Bound] is a widget a document
/// places and the application builds.
///
/// An application names these only when it declares a widget of its own.
/// Annotated `@NullMarked`: every type here is non-null unless it says
/// `@Nullable`.
///
/// Read more: [Markup](https://goldberry.dev/docs/guide/markup.html).
@NullMarked
package dev.goldberry.widgets.markup;

import org.jspecify.annotations.NullMarked;
