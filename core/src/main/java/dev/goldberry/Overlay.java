package dev.goldberry;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Corner;

/// One thing floating over a window's content, and the handle that takes it away
/// again.
///
/// ```java
/// var hud = host.overlay(new Hud(), Corner.BOTTOM_END);
/// // ...
/// hud.remove();
/// ```
///
/// An overlay is a widget, a corner it is pinned to, and a margin from the two
/// edges that corner touches. It is painted after the window's content and takes
/// no space from it. It is **not** the popup path: a menu that has to escape the
/// window needs a platform window of its own, which is a [Popup]. This is the
/// half that stays inside.
///
/// Adding is one call and removing is the same object; nothing has to be found
/// again by index or by equality. Two identical HUDs in the same corner are two
/// overlays, and removing one removes the one you were handed, which is why this
/// is a class with identity rather than a record with value equality.
///
/// It lives beside [Window] rather than in the widget package because it is the
/// window's list an overlay is on and [Host] is what puts it there. The node that
/// *draws* it is [dev.goldberry.widget.root.WindowRoot].
///
/// Confined to the UI thread, like the tree it appears in.
///
/// Read more: [Overlays and popups](https://goldberry.dev/docs/guide/windows.html#overlays-and-popups).
public final class Overlay {

    /// The gap from the window's edges, in logical pixels, when a caller does not
    /// choose one.
    ///
    /// A number in Java rather than in a stylesheet because the CSS subset has no
    /// `position` property at all, which is also why `affix` is a widget and not
    /// `position: sticky`. `--gb-window-margin` is the name it will have when a
    /// floating button needs it in a rule; until something can read it there, one
    /// place is better than two.
    public static final float WINDOW_MARGIN = 16;

    private final Widget widget;
    private final Corner corner;
    private final float margin;

    /// How this overlay takes itself out of the layer that holds it, or null once
    /// it has. Set by whoever attached it.
    private @Nullable Runnable detach;

    /// An overlay laid out to **the whole window** rather than pinned to a
    /// corner.
    ///
    /// Every other overlay is content-sized and tucked into a corner, which is
    /// what a `hud` or a `toast` wants. A `tour` wants the opposite: it dims
    /// everything except one widget, so it has to cover everything.
    ///
    /// The corner is [Corner#TOP_START] and the margin zero, which with insets on
    /// all four sides is Yoga's way of saying "fill" — so this adds a flag and no
    /// new placement code.
    public static Overlay filling(Widget widget) {
        return new Overlay(widget, Corner.TOP_START, 0, true);
    }

    /// Whether this overlay fills the window — see [#filling].
    public boolean isFilling() {
        return filling;
    }

    /// An overlay that is not on a window yet, [#WINDOW_MARGIN] from its corner's
    /// edges.
    ///
    /// [Host#overlay(Widget, Corner)] is how one gets onto a window and is what
    /// almost every caller wants. This is the factory behind it, public because
    /// [dev.goldberry.widget.root.WindowRoot] is: an application assembling its
    /// own root, and every test of the overlay layer, needs to be able to say
    /// "this widget, that corner" without a launcher.
    ///
    /// A detached overlay is inert: [#remove()] does nothing and nothing draws it.
    public static Overlay of(Widget widget, Corner corner) {
        return new Overlay(widget, corner, WINDOW_MARGIN);
    }

    /// [#of(Widget, Corner)] with a chosen distance from the window's edges.
    public static Overlay of(Widget widget, Corner corner, float margin) {
        return new Overlay(widget, corner, margin);
    }

    private final boolean filling;

    private Overlay(Widget widget, Corner corner, float margin) {
        this(widget, corner, margin, false);
    }

    private Overlay(Widget widget, Corner corner, float margin, boolean filling) {
        this.filling = filling;
        this.widget = Objects.requireNonNull(widget, "widget");
        this.corner = Objects.requireNonNull(corner, "corner");
        if (!Float.isFinite(margin) || margin < 0) {
            throw new IllegalArgumentException(
                    "an overlay's margin is a distance from the window's edge, and " + margin + " is not one");
        }
        this.margin = margin;
    }

    /// The widget being floated.
    public Widget widget() {
        return widget;
    }

    /// The corner it is pinned to.
    public Corner corner() {
        return corner;
    }

    /// How far from that corner's two edges it sits, in logical pixels.
    public float margin() {
        return margin;
    }

    /// Whether this overlay is still in a window.
    public boolean isAttached() {
        return detach != null;
    }

    /// Takes it off the window. Idempotent — removing twice is what shutdown
    /// looks like when two things both think they own it.
    public void remove() {
        var run = detach;
        detach = null;
        if (run != null) {
            run.run();
        }
    }

    /// Called by the layer that accepted this overlay, with the way back out.
    void attached(Runnable detach) {
        this.detach = detach;
    }

    @Override
    public String toString() {
        return "Overlay[" + widget.getClass().getSimpleName() + " at " + corner.cssName()
                + (isAttached() ? "" : ", removed") + "]";
    }
}
