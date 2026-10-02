package dev.goldberry.widgets.text;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.icon.Icon;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.input.key.Key;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.semantics.Role;
import dev.goldberry.widget.semantics.Semantics;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// What a [Link] draws and answers to: the word, the icon after it when it
/// opens outside, and the press.
///
/// The CSS type `link` is this node. `visited` and `external` are classes; the
/// underline on hover is the stylesheet's, through `text-decoration`.
///
/// @param label      the word
/// @param onPress    what a press does, or null for a word that only looks
///                   like a link
/// @param external   whether it opens outside the window
/// @param visited    whether the application says it has been followed
/// @param icon       the external-link icon, when [#external]
/// @param attributes the link's, verbatim
record LinkText(
        String label,
        @Nullable Runnable onPress,
        boolean external,
        boolean visited,
        @Nullable Icon icon,
        Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Handles, Semantics {

    @Override
    public String cssType() {
        return "link";
    }

    @Override
    public @Nullable String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        var classes = new HashSet<>(attributes.classes());
        if (visited) {
            classes.add("visited");
        }
        if (external) {
            classes.add("external");
        }
        return Set.copyOf(classes);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    /// Focusable and in the Tab order, unlike `text`, when it goes somewhere.
    /// A word with nothing behind it is not a Tab stop.
    @Override
    public boolean isFocusable() {
        return onPress != null;
    }

    @Override
    public void onPointer(PointerEvent event) {
        if (event.kind() == PointerEvent.Kind.CLICKED && onPress != null) {
            onPress.run();
            event.consume();
        }
    }

    /// `Enter` activates a link; `Space` scrolls a page in every browser and
    /// does the same here.
    @Override
    public void onKey(KeyEvent event) {
        if (event.kind() != KeyEvent.Kind.PRESSED
                || event.isRepeat()
                || !event.modifiers().none()
                || onPress == null) {
            return;
        }
        if (event.key() == Key.ENTER) {
            onPress.run();
            event.consume();
        }
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var content = new ArrayList<Box>(2);
        content.add(Box.text(context.paragraph(style, label), style.color()));
        if (icon != null) {
            content.add(Box.icon(icon, style.color()));
        }
        return Box.of().style(style).children(content.toArray(Box[]::new));
    }

    /// [Role#BUTTON]: [Role] has no `link`, and "something you press to make
    /// it happen" is true of a link in every way that matters to somebody
    /// listening. `crumb` answers the same.
    @Override
    public Role role() {
        return Role.BUTTON;
    }

    /// The word and, for an external link, that it opens outside the window,
    /// because a colour cannot say that.
    @Override
    public String accessibleName() {
        return external ? label + ", opens outside this window" : label;
    }
}
