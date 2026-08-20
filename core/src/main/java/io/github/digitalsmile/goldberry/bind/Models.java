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
/// bridges the two.
///
/// ## Two ways in, and an application cannot tell which it got
///
/// There are two implementations of [BoundModel] and this is what picks between
/// them ([ADR-0155](../../../../../../book/src/adr/0155-a-jar-binds-at-run-time-an-image-is-woven.md)):
///
/// - the model's **own class**, if the weaver rewrote it. Nothing is reflected,
///   nothing is looked up, and a change notifies from inside the assignment that
///   made it. This is what a GraalVM native image is built from, because a closed
///   world can resolve it and cannot resolve the other one.
/// - a **runtime binding** otherwise, which reads the same annotations through
///   handles. What an ordinary jar uses, so a plain `./gradlew run` and a plain
///   `mvn exec:java` need no build step at all.
///
/// Every method below answers the same for both. The one visible difference is
/// *when* a change is noticed: the woven form notices the write, and the runtime
/// one notices at the next sweep — after the action that did it, at the top of the
/// next frame, or wherever [#refresh] is called.
public final class Models {

    private Models() {
    }

    /// Every `@Bind` path on `model`, strict.
    ///
    /// @throws IllegalStateException if `model`'s class is annotated neither
    ///         [Model] nor [Actions]
    public static BindingRegistry bindings(Object model) {
        return bound(model).bindings();
    }

    /// Every `@Action` name on `model`, strict.
    ///
    /// @throws IllegalStateException if `model`'s class is annotated neither
    ///         [Model] nor [Actions]
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
    /// @throws IllegalStateException if `model` publishes nothing
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
    /// @throws IllegalStateException if `model` publishes nothing
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
    /// @throws IllegalStateException if `model` publishes nothing
    public static Subscription onRestyle(Object model, Runnable listener) {
        return bound(model).boundListeners().onRestyle(listener);
    }

    /// Notices a change made to `model` where nothing could have seen it, and
    /// notifies whoever was watching.
    ///
    /// **A no-op for a woven model**, which noticed the write as it happened, and
    /// the reason this is safe to call unconditionally: an application that calls
    /// it is correct in a jar and pays a returned `false` in an image.
    ///
    /// For the runtime binding it is the sweep — every `@Bind` field compared
    /// against what it last held, the listeners of the ones that moved notified,
    /// a restyle asked for first and a frame asked for after. The toolkit runs it
    /// after every action a document dispatches and at the top of every frame, so
    /// an application needs this only for a change made from neither: a callback
    /// off the UI thread's timer, a background job reporting in, a field written
    /// during [io.github.digitalsmile.goldberry.Application#start].
    ///
    /// ```java
    /// job.onFinished(() -> { model.status = "done"; Models.refresh(model); });
    /// ```
    ///
    /// @return whether anything had in fact changed
    /// @throws IllegalStateException if `model` publishes nothing
    public static boolean refresh(Object model) {
        return bound(model) instanceof RuntimeBinding runtime && runtime.refresh();
    }

    /// Whether `model`'s class was woven.
    ///
    /// The two forms answer every other method here the same way, so this is a
    /// **diagnostic** rather than a branch to write: what it tells you is which
    /// build produced the class, not what the class can do. A native image says
    /// true; a plain jar says false and works (ADR-0155).
    public static boolean isWoven(Object model) {
        Objects.requireNonNull(model, "model");
        return model instanceof BoundModel;
    }

    /// `model` as the interface the weaver added, or the runtime binding that
    /// stands in for it.
    ///
    /// The order matters and only one way round: a woven class *is* a
    /// [BoundModel], and asking it directly is what keeps the fast path free of
    /// this class entirely — no map, no handle, no reflection on a path that a
    /// widget takes per frame.
    private static BoundModel bound(Object model) {
        Objects.requireNonNull(model, "model");
        if (model instanceof BoundModel woven) {
            return woven;
        }
        return RuntimeBinding.of(model);
    }
}
