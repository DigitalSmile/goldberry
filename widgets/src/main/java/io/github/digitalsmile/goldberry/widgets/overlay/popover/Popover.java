package io.github.digitalsmile.goldberry.widgets.overlay.popover;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Attributed;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// The floating panel a popup draws — `docs/core-widgets.md` §7's `popover`, and
/// what §7 calls "the primitive under menus, dropdowns, `date-picker`,
/// `color-picker` and autocomplete".
///
/// ```kdl
/// popover {
///     button "Rename…"
///     button "Duplicate"
///     separator
///     button class="danger" "Delete"
/// }
/// ```
///
/// ```java
/// host.popup(new Popover(items), "menu-button", Placement.BELOW)
///     .ifPresent(open -> this.menu = open);
/// ```
///
/// ## It is the panel, not the opening
///
/// A popover is two things everywhere else: a surface, and the machinery that
/// decides where that surface goes and when it goes away. Here the second half is
/// **not a widget** — it is `Host.popup`, which measures the content, applies
/// `Placement`'s flip and shift against the display's work area, opens a platform
/// window and light-dismisses it. That machinery serves a `tooltip`, a `select`
/// and a `menu` equally, none of which is a popover, so it does not belong inside
/// one ([ADR-0104]).
///
/// What is left is worth a widget on its own: the surface, the edge, the radius,
/// the elevation and the padding that make a floating panel read as floating.
/// Being a widget is also what makes it themeable, densities included, and what
/// lets a document write one.
///
/// ## It is the root of its own tree
///
/// A popup's contents are a root and not a descendant, so **nothing inherits into
/// this node** — not `color`, not `font-size`, and no descendant selector from
/// the window that opened it. That is why `popover` carries its own surface and
/// its own foreground rather than relying on an ancestor for either
/// ([ADR-0103]).
///
/// The `class="menu"` variant is the one this ships with: column direction, no
/// padding of its own beyond a hairline, and items that fill the width.
///
/// @param children   what is in the panel
/// @param attributes `id` and `class`, exactly as on the primitives
public record Popover(List<Widget> children, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Attributed<Popover> {

    public Popover {
        children = List.copyOf(children == null ? List.of() : children);
        attributes = attributes == null ? Attributes.NONE : attributes;
        Objects.requireNonNull(children, "children");
    }

    public Popover(Widget... children) {
        this(List.of(children), Attributes.NONE);
    }

    @Override
    public String cssType() {
        return "popover";
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
    public Popover withAttributes(Attributes value) {
        return new Popover(children, value);
    }

    @Override
    public List<Widget> children() {
        return children;
    }

    /// Sized by its content, and **deliberately not growing**.
    ///
    /// A popup window is created at exactly this panel's measured size, so being
    /// content-sized and filling the window are the same thing here. Growing is
    /// not: `flex-grow` on a root with a definite available size grows *into* it,
    /// so a growing panel measures as whatever it was measured against — which is
    /// how the first version of this opened a menu the size of the whole window
    /// (see [io.github.digitalsmile.goldberry.layout.RenderTree#measure]).
    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }
}
