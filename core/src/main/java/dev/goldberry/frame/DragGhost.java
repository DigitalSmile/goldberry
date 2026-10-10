package dev.goldberry.frame;

import java.util.ArrayList;

import org.jspecify.annotations.Nullable;

import dev.goldberry.input.drop.Dragging;
import dev.goldberry.layout.Insets;
import dev.goldberry.layout.Length;
import dev.goldberry.layout.Position;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Element;

/// The picture that follows the pointer while something is dragged: the
/// source's own box from the frame being built, faded, drawn over everything
/// else at the pointer.
///
/// Added to the root box after the widget tree has rendered and before layout,
/// so it is laid out and painted like any other box and is in no widget's
/// tree. It is [Box#scenery()], owned by nobody, so a hit test passes through
/// it to the target under the pointer: the ghost is always under the pointer,
/// and a ghost that took the pointer would be the only target a drag could
/// ever find.
///
/// Taken from **this** frame's boxes rather than remembered from the press,
/// so a source that restyles itself while it is carried, as `:active` does,
/// is carried as it looks now.
final class DragGhost {

    /// How opaque the ghost is: solid enough to read, faint enough that what
    /// it is over shows through.
    static final double OPACITY = 0.7;

    private DragGhost() {}

    /// `root` with the ghost of `drag` on top, or `root` as it was when there is
    /// nothing to draw: a keyboard drag, which has no pointer to follow, or a
    /// source that painted nothing this frame.
    static Box over(Box root, Dragging drag) {
        if (drag.fromKeyboard() || Float.isNaN(drag.pointer().x())) {
            return root;
        }
        var source = find(root, drag.source());
        if (source == null) {
            return root;
        }
        var left = drag.pointer().x() - drag.grab().x();
        var top = drag.pointer().y() - drag.grab().y();
        var ghost = source.scenery()
                .position(Position.ABSOLUTE)
                .inset(new Insets(Length.points(top), Length.UNDEFINED, Length.UNDEFINED, Length.points(left)))
                .margin(Insets.ZERO)
                .size(
                        Length.points(drag.from().width()),
                        Length.points(drag.from().height()))
                .opacity(source.opacity() * OPACITY);
        var children = new ArrayList<>(root.children());
        children.add(ghost);
        return root.children(children.toArray(Box[]::new));
    }

    /// The outermost box `source` or anything inside it painted, or null.
    ///
    /// Depth first in paint order, so a source that is a composition, with no
    /// box of its own, is drawn as its first painted child.
    private static @Nullable Box find(Box box, Element source) {
        if (box.owner() instanceof Element owner && within(owner, source)) {
            return box;
        }
        for (var child : box.children()) {
            var found = find(child, source);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static boolean within(Element node, Element ancestor) {
        for (var at = node; at != null; at = at.parent() instanceof Element parent ? parent : null) {
            if (at == ancestor) {
                return true;
            }
        }
        return false;
    }
}
