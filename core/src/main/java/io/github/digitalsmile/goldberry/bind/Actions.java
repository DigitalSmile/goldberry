package io.github.digitalsmile.goldberry.bind;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// A class whose `@Action` methods are what markup can call.
///
/// ```java
/// @Model
/// public final class Settings {
///
///     @Bind("app.gain") private Number gain = 40;
///
///     @Actions
///     public record Actions(Settings values) {
///         @Action("app.louder") public void louder() { values.gain = … }
///     }
/// }
/// ```
///
/// The other half of [Model], and a separate annotation for a reason that is not
/// cosmetic: a class of actions holds no values, publishes no paths, and is
/// nothing anybody would call a model. Marking it `@Model` said otherwise on
/// every one of them — including, at its worst, on a class that also implemented
/// `Application`
/// ([ADR-0139](../../../../../../book/src/adr/0139-actions-are-annotated-as-actions.md)).
///
/// ## What it may not have
///
/// A `@Bind` field. A class with values *is* a model, and the build says so
/// rather than quietly weaving half of each — the two annotations are exclusive
/// and a class carrying both is refused.
///
/// A `@Model` may still carry `@Action` methods, which is the right shape for a
/// model small enough that splitting it would be ceremony. `@Actions` is for the
/// case where the split has happened.
///
/// ## What it is for besides tidiness
///
/// The actions a *window* owns — "open the menu", "toggle the HUD" — need a
/// `Host` and have no business on anything holding application values. They get a
/// small `@Actions` record of their own, which is what keeps the annotation off
/// the class implementing `Application`
/// ([ADR-0138](../../../../../../book/src/adr/0138-a-window-s-actions-are-a-model-of-their-own.md)).
///
/// ## Read at build time, kept at run time
///
/// `RUNTIME`-retained like [Model], and for the one purpose [Models] needs: so an
/// author whose build step did not run is told their class was annotated and
/// never woven, rather than handed a registry with nothing in it.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Actions {
}
