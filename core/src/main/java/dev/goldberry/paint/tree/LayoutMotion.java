package dev.goldberry.paint.tree;

import dev.goldberry.css.value.Affine;
import dev.goldberry.motion.layout.LayoutTracks;
import dev.goldberry.motion.layout.Place;
import dev.goldberry.motion.layout.Stretch;

/// Layout animation over a render tree: after layout, every box with a
/// `view-transition-name` is given the move that draws it where [LayoutTracks]
/// says, rather than where Yoga put it.
///
/// ## The move is a matrix on the node
///
/// Each named node gets a flip, a scale and a translation in its own
/// coordinates, which [RenderTree#compose] applies after the box's own
/// `transform` and before its ancestors'. Painting, hit testing, damage and
/// the culling ink all compose through there, so a box mid-move is drawn,
/// clicked and repainted where it is seen.
///
/// Places are measured in the untransformed layout's coordinates, which is
/// where Yoga's answers are. A named node under another one that is moving
/// takes the ancestor's move off its own, so each named box travels its own
/// path from where it was drawn to where it is laid out, at its own pace. A box
/// with no name moves with its parent, as anything inside a transformed box
/// does.
///
/// ## Free for a tree without names
///
/// Reconciling visits every node anyway, so it notes whether any box is named
/// ([#named()]). A tree that has none, and had none on the last pass, is not
/// walked at all.
final class LayoutMotion {

    private final LayoutTracks tracks = new LayoutTracks();
    private boolean sawName;
    private boolean walked;

    /// Before a reconcile.
    void beginReconcile() {
        sawName = false;
    }

    /// A reconciled box has a `view-transition-name`.
    void named() {
        sawName = true;
    }

    /// Gives every named node its move for `nowMillis`.
    ///
    /// @param nowMillis the frame's time, or [LayoutTracks#NO_CLOCK] to put
    ///                  every box straight where it is laid out
    /// @return whether any node's move changed, which means the ink [RenderObject#settle]
    ///         measured is out of date along those nodes' paths
    boolean step(RenderObject root, double nowMillis) {
        if (!sawName && !walked) {
            return false;
        }
        // Walked once more after the last name goes, to take every flip off.
        walked = sawName;
        tracks.begin();
        var changed = walk(root, 0, 0, Stretch.IDENTITY, nowMillis);
        tracks.end();
        return changed;
    }

    /// Whether a box is still travelling.
    boolean isMoving() {
        return tracks.isMoving();
    }

    private boolean walk(RenderObject object, double parentLeft, double parentTop, Stretch above, double nowMillis) {
        var layout = object.layout();
        var left = parentLeft + layout.left();
        var top = parentTop + layout.top();
        var box = object.box();
        var named = box == null ? null : box.transform().viewTransition();
        var below = above;
        var flip = Affine.IDENTITY;
        if (named != null) {
            var laidOut = new Place(left, top, layout.width(), layout.height());
            var drawn = tracks.place(named, laidOut, nowMillis);
            // Where the box is to be drawn, as one map of the page; what the
            // moving ancestors already do to it, taken off; and the rest
            // expressed in the box's own coordinates, whose origin is its
            // laid-out corner.
            var onto = Stretch.onto(laidOut, drawn);
            var own = onto.then(above.inverse());
            var corner = new Stretch(1, 1, left, top);
            var local = corner.then(own).then(corner.inverse());
            flip = local.isIdentity() ? Affine.IDENTITY : local.affine();
            below = onto;
        }
        var changed = object.flip(flip);
        for (var child : object.children()) {
            if (walk(child, left, top, below, nowMillis)) {
                object.descendantMoved();
                changed = true;
            }
        }
        return changed;
    }
}
