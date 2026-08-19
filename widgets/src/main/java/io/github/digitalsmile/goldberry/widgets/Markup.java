package io.github.digitalsmile.goldberry.widgets;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// The node name a widget answers to in markup — `button`, `radio-group`.
///
/// ```java
/// @Markup("button")
/// public record Button(String label, Icon icon, Runnable onPress, …) implements Widget.Leaf {
///
///     public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) { … }
/// }
/// ```
///
/// That is the whole registration. The build collects every annotated class in
/// the module into one [WidgetCatalog] and declares it as a service, so a module
/// that ships widgets is found by an application that never names it
/// ([ADR-0131](../../../../../../book/src/adr/0131-a-widget-package-announces-itself.md)).
///
/// ## What it requires
///
/// A `public static Widget inflate(KdlNode, List<Widget>, Wiring)` on the same
/// class. Java cannot express that as a type constraint on an annotation, so the
/// **build** checks it: a `@Markup` class without one is a build failure naming
/// the class, rather than a node that fails to inflate the first time a document
/// uses it.
///
/// ## Read at build time
///
/// `CLASS` retention, like [io.github.digitalsmile.goldberry.bind.Bind] and for
/// the same reason: the weaver has already read it and an image that carried it
/// would be carrying metadata for nobody. Nothing scans at run time — the catalog
/// is ordinary generated code that calls `Button::inflate` (ADR-0127).
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Markup {

    /// The node name — `button`. What a document writes and what an unknown-node
    /// error lists.
    String value();
}
