package io.github.digitalsmile.goldberry.bind;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// A class whose `@Bind` fields are rewired into bindings by the build.
///
/// ```java
/// @Model
/// public final class Settings {
///     @Bind("app.gain")  int gain = 40;
///     @Bind("app.theme") String theme = "dark";
///
///     @Action("app.louder") void louder() { gain++; }   // plain Java
/// }
/// ```
///
/// `gain++` is an ordinary field increment. After weaving it also notifies every
/// widget bound to `app.gain`, because the build rewrote that one `putfield` into
/// a call that stores the value and then tells the listeners
/// ([ADR-0125](../../../../../../book/src/adr/0125-a-raw-field-is-woven-into-a-binding.md)).
///
/// ## Why the field, and not a `Property`
///
/// A `Property<Integer>` costs an object per value, a box per read, and — the
/// part that actually hurts — a vocabulary. `gain.set(gain.get() + 1)` is what
/// `gain++` means, written out, and every model that used one was a model whose
/// own methods could not touch their own state without going through an accessor
/// nobody chose to write. [Property] is still there for a value with no class to
/// live in; this is for the usual case, where there is one.
///
/// ## What it does not do
///
/// It does not make the field thread-safe, shared, or persistent, and it does not
/// track dependencies between fields. It makes an assignment observable. Data
/// still flows down and events still flow up (ADR-0063): the widget tree gets the
/// [Observable] half and reports what the user did as an `@Action`.
///
/// ## Weaving is a build step, not a runtime one
///
/// The rewiring happens to the compiled class, in the build, before the jar —
/// which is what lets the result run under GraalVM's closed world with nothing
/// generated, loaded or reflected at runtime (ADR-0127). A class annotated here
/// and not woven fails loudly the first time [Models] is asked about it, rather
/// than quietly never notifying anybody.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Model {
}
