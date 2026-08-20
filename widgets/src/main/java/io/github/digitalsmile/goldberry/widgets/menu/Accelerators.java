package io.github.digitalsmile.goldberry.widgets.menu;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.input.Shortcut;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// Every accelerator a menu description names — `docs/core-widgets.md` §8's
/// "displayed right-aligned **and** auto-registered in the window's shortcut
/// map", second half.
///
/// ## Why this can exist now
///
/// [ADR-0106](../../../../../../../book/src/adr/0106-a-menu-is-a-widget-and-opening-one-is-not.md)
/// left the registration undone with a reason: "a shortcut has to work while the
/// menu is shut, and a menu is built when it opens and thrown away when it
/// closes". That is true of the *popup*. It was never true of the [Menu] — a
/// widget is a value, and a value handed to a `menubar` is held for as long as
/// the bar is
/// ([ADR-0163](../../../../../../../book/src/adr/0163-a-menu-bar-owns-its-menus.md)).
/// So the model that has to outlive one opening is the one the author already
/// wrote, and this walks it.
///
/// ## What is registrable and what is skipped
///
/// A binding needs three things and an item that is missing one is passed over
/// in silence, because each absence is an ordinary thing to write:
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
public final class Accelerators {

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(Accelerators.class);

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

    private Accelerators() {
    }

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
        if (text == null || text.isBlank() || item.disabled() || item.hasSubmenu()
                || item.onPress() == null) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(
                    new Binding(Shortcut.of(text), item.label(), item.onPress()));
        } catch (IllegalArgumentException e) {
            LOG.warn("\"{}\" on the menu item \"{}\" is not a shortcut this toolkit can bind,"
                    + " so it is displayed and not registered: {}", text, item.label(),
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
        Objects.requireNonNull(host, "host");
        var bound = new LinkedHashSet<Shortcut>();
        var byShortcut = new java.util.LinkedHashMap<Shortcut, String>();
        for (var binding : in(widgets)) {
            var previous = byShortcut.put(binding.shortcut(), binding.label());
            if (previous != null) {
                LOG.warn("{} is the accelerator of both \"{}\" and \"{}\"; the later one wins",
                        binding.shortcut(), previous, binding.label());
            }
            host.shortcut(binding.shortcut(), binding.action());
            bound.add(binding.shortcut());
        }
        return bound;
    }

    /// Unbinds what [#bind] bound.
    ///
    /// Removes by key, which is all the window's map can do — so a shortcut some
    /// other part of the application bound to the *same* key afterwards goes with
    /// it. That collision is the authoring mistake above at the other end, and it
    /// is stated rather than defended against, because defending would mean the
    /// map remembering who bound what and a `menubar` being the only thing that
    /// could use it.
    public static void unbind(Host host, Set<Shortcut> bound) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(bound, "bound");
        for (var shortcut : bound) {
            host.removeShortcut(shortcut);
        }
    }
}
