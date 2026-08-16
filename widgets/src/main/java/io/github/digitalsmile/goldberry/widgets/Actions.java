package io.github.digitalsmile.goldberry.widgets;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

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
        if (byName.putIfAbsent(name, action) != null) {
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
        if (action == null && strict) {
            throw new IllegalArgumentException(
                    "no action named \"" + name + "\" is bound. Bound: "
                            + (byName.isEmpty() ? "(none)" : String.join(", ", byName.keySet())));
        }
        return action;
    }

    /// Every name bound here, in the order they were bound.
    public Map<String, Runnable> bound() {
        return Map.copyOf(byName);
    }
}
