package io.github.digitalsmile.goldberry.bind;

import java.util.Objects;

/// The two registries a [Model] publishes — §9's `bind` and `action` halves.
///
/// ```java
/// var model = new Settings();
/// var inflater = Widgets.inflater(Models.actions(model), icons, Models.bindings(model));
/// ```
///
/// ## Why a facade and not a method on the model
///
/// The weaver adds [BoundModel] to the compiled class, which is after javac has
/// already decided what the author's source means — so `model.bindings()` does not
/// compile, however true it is by the time the class runs. This is the cast that
/// bridges the two, and the reason it is worth having a class for is the failure
/// message: a model that was annotated and never woven is a build that silently
/// did nothing, and the difference between finding that out here and finding it
/// out as a slider that never moves is the whole value of the check.
public final class Models {

    private Models() {
    }

    /// Every `@Bind` path on `model`, strict.
    ///
    /// @throws IllegalStateException if `model` was not woven
    public static BindingRegistry bindings(Object model) {
        return bound(model).bindings();
    }

    /// Every `@Action` name on `model`, strict.
    ///
    /// @throws IllegalStateException if `model` was not woven
    public static ActionRegistry actions(Object model) {
        return bound(model).actions();
    }

    /// One value of `model`, read-only, by the path markup would name.
    ///
    /// What a widget built in Java uses, and deliberately the same lookup a
    /// document does — `bind="app.tab"` in KDL and `observable(model, "app.tab")`
    /// in Java resolve the same path against the same registry, so there is one
    /// way a value is named rather than two
    /// ([ADR-0129](../../../../../../book/src/adr/0129-a-value-is-named-one-way.md)).
    ///
    /// This replaces the per-path accessor a model used to carry — nine
    /// `public Observable<String> tab() { … }` methods that existed only because
    /// the view had no other way in. A model publishes paths; it should not also
    /// publish a Java API that says the same thing again.
    ///
    /// A map lookup on a registry built once, so calling it while building a
    /// widget costs what reading a field costs.
    ///
    /// The element type is inferred from where the result is used and **not**
    /// checked, which is the same bargain a `Map<String, Object>` cast makes and
    /// is made here for a reason: the model declared the field, so it is the one
    /// thing in the process that already knows. Use
    /// [#observable(Object, String, Class)] where the answer matters more than
    /// the brevity — a generic type it cannot check is exactly when it does.
    ///
    /// @throws IllegalArgumentException if the path is malformed or not bound
    /// @throws IllegalStateException if `model` was not woven
    @SuppressWarnings("unchecked")
    public static <T> Observable<T> observable(Object model, String path) {
        return (Observable<T>) bindings(model).resolve(path);
    }

    /// The same, checked against the value currently held.
    ///
    /// A model holding null passes — "not loaded yet" is a state a binding has to
    /// be able to represent, and there is nothing there to disagree with yet.
    ///
    /// @throws IllegalArgumentException if the value held is not of `type`
    public static <T> Observable<T> observable(Object model, String path, Class<T> type) {
        return bindings(model).resolve(path, type);
    }

    /// Calls `listener` after a `@Bind` field that asks for a frame changes.
    ///
    /// The frame request, and the reason an action is now just an assignment.
    /// Every action used to end in a `changed()` that asked the window to
    /// repaint — a line with no meaning of its own, present in every method,
    /// and wrong only by being absent. A model changing is *already* the signal;
    /// this is where a window subscribes to it
    /// ([ADR-0128](../../../../../../book/src/adr/0128-a-change-is-its-own-frame-request.md)).
    ///
    /// ```java
    /// Models.onRepaint(model, host::repaint);
    /// ```
    ///
    /// Fired once per change and not once per write, so a model that assigns the
    /// value already there asks for no frame. A single action that moves three
    /// fields asks for three, which the frame scheduler coalesces the same way it
    /// coalesces three `setState` calls (ADR-0122).
    ///
    /// @return a subscription that stops the notifications
    /// @throws IllegalStateException if `model` was not woven
    public static Subscription onRepaint(Object model, Runnable listener) {
        return bound(model).boundListeners().onRepaint(listener);
    }

    /// Calls `listener` after a `@Bind(restyle = true)` field on `model` changes.
    ///
    /// Wired by whatever installed the model, so an application declares
    /// `@Bind(value = "app.theme", restyle = true)` and says nothing else
    /// ([ADR-0133](../../../../../../book/src/adr/0133-a-restyle-is-declared.md)).
    ///
    /// @return a subscription that stops the notifications
    /// @throws IllegalStateException if `model` was not woven
    public static Subscription onRestyle(Object model, Runnable listener) {
        return bound(model).boundListeners().onRestyle(listener);
    }

    /// Whether `model`'s class was woven.
    ///
    /// For a test, and for an application that wants to degrade rather than fail
    /// — a preview tool with an unwoven model can still inflate its document
    /// against [BindingRegistry#none()].
    public static boolean isWoven(Object model) {
        return model instanceof BoundModel;
    }

    /// `model` as the interface the weaver added, or a message explaining which
    /// build step did not run.
    private static BoundModel bound(Object model) {
        Objects.requireNonNull(model, "model");
        if (model instanceof BoundModel woven) {
            return woven;
        }
        var type = model.getClass();
        throw new IllegalStateException(type.getName()
                + (type.isAnnotationPresent(Model.class) || type.isAnnotationPresent(Actions.class)
                        ? " is annotated @Model or @Actions but was not woven: its members are"
                                + " still ordinary ones and nothing would ever be notified. The"
                                + " `goldberry.weave` build step has to run on the module that"
                                + " compiles it."
                        : " is annotated neither @Model nor @Actions, so it publishes no"
                                + " bindings or actions. Annotate the class — @Model if it holds"
                                + " values, @Actions if it only has methods — or build the"
                                + " registries by hand with BindingRegistry.strict() and"
                                + " ActionRegistry.strict()."));
    }
}
