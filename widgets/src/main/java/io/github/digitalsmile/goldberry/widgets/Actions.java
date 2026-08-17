package io.github.digitalsmile.goldberry.widgets;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/// What a name in markup means (§9's `action` half).
///
/// KDL is data. `button press="save"` can say *which* action, and cannot say what
/// the action does — a markup file that could name a Java method would be code
/// with a different syntax, and hot-reloading it would mean hot-reloading code.
/// So markup names, and this resolves.
///
/// The indirection is what makes markup reloadable: a document reloaded at
/// runtime re-resolves every name against the same registry, so the new tree's
/// buttons are wired to the same handlers the old one had, without the
/// application being asked to rebuild anything ([ADR-0051](../../../../../../../book/src/adr/0051-kdl-is-parsed-here-and-reloading-is-forgiving.md)).
///
/// Confined to the UI thread, like everything a handler will touch.
public final class Actions {

    private final Map<String, Runnable> byName = new LinkedHashMap<>();

    /// Actions that are told which one — see [#bind(String, Consumer)].
    ///
    /// A second map rather than storing every action as a `Consumer<String>` that
    /// ignores its argument: [#resolve(String)] hands a `Runnable` straight to a
    /// button, and adapting one back out of a consumer would mean a `press=` could
    /// silently resolve to a handler written to expect a value it will never be
    /// given.
    private final Map<String, Consumer<String>> valuedByName = new LinkedHashMap<>();

    private final boolean strict;

    private Actions(boolean strict) {
        this.strict = strict;
    }

    /// A registry that **refuses** an unknown name.
    ///
    /// The right default for an application: `press="svae"` is a typo, and a
    /// button that silently does nothing is the hardest kind of bug to notice —
    /// there is no error, no log line, and the button looks perfectly normal.
    public static Actions strict() {
        return new Actions(true);
    }

    /// A registry that resolves an unknown name to nothing.
    ///
    /// For a preview, a golden image, or a document being edited: reload is
    /// deliberately forgiving (ADR-0051), and refusing to inflate a window
    /// because one handler is not wired yet would make markup-first development
    /// impossible.
    public static Actions lenient() {
        return new Actions(false);
    }

    /// No actions at all, and no complaints. What [Controls#inflater()] uses.
    public static Actions none() {
        return lenient();
    }

    /// Binds a name.
    ///
    /// @throws IllegalStateException if the name is already bound — shadowing a
    ///         handler by accident is how two features end up fighting over one
    ///         button
    public Actions bind(String name, Runnable action) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(action, "action");
        if (valuedByName.containsKey(name) || byName.putIfAbsent(name, action) != null) {
            throw new IllegalStateException(
                    "\"" + name + "\" is already bound; use rebind() to replace it deliberately");
        }
        return this;
    }

    /// Replaces a binding, or adds it if there is none.
    public Actions rebind(String name, Runnable action) {
        byName.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(action, "action"));
        return this;
    }

    /// Binds a name to an action that is told **which one**.
    ///
    /// `button press="save"` needs no argument: there is one thing to say and the
    /// button is it. `radio-group change="pickTheme"` is the first case where
    /// there is not — the handler has to know whether the user picked `light` or
    /// `dark`, and a registry of six separate actions, one per option, would make
    /// adding an option an edit in Java as well as in markup
    /// ([ADR-0073](../../../../../../../book/src/adr/0073-a-composite-is-one-tab-stop.md)).
    ///
    /// The argument is a `String` and stays one: it is the `value` attribute the
    /// document already wrote down, so it crosses no type boundary and needs no
    /// coercion rule. An application that wants an enum parses it, in Java, where
    /// a bad value is a bug it can see.
    ///
    /// @throws IllegalStateException if the name is already bound
    public Actions bind(String name, Consumer<String> action) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(action, "action");
        if (valuedByName.containsKey(name) || byName.containsKey(name)) {
            throw new IllegalStateException(
                    "\"" + name + "\" is already bound; use rebind() to replace it deliberately");
        }
        valuedByName.put(name, action);
        return this;
    }

    /// Replaces a valued binding, or adds it if there is none.
    public Actions rebind(String name, Consumer<String> action) {
        Objects.requireNonNull(name, "name");
        byName.remove(name);
        valuedByName.put(name, Objects.requireNonNull(action, "action"));
        return this;
    }

    /// The valued action a markup attribute names.
    ///
    /// A plain [Runnable] binding resolves here too, adapted to ignore the value.
    /// The two are one vocabulary rather than two: `change="refresh"` is a
    /// perfectly reasonable thing to bind on a radio group when the handler reads
    /// the property itself, and making the author pick the matching `bind`
    /// overload to match the widget would be a distinction only the registry
    /// cares about.
    ///
    /// @throws IllegalArgumentException if this registry is [#strict()] and the
    ///         name is not bound
    public Consumer<String> resolveValued(String name) {
        if (name == null) {
            return null;
        }
        var valued = valuedByName.get(name);
        if (valued != null) {
            return valued;
        }
        var plain = byName.get(name);
        if (plain != null) {
            return value -> plain.run();
        }
        if (strict) {
            throw new IllegalArgumentException(
                    "no action named \"" + name + "\" is bound. Bound: "
                            + (bound().isEmpty() ? "(none)" : String.join(", ", bound().keySet())));
        }
        return null;
    }

    /// The action a markup attribute names.
    ///
    /// A null name is not an error — `button "Cancel"` with no `press=` is a
    /// perfectly good button that a stylesheet is still being written for.
    ///
    /// @throws IllegalArgumentException if this registry is [#strict()] and the
    ///         name is not bound
    public Runnable resolve(String name) {
        if (name == null) {
            return null;
        }
        var action = byName.get(name);
        if (action != null) {
            return action;
        }
        // A valued action deliberately does *not* adapt down to a Runnable. The
        // handler was written to be told which one, and calling it with nothing
        // would mean picking a value here -- so this says which half of the
        // registry the name is in rather than inventing an argument.
        if (valuedByName.containsKey(name)) {
            throw new IllegalArgumentException(
                    "\"" + name + "\" is bound to an action that expects a value,"
                            + " and this attribute names one that takes none."
                            + " Bind it with bind(String, Runnable) instead.");
        }
        if (strict) {
            throw new IllegalArgumentException(
                    "no action named \"" + name + "\" is bound. Bound: "
                            + (bound().isEmpty() ? "(none)" : String.join(", ", bound().keySet())));
        }
        return null;
    }

    /// Every name bound here, valued or not, in the order they were bound —
    /// which is what the "Bound: …" half of a strict registry's error message
    /// prints, and the reason the order is kept rather than copied away.
    public Map<String, Object> bound() {
        var all = new LinkedHashMap<String, Object>(byName);
        all.putAll(valuedByName);
        return Collections.unmodifiableMap(all);
    }
}
