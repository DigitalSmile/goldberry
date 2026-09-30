/// The markup contract: how a widget declares the node it answers to in a KDL
/// document, and how a document is inflated into widgets (ADR-0172).
///
/// A widget carries [io.github.digitalsmile.goldberry.widgets.markup.Markup] with its
/// node name and satisfies
/// [io.github.digitalsmile.goldberry.widgets.markup.Inflatable]; the build collects
/// both into a generated
/// [io.github.digitalsmile.goldberry.widgets.markup.WidgetCatalog] per module, which
/// an application finds through `ServiceLoader` without naming it (ADR-0131). A
/// document is inflated against
/// [io.github.digitalsmile.goldberry.widgets.markup.Wiring], the registries its names
/// resolve in; [io.github.digitalsmile.goldberry.widgets.markup.Named] holds the
/// objects a document may name, such as a `FormController`, and
/// [io.github.digitalsmile.goldberry.widgets.markup.Bound] is a widget a document
/// places and the application describes.
///
/// An application names these only when it declares a widget of its own.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.widgets.markup;

import org.jspecify.annotations.NullMarked;
