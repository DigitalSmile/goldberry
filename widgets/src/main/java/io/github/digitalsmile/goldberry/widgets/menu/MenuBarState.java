package io.github.digitalsmile.goldberry.widgets.menu;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.Placement;
import io.github.digitalsmile.goldberry.Popup;
import io.github.digitalsmile.goldberry.input.Shortcut;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/// Which of a [MenuBar]'s menus is showing, and what its accelerators are bound
/// to.
///
/// Everything here is the half of a bar that cannot be a value: a platform
/// window, and a set of registrations in somebody else's map
/// ([ADR-0163](../../../../../../../book/src/adr/0163-a-menu-bar-owns-its-menus.md)).
final class MenuBarState extends State<MenuBar> {

    /// A prefix for the generated heading ids, which is how a menu is anchored
    /// under the heading that opened it. Generated for [Menus]'s reason: an
    /// author should not have to name every heading to be able to click one.
    private static final String TITLE_ID = "menubar-title-";

    /// Where a heading's menu goes: below it, left edges lined up, touching.
    private static final Placement UNDER_THE_BAR =
            new Placement(Placement.Side.BOTTOM, Placement.Align.START, 0);

    /// The open menu, or null. Closed by choosing a command, by clicking the
    /// heading again, and by the popup's own light dismissal — a press outside or
    /// `Escape` — which this notices through [Popup#isOpen()] rather than being
    /// told, exactly as `select` does.
    private Popup open;

    /// Which heading [#open] belongs to; -1 when nothing is showing.
    private int openIndex = -1;

    /// The window this is being built into, captured for the handlers.
    private Host host;

    /// What [#host] currently has bound on this bar's behalf.
    ///
    /// Held rather than re-derived at unbind time, because the widget may have
    /// been replaced by one with different menus between binding and unbinding —
    /// and a set derived from the *new* description would leave the old
    /// accelerators bound forever.
    private Set<Shortcut> bound = Set.of();

    /// The host the accelerators in [#bound] are bound on. Not always [#host]:
    /// an element can be rebuilt without one and must still give back what it
    /// took from the one it had.
    private Host boundOn;

    @Override
    public Widget build(BuildContext context) {
        host = context.host().orElse(null);
        rebind();

        var bar = widget();
        var titles = new ArrayList<Widget>(bar.children().size());
        for (var index = 0; index < bar.children().size(); index++) {
            titles.add(title(bar.children().get(index), index));
        }
        return new MenuBarRow(titles, bar.attributes());
    }

    @Override
    protected void didUpdateWidget(MenuBar previous) {
        // A bar whose menus were replaced has different accelerators, and the old
        // ones would otherwise stay bound to commands the document no longer
        // names. `build` runs after this and does the rebinding; what matters
        // here is closing a menu whose heading may no longer exist.
        if (!previous.children().equals(widget().children())) {
            close();
        }
    }

    @Override
    protected void dispose() {
        // Both halves, and both are leaks of a kind a widget is otherwise
        // incapable of: a popup is a platform window parented to this one, and an
        // accelerator is an entry in a map that outlives the tree.
        close();
        unbind();
        super.dispose();
    }

    /// One heading, wired to open its own menu.
    private Widget title(Widget child, int index) {
        if (!(child instanceof Item item)) {
            // Anything that is not an item is passed through untouched — a
            // `separator` between groups of headings, or whatever an application
            // puts at the end of its bar.
            return child;
        }
        var id = item.attributes().id() == null ? TITLE_ID + index : item.attributes().id();
        var attributes = Attributes.NONE
                .id(id)
                .classes(item.attributes().classes().toArray(String[]::new));
        if (index == openIndex && isOpen()) {
            // `.open` rather than a pseudo-class, because "the branch that is
            // showing" is not one of CSS's states and inventing one would put a
            // menu bar's internals in the selector engine. The same shape
            // `select` uses for its field while the list is down.
            attributes = attributes.classes(concat(item.attributes().classes(), "open"));
        }
        return new MenuTitle(item.label(), item.icon(), item.disabled(), attributes,
                () -> toggle(index), () -> switchTo(index));
    }

    private static String[] concat(Set<String> classes, String extra) {
        var all = new ArrayList<>(classes);
        all.add(extra);
        return all.toArray(String[]::new);
    }

    /// Whether a menu is showing, allowing for a popup that dismissed itself.
    private boolean isOpen() {
        if (open != null && !open.isOpen()) {
            open = null;
            openIndex = -1;
        }
        return open != null;
    }

    /// A click or `Enter` on a heading: open its menu, or put it away if it is
    /// the one already showing.
    private void toggle(int index) {
        if (isOpen() && openIndex == index) {
            setState(this::close);
            return;
        }
        setState(() -> show(index));
    }

    /// The pointer arriving on a heading.
    ///
    /// **Only while something is already open**, which is the rule every desktop
    /// menu bar has: with a menu down, running along the bar swaps menus without
    /// a click; with nothing down, crossing the bar on the way somewhere else
    /// must not drop a menu on the screen.
    private void switchTo(int index) {
        if (!isOpen() || openIndex == index) {
            return;
        }
        setState(() -> show(index));
    }

    /// Closes whatever is showing and opens `index`'s menu under its heading.
    private void show(int index) {
        close();
        var bar = widget();
        if (host == null || index < 0 || index >= bar.children().size()) {
            return;
        }
        if (!(bar.children().get(index) instanceof Item item) || item.disabled()
                || !item.hasSubmenu()) {
            return;
        }
        // `Placement.BELOW`'s side and alignment with **no gap**: a menu hangs
        // from its heading's left edge and touches the bar, where a context menu
        // stands 4px off the pointer so as not to open under it. The alignment is
        // already START, which is what puts a `File` menu at the left edge of the
        // word rather than centred under it.
        var id = item.attributes().id() == null ? TITLE_ID + index : item.attributes().id();
        var menu = new Menu(item.submenu(), Attributes.NONE);
        var opened = Menus.open(host, id, menu, UNDER_THE_BAR);
        // Empty is normal — a driver with no popup windows (ADR-0102) — and it
        // must not leave the heading marked open, because nothing would ever
        // unmark it.
        opened.ifPresent(popup -> {
            open = popup;
            openIndex = index;
        });
    }

    private void close() {
        if (open != null) {
            open.close();
            open = null;
        }
        openIndex = -1;
    }

    /// Brings the window's shortcut map in line with the bar's current menus.
    ///
    /// Called from `build` rather than from `initState` because the `Host` is
    /// only reachable through a [BuildContext], and because a rebuild is exactly
    /// when the description may have changed. Cheap when nothing did: unbinding
    /// and rebinding the same keys leaves the map holding the same entries, and a
    /// bar is rebuilt when its own state changes, which is when a menu opens.
    private void rebind() {
        unbind();
        if (host == null) {
            return;
        }
        bound = Accelerators.bind(host, widget().children());
        boundOn = host;
        // F10 is the keyboard's way in. Registered with the accelerators so it
        // goes away with them, and reported as bound for the same reason.
        if (!bound.isEmpty() || !widget().children().isEmpty()) {
            var focusBar = Shortcut.of(io.github.digitalsmile.goldberry.input.Key.F10);
            host.shortcut(focusBar, this::activateFromKeyboard);
            var all = new java.util.LinkedHashSet<>(bound);
            all.add(focusBar);
            bound = Set.copyOf(all);
        }
    }

    private void unbind() {
        if (boundOn != null && !bound.isEmpty()) {
            Accelerators.unbind(boundOn, bound);
        }
        bound = Set.of();
        boundOn = null;
    }

    /// `F10`: open the first heading that can be opened.
    ///
    /// Opening rather than merely focusing, because focus is not a thing a widget
    /// can ask for from here — there is no `Host.focus(id)` — and a bar that took
    /// `F10` and did nothing visible would read as a broken binding rather than a
    /// missing one. Once the menu is down, its own arrows work.
    private void activateFromKeyboard() {
        var bar = widget();
        for (var index = 0; index < bar.children().size(); index++) {
            if (bar.children().get(index) instanceof Item item
                    && item.hasSubmenu() && !item.disabled()) {
                var target = index;
                setState(() -> show(target));
                return;
            }
        }
    }
}
