package dev.goldberry.widgets.markup;

/// Every widget one module contributes to markup.
///
/// A module that ships widgets provides one of these as a service; an
/// application that inflates a document gets all of them, from every module on
/// the path, without naming any. Built-in and application widgets register the
/// same way.
///
/// Nobody writes one by hand in the ordinary case. The build generates the
/// implementation from the [Markup] annotations in the module and declares it
/// as a service in the module's own `module-info`. A widget author annotates a
/// class; a module author does nothing. It is an interface rather than a
/// generated map so that a module which must register something conditionally,
/// such as a platform-specific control, can write one itself.
///
/// Catalogues are found through `ServiceLoader`, which a native image resolves
/// when the image is built, so discovery needs no scan at run time.
///
/// Read more: [Markup](https://goldberry.dev/docs/guide/markup.html#parsing-and-inflating).
@FunctionalInterface
public interface WidgetCatalog {

    /// Adds this module's node names to `into`.
    ///
    /// Called once per inflater. The catalog does not know what it is registering
    /// against — an application may build several inflaters with different
    /// [Wiring], and a document reloaded at runtime gets a fresh one.
    void register(Inflatable.Catalog into);
}
