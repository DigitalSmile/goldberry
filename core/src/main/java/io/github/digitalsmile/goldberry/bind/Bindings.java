package io.github.digitalsmile.goldberry.bind;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/// What a path in markup means (§9's `bind` half).
///
/// The counterpart of `Actions` in `:widgets`, and deliberately the same shape:
/// markup names, and this resolves. `checkbox bind="prefs.frost"` says *which*
/// value, and cannot say what the value is or where it is stored — a markup file
/// that could reach into an object graph would be code with a different syntax,
/// and hot-reloading it would mean hot-reloading code.
///
/// The indirection is what makes markup reloadable: a document reloaded at
/// runtime re-resolves every path against the same registry, so the new tree's
/// controls are bound to the same properties the old one had, and the values
/// survive the reload
/// ([ADR-0051](../../../../../../book/src/adr/0051-kdl-is-parsed-here-and-reloading-is-forgiving.md)).
///
/// ## Dotted paths, and nothing else
///
/// A path is `identifier(.identifier)*` — `frost`, `prefs.frost`,
/// `prefs.window.opacity` — and that is the entire grammar
/// ([ADR-0062](../../../../../../book/src/adr/0062-bind-is-a-path-and-nothing-else.md)).
/// `!prefs.frost`, `prefs.frost == true` and `prefs.frost ? "on" : "off"` are
/// **refused at inflation**, with the path quoted in the message, rather than
/// resolving to nothing and leaving a control that never updates.
///
/// The dots are part of the name today: this is a flat registry, and `prefs` is
/// not a scope that can be handed to a subtree. That is a deliberate floor rather
/// than an oversight — a scoped model is an additive change if grouping ever earns
/// its keep, and a path that means the same thing everywhere is easier to reload
/// against.
///
/// Confined to the UI thread, like the properties it holds.
public final class Bindings {

    /// `identifier(.identifier)*`, and an identifier is what Java and CSS would
    /// both accept — which is the point: a path has to be writable in markup, in
    /// Java, and in a log line.
    private static final Pattern PATH = Pattern.compile("[A-Za-z_][A-Za-z0-9_-]*(\\.[A-Za-z_][A-Za-z0-9_-]*)*");

    private final Map<String, Property<?>> byPath = new LinkedHashMap<>();
    private final boolean strict;

    private Bindings(boolean strict) {
        this.strict = strict;
    }

    /// A registry that **refuses** an unknown path.
    ///
    /// The right default for an application. `bind="prefs.frsot"` is a typo, and a
    /// checkbox bound to nothing looks exactly like a checkbox bound to something
    /// that never changes.
    public static Bindings strict() {
        return new Bindings(true);
    }

    /// A registry that resolves an unknown path to nothing.
    ///
    /// For a preview, a golden image, or a document being edited: reload is
    /// deliberately forgiving (ADR-0051), and refusing to inflate a window because
    /// one property is not wired yet would make markup-first development
    /// impossible.
    public static Bindings lenient() {
        return new Bindings(false);
    }

    /// No bindings at all, and no complaints.
    public static Bindings none() {
        return lenient();
    }

    /// Binds a path to a property.
    ///
    /// @throws IllegalArgumentException if the path is not a dotted path
    /// @throws IllegalStateException if the path is already bound — two features
    ///         quietly sharing one path is a bug that presents as a value
    ///         changing by itself
    public Bindings bind(String path, Property<?> property) {
        requirePath(path);
        Objects.requireNonNull(property, "property");
        if (byPath.putIfAbsent(path, property) != null) {
            throw new IllegalStateException(
                    "\"" + path + "\" is already bound; use rebind() to replace it deliberately");
        }
        return this;
    }

    /// Replaces a binding, or adds it if there is none.
    public Bindings rebind(String path, Property<?> property) {
        requirePath(path);
        byPath.put(path, Objects.requireNonNull(property, "property"));
        return this;
    }

    /// Binds a path to a new property holding `initial`, and hands the property
    /// back.
    ///
    /// The common case in an application: the registry is where the property is
    /// meant to live, so there is nothing to declare it separately.
    public <T> Property<T> bind(String path, Class<T> type, T initial) {
        Objects.requireNonNull(type, "type");
        var property = Property.of(initial);
        bind(path, property);
        return property;
    }

    /// The value a markup attribute names, **read-only**.
    ///
    /// An [Observable] rather than the [Property] behind it, and that is the
    /// whole of one-way binding: what markup names, it can read and watch and
    /// cannot write (ADR-0063). A control that needs to change a value says so
    /// through its action instead, and the application decides what that means.
    ///
    /// A null path is not an error — a node with no `bind=` is an ordinary node,
    /// which is what almost all of them are.
    ///
    /// @throws IllegalArgumentException if the path is malformed, or if this
    ///         registry is [#strict()] and the path is not bound
    public Observable<?> resolve(String path) {
        if (path == null) {
            return null;
        }
        requirePath(path);
        var property = byPath.get(path);
        if (property == null && strict) {
            throw new IllegalArgumentException(
                    "nothing is bound to \"" + path + "\". Bound: "
                            + (byPath.isEmpty() ? "(none)" : String.join(", ", byPath.keySet())));
        }
        return property;
    }

    /// The value a path names, read-only and typed.
    ///
    /// What a control resolves with when it has to *interpret* the value rather
    /// than print it — a checkbox needs a `Boolean` to know whether to draw a
    /// tick, and finding out three frames later in a listener is not the moment
    /// to discover the application holds a `String` there.
    ///
    /// The check is against the value currently held, so a property holding null
    /// passes — there is nothing there to disagree with yet, and refusing would
    /// make "not loaded yet" impossible to express.
    ///
    /// @throws IllegalArgumentException if the value held is not of `type`
    @SuppressWarnings("unchecked")
    public <T> Observable<T> resolve(String path, Class<T> type) {
        Objects.requireNonNull(type, "type");
        var property = resolve(path);
        if (property == null) {
            return null;
        }
        var value = property.get();
        if (value != null && !type.isInstance(value)) {
            throw new IllegalArgumentException(
                    "\"" + path + "\" holds a " + value.getClass().getSimpleName()
                            + ", but it is bound to something expecting a " + type.getSimpleName());
        }
        return (Observable<T>) property;
    }

    /// Every path bound here, in the order they were bound — read-only, like
    /// everything else this registry hands out.
    ///
    /// There is deliberately **no** way to get a writable [Property] back out of a
    /// path. The registry is how a value is published to markup; it is not a
    /// service locator for the application's own state, and one that could be
    /// would make "who wrote this value?" unanswerable (ADR-0063). Keep the
    /// property where the state lives.
    public Map<String, Observable<?>> bound() {
        return Map.copyOf(byPath);
    }

    private static void requirePath(String path) {
        Objects.requireNonNull(path, "path");
        if (!PATH.matcher(path).matches()) {
            throw new IllegalArgumentException(
                    "\"" + path + "\" is not a binding path. A path is a name, or names"
                            + " joined by dots — `frost`, `prefs.frost`. Expressions are not"
                            + " part of the markup contract (ADR-0062).");
        }
    }
}
