package io.github.digitalsmile.goldberry.widgets.menu;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.input.Handles;
import io.github.digitalsmile.goldberry.input.Key;
import io.github.digitalsmile.goldberry.input.KeyEvent;
import io.github.digitalsmile.goldberry.input.PointerEvent;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Attributed;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// One command in a [Menu] — `docs/core-widgets.md` §8's `item`: "label, optional
/// icon, accelerator (displayed right-aligned …), checkable items, disabled
/// state, nested submenus".
///
/// ```kdl
/// item press="app.save" icon="save" accelerator="Ctrl+S" "Save"
/// item press="app.wrap" checked=#true "Word wrap"
/// item "Recent" { item press="app.recent-1" "notes.txt" }
/// ```
///
/// ## What an item does not do
///
/// **It does not open its own submenu.** Opening anything is [Menus]'s, because
/// it needs a `Host` and a widget does not have one — so an item with children
/// carries them and is handed an `onOpenSubmenu` by whoever opened the menu it is
/// in, exactly as a `radio` is handed `selected` and `onSelect` by its group
/// ([ADR-0106](../../../../../../../book/src/adr/0106-a-menu-is-a-widget-and-opening-one-is-not.md)).
///
/// **It does not register its accelerator.** §8 says an accelerator is "displayed
/// right-aligned *and* auto-registered in the window's shortcut map"; this is the
/// display half. The registration needs the window and a lifetime — a menu is
/// built and thrown away every time it opens, and a shortcut must outlive that —
/// so it is the application's `host.shortcut(…)` until something owns menus for
/// longer than one opening.
///
/// @param label       the command's name
/// @param icon        an optional icon before it
/// @param accelerator the shortcut to *show*, right-aligned, or null
/// @param onPress     what it does. Null for an item that opens a submenu and
///                    nothing else
/// @param checked     whether it draws a tick — §8's "checkable items"
/// @param disabled    whether it refuses
/// @param submenu     the items of its submenu, or empty
/// @param onOpenSubmenu how it asks for that submenu to be opened. Supplied by
///                    [Menus], never by an author
/// @param attributes  `id` and `class`, exactly as on the primitives
public record Item(
        String label, Icon icon, String accelerator, Runnable onPress, boolean checked,
        boolean disabled, List<Widget> submenu, Runnable onOpenSubmenu, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Handles, Attributed<Item> {

    public Item {
        Objects.requireNonNull(label, "label");
        submenu = List.copyOf(submenu == null ? List.of() : submenu);
        if (label.isEmpty() && icon == null) {
            throw new IllegalArgumentException(
                    "a menu item with neither a label nor an icon has nothing to read out (§13)");
        }
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    /// A command.
    public Item(String label, Runnable onPress) {
        this(label, null, null, onPress, false, false, List.of(), null, Attributes.NONE);
    }

    /// A command with nothing behind it yet.
    public Item(String label) {
        this(label, null);
    }

    /// This item with an icon before its label.
    public Item icon(Icon value) {
        return new Item(label, value, accelerator, onPress, checked, disabled, submenu,
                onOpenSubmenu, attributes);
    }

    /// This item showing `text` as its accelerator — the display half of §8's
    /// accelerator, right-aligned. See the class note for the other half.
    public Item accelerator(String text) {
        return new Item(label, icon, text, onPress, checked, disabled, submenu, onOpenSubmenu,
                attributes);
    }

    /// This item with a tick, or without one.
    public Item checked(boolean value) {
        return new Item(label, icon, accelerator, onPress, value, disabled, submenu,
                onOpenSubmenu, attributes);
    }

    public Item disabled(boolean value) {
        return new Item(label, icon, accelerator, onPress, checked, value, submenu,
                onOpenSubmenu, attributes);
    }

    /// This item with a submenu under it.
    public Item submenu(Widget... items) {
        return new Item(label, icon, accelerator, onPress, checked, disabled, List.of(items),
                onOpenSubmenu, attributes);
    }

    /// This item running `action` when chosen — used by [Menus] to wrap an
    /// author's command in "and close the menu", which is what choosing a command
    /// does everywhere.
    public Item pressing(Runnable action) {
        return new Item(label, icon, accelerator, action, checked, disabled, submenu,
                onOpenSubmenu, attributes);
    }

    /// Used by [Menus] to hand an item the way to open its own submenu.
    public Item opensWith(Runnable opener) {
        return new Item(label, icon, accelerator, onPress, checked, disabled, submenu, opener,
                attributes);
    }

    /// Whether this item leads somewhere rather than doing something.
    public boolean hasSubmenu() {
        return !submenu.isEmpty();
    }

    @Override
    public String cssType() {
        return "item";
    }

    @Override
    public String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public Item withAttributes(Attributes value) {
        return new Item(label, icon, accelerator, onPress, checked, disabled, submenu,
                onOpenSubmenu, value);
    }

    @Override
    public boolean isFocusable() {
        return !disabled;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    /// Mirrored to `:checked`, so a tick is a stylesheet's business and not a
    /// second drawing.
    @Override
    public boolean isChecked() {
        return checked;
    }

    /// A click activates; a **hover opens a submenu**, which is what makes a menu
    /// bar feel like one.
    ///
    /// The hover-intent delay §8 asks for is [Menus]'s: the timer belongs to the
    /// event loop and a widget has no way to reach it
    /// ([ADR-0105](../../../../../../../book/src/adr/0105-a-tooltip-is-an-attribute-not-a-widget.md)).
    @Override
    public void onPointer(PointerEvent event) {
        if (disabled) {
            return;
        }
        if (event.kind() == PointerEvent.Kind.CLICKED) {
            activate();
            event.consume();
        } else if (event.kind() == PointerEvent.Kind.ENTERED && hasSubmenu()) {
            openSubmenu();
        }
    }

    /// `Enter` and `Space` activate. `Right` opens a submenu, which is the one
    /// arrow a menu does not spend on traversal.
    @Override
    public void onKey(KeyEvent event) {
        if (disabled || event.kind() != KeyEvent.Kind.PRESSED || event.isRepeat()
                || !event.modifiers().none()) {
            return;
        }
        if (event.key() == Key.ENTER || event.key() == Key.SPACE) {
            activate();
            event.consume();
        } else if (event.key() == Key.RIGHT && hasSubmenu()) {
            openSubmenu();
            event.consume();
        }
    }

    /// Opening a submenu when there is one, running the command when there is
    /// not.
    ///
    /// An item cannot be both: §8 gives no meaning to a command that is also a
    /// heading, and every desktop menu agrees.
    private void activate() {
        if (hasSubmenu()) {
            openSubmenu();
        } else if (onPress != null) {
            onPress.run();
        }
    }

    private void openSubmenu() {
        if (onOpenSubmenu != null) {
            onOpenSubmenu.run();
        }
    }

    /// A row: the tick's column, an optional icon, the label, a spacer, and the
    /// accelerator.
    ///
    /// The spacer is what right-aligns the accelerator, and it is a box rather
    /// than a `text-align` because §8's CSS subset has neither one — the same
    /// reason `slider`'s value label sits where it does.
    ///
    /// The tick is **always present** — see [#children()] and [ItemCheck].
    /// The tick column, as a part — always there, so labels line up whether or
    /// not anything in the menu is checkable. See [ItemCheck].
    ///
    /// A submenu's own items are **not** children of this node: they are a
    /// separate widget tree in a separate window, built by [Menus] when the
    /// submenu opens.
    @Override
    public List<Widget> children() {
        return List.of(new ItemCheck(checked));
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var content = new ArrayList<Box>(5);
        // Child 0 is the tick column.
        content.addAll(children);
        if (icon != null) {
            content.add(Box.icon(icon, style.color()));
        }
        if (!label.isEmpty()) {
            content.add(Box.text(context.paragraph(style, label), style.color()));
        }
        // Grows, so everything after it is pushed to the far edge.
        content.add(Box.of().grow(1));
        if (accelerator != null && !accelerator.isEmpty()) {
            content.add(Box.text(context.paragraph(style, accelerator), style.color()));
        }
        return Box.of().style(style).children(content.toArray(Box[]::new));
    }
}
