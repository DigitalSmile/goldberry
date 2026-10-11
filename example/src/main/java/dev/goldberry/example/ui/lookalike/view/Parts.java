package dev.goldberry.example.ui.lookalike.view;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.pressable.Pressable;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.core.icon.IconView;
import dev.goldberry.widgets.text.Text;

/// The four shapes every part of the player is written in, so a view reads as
/// its layout rather than as `Attributes.NONE.classes(…)` over and over.
final class Parts {

    private Parts() {}

    /// `classes`, and `flag` after them when `on` — a row's `selected`, a
    /// title's `now`.
    static String[] classes(boolean on, String flag, String... classes) {
        return on ? Stream.concat(Arrays.stream(classes), Stream.of(flag)).toArray(String[]::new) : classes;
    }

    /// A line of text with classes.
    static Text text(String content, String... classes) {
        return new Text(content, Attributes.NONE.classes(classes));
    }

    /// A row of `children` with classes.
    static Row row(List<? extends Widget> children, String... classes) {
        return new Row(List.copyOf(children), Attributes.NONE.classes(classes));
    }

    /// A column of `children` with classes.
    static Column column(List<? extends Widget> children, String... classes) {
        return new Column(List.copyOf(children), Attributes.NONE.classes(classes));
    }

    /// A bundled icon with classes, drawn in its box's `color`.
    static IconView icon(String name, String... classes) {
        return new IconView(name).withAttributes(Attributes.NONE.classes(classes));
    }

    /// An icon that does something: a pressable named for a screen reader, since
    /// the picture alone says nothing to one.
    ///
    /// @param name    what a reader announces, and the tooltip
    /// @param icon    the bundled icon's name
    /// @param action  what a press does
    /// @param classes `icon-button` and whatever else the stylesheet needs
    static Pressable iconButton(String name, String icon, Runnable action, String... classes) {
        return new Pressable(name, action, false, List.of(icon(icon)), Attributes.NONE.classes(classes)).tooltip(name);
    }
}
