package io.github.digitalsmile.goldberry.bind;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// The path a [Property] answers to in markup — `bind="app.gain"`.
///
/// ```java
/// final class Model {
///     @Bind("app.gain")  final Property<Number> gain = Property.of(40);
///     @Bind("app.theme") final Property<String> theme = Property.of("dark");
/// }
/// ```
///
/// ## It is read at compile time, and never at run time
///
/// `docs/ARCHITECTURE.md` §9 is explicit: names are bound "against a controller
/// object explicitly … no reflective `#handler` magic". This annotation does not
/// change that — it is read by an **annotation processor**, which writes the
/// `Bindings.strict().bind("app.gain", target.gain)` a person would otherwise
/// have written. The generated call is ordinary code: you can open it, step into
/// it, and get a stack trace out of it. What is removed is the copying, not the
/// explicitness ([ADR-0096](../../../../../../book/src/adr/0096-a-registry-is-generated-not-reflected.md)).
///
/// A typo is therefore a **compile** error rather than a control that renders
/// perfectly and never moves.
///
/// ## Rules the processor enforces
///
/// - The field's type must be a [Property].
/// - Two fields may not claim the same path.
///
/// ## `private` is fine
///
/// It was not, at first: generated code sits in the same package and cannot see
/// a private field, so one was a compile error telling the author to widen it —
/// which made a model's encapsulation a consequence of how the toolkit reads it.
/// A private field is now read through a `VarHandle` looked up once in the
/// generated class, by a name and type the processor already verified. Nothing
/// scans and nothing is opened
/// ([ADR-0098](../../../../../../book/src/adr/0098-a-private-member-is-reached-by-a-handle.md)).
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.FIELD)
public @interface Bind {

    /// The dotted path markup names — `app.gain`. Dotted paths only, which is
    /// what [Bindings] enforces at run time and the processor checks first.
    String value();
}
