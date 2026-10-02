package dev.goldberry.widgets.markup;

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
/// that ships widgets is found by an application that never names it.
///
/// The annotated class must have a
/// `public static Widget inflate(KdlNode, List<Widget>, Wiring)` method. Java
/// cannot express that as a constraint on an annotation, so the build checks it:
/// a `@Markup` class without one fails the build with the class named, rather
/// than failing the first document that uses the node.
///
/// The annotation has `CLASS` retention: the build has already read it, and
/// nothing scans for it at run time. The catalogue is ordinary generated code
/// that calls `Button::inflate`, which is what lets a native image resolve every
/// widget ahead of time.
///
/// Read more: [Markup](https://goldberry.dev/docs/guide/markup.html#parsing-and-inflating).
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Markup {

    /// The node name — `button`. What a document writes and what an unknown-node
    /// error lists.
    String value();
}
