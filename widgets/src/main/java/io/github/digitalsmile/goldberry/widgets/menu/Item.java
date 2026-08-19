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
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widgets.Wiring;
import io.github.digitalsmile.goldberry.widgets.Markup;

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
/// carries them and is handed an `onHovered` by whoever opened the menu it is
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
/// @param checked     `TRUE` or `FALSE` for a **checkable** row — §8's "checkable
///                    items" — and `null` for one that is not checkable at all.
///                    Three states rather than two, because "unchecked" and "not
///                    a checkbox" are different things and only the second means
///                    "reserve no room for a tick"
///                    ([ADR-0113](../../../../../../../book/src/adr/0113-a-submenu-is-placed-beside-its-menu.md))
/// @param reservesLead whether this row leaves room for the leading column — the
///                    tick, or the icon, or nothing. Supplied by [Menus] and the
///                    same for every row in one menu: a column that appeared only
///                    on the rows that had something to put in it would step the
///                    labels in and out down the list ([ItemLead])
/// @param disabled    whether it refuses
/// @param submenu     the items of its submenu, or empty
/// @param onHovered   how it tells the menu the pointer has arrived on it.
///                    Supplied by [Menus], never by an author — and given to
///                    **every** row, not only the ones with children: a submenu
///                    closes when the pointer moves to a sibling, and a row with
///                    no submenu is the commonest sibling there is
///                    ([ADR-0112](../../../../../../../book/src/adr/0112-a-menu-follows-the-pointer-and-lights-for-the-keyboard.md))
/// @param attributes  `id` and `class`, exactly as on the primitives
@Markup("item")
public record Item(
        String label, Icon icon, String accelerator, Runnable onPress, Boolean checked,
        boolean disabled, List<Widget> submenu, boolean reservesLead, Runnable onHovered,
        Attributes attributes)
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
        this(label, null, null, onPress, null, false, List.of(), false, null, Attributes.NONE);
    }

    /// A command with nothing behind it yet.
    public Item(String label) {
        this(label, null);
    }

    /// This item with an icon before its label.
    public Item icon(Icon value) {
        return new Item(label, value, accelerator, onPress, checked, disabled, submenu,
                reservesLead, onHovered, attributes);
    }

    /// This item showing `text` as its accelerator — the display half of §8's
    /// accelerator, right-aligned. See the class note for the other half.
    public Item accelerator(String text) {
        return new Item(label, icon, text, onPress, checked, disabled, submenu, reservesLead,
                onHovered, attributes);
    }

    /// This item with a tick, or without one.
    public Item checked(boolean value) {
        return new Item(label, icon, accelerator, onPress, value, disabled, submenu,
                reservesLead, onHovered, attributes);
    }

    /// This row with room for a tick and no tick in it — a checkable command that
    /// is currently off.
    ///
    /// `checked(false)` means the same thing; this exists because "checkable"
    /// reads better than "checked, false" where what is being said is that the row
    /// *can* be ticked.
    public Item checkable() {
        return checked(false);
    }

    /// Whether this row is checkable at all — see [#checked].
    public boolean isCheckable() {
        return checked != null;
    }

    /// Used by [Menus] to tell a row whether its menu reserves a leading column.
    Item reservingLead(boolean value) {
        return new Item(label, icon, accelerator, onPress, checked, disabled, submenu, value,
                onHovered, attributes);
    }

    public Item disabled(boolean value) {
        return new Item(label, icon, accelerator, onPress, checked, value, submenu,
                reservesLead, onHovered, attributes);
    }

    /// This item with a submenu under it.
    public Item submenu(Widget... items) {
        return new Item(label, icon, accelerator, onPress, checked, disabled, List.of(items),
                reservesLead, onHovered, attributes);
    }

    /// This item running `action` when chosen — used by [Menus] to wrap an
    /// author's command in "and close the menu", which is what choosing a command
    /// does everywhere.
    public Item pressing(Runnable action) {
        return new Item(label, icon, accelerator, action, checked, disabled, submenu,
                reservesLead, onHovered, attributes);
    }

    /// Used by [Menus] to hand an item the way to tell its menu that the pointer
    /// has arrived — see [#onHovered].
    public Item hovering(Runnable onHovered) {
        return new Item(label, icon, accelerator, onPress, checked, disabled, submenu,
                reservesLead, onHovered, attributes);
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
                reservesLead, onHovered, value);
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
        return Boolean.TRUE.equals(checked);
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
        } else if (event.kind() == PointerEvent.Kind.ENTERED) {
            // **Every** row, not only the ones with children. A submenu closes
            // when the pointer moves to a sibling, and the menu is the only thing
            // that can close it — so every row says "I am the one now" and the
            // menu decides what that means (ADR-0112).
            hovered();
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
            hovered();
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
            hovered();
        } else if (onPress != null) {
            onPress.run();
        }
    }

    private void hovered() {
        if (onHovered != null) {
            onHovered.run();
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
    /// The leading column, when the menu this row is in has anything to put in
    /// one — a tick or an icon.
    ///
    /// **Per menu, not per row.** Every row in one menu agrees, so labels line up
    /// — and a menu with nothing checkable and no icons has no column at all,
    /// where before every label in the toolkit was indented for a tick nobody
    /// could ever have (ADR-0113).
    ///
    /// A submenu's own items are **not** children of this node: they are a
    /// separate widget tree in a separate window, built by [Menus] when the
    /// submenu opens.
    @Override
    public List<Widget> children() {
        var parts = new ArrayList<Widget>(2);
        if (reservesLead) {
            // One column, holding a tick *or* an icon — never both, which is what
            // made a row with an icon sit further in than the rows above it
            // (ADR-0113).
            parts.add(new ItemLead(isChecked(), icon));
        }
        if (hasSubmenu()) {
            // The only thing that tells a row which opens something from a row
            // which does something (ADR-0113).
            parts.add(new ItemChevron());
        }
        return List.copyOf(parts);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var content = new ArrayList<Box>(5);
        // The leading column, when there is one — always child 0, so the chevron
        // is whatever comes after it. The icon is *in* it rather than beside it.
        if (reservesLead && !children.isEmpty()) {
            content.add(children.getFirst());
        }
        if (!label.isEmpty()) {
            // **It does not shrink**, and a menu row is the place that matters.
            // A box with text is a measured leaf, so a row squeezed narrower than
            // its content does not clip the label -- it *wraps* it, and a
            // two-line label in a fixed-height row is centred to the row's top
            // edge. That is what "the item after the iconed one is aligned to the
            // top" turned out to be: the widest row wraps first, and the widest
            // row is rarely the one with the icon
            // ([ADR-0148](../../../../../../../book/src/adr/0148-a-menu-row-does-not-wrap.md)).
            //
            // The cost is `option`'s, documented there and taken for the same
            // reason: a label longer than the room for it overflows, because
            // nothing in this toolkit clips. A menu one word too wide is legible;
            // a menu of two-line rows is not.
            content.add(Box.text(context.paragraph(style, label), style.color()).shrink(0));
        }
        // Grows, so everything after it is pushed to the far edge.
        content.add(Box.of().grow(1));
        if (accelerator != null && !accelerator.isEmpty()) {
            // The same, and more obviously: `Ctrl+Shift+K` broken over two lines
            // is not an accelerator anybody can read.
            content.add(Box.text(context.paragraph(style, accelerator), style.color()).shrink(0));
        }
        if (hasSubmenu() && !children.isEmpty()) {
            content.add(children.getLast());
        }
        return Box.of().style(style).children(content.toArray(Box[]::new));
    }

    /// Builds an `item` from markup.
    ///
    /// A nested `item` is a submenu, which is the only thing a menu item can
    /// contain — so nesting *is* the syntax and there is no `submenu` node to
    /// forget. `reservesCheck` and the hover callback are the menu's to supply on
    /// every open, which is why neither is an attribute.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Item(Wiring.label(node), wiring.icon(node),
                node.stringProperty("accelerator"),
                wiring.action(node, "press"),
                // Three states: `checked=#true` is on, `checked=#false` is a
                // checkable row that is off, and no attribute at all is a row
                // that is not checkable -- which is what decides whether its menu
                // reserves a tick column (ADR-0113).
                node.property("checked").isPresent() ? node.booleanProperty("checked") : null,
                Wiring.disabled(node), children, false, null,
                Attributes.of(node));
    }
}
