package io.github.digitalsmile.goldberry.example.ui;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// One cell of the [IconsScreen]'s sheet: the glyph, and the name you would type
/// to get it.
///
/// ## Why the application declares a widget at all
///
/// There is no `icon` widget in the catalog — an icon reaches the screen as a
/// [Box#icon] inside whatever is drawing it, which is what `button`, `tab`,
/// `crumb` and `chip` all do. That is the right shape for the toolkit: an `Icon`
/// is a value an application holds, and a widget that looked one up by name would
/// be a widget with a registry inside it.
///
/// It is the wrong shape for a *sheet of icons*, which needs a thousand cells
/// that are nothing but a glyph and a caption. So the showcase writes the widget,
/// which is the part worth demonstrating: `Widget.Leaf` plus [Styled] plus
/// [Paints] is the whole contract, it is three methods, and an application can
/// reach for it without asking the toolkit for anything.
///
/// ## The icon is borrowed
///
/// Handed in rather than looked up, exactly as a `button`'s is: a widget is a
/// value rebuilt every frame and thrown away, and building 1544 of them per frame
/// would parse 221 KiB of path data per frame. [IconsScreen] keeps the cache
/// (ADR-0043).
///
/// ## Pressing it opens the icon's sizes
///
/// A [PressableTile]: a click, `Space` or `Enter` runs `onOpen`, which the
/// screen answers with a dialog of this icon at five sizes. The tile does not
/// open the dialog itself — a dialog needs the window, and the tile is a value
/// with no window in it (ADR-0106).
///
/// @param name   what a document would write in `icon="…"` — the caption, and
///               the thing a reader is actually here for
/// @param icon   the glyph, already at the size it is drawn at (ADR-0034)
/// @param onOpen what pressing the tile does
record IconTile(String name, Icon icon, Runnable onOpen, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, PressableTile {

    IconTile(String name, Icon icon) {
        this(name, icon, () -> {}, Attributes.NONE);
    }

    @Override
    public String accessibleName() {
        return name;
    }

    @Override
    public String cssType() {
        return "icon-tile";
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
    public Object key() {
        return attributes.key();
    }

    /// The name is a **child box** and not text on this node, for the reason
    /// every other widget in the catalog splits the two: a box with text is a
    /// measured leaf and Yoga never lays a measured node's children out, so a
    /// tile that drew its own caption would have nowhere to put the glyph.
    @Override
    public List<Widget> children() {
        return List.of(new IconTileName(name));
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var content = new java.util.ArrayList<Box>(2);
        content.add(Box.icon(icon, style.color()));
        content.addAll(children);
        return Box.of().style(style).children(content.toArray(Box[]::new));
    }

    /// The caption under the glyph — a part, styled separately because it is
    /// `caption`-ranked and muted where the glyph is not.
    record IconTileName(String text) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "icon-tile-name";
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).children(Box.text(context.paragraph(style, text), style.color()));
        }
    }
}
