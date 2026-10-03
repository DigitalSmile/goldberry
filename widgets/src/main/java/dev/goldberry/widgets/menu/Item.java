package dev.goldberry.widgets.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.icon.Icon;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.kdl.KdlNode;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.semantics.Role;
import dev.goldberry.widget.semantics.Semantics;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;

/// One row of a [Menu]: a label, an optional icon, an accelerator to show, a
/// tick, and either a command or a submenu.
///
/// ```kdl
/// item press="app.save" icon="save" accelerator="Ctrl+S" "Save"
/// item press="app.wrap" checked=#true "Word wrap"
/// item "Recent" { item press="app.recent-1" "notes.txt" }
/// ```
///
/// In Java, `new Item("Save", actions::save).icon(save).accelerator("Ctrl+S")`;
/// `submenu(Widget...)`, `checked(boolean)`, `checkable()` and
/// `disabled(boolean)` set the rest.
///
/// A nested `item` is a submenu, and that is the only thing an item can
/// contain: there is no `submenu` node. A row cannot be both a command and a
/// heading. `checked` has three states: `#true` shows a tick, `#false` is a
/// checkable row that is off, and no attribute at all is a row that is not
/// checkable, which is what decides whether its menu reserves room for a tick.
/// A row with neither a label nor an icon is refused, because nothing could
/// read it out.
///
/// An item does not open its own submenu or close the menu it is in: that
/// needs a `Host`, and a widget is a value. [Menus] hands each row a
/// [MenuSignals] when it opens the menu, and the row reports what happened to
/// it. Nor does an item register its accelerator; the text is shown
/// right-aligned, and a `menubar` binds it while the bar is mounted.
///
/// Read more: [Menus and the tray](https://goldberry.dev/docs/components/menus.html#item).
///
/// @param label       the command's name
/// @param icon        an optional icon before it
/// @param accelerator the shortcut to *show*, right-aligned, or null
/// @param onPress     what it does. Null for an item that opens a submenu and
///                    nothing else
/// @param checked     `TRUE` or `FALSE` for a **checkable** row, and `null` for
///                    one that is not checkable at all. Three states rather than
///                    two, because "unchecked" and "not a checkbox" are different
///                    things and only the second means "reserve no room for a
///                    tick"
/// @param reservesLead whether this row leaves room for the leading column — the
///                    tick, or the icon, or nothing. Supplied by [Menus] and the
///                    same for every row in one menu: a column that appeared only
///                    on the rows that had something to put in it would step the
///                    labels in and out down the list ([ItemLead])
/// @param disabled    whether it refuses
/// @param submenu     the items of its submenu, or empty
/// @param signals     how it tells the menu what happened to it — the pointer
///                    arriving, a keyboard `Right`, a `Left`. Supplied by
///                    [Menus], never by an author, and never null: an unwired
///                    item holds [MenuSignals#NONE]
/// @param attributes  `id` and `class`, exactly as on the primitives
@Markup("item")
public record Item(
        String label,
        @Nullable Icon icon,
        @Nullable String accelerator,
        @Nullable Runnable onPress,
        @Nullable Boolean checked,
        boolean disabled,
        List<Widget> submenu,
        boolean reservesLead,
        MenuSignals signals,
        Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Handles, Attributed<Item>, Semantics {

    /// The canonical constructor, written out because `submenu`, `signals` and `attributes` take null
    /// for a default.
    public Item(
            String label,
            @Nullable Icon icon,
            @Nullable String accelerator,
            @Nullable Runnable onPress,
            @Nullable Boolean checked,
            boolean disabled,
            @Nullable List<Widget> submenu,
            boolean reservesLead,
            @Nullable MenuSignals signals,
            @Nullable Attributes attributes) {
        Objects.requireNonNull(label, "label");
        submenu = List.copyOf(submenu == null ? List.of() : submenu);
        signals = signals == null ? MenuSignals.NONE : signals;
        if (label.isEmpty() && icon == null) {
            throw new IllegalArgumentException("a menu item with neither a label nor an icon has nothing to read out");
        }
        attributes = attributes == null ? Attributes.NONE : attributes;
        this.label = label;
        this.icon = icon;
        this.accelerator = accelerator;
        this.onPress = onPress;
        this.checked = checked;
        this.disabled = disabled;
        this.submenu = submenu;
        this.reservesLead = reservesLead;
        this.signals = signals;
        this.attributes = attributes;
    }

    /// A command.
    public Item(String label, @Nullable Runnable onPress) {
        this(label, null, null, onPress, null, false, List.of(), false, MenuSignals.NONE, Attributes.NONE);
    }

    /// A command with nothing behind it yet.
    public Item(String label) {
        this(label, null);
    }

    /// This item with an icon before its label.
    public Item icon(Icon value) {
        return new Item(
                label, value, accelerator, onPress, checked, disabled, submenu, reservesLead, signals, attributes);
    }

    /// This item showing `text` as its accelerator, right-aligned. Showing is all
    /// an item does with it; a `menubar` binds the key while it holds the row.
    public Item accelerator(String text) {
        return new Item(label, icon, text, onPress, checked, disabled, submenu, reservesLead, signals, attributes);
    }

    /// This item with a tick, or without one.
    public Item checked(boolean value) {
        return new Item(label, icon, accelerator, onPress, value, disabled, submenu, reservesLead, signals, attributes);
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
        return new Item(label, icon, accelerator, onPress, checked, disabled, submenu, value, signals, attributes);
    }

    public Item disabled(boolean value) {
        return new Item(label, icon, accelerator, onPress, checked, value, submenu, reservesLead, signals, attributes);
    }

    /// This item with a submenu under it.
    public Item submenu(Widget... items) {
        return new Item(
                label,
                icon,
                accelerator,
                onPress,
                checked,
                disabled,
                List.of(items),
                reservesLead,
                signals,
                attributes);
    }

    /// This item running `action` when chosen — used by [Menus] to wrap an
    /// author's command in "and close the menu", which is what choosing a command
    /// does everywhere.
    public Item pressing(Runnable action) {
        return new Item(
                label, icon, accelerator, action, checked, disabled, submenu, reservesLead, signals, attributes);
    }

    /// Used by [Menus] to hand an item the way to talk back to the menu it is in
    /// — see [MenuSignals].
    public Item signalling(MenuSignals value) {
        return new Item(label, icon, accelerator, onPress, checked, disabled, submenu, reservesLead, value, attributes);
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
    public @Nullable String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public Item withAttributes(Attributes value) {
        return new Item(label, icon, accelerator, onPress, checked, disabled, submenu, reservesLead, signals, value);
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
    /// The hover-intent delay before a submenu opens is [Menus]'s: the timer
    /// belongs to the event loop and a widget has no way to reach it.
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
            // menu decides what that means.
            signals.hovered();
        }
    }

    /// `Enter` and `Space` activate. `Right` opens a submenu or leaves for the
    /// next menu; `Left` goes back. Those two are the arrows a menu does not spend
    /// on traversal, because `Up` and `Down` already are.
    ///
    /// **None of it happens here.** Each key becomes a [MenuSignals] call, and
    /// what "back" means — close this submenu, or move along the bar — is the
    /// menu's to decide: only it knows whether there is a menu to the left of it.
    @Override
    public void onKey(KeyEvent event) {
        if (disabled
                || event.kind() != KeyEvent.Kind.PRESSED
                || event.isRepeat()
                || !event.modifiers().none()) {
            return;
        }
        switch (event.key()) {
            case ENTER, SPACE -> {
                activate();
                event.consume();
            }
            // Opening the branch under the cursor beats leaving the menu it is
            // in, so a row with children takes this key and a row without it
            // passes it on to whatever is holding the menu.
            case RIGHT -> {
                if (hasSubmenu()) {
                    signals.open();
                } else {
                    signals.forward();
                }
                event.consume();
            }
            case LEFT -> {
                signals.back();
                event.consume();
            }
            default -> {}
        }
    }

    /// Opening a submenu when there is one, running the command when there is
    /// not.
    ///
    /// An item cannot be both: a command that is also a heading has no meaning,
    /// and every desktop menu agrees.
    private void activate() {
        if (hasSubmenu()) {
            // Now, not after the pointer's hover-intent delay: `Enter` on a row
            // that leads somewhere is as deliberate as a key gets.
            signals.open();
        } else if (onPress != null) {
            onPress.run();
        }
    }

    /// The row's parts: the leading column, when the menu this row is in has
    /// anything to put in one — a tick or an icon — and the chevron, when the
    /// row leads to a submenu.
    ///
    /// The column is reserved **per menu, not per row**. Every row in one menu
    /// agrees, so labels line up, and a menu with nothing checkable and no icons
    /// has no column at all. See [ItemLead].
    ///
    /// A submenu's own items are **not** children of this node: they are a
    /// separate widget tree in a separate window, built by [Menus] when the
    /// submenu opens.
    @Override
    public List<Widget> children() {
        var parts = new ArrayList<Widget>(2);
        if (reservesLead) {
            // One column, holding a tick *or* an icon — never both, so a row with
            // an icon does not sit further in than the rows above it.
            parts.add(new ItemLead(isChecked(), icon));
        }
        if (hasSubmenu()) {
            // The only thing that tells a row which opens something from a row
            // which does something.
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
            // The label shrinks, and it is cut rather than wrapped: the stylesheet
            // writes `white-space: nowrap` on `item`, so the paragraph reports its
            // natural width whatever width Yoga offers, the row may shrink
            // without the label wrapping, and `text-overflow: ellipsis` marks
            // where it was cut. A wrapped two-line label in a fixed-height row
            // would sit against the row's top edge.
            //
            // The flow comes off the *row's* style because the label is an
            // anonymous box: `render` applies the style to the box it returns,
            // and nothing applies it to a child. `white-space` inherits in the
            // cascade and this is the same inheritance one level lower down.
            content.add(Box.text(context.paragraph(style, label), style.color(), style.textFlow()));
        }
        // Grows, so everything after it is pushed to the far edge. A growing box
        // rather than `text-align`, because the room is shared out between five
        // boxes -- the lead, the label, the gap, the accelerator and the chevron.
        content.add(Box.of().grow(1));
        if (accelerator != null && !accelerator.isEmpty()) {
            // This one does not shrink: `Ctrl+Shift+K` with its tail cut off is
            // not an accelerator anybody can read, so a cramped row spends its
            // missing pixels on the label -- which has an ellipsis to say so --
            // and never on the shortcut.
            content.add(Box.text(context.paragraph(style, accelerator), style.color(), style.textFlow())
                    .shrink(0));
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
    @SuppressWarnings("unused")
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Item(
                Wiring.label(node),
                wiring.icon(node),
                node.stringProperty("accelerator"),
                wiring.action(node, "press"),
                // Three states: `checked=#true` is on, `checked=#false` is a
                // checkable row that is off, and no attribute at all is a row
                // that is not checkable -- which is what decides whether its menu
                // reserves a tick column.
                node.property("checked").isPresent() ? node.booleanProperty("checked") : null,
                Wiring.disabled(node),
                children,
                false,
                null,
                Attributes.of(node));
    }

    @Override
    public Role role() {
        return Role.MENU_ITEM;
    }

    @Override
    public String accessibleName() {
        return label;
    }
}
