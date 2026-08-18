package io.github.digitalsmile.goldberry.bind;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Marks a class whose [Bind] and [Action] members should be collected into a
/// generated registry.
///
/// ```java
/// @Registry
/// final class ShowcaseModel {
///     @Bind("app.gain") final Property<Number> gain = Property.of(40);
///     @Action("app.click") void click() { … }
/// }
/// ```
///
/// generates `ShowcaseModelRegistry` beside it, with a `bindings(model)` and an
/// `actions(model)` — each returning a strict registry holding exactly what was
/// annotated.
///
/// Explicit rather than inferred from the presence of a [Bind], so that the
/// generated type has a name a person chose to create and the processor never
/// writes a file nobody asked for
/// ([ADR-0096](../../../../../../book/src/adr/0096-a-registry-is-generated-not-reflected.md)).
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface Registry {

    /// The generated class's name, or empty for `<Type>Registry`.
    String value() default "";
}
