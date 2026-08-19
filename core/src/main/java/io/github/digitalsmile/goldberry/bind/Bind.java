package io.github.digitalsmile.goldberry.bind;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// The path a field answers to in markup — `bind="app.gain"`.
///
/// ```java
/// @Model
/// final class Settings {
///     @Bind("app.gain")  int gain = 40;
///     @Bind("app.theme") String theme = "dark";
/// }
/// ```
///
/// A **plain field**, of whatever type the model wants it to be. The weaver
/// rewrites every assignment to it inside the class into a store that also
/// notifies, and gives [BindingRegistry] a [BoundField] window onto it — so `gain++`
/// moves a slider and the model never mentions a `Property`
/// ([ADR-0125](../../../../../../book/src/adr/0125-a-raw-field-is-woven-into-a-binding.md)).
///
/// A [Property] field is still accepted and left alone. It is already observable,
/// so there is nothing to rewire — the weaver binds it directly. That is what
/// lets a model hold one value somebody else owns beside its own.
///
/// ## Rules the weaver enforces
///
/// - The field is not `static` — a binding belongs to a model instance, and a
///   static one would be shared by every window in the process.
/// - The field is not `final`, unless it is a `Property` — a value that cannot
///   change is not something to subscribe to, and binding one is a mistake that
///   shows up as a control that never moves.
/// - Two fields may not claim the same path.
///
/// Each is a **build** failure naming the field, for the reason the old
/// annotation processor gave: a typo that reaches runtime is a control that
/// renders perfectly and never moves (ADR-0096, still true — only the mechanism
/// changed).
///
/// ## `private` is the expected case
///
/// The weaver works on the class's own bytecode, from inside, so there is no
/// access question to answer: no `opens`, no `setAccessible`, no handle lookup.
/// A model's fields should be private, and the toolkit has no opinion about it
/// because it never needs one (ADR-0125, superseding ADR-0098).
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface Bind {

    /// The dotted path markup names — `app.gain`. Dotted paths only, which is
    /// what [BindingRegistry] enforces at run time and the weaver checks first.
    String value();

    /// Whether changing this value invalidates the stylesheets.
    ///
    /// ```java
    /// @Bind(value = "app.theme", restyle = true) private String theme = "dark";
    /// ```
    ///
    /// A theme and a density are the two values in a real application that a
    /// *rule* depends on rather than a widget: changing one means every resolved
    /// style is stale, which is a much bigger hammer than a repaint and is why
    /// [io.github.digitalsmile.goldberry.Host#restyle] is a separate call.
    ///
    /// Declared here rather than subscribed to by hand, because
    /// `Models.observable(model, "app.theme").subscribe(v -> host.restyle())` is
    /// a line that says in Java what this says in one word, and gets forgotten
    /// the same way the old `changed()` did
    /// ([ADR-0133](../../../../../../book/src/adr/0133-a-restyle-is-declared.md)).
    ///
    /// The toolkit wires it: an application that hands its model to
    /// [io.github.digitalsmile.goldberry.Application#model()] restyles on a
    /// change to this field and repaints on a change to any other, and says
    /// nothing about either.
    boolean restyle() default false;

    /// Whether changing this value asks the window for a frame.
    ///
    /// On, because a value markup can name is a value markup can show, and a
    /// value that changed with no repaint is the bug this whole mechanism exists
    /// to remove.
    ///
    /// ```java
    /// @Bind(value = "job.bytesRead", repaint = false) private long bytesRead;
    /// ```
    ///
    /// Off for a value nothing on screen depends on — a counter something else
    /// polls, a field kept for a log line — where every write would otherwise
    /// wake a window with nothing new to draw.
    ///
    /// **Per value and not per model**, because that is the granularity the
    /// question has: one model routinely holds both the gain a slider shows and
    /// the byte count nothing shows, and a switch on the class would have to be
    /// wrong about one of them
    /// ([ADR-0135](../../../../../../book/src/adr/0135-a-frame-is-asked-for-by-the-value-that-moved.md)).
    ///
    /// Decided in the **build**: a field that does not ask has no call to emit,
    /// so it costs an instruction that is not there rather than a branch that is.
    boolean repaint() default true;
}
