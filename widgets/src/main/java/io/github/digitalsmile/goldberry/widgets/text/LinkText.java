package io.github.digitalsmile.goldberry.widgets.text;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widget.semantics.Semantics;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// What a [Link] draws and answers to: the word, the icon after it when it
/// opens outside, and the press.
///
/// `link` as a **CSS type** is this node (ADR-0109). `visited` and `external`
/// are classes; the underline on hover is the stylesheet's, through §8's
/// `text-decoration` (ADR-0321).
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

    /// "Focusable and in the Tab order (unlike `text`)" — when it goes
    /// somewhere. A word with nothing behind it is not a Tab stop.
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

    /// `Enter` activates, which is §2's word for a link; `Space` scrolls a
    /// page in every browser and does the same here.
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

    /// [Role#BUTTON] — §2 asks for `link`, and [Role] has none: a role nothing
    /// can consume is a value written for a bridge that does not exist, and
    /// "something you press to make it happen" is true of a link in every way
    /// that matters to somebody listening. `crumb` answers the same, for the
    /// same reason; the word waits for the AccessKit bridge.
    @Override
    public Role role() {
        return Role.BUTTON;
    }

    /// The word — and, outside the window, that it opens there, "because
    /// 'opens outside this window' is not something a colour can convey".
    @Override
    public String accessibleName() {
        return external ? label + ", opens outside this window" : label;
    }
}
