package dev.goldberry.widgets;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.goldberry.bind.registry.ActionRegistry;
import dev.goldberry.icon.Icon;

/// What an icon name in markup means: the registry `icon="plus"` resolves
/// against.
///
/// ```java
/// var icons = Icons.strict().bind("plus").bind("save", diskIcon);
/// var inflater = Widgets.inflater(icons, model);
/// ```
///
/// The same shape as [ActionRegistry] and for a sharper reason: an `Icon` owns native
/// memory and has to be closed. If `icon="plus"` built one on the spot, a
/// document reloaded on every keystroke would leak one per reload, and nothing
/// would ever close the last of them. So the application builds the icons it
/// uses — once, at the sizes it uses them at, alongside its fonts — registers
/// them here, and closes them when the window does. Markup names one of those.
///
/// Confined to the UI thread. The icons it hands out are too: a `BlendPath` is
/// thread-confined like everything else Blend2D owns.
///
/// Read more: [Text, fonts and icons](https://goldberry.dev/docs/guide/text.html#icons).
public final class Icons {

    /// The size every **fixed** slot in this catalog is, in logical pixels.
    ///
    /// The design system offers 16, 20 and 24, and 16 is the one the catalog's
    /// own slots are built around: a menu's leading column is 16 square because
    /// it has to hold a tick *and* an icon at one width, and a `select`'s chevron
    /// is 16 for the same reason.
    ///
    /// It is here because this is the door. An `Icon` is a path built at a size,
    /// and a control's slot draws it at that size, so the size is chosen at the
    /// moment of construction; naming it where the icon is built saves an author a trip to
    /// `controls.css`, where somebody building an icon in Java has no reason to
    /// look. A 20px glyph in the 16px column is centred and overhangs: legible,
    /// deliberate-looking, and not what anybody meant.
    ///
    /// **Not a maximum.** A `button`, a `chip`, a `tab` and a `crumb` size their
    /// boxes *to* the icon, so 20 and 24 are ordinary there and nothing is
    /// overflowed. This is the number for the slots that cannot. The `icon`
    /// node is the exception the other way: it draws any icon at the size its
    /// stylesheet gives the box.
    public static final double SLOT = 16;

    private final Map<String, Icon> byName = new LinkedHashMap<>();
    private final boolean strict;

    private Icons(boolean strict) {
        this.strict = strict;
    }

    /// A registry that refuses an unknown name — the right default, for the
    /// reason [ActionRegistry#strict()] gives.
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
    /// and is how an application uses its own icon pack.
    ///
    /// @throws IllegalStateException if the name is already registered
    public Icons bind(String name, Icon icon) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(icon, "icon");
        if (byName.putIfAbsent(name, icon) != null) {
            throw new IllegalStateException("\"" + name + "\" is already registered; use rebind() to replace it");
        }
        return this;
    }

    /// Registers a **bundled** icon under its own Lucide name, at [#SLOT].
    ///
    /// `icons.bind("folder")` rather than
    /// `icons.bind("folder", Icon.bundled("folder", 16))` — the same registration,
    /// with the size the catalog's fixed slots want already chosen. It is the
    /// shortest correct call, which is the only thing that reliably competes with
    /// the shortest call.
    ///
    /// An application that wants 20 or 24 says so, through [#bind(String, Icon)],
    /// and is right to: a `button`'s icon sizes the button's own box and has no
    /// slot to overflow.
    ///
    /// @throws IllegalStateException  if the name is already registered
    /// @throws java.util.NoSuchElementException if Lucide has no icon of that name
    public Icons bind(String name) {
        return bind(name, Icon.bundled(Objects.requireNonNull(name, "name"), SLOT));
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
    public @Nullable Icon resolve(@Nullable String name) {
        if (name == null) {
            return null;
        }
        var icon = byName.get(name);
        if (icon == null && strict) {
            throw new IllegalArgumentException("no icon named \"" + name + "\" is registered. Registered: "
                    + (byName.isEmpty() ? "(none)" : String.join(", ", byName.keySet()))
                    + ". Register a bundled one with bind(\"" + name + "\"), which builds it at the"
                    + " size this catalog's slots are, or bind(name, icon) for a size of your own —"
                    + " markup cannot build an icon because nothing would close it.");
        }
        return icon;
    }

    /// The icon registered under `name`, or nothing — whether or not this
    /// registry is [#strict()].
    ///
    /// The lookup for a caller that has a fallback of its own, which the
    /// `icon` node is: a name not registered here goes on to the bundled set
    /// rather than failing.
    public Optional<Icon> find(String name) {
        return Optional.ofNullable(byName.get(Objects.requireNonNull(name, "name")));
    }

    /// Every name registered here, in the order they were registered.
    public Map<String, Icon> registered() {
        return Map.copyOf(byName);
    }
}
