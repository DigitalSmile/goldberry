package dev.goldberry.widgets.form.richinput;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.i18n.Labels;
import dev.goldberry.i18n.ToolkitLabel;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.input.key.Key;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.semantics.Role;
import dev.goldberry.widget.semantics.Semantics;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.form.textinput.TextInput;

/// The node a stylesheet calls `link-editor`: an address field, Apply, and
/// Remove link when the words already link somewhere.
///
/// Opened by a `rich-input` over its selection, for `Ctrl+K` or the bar's Link.
/// `Enter` in the address applies, as Apply does; `Escape` closes the panel and
/// changes nothing.
///
/// @param address  the address it opens with: the link's own, or nothing
/// @param existing whether the words link somewhere already, which is what
///                 offers Remove link
/// @param onEdit   hears the address as it is typed
/// @param onApply  puts the typed address on the words
/// @param onRemove takes the link off them
record LinkEditor(String address, boolean existing, Consumer<String> onEdit, Runnable onApply, Runnable onRemove)
        implements Widget.Leaf, Styled, Paints, Handles, Semantics {

    /// The address field's id, which the panel focuses when it opens.
    static final String ADDRESS = "link-address";

    LinkEditor {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(onEdit, "onEdit");
        Objects.requireNonNull(onApply, "onApply");
        Objects.requireNonNull(onRemove, "onRemove");
    }

    @Override
    public String cssType() {
        return "link-editor";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public List<Widget> children() {
        var children = new ArrayList<Widget>(3);
        children.add(new TextInput(address, onEdit)
                .placeholder(Labels.text(ToolkitLabel.LINK_ADDRESS))
                .withAttributes(Attributes.NONE.id(ADDRESS).name(Labels.text(ToolkitLabel.LINK_ADDRESS))));
        children.add(new Button(Labels.text(ToolkitLabel.APPLY), onApply)
                .id("link-apply")
                .styled("primary"));
        if (existing) {
            children.add(new Button(Labels.text(ToolkitLabel.REMOVE_LINK), onRemove)
                    .id("link-remove")
                    .styled("ghost"));
        }
        return children;
    }

    /// `Enter` from the address field, which a field leaves to whatever is
    /// around it, applies.
    @Override
    public void onKey(KeyEvent event) {
        if (event.kind() == KeyEvent.Kind.PRESSED && event.is(Key.ENTER) && !event.isConsumed()) {
            onApply.run();
            event.consume();
        }
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
