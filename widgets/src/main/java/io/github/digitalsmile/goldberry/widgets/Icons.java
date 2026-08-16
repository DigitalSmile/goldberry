package io.github.digitalsmile.goldberry.widgets;

import io.github.digitalsmile.goldberry.icon.Icon;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/// What an icon name in markup means.
///
/// The same shape as [Actions] and for a sharper reason: an `Icon` owns native
/// memory and has to be closed. If `icon="plus"` built one on the spot, a
/// document reloaded on every keystroke would leak one per reload, and nothing
/// would ever close the last of them.
///
/// So the application builds the icons it uses — once, at the sizes it uses them
/// at, alongside its fonts — registers them here, and closes them when the window
/// does. Markup names one of those.
///
/// Confined to the UI thread. The icons it hands out are too: a `BlendPath` is
/// thread-confined like everything else Blend2D owns.
public final class Icons {

    private final Map<String, Icon> byName = new LinkedHashMap<>();
    private final boolean strict;

    private Icons(boolean strict) {
        this.strict = strict;
    }

    /// A registry that refuses an unknown name — the right default, for the
    /// reason [Actions#strict()] gives.
    public static Icons strict() {
        return new Icons(true);
    }

    /// A registry that resolves an unknown name to nothing, for a preview or a
    /// document mid-edit.
    public static Icons lenient() {
        return new Icons(false);
    }

    /// No icons at all, and no complaints.
    public static Icons none() {
        return lenient();
    }

    /// Registers an icon under a name.
    ///
    /// The name need not be Lucide's — `icons.bind("save", diskIcon)` is fine,
    /// and is how an application uses its own icon pack (§6.3).
    ///
    /// @throws IllegalStateException if the name is already registered
    public Icons bind(String name, Icon icon) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(icon, "icon");
        if (byName.putIfAbsent(name, icon) != null) {
            throw new IllegalStateException(
                    "\"" + name + "\" is already registered; use rebind() to replace it");
        }
        return this;
    }

    /// Replaces a registration, or adds it.
    public Icons rebind(String name, Icon icon) {
        byName.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(icon, "icon"));
        return this;
    }

    /// The icon a markup attribute names, or null when there is no attribute.
    ///
    /// @throws IllegalArgumentException if this registry is [#strict()] and the
    ///         name is not registered
    public Icon resolve(String name) {
        if (name == null) {
            return null;
        }
        var icon = byName.get(name);
        if (icon == null && strict) {
            throw new IllegalArgumentException(
                    "no icon named \"" + name + "\" is registered. Registered: "
                            + (byName.isEmpty() ? "(none)" : String.join(", ", byName.keySet()))
                            + ". Build one with Icon.bundled(name, size) and register it here —"
                            + " markup cannot build an icon because nothing would close it.");
        }
        return icon;
    }

    /// Every name registered here, in the order they were registered.
    public Map<String, Icon> registered() {
        return Map.copyOf(byName);
    }
}
