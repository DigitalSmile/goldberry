package io.github.digitalsmile.goldberry.widgets.overlay.message;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// What a [Message] says, and the links under it — a **part**.
///
/// A column, and the only part of a banner that grows: the glyph and the × are
/// both fixed, so this is what takes the width and what the words wrap inside.
///
/// The words are a **child box** rather than text on this node, which is
/// `badge`'s split and here it buys the thing a banner needs: a box with text is
/// a measured leaf, and Yoga never lays a measured node's children out — so a
/// body that held its own text could not also hold the action row underneath it.
///
/// @param text    the words, with hard newlines already meaning line breaks
/// @param actions the author's links, or empty
record MessageBody(String text, List<Widget> actions) implements Widget.Leaf, Styled, Paints {

    MessageBody {
        actions = List.copyOf(actions == null ? List.of() : actions);
    }

    @Override
    public String cssType() {
        return "message-body";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    /// The action row, when there is one.
    ///
    /// **Absent rather than present and empty**, which is the opposite of what
    /// `field-message` does and for a reason that does not apply here: a field's
    /// message appears and disappears as the value changes, so a node that came
    /// and went could never transition. A banner's actions are decided when the
    /// banner is written and do not change under it.
    @Override
    public List<Widget> children() {
        return actions.isEmpty() ? List.of() : List.of(new MessageActions(actions));
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        var content = new ArrayList<Box>(boxes.size() + 1);
        content.add(Box.text(context.paragraph(style, text), style.color()));
        content.addAll(boxes);
        return Box.of().style(style).children(content.toArray(Box[]::new));
    }

    /// The row the author's links sit in — a part of a part, so that the gap
    /// between two links and the gap between the words and the links are two
    /// declarations rather than one compromise.
    record MessageActions(List<Widget> children) implements Widget.Leaf, Styled, Paints {

        MessageActions {
            children = List.copyOf(children == null ? List.of() : children);
        }

        @Override
        public String cssType() {
            return "message-actions";
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public List<Widget> children() {
            return children;
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            return Box.of().style(style).children(boxes.toArray(Box[]::new));
        }
    }
}
