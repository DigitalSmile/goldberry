/// The weaver: a build step that rewrites a module's compiled classes in place,
/// between `compileJava` and `jar`, with the JDK's class-file API.
///
/// It does two unrelated jobs. `ModelWeaver` rewires a `@Model`'s `@Bind` fields
/// into bindings in the model's own bytecode (ADR-0125); `CatalogWeaver` collects a
/// module's `@Markup` widgets into one generated widget catalog (ADR-0131). A model
/// the weaver refuses fails the build with a reason a person can act on.
///
/// A plain `main` rather than a Gradle plugin, run by the `goldberry.weave`
/// convention plugin, which asks for the two halves separately (ADR-0155): only a
/// native image needs the models woven, and every build needs the catalog.
package dev.goldberry.weaver;
