package dev.goldberry.widgets.markup;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.widgets.Icons;

/// The objects a document may name but cannot build — a `FormController`, a
/// `Validator`.
///
/// ```java
/// var named = Named.strict().bind("app.signup-form", controller);
/// var inflater = Widgets.inflater(named, icons, model);
/// ```
///
/// ```kdl
/// form controller="app.signup-form" { … }
/// ```
///
/// This is the fourth registry a document resolves names against, beside the
/// three the model publishes: an `ActionRegistry` answers `press=` with a
/// method, a `BindingRegistry` answers `bind=` with a value that changes, and
/// [Icons] answers `icon=` with a resource built once. `controller=` and
/// `validator=` name an object that neither changes nor closes, which is why it
/// is not a binding: a binding is a subscription, and a handle that never
/// changes is nothing to subscribe to.
///
/// A document names the rule and never writes it. `validator="app.port-rule"`
/// says which rule; a document that could say what the rule is would be code in
/// another syntax, and reloading it would mean reloading code.
///
/// Confined to the UI thread, like everything it hands out.
///
/// Read more: [Markup](https://goldberry.dev/docs/guide/markup.html#the-four-registries).
public final class Named {

    private final Map<String, Object> byName = new LinkedHashMap<>();
    private final boolean strict;

    private Named(boolean strict) {
        this.strict = strict;
    }

    /// A registry that refuses an unknown name, which is the right default:
    /// `controller="signip"` is a typo, and a form that silently cannot be
    /// submitted is the hardest kind of bug to notice.
    public static Named strict() {
        return new Named(true);
    }

    /// A registry that resolves an unknown name to nothing, for a preview or a
    /// document mid-edit.
    public static Named lenient() {
        return new Named(false);
    }

    /// Nothing registered, and no complaints. What `Widgets.inflater()` uses.
    public static Named none() {
        return lenient();
    }

    /// Registers `value` under `name`.
    ///
    /// @throws IllegalStateException if the name is already registered
    public Named bind(String name, Object value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        if (byName.putIfAbsent(name, value) != null) {
            throw new IllegalStateException("\"" + name + "\" is already registered; use rebind() to replace it");
        }
        return this;
    }

    /// Replaces a registration, or adds it if there is none.
    public Named rebind(String name, Object value) {
        byName.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"));
        return this;
    }

    /// The object a name refers to, checked against `type`.
    ///
    /// A null name is not an error: `form` with no `controller=` is a form
    /// nothing submits, which is a perfectly good form to write while a screen is
    /// being laid out.
    ///
    /// @throws IllegalArgumentException if this registry is [#strict()] and the
    ///         name is not registered, or if what is registered is not a `type`
    public <T> @Nullable T resolve(@Nullable String name, Class<T> type) {
        Objects.requireNonNull(type, "type");
        if (name == null || name.isEmpty()) {
            return null;
        }
        var value = byName.get(name);
        if (value == null) {
            if (strict) {
                throw new IllegalArgumentException("nothing is registered as \"" + name + "\". Registered: "
                        + (byName.isEmpty() ? "(none)" : String.join(", ", byName.keySet())));
            }
            return null;
        }
        if (!type.isInstance(value)) {
            // Named and typed separately, so this is where the two meet. A
            // `controller=` that resolved to a validator would be a form that
            // cannot be submitted and says so nowhere.
            throw new IllegalArgumentException("\"" + name + "\" is a "
                    + value.getClass().getSimpleName() + ", and this attribute needs a " + type.getSimpleName());
        }
        return type.cast(value);
    }

    /// Every name registered here, in the order they were registered — which is
    /// what a strict registry's error message prints.
    public Map<String, Object> bound() {
        return Collections.unmodifiableMap(byName);
    }
}
