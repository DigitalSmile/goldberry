package io.github.digitalsmile.goldberry.bind;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// The name a method answers to in markup — `press="app.save"` or
/// `change="app.set-gain"`.
///
/// ```java
/// @Action("app.click")    void click()               { clicks++; }
/// @Action("app.set-gain") void setGain(double value) { gain = value; }
/// ```
///
/// ## Two shapes, because a control reports two different things
///
/// A button says *that* something happened; a slider says *what* it should
/// become. So an annotated method takes either no argument or exactly one, and
/// the weaver emits the right registration for each — including the parse from
/// the `String` a valued action crosses as, which is the one piece of boilerplate
/// every application was writing by hand
/// ([ADR-0073](../../../../../../book/src/adr/0073-a-composite-is-one-tab-stop.md)).
///
/// Supported parameter types: `String`, `double`, `int`, `boolean` and their
/// boxes. Anything else is a build failure naming the method.
///
/// ## Bound through `LambdaMetafactory`, at the same call site javac would use
///
/// The weaver writes an `invokedynamic` per action, bootstrapped by
/// [java.lang.invoke.LambdaMetafactory] against the method's real descriptor —
/// byte for byte the call site `javac` emits for `model::click`. So an action is
/// a direct virtual call behind an interface the JIT inlines through, there is no
/// reflection on the path, and the linkage is the one shape GraalVM's closed
/// world can resolve when it builds the image
/// ([ADR-0126](../../../../../../book/src/adr/0126-actions-are-bound-by-lambdametafactory.md)).
///
/// A `private` method is fine, and is the expected case: the call site is written
/// into the model's own class, where private is not a barrier. An action only the
/// markup calls has no reason to be part of a model's API.
///
/// ## Or bound by a `MethodHandle`, when nothing wove it
///
/// The paragraph above is what a **native image** runs, and an ordinary jar
/// resolves the same method reflectively instead — which is why this is
/// `RUNTIME`-retained since [ADR-0155]. A handle unreflected from a private
/// method needs the model's package open to the toolkit; an image needs nothing
/// at all.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Action {

    /// The name markup uses in `press=` or `change=`.
    String value();
}
