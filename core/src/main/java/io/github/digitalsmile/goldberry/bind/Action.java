package io.github.digitalsmile.goldberry.bind;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// The name a method answers to in markup — `press="app.save"` or
/// `change="app.set-gain"`.
///
/// ```java
/// @Action("app.click")    void click()                 { … }
/// @Action("app.set-gain") void setGain(double value)   { … }
/// ```
///
/// ## Two shapes, because a control reports two different things
///
/// A button says *that* something happened; a slider says *what* it should
/// become. So an annotated method takes either no argument or exactly one, and
/// the processor generates the right registration for each — including the parse
/// from the `String` a valued action crosses as, which is the one piece of
/// boilerplate every application was writing by hand
/// ([ADR-0073](../../../../../../book/src/adr/0073-a-composite-is-one-tab-stop.md)).
///
/// Supported parameter types: `String`, `double`, `int`, `boolean` and their
/// boxes. Anything else is a compile error naming the method.
///
/// ## Read at compile time
///
/// Like [Bind], and for §9's reason: this generates the explicit
/// `Actions.strict().bind("app.click", target::click)` rather than looking a
/// method up reflectively.
///
/// A `private` method is fine. It is bound through a `MethodHandle` looked up
/// once in the generated class from the exact descriptor the processor verified,
/// which is the same explicit registration with the *call* changed — an action
/// only the markup calls has no reason to be part of a model's API
/// ([ADR-0098](../../../../../../book/src/adr/0098-a-private-member-is-reached-by-a-handle.md)).
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface Action {

    /// The name markup uses in `press=` or `change=`.
    String value();
}
