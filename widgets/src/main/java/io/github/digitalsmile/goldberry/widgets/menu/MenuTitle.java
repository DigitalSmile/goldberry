package io.github.digitalsmile.goldberry.widgets.menu;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.input.Handles;
import io.github.digitalsmile.goldberry.input.Key;
import io.github.digitalsmile.goldberry.input.KeyEvent;
import io.github.digitalsmile.goldberry.input.PointerEvent;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/// One heading in a [MenuBar] — `File`, `Edit`, `View`.
///
/// A part, not a node anybody writes: a document declares a bar of `item`s and
/// the bar turns each into one of these, the way `radio-group` turns its
/// children into wired `radio`s
/// ([ADR-0073](../../../../../../../book/src/adr/0073-a-composite-is-one-tab-stop.md)).
/// It is a separate widget from [Item] rather than a flag on it because the two
/// answer the keyboard differently, and that difference is the whole of what
/// makes a bar a bar:
///
/// | Key | In a menu ([Item]) | In a bar (here) |
/// |---|---|---|
/// | `Down` | move to the next row | **open this menu** |
/// | `Right` | open this row's submenu | move to the next heading |
/// | `Left` | nothing | move to the previous heading |
///
/// `Right` and `Left` are the scope's, not this widget's: a bar is a
/// **horizontal** focus scope where a menu is a vertical one
/// ([ADR-0078](../../../../../../../book/src/adr/0078-a-focus-scope-has-an-axis.md)),
/// so traversal comes free and the two arrows the bar does not spend on it are
/// `Up` and `Down`.
///
/// ## The pointer, and why hovering is conditional
///
/// A heading opens on a **click**, and once a menu is showing every other
/// heading opens on hover — which is what every desktop menu bar does and the
/// reason moving along the bar with a menu down does not need a click per menu.
/// Hovering a heading when nothing is open does nothing at all: a bar that
/// dropped a menu because the pointer crossed it on the way to somewhere else
/// would be unusable.
///
/// The bar decides which of those it is; this reports the hover unconditionally
/// for [Item]'s reason — the widget that knows whether anything is open is the
/// one that owns the popup.
///
/// @param label      what it says
/// @param icon       an optional icon before the label
/// @param disabled   whether it refuses to open, and matches `:disabled`
/// @param attributes the `id` and classes, including `open` while this heading's
///                   menu is showing
/// @param onActivate what a click, `Enter`, `Space` or `Down` does
/// @param onHovered  that the pointer has arrived, which the bar reads as
///                   "switch to me" only when something is already open
record MenuTitle(
        String label, Icon icon, boolean disabled, Attributes attributes,
        Runnable onActivate, Runnable onHovered)
        implements Widget.Leaf, Styled, Paints, Handles {

    @Override
    public String cssType() {
        return "menu-title";
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
    public boolean isFocusable() {
        return !disabled;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public void onPointer(PointerEvent event) {
        if (disabled) {
            return;
        }
        if (event.kind() == PointerEvent.Kind.CLICKED) {
            activate();
            event.consume();
        } else if (event.kind() == PointerEvent.Kind.ENTERED) {
            hovered();
        }
    }

    /// `Enter`, `Space` and **`Down`** open. `Down` is the one that is not
    /// [Item]'s: in a bar the menu is below, so the arrow that points at it is
    /// the arrow that opens it, and it is free because the scope is horizontal.
    @Override
    public void onKey(KeyEvent event) {
        if (disabled || event.kind() != KeyEvent.Kind.PRESSED || event.isRepeat()
                || !event.modifiers().none()) {
            return;
        }
        if (event.key() == Key.ENTER || event.key() == Key.SPACE || event.key() == Key.DOWN) {
            activate();
            event.consume();
        }
    }

    private void activate() {
        if (onActivate != null) {
            onActivate.run();
        }
    }

    private void hovered() {
        if (onHovered != null) {
            onHovered.run();
        }
    }

    /// An optional icon and the label, and nothing else: a heading has no tick
    /// column, no accelerator and no chevron, which is three quarters of what an
    /// [Item] row is made of.
    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var content = new ArrayList<Box>(2);
        if (icon != null) {
            content.add(Box.icon(icon, style.color()).shrink(0));
        }
        if (!label.isEmpty()) {
            // Does not shrink, for the reason a menu row does not
            // ([ADR-0148](../../../../../../../book/src/adr/0148-a-menu-row-does-not-wrap.md)):
            // a heading squeezed narrower than its label would wrap it, and a
            // two-line `File` in a one-line bar is worse than a bar too wide.
            content.add(Box.text(context.paragraph(style, label), style.color()).shrink(0));
        }
        return Box.of().style(style).children(content.toArray(Box[]::new));
    }
}
