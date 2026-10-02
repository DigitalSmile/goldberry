/// Markup: a KDL 2.0 parser, the node it produces, and the inflater that turns a
/// document into objects through a registry of node name to factory.
///
/// A node is one widget — its name is the type, its first argument the content,
/// its properties the attributes, its children the children. The parser is
/// hand-written so every error carries a line and a column, and the inflater is
/// generic in what it builds. Exported to applications.
///
/// `@NullMarked`, which puts this package under NullAway.
///
/// Read more: [Markup](https://goldberry.dev/docs/guide/markup.html).
@NullMarked
package dev.goldberry.kdl;

import org.jspecify.annotations.NullMarked;
