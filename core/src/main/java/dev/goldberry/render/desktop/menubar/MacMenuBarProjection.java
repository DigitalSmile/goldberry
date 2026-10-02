package dev.goldberry.render.desktop.menubar;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.natives.desktop.macos.MacMenuBar;

/// [BackendMenuBar] on macOS: the rows become `NSMenuItem`s in
/// `NSApp.mainMenu`, after AppKit's own application menu.
///
/// Each command row is given a **tag**, its index in a list of actions kept
/// here, and the native side reports the tag when the row is chosen — so a
/// row's action is a Java object that never crosses the boundary, and showing
/// the bar again with new actions replaces the list rather than leaking one
/// upcall per row.
///
/// The translation is [#plan], a pure function a test can run without AppKit.
///
/// UNVERIFIED on a Mac: written against AppKit's documentation.
///
/// Read more: [Menus and the tray](https://goldberry.dev/docs/components/menus.html#the-macos-menu-bar).
public final class MacMenuBarProjection implements BackendMenuBar {

    private static final Logger LOG = Logs.of(MacMenuBarProjection.class);

    private final MacMenuBar bar;
    private List<Runnable> actions = List.of();
    private @Nullable Plan shown;

    private MacMenuBarProjection(MacMenuBar bar) {
        this.bar = bar;
    }

    /// The projection, or empty off macOS.
    public static Optional<BackendMenuBar> current() {
        return MacMenuBar.get().map(MacMenuBarProjection::new);
    }

    @Override
    public boolean show(String application, List<AppMenuItem> headings) {
        Objects.requireNonNull(application, "application");
        var plan = plan(headings);
        // The actions are always the latest -- a rebuilt bar's lambdas are new
        // objects -- but AppKit is only asked again when what it draws changed.
        actions = plan.actions();
        if (plan.items().equals(shown == null ? null : shown.items())) {
            return true;
        }
        if (!bar.install(application, plan.items(), this::chosen)) {
            return false;
        }
        shown = plan;
        return true;
    }

    @Override
    public void clear() {
        if (shown != null) {
            bar.uninstall();
            shown = null;
            actions = List.of();
        }
    }

    private void chosen(int tag) {
        if (tag < 0 || tag >= actions.size()) {
            return;
        }
        try {
            actions.get(tag).run();
        } catch (RuntimeException e) {
            LOG.warn("a menu bar command failed", e);
        }
    }

    /// The native rows and the actions their tags index.
    ///
    /// @param items   the rows, in AppKit's terms
    /// @param actions what each tag runs
    record Plan(List<MacMenuBar.Item> items, List<Runnable> actions) {}

    /// Translates `headings` into AppKit's terms, without touching AppKit.
    ///
    /// A top-level row that is not a submenu is put in a menu of its own
    /// named after it, because a macOS menu bar has nothing but menus on it.
    static Plan plan(List<AppMenuItem> headings) {
        Objects.requireNonNull(headings, "headings");
        var actions = new ArrayList<Runnable>();
        var items = new ArrayList<MacMenuBar.Item>();
        for (var heading : headings) {
            if (heading.kind() == AppMenuItem.Kind.SEPARATOR) {
                continue;
            }
            var native_ = item(heading, actions);
            items.add(
                    heading.kind() == AppMenuItem.Kind.SUBMENU
                            ? native_
                            : new MacMenuBar.Item(
                                    MacMenuBar.Item.Kind.SUBMENU,
                                    native_.title(),
                                    "",
                                    0,
                                    native_.enabled(),
                                    false,
                                    -1,
                                    List.of(native_)));
        }
        return new Plan(List.copyOf(items), List.copyOf(actions));
    }

    private static MacMenuBar.Item item(AppMenuItem row, List<Runnable> actions) {
        return switch (row.kind()) {
            case SEPARATOR ->
                new MacMenuBar.Item(MacMenuBar.Item.Kind.SEPARATOR, "", "", 0, true, false, -1, List.of());
            case SUBMENU ->
                new MacMenuBar.Item(
                        MacMenuBar.Item.Kind.SUBMENU,
                        Objects.requireNonNull(row.label()),
                        "",
                        0,
                        row.enabled(),
                        false,
                        -1,
                        row.children().stream()
                                .map(child -> item(child, actions))
                                .toList());
            case COMMAND -> {
                var key = row.shortcut() == null ? KeyEquivalent.NONE : KeyEquivalent.of(row.shortcut());
                var tag = -1;
                if (row.onChosen() != null) {
                    tag = actions.size();
                    actions.add(row.onChosen());
                }
                yield new MacMenuBar.Item(
                        MacMenuBar.Item.Kind.COMMAND,
                        Objects.requireNonNull(row.label()),
                        key.key(),
                        key.modifiers(),
                        row.enabled() && row.onChosen() != null,
                        Boolean.TRUE.equals(row.checked()),
                        tag,
                        List.of());
            }
        };
    }
}
