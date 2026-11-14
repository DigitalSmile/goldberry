package dev.goldberry.widgets.form.richinput;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.icon.Icon;
import dev.goldberry.paint.Box;
import dev.goldberry.text.edit.keys.FormatCommand;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.semantics.Role;
import dev.goldberry.widget.semantics.Semantics;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.controls.button.Button;

/// The node a stylesheet calls `format-bar`: a row of icon buttons over a
/// `rich-input`'s selection, each turning a style on or off.
///
/// A button whose style covers all of the selection carries the class `on`.
/// Opened by the field in a panel that never takes the keyboard, so the
/// selection stays where it was while a button is pressed.
///
/// @param buttons what the bar offers
/// @param active  the commands that are on for the selection now
/// @param icons   the icon of each button, by name, owned by the field
/// @param onPress what a press does
record FormatBar(
        List<FormatButton> buttons, Set<FormatCommand> active, Map<String, Icon> icons, Consumer<FormatCommand> onPress)
        implements Widget.Leaf, Styled, Paints, Semantics {

    /// The class an active button carries.
    static final String ON = "on";

    FormatBar {
        buttons = List.copyOf(buttons);
        active = Set.copyOf(active);
        icons = Map.copyOf(icons);
        Objects.requireNonNull(onPress, "onPress");
    }

    @Override
    public String cssType() {
        return "format-bar";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public List<Widget> children() {
        var children = new ArrayList<Widget>(buttons.size());
        for (var button : buttons) {
            var command = button.command();
            var classes = active.contains(command) ? Set.of("ghost", ON) : Set.of("ghost");
            children.add(new Button(
                    "",
                    icons.get(button.icon()),
                    () -> onPress.accept(command),
                    false,
                    new Attributes("format-" + button.icon(), classes, null).name(button.label())));
        }
        return children;
    }

    @Override
    public Role role() {
        return Role.GROUP;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
