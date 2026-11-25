package dev.goldberry.widgets.form.edit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Host;
import dev.goldberry.bind.Subscription;
import dev.goldberry.i18n.Labels;
import dev.goldberry.i18n.ToolkitLabel;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Shortcut;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.menu.Item;
import dev.goldberry.widgets.menu.Menu;
import dev.goldberry.widgets.menu.Menus;
import dev.goldberry.widgets.menu.Separator;
import dev.goldberry.widgets.menu.ShownMenu;

/// A text field's own context menu: Cut, Copy, Paste, and Select all.
///
/// One per field state, opened from the field's
/// [dev.goldberry.input.handler.ContextMenuOwner] answer. While it is open it
/// listens to the clipboard, so Paste enables itself when another application
/// copies and disables itself when the clipboard is cleared. The listener goes
/// when the menu does: on the next change after it closed, on the next opening,
/// or on [#close()] from the state's `dispose`.
///
/// The accelerators are the platform's primary modifier, `Cmd` on macOS and
/// `Ctrl` elsewhere, which is what the field's keys already answer. They are
/// shown and not bound: the field binds them.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#text-input).
public final class TextMenu {

    private final Host host;
    private final TextCommands commands;

    /// The rows a field adds after the stock ones, asked for each time the menu
    /// is drawn, so a row that shows a state shows the current one.
    private final Supplier<List<Widget>> more;

    /// The rows a field puts before the stock ones, asked for the same way: a
    /// misspelled word's suggestions.
    private final Supplier<List<Widget>> before;

    private @Nullable ShownMenu shown;
    private @Nullable Subscription watching;

    /// @param host     the window the menu opens on
    /// @param commands the field the menu acts on
    public TextMenu(Host host, TextCommands commands) {
        this(host, commands, List::of);
    }

    /// A menu with rows of the field's own after the stock ones, under a
    /// separator: a rich field's Format submenu.
    ///
    /// @param more the rows, asked for each time the menu is drawn
    public TextMenu(Host host, TextCommands commands, Supplier<List<Widget>> more) {
        this(host, commands, List::of, more);
    }

    /// A menu with rows of the field's own before the stock ones, under which
    /// a separator goes, and after them.
    ///
    /// @param before the rows above, asked for each time the menu is drawn
    /// @param more   the rows below, asked for the same way
    public TextMenu(Host host, TextCommands commands, Supplier<List<Widget>> before, Supplier<List<Widget>> more) {
        this.host = Objects.requireNonNull(host, "host");
        this.commands = Objects.requireNonNull(commands, "commands");
        this.before = Objects.requireNonNull(before, "before");
        this.more = Objects.requireNonNull(more, "more");
    }

    /// Opens the menu at `anchor`, closing one this field already had open.
    ///
    /// @return whether it opened
    public boolean open(LogicalRect anchor) {
        Objects.requireNonNull(anchor, "anchor");
        close();
        var opened = Menus.show(host, anchor, menu(commands, before.get(), more.get()));
        if (opened.isEmpty()) {
            return false;
        }
        shown = opened.get();
        watching = host.clipboard().onChange(this::clipboardChanged);
        return true;
    }

    /// Closes the menu if it is open and stops listening. Idempotent.
    public void close() {
        stopWatching();
        var current = shown;
        shown = null;
        if (current != null && current.isOpen()) {
            current.close();
        }
    }

    private void clipboardChanged() {
        var current = shown;
        if (current == null || !current.isOpen()) {
            // Closed by a choice, a press outside or Escape, none of which tells
            // this class. The first change after it is when it finds out.
            stopWatching();
            shown = null;
            return;
        }
        current.update(menu(commands, before.get(), more.get()));
    }

    /// Draws the open menu's rows again, as they stand now: suggestions that
    /// arrived after it opened. Nothing when it is not open.
    public void refresh() {
        var current = shown;
        if (current != null && current.isOpen()) {
            current.updateAndPreferFirst(menu(commands, before.get(), more.get()));
        }
    }

    private void stopWatching() {
        var subscription = watching;
        watching = null;
        if (subscription != null) {
            subscription.close();
        }
    }

    /// The rows, as `commands` stands now. Each disabled row is one whose `can…`
    /// said false.
    public static Menu menu(TextCommands commands) {
        return menu(commands, List.of());
    }

    /// The rows, as `commands` stands now, and `more` after them under a
    /// separator when there are any.
    public static Menu menu(TextCommands commands, List<Widget> more) {
        return menu(commands, List.of(), more);
    }

    /// The rows, as `commands` stands now, with `before` above them and `more`
    /// below, each under or over a separator when there are any.
    public static Menu menu(TextCommands commands, List<Widget> before, List<Widget> more) {
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(more, "more");
        if (!before.isEmpty()) {
            var rows = new ArrayList<Widget>(before);
            rows.add(new Separator());
            rows.addAll(menu(commands, more).children());
            return new Menu(rows, null);
        }
        if (!more.isEmpty()) {
            var rows = new ArrayList<Widget>(menu(commands).children());
            rows.add(new Separator());
            rows.addAll(more);
            return new Menu(rows, null);
        }
        return new Menu(
                row(ToolkitLabel.CUT, Key.X, commands.canCut(), commands::cut),
                row(ToolkitLabel.COPY, Key.C, commands.canCopy(), commands::copy),
                row(ToolkitLabel.PASTE, Key.V, commands.canPaste(), commands::paste),
                new Separator(),
                row(ToolkitLabel.SELECT_ALL, Key.A, commands.canSelectAll(), commands::selectAll));
    }

    /// One row. The command's answer is dropped: the field redraws itself, and
    /// `Menus` closes the menu either way.
    private static Item row(ToolkitLabel label, Key key, boolean enabled, BooleanSupplier command) {
        return new Item(Labels.text(label), () -> {
                    var _ = command.getAsBoolean();
                })
                .id(label.key())
                .accelerator(Shortcut.primary(key).toString())
                .disabled(!enabled);
    }
}
