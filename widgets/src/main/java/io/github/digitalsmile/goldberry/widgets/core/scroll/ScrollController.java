package io.github.digitalsmile.goldberry.widgets.core.scroll;

import io.github.digitalsmile.goldberry.backend.LogicalRect;

/// A handle on a [Scroll] that something outside it can hold —
/// `docs/core-widgets.md` §1's `scrollIntoView(widget)` API.
///
/// ```java
/// private final ScrollController list = new ScrollController();
/// // …
/// new Scroll(rows, ScrollAxis.VERTICAL, list, attributes)
/// ```
///
/// ## Why an object and not a node
///
/// The first attempt was a `Reveal` widget wrapping whatever wanted to be seen,
/// which reads well and was wrong: a wrapper is a **box**, and a box in a flex
/// row changes how everything in that row is sized. It broke two tab goldens and
/// two motion tests the moment it was put around a tab header — the widget did
/// what it promised and the layout underneath it was no longer the same layout
/// ([ADR-0120](../../../../../../../../book/src/adr/0120-a-widget-scrolls-itself-into-view.md)).
///
/// §1 words this as an API rather than as markup, and that turns out to be the
/// load-bearing part of the wording: an API adds no node.
///
/// ## What it can and cannot do
///
/// It can move a viewport by a distance, and it can work out what distance would
/// bring a rectangle into view. It **cannot find anything**: a controller has no
/// idea where any widget is, because nothing in the toolkit can answer that
/// except the router, and the router answers it to the widget itself
/// ([ADR-0119](../../../../../../../../book/src/adr/0119-a-widget-may-be-told-where-it-is.md)).
///
/// So the shape is: a widget that wants to be seen implements
/// [io.github.digitalsmile.goldberry.input.Located], is told its own rectangle and
/// the one that clips it, and passes both to [#reveal]. Two rectangles in, a
/// scroll out.
///
/// ## Lifetime
///
/// Create one and keep it — an application field, or a state's. A controller with
/// no viewport attached is inert rather than an error: it is perfectly ordinary
/// for a controller to exist for a frame before the `Scroll` that answers to it
/// is built, and throwing there would make the order of construction load-bearing.
public final class ScrollController {

    /// A controller with nothing attached yet, which is every controller for at
    /// least one frame — see the note on lifetime.
    public ScrollController() {
    }

    /// The attached viewport's state, or null before one is built.
    ///
    /// Package-private and set only by [ScrollState], which attaches on mount and
    /// detaches on unmount — so a controller outliving its viewport holds nothing
    /// and a stale one cannot scroll a tree that is gone.
    ScrollState attached;

    /// Whether a viewport is currently listening.
    public boolean isAttached() {
        return attached != null;
    }

    /// Moves the viewport by `dx`, `dy`, clamped to what there is to show.
    ///
    /// A **distance** and not a target, so the viewport needs to know nothing
    /// about what asked or why — and so this composes: two things asking in one
    /// frame both get what they asked for, in order, rather than the second
    /// silently winning.
    public void scrollBy(double dx, double dy) {
        if (attached != null) {
            attached.scrollBy(dx, dy);
        }
    }

    /// Scrolls the least it can to bring `self` inside `clip`.
    ///
    /// Both in the window's logical coordinates — which is exactly the pair
    /// [io.github.digitalsmile.goldberry.input.Located] hands a widget, so the
    /// call site is one line and does no arithmetic of its own.
    ///
    /// Does nothing when the rectangle is already inside, so calling it on a
    /// frame where nothing has changed is free.
    public void reveal(LogicalRect self, LogicalRect clip) {
        if (attached == null) {
            return;
        }
        var dy = distance(self.top(), self.top() + self.size().height(),
                clip.top(), clip.top() + clip.size().height());
        var dx = distance(self.left(), self.left() + self.size().width(),
                clip.left(), clip.left() + clip.size().width());
        if (dx != 0 || dy != 0) {
            attached.scrollBy(dx, dy);
        }
    }

    /// How far a viewport must move to bring `near`..`far` inside
    /// `clipNear`..`clipFar`, or 0 when it already is.
    ///
    /// Positive means further down or right. **The least it can**: a reveal that
    /// centred its target would throw away everything the user was already
    /// looking at, and §1 asks for the target to be in view rather than for it to
    /// be anywhere in particular.
    ///
    /// The near edge wins when the target is larger than the viewport, because
    /// showing the top of something too big to fit is what every browser does —
    /// the alternative shows its bottom and hides the heading.
    private static double distance(float near, float far, float clipNear, float clipFar) {
        if (near < clipNear) {
            return near - clipNear;
        }
        if (far > clipFar) {
            return Math.min(far - clipFar, near - clipNear);
        }
        return 0;
    }
}
