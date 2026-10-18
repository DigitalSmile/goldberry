package dev.goldberry.widgets.form.edit;

import java.util.Objects;
import java.util.function.BooleanSupplier;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Host;
import dev.goldberry.bind.Subscription;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Shortcut;
import dev.goldberry.render.model.LogicalRect;
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

    private @Nullable ShownMenu shown;
    private @Nullable Subscription watching;

    /// @param host     the window the menu opens on
    /// @param commands the field the menu acts on
    public TextMenu(Host host, TextCommands commands) {
        this.host = Objects.requireNonNull(host, "host");
        this.commands = Objects.requireNonNull(commands, "commands");
    }

    /// Opens the menu at `anchor`, closing one this field already had open.
    ///
    /// @return whether it opened
    public boolean open(LogicalRect anchor) {
        Objects.requireNonNull(anchor, "anchor");
        close();
        var opened = Menus.show(host, anchor, menu(commands));
        if (opened.isEmpty()) {
            return false;
        }
        shown = opened.get();
        watching = host.clipboard().onChange(this::clipboardChanged);
        return true;
    }

    /// Whether this field's menu is on screen.
    public boolean isOpen() {
        var current = shown;
        return current != null && current.isOpen();
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
        current.update(menu(commands));
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
        Objects.requireNonNull(commands, "commands");
        return new Menu(
                row("Cut", Key.X, commands.canCut(), commands::cut),
                row("Copy", Key.C, commands.canCopy(), commands::copy),
                row("Paste", Key.V, commands.canPaste(), commands::paste),
                new Separator(),
                row("Select all", Key.A, commands.canSelectAll(), commands::selectAll));
    }

    /// One row. The command's answer is dropped: the field redraws itself, and
    /// `Menus` closes the menu either way.
    private static Item row(String label, Key key, boolean enabled, BooleanSupplier command) {
        return new Item(label, () -> {
                    var _ = command.getAsBoolean();
                })
                .accelerator(Shortcut.primary(key).toString())
                .disabled(!enabled);
    }
}
