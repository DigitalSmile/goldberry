package io.github.digitalsmile.goldberry.widgets;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/// The **objects** a document may name — a `FormController`, a `Validator`.
///
/// [Icons]'s shape, and the third registry for the third kind of thing markup can
/// refer to and cannot describe. The set is now:
///
/// - [io.github.digitalsmile.goldberry.bind.ActionRegistry] — what `press=` names.
///   A method.
/// - [io.github.digitalsmile.goldberry.bind.BindingRegistry] — what `bind=` names.
///   A **value that changes**.
/// - [Icons] — what `icon=` names. A resource with a lifetime.
/// - this — what `controller=` and `validator=` name. An object that does not
///   change and is not a resource.
///
/// ## Why not `bind=`
///
/// That was the first answer, and the binding machinery refused it in as many
/// words: *"a `@Bind` field is final; a value that cannot change is not something
/// to subscribe to, and binding one shows up as a control that never moves."*
/// Which is right. A binding is a subscription, a controller is a handle, and the
/// registry that told the difference was the one already written.
///
/// ## Why not just build one in markup
///
/// Because markup is data (§9). `validator="app.port-rule"` says *which* rule;
/// a document that could say what the rule **is** would be code with a different
/// syntax, and hot-reloading it would mean hot-reloading code — [Icons] and
/// `ActionRegistry` both turn on exactly this sentence.
///
/// Confined to the UI thread, like everything it hands out.
public final class Named {

    private final Map<String, Object> byName = new LinkedHashMap<>();
    private final boolean strict;

    private Named(boolean strict) {
        this.strict = strict;
    }

    /// A registry that refuses an unknown name — the right default, for the
    /// reason [io.github.digitalsmile.goldberry.bind.ActionRegistry#strict()]
    /// gives: `controller="signip"` is a typo, and a form that silently cannot
    /// be submitted is the hardest kind of bug to notice.
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
            throw new IllegalStateException(
                    "\"" + name + "\" is already registered; use rebind() to replace it");
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
    public <T> T resolve(String name, Class<T> type) {
        Objects.requireNonNull(type, "type");
        if (name == null || name.isEmpty()) {
            return null;
        }
        var value = byName.get(name);
        if (value == null) {
            if (strict) {
                throw new IllegalArgumentException(
                        "nothing is registered as \"" + name + "\". Registered: "
                                + (byName.isEmpty() ? "(none)" : String.join(", ", byName.keySet())));
            }
            return null;
        }
        if (!type.isInstance(value)) {
            // Named and typed separately, so this is where the two meet. A
            // `controller=` that resolved to a validator would be a form that
            // cannot be submitted and says so nowhere.
            throw new IllegalArgumentException(
                    "\"" + name + "\" is a " + value.getClass().getSimpleName()
                            + ", and this attribute needs a " + type.getSimpleName());
        }
        return type.cast(value);
    }

    /// Every name registered here, in the order they were registered — which is
    /// what a strict registry's error message prints.
    public Map<String, Object> bound() {
        return Collections.unmodifiableMap(byName);
    }
}
