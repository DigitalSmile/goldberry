package dev.goldberry.widgets.menu;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Host;
import dev.goldberry.input.key.Shortcut;
import dev.goldberry.widget.Widget;

/// The accelerators a menu description names, collected and bound on the
/// window so that they work while the menu is shut.
///
/// ```java
/// Set<Shortcut> bound = Accelerators.bind(host, menu.children());
/// Accelerators.unbind(host, bound);
/// ```
///
/// A `menubar` does this itself on mount and unmount. A menu that is only ever
/// opened, such as a context menu, shows its accelerators and registers none,
/// so an application binds those with this class or with `host.shortcut(…)`.
/// The walk is over the [Menu] value, which outlives any one opening of its
/// popup, so the bound keys stay right for as long as the description does.
///
/// A binding needs three things, and an item missing one is passed over in
/// silence, because each absence is an ordinary thing to write:
///
///   - **an accelerator**, which most rows have not got;
///   - **a command** — a row with a submenu leads somewhere rather than doing
///     something, and there is nothing to bind a key to;
///   - **not being disabled**, because a greyed row that still fires on its key
///     is worse than no accelerator at all. Disabled is read at the moment of
///     walking, so a bar re-registers when its menus change.
///
/// An accelerator that does not **parse** is different: it is a typo, it is
/// already being drawn beside the row where the user can see it, and taking the
/// window down over it would be a stylesheet error crashing an application. It
/// is logged and skipped.
///
/// Read more: [Menus and the tray](https://goldberry.dev/docs/components/menus.html#accelerators).
public final class Accelerators {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(Accelerators.class);

    /// One registrable accelerator: the key, what the menu calls it, and what it
    /// runs.
    ///
    /// @param shortcut the parsed key and modifiers
    /// @param label    the item's label, for a diagnostic — an accelerator that
    ///                 collides is only useful to report if you can say which
    ///                 two commands wanted it
    /// @param action   the item's own command, not wrapped in anything: firing a
    ///                 key does not open, close or otherwise touch a menu
    public record Binding(Shortcut shortcut, String label, Runnable action) {

        public Binding {
            Objects.requireNonNull(shortcut, "shortcut");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(action, "action");
        }
    }

    private Accelerators() {}

    /// Every accelerator in `widgets` and in the submenus underneath them.
    ///
    /// Depth-first in document order, so a collision report names the two rows in
    /// the order somebody reading the menu would meet them.
    public static List<Binding> in(List<Widget> widgets) {
        Objects.requireNonNull(widgets, "widgets");
        var found = new ArrayList<Binding>();
        collect(widgets, found);
        return List.copyOf(found);
    }

    /// [#in(List)] for a menu.
    public static List<Binding> in(Menu menu) {
        Objects.requireNonNull(menu, "menu");
        return in(menu.children());
    }

    private static void collect(List<Widget> widgets, List<Binding> into) {
        for (var widget : widgets) {
            if (!(widget instanceof Item item)) {
                continue;
            }
            // Before the recursion, so a parent's own accelerator -- if somebody
            // writes one on a row that also has a submenu -- is reported in the
            // place it appears rather than after its children.
            binding(item).ifPresent(into::add);
            if (item.hasSubmenu()) {
                collect(item.submenu(), into);
            }
        }
    }

    private static java.util.Optional<Binding> binding(Item item) {
        var text = item.accelerator();
        if (text == null || text.isBlank() || item.disabled() || item.hasSubmenu() || item.onPress() == null) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(new Binding(Shortcut.of(text), item.label(), item.onPress()));
        } catch (IllegalArgumentException e) {
            LOG.warn(
                    "\"{}\" on the menu item \"{}\" is not a shortcut this toolkit can bind,"
                            + " so it is displayed and not registered: {}",
                    text,
                    item.label(),
                    e.getMessage());
            return java.util.Optional.empty();
        }
    }

    /// Binds every accelerator in `widgets` on `host`, and answers what was bound.
    ///
    /// The answer is what [#unbind] takes back, and it is the *shortcuts* rather
    /// than the bindings because that is what the window's map is keyed by.
    ///
    /// **A collision is logged and the later row wins**, which is the map's own
    /// behaviour stated out loud. Two commands on one key is an authoring
    /// mistake with no good silent resolution: refusing the second would make a
    /// menu whose second `Ctrl+O` does nothing and says nothing.
    public static Set<Shortcut> bind(Host host, List<Widget> widgets) {
        return bind(host, widgets, null);
    }

    /// The same, binding **on behalf of `owner`** — which is what makes
    /// [#unbind(Host, Set, Object)] able to give back only what it took.
    ///
    /// A `menubar` passes its own state object. Nothing else in the toolkit binds
    /// accelerators, and an application binding its own does not need an owner:
    /// unbinding by key is what it means to give up a key you took.
    public static Set<Shortcut> bind(Host host, List<Widget> widgets, @Nullable Object owner) {
        Objects.requireNonNull(host, "host");
        var bound = new LinkedHashSet<Shortcut>();
        var byShortcut = new java.util.LinkedHashMap<Shortcut, String>();
        for (var binding : in(widgets)) {
            var previous = byShortcut.put(binding.shortcut(), binding.label());
            if (previous != null) {
                LOG.warn(
                        "{} is the accelerator of both \"{}\" and \"{}\"; the later one wins",
                        binding.shortcut(),
                        previous,
                        binding.label());
            }
            if (owner == null) {
                host.shortcut(binding.shortcut(), binding.action());
            } else {
                host.shortcut(binding.shortcut(), binding.action(), owner);
            }
            bound.add(binding.shortcut());
        }
        return bound;
    }

    /// Unbinds what [#bind] bound, whoever holds those keys now.
    public static void unbind(Host host, Set<Shortcut> bound) {
        unbind(host, bound, null);
    }

    /// Unbinds what `owner` bound, and **only** what it still holds.
    ///
    /// The window's map remembers who bound what, so a bar being unmounted gives
    /// back the keys it took and no others: a shortcut some other part of the
    /// application bound to the same key afterwards stays bound. The collision at
    /// bind time is unchanged — two commands on one key is an authoring mistake
    /// and the later one wins — but the loser cannot take the winner away with
    /// it.
    public static void unbind(Host host, Set<Shortcut> bound, @Nullable Object owner) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(bound, "bound");
        for (var shortcut : bound) {
            // No owner is the two-argument form's "whoever holds those keys now":
            // by key, which is how an application gives up a key it took. Passing
            // the null on asked the host for the keys *nobody* holds instead.
            if (owner == null) {
                host.removeShortcut(shortcut);
            } else {
                host.removeShortcut(shortcut, owner);
            }
        }
    }
}
