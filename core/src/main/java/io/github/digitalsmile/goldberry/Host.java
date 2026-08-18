package io.github.digitalsmile.goldberry;

import io.github.digitalsmile.goldberry.text.Fonts;
import io.github.digitalsmile.goldberry.widget.Corner;
import io.github.digitalsmile.goldberry.widget.Widget;

/// What a running [Application] can ask of the toolkit.
///
/// Handed to [Application#start], and the only handle an application needs: the
/// window, the frame loop and the trees are the launcher's, and everything an
/// application legitimately wants from them is a method here
/// ([ADR-0093](../../../../../book/src/adr/0093-an-application-is-a-root-widget.md)).
///
/// Confined to the UI thread, except [#repaint()] — see there.
public interface Host {

    /// Asks for another frame.
    ///
    /// Coalesced, so calling it ten times before the next frame costs one frame.
    /// The framework does **not** call this for you after a `setState`: it does
    /// not know whether the change is visible, and an application does.
    ///
    /// The one method here that is safe from any thread, because a value set from
    /// a virtual thread wanting to redraw is the ordinary case
    /// ([ADR-0020](../../../../../book/src/adr/0020-one-ui-thread-and-virtual-threads-behind-it.md)).
    void repaint();

    /// Re-reads [Application#stylesheets()] before the next frame and rebuilds
    /// the renderer.
    ///
    /// What a theme switch is. Separate from [#repaint()] because it is much more
    /// expensive — a new renderer throws away every resolved style — and because
    /// the common case is a repaint that changes no rule at all.
    ///
    /// Asking for a restyle also asks for a repaint; there is no reason to want
    /// one without the other.
    void restyle();

    /// Sets the window's title.
    void title(String title);

    /// Binds a window accelerator built from enums — `Mod.CTRL.and(Key.S)`.
    ///
    /// The form to reach for: a `Shortcut` built this way cannot be misspelled,
    /// where a string is only checked when it is parsed
    /// ([ADR-0095](../../../../../book/src/adr/0095-a-shortcut-is-built-from-enums.md)).
    void shortcut(io.github.digitalsmile.goldberry.input.Shortcut accelerator, Runnable action);

    /// Binds a window accelerator, written the way a menu prints it — `"Ctrl+S"`.
    ///
    /// The modifiers must match exactly, so `Ctrl+S` does not fire on
    /// `Ctrl+Shift+S`.
    ///
    /// @throws IllegalArgumentException if the text names no key this toolkit has
    void shortcut(String accelerator, Runnable action);

    /// Floats `widget` over the window's content, pinned to `corner`.
    ///
    /// The in-window overlay layer (`docs/core-widgets.md` §7): the widget is a
    /// sibling of the application's root rather than a descendant of it, so it is
    /// painted after everything and takes no space from anything. A widget
    /// already in the tree cannot do this for itself — an absolute box is placed
    /// against its own parent, so the furthest it can reach is the panel it is in.
    ///
    /// Adding, moving and removing overlays never re-parents the application:
    /// every window has an overlay layer from the first frame whether or not
    /// anything is in it, so a toast cannot cost the tree its state.
    ///
    /// ```java
    /// var hud = host.overlay(new Hud(), Corner.BOTTOM_END);
    /// // ...
    /// hud.remove();
    /// ```
    ///
    /// @param widget what to float
    /// @param corner which corner it is pinned to
    /// @return the handle that takes it away again
    Overlay overlay(Widget widget, Corner corner);

    /// [#overlay(Widget, Corner)] with a chosen distance from the window's edges,
    /// in logical pixels, instead of [Overlay#WINDOW_MARGIN].
    Overlay overlay(Widget widget, Corner corner, float margin);

    /// The painted rectangle of the node with this `id`, in the window's logical
    /// coordinates.
    ///
    /// **What a popup is anchored to.** A menu belongs under the button that
    /// opened it, and where that button *is* is a fact about the last frame:
    /// geometry exists after a paint and it is the router that has it
    /// ([ADR-0080](../../../../../book/src/adr/0080-a-value-is-measured-along-a-part.md)).
    ///
    /// By `id` rather than by element because that is how the specification asks
    /// for it — `docs/core-widgets.md` §7's `tour` "names a target by id" — and
    /// because an application holds ids, not elements. A `popover` anchoring to
    /// *itself* wants the element form, and will want it when it is built.
    ///
    /// Empty before the first frame, and for a node that was not painted: a
    /// rectangle for something invisible would be a lie a menu would then point
    /// at.
    java.util.Optional<io.github.digitalsmile.goldberry.input.HitTest.Region> anchor(String id);

    /// Opens a widget tree in a platform window of its own — a menu, a dropdown,
    /// a tooltip.
    ///
    /// The other place an overlay can go, and the one thing
    /// [#overlay(Widget, Corner)] cannot do: **leave the window**. A dropdown near
    /// the bottom of a window is routinely taller than the space below its
    /// button, and an in-window overlay would be clipped to four of its nine
    /// options.
    ///
    /// `at` is in the window's own logical coordinates — the space a hit test
    /// reports in, so a menu under the button that opened it is that button's
    /// rectangle and no conversion.
    ///
    /// **Empty is a normal answer.** Popup support belongs to the platform's
    /// video driver rather than to the request: every desktop driver has it, and
    /// a caller that gets empty falls back to [#overlay(Widget, Corner)] at the
    /// cost of being clipped to the window
    /// ([ADR-0102](../../../../../book/src/adr/0102-a-popup-is-a-window-the-platform-may-refuse.md)).
    ///
    /// The popup is light-dismissed by default: a press anywhere in this window,
    /// or `Escape`, closes it.
    ///
    /// @param content what to draw in it
    /// @param at      its top-left, in this window's logical coordinates
    /// @param size    its logical size — a popup does not size itself to its
    ///                content yet, because measuring a tree needs a surface to
    ///                measure against
    /// @return the popup, or empty if the platform has no popup windows
    java.util.Optional<Popup> popup(Widget content,
            io.github.digitalsmile.goldberry.backend.LogicalPoint at,
            io.github.digitalsmile.goldberry.backend.LogicalSize size);

    /// [#popup(Widget, LogicalPoint, LogicalSize)] as a tooltip: never focusable,
    /// and treated as a tooltip by the window manager.
    java.util.Optional<Popup> tooltip(Widget content,
            io.github.digitalsmile.goldberry.backend.LogicalPoint at,
            io.github.digitalsmile.goldberry.backend.LogicalSize size);

    /// Opens a popup **against a rectangle**, sized to its own content and moved
    /// to stay on the screen.
    ///
    /// The form almost every caller wants, and the one `docs/core-widgets.md`
    /// §7's `popover` is: a dropdown belongs under its control, a submenu beside
    /// its item, a tooltip above the thing it describes — and none of them has a
    /// size until its content has been laid out, or a position until that size is
    /// compared against the edges of the screen.
    ///
    /// Three things happen, in order, and each is separately observable:
    ///
    /// 1. **Measure.** The content is laid out with no surface, bounded by the
    ///    window's own size, and comes back with the size it wants.
    /// 2. **Place.** [Placement] applies its preferred side, flips it if it would
    ///    not fit, and shifts it along until it is inside the display's work area
    ///    — the usable part, less whatever a taskbar has taken.
    /// 3. **Open**, at the result.
    ///
    /// `anchor` is in this window's logical coordinates, which is what
    /// [#anchor(String)] returns and what a hit test reports, so anchoring to a
    /// button is that button's rectangle and no conversion.
    ///
    /// Empty for [#popup(Widget, LogicalPoint, LogicalSize)]'s reason: the
    /// platform may have no popup windows.
    java.util.Optional<Popup> popup(Widget content,
            io.github.digitalsmile.goldberry.backend.LogicalRect anchor, Placement placement);

    /// [#popup(Widget, LogicalRect, Placement)] against the node with this id —
    /// "open this under that button", in one call.
    ///
    /// Empty when the platform has no popups **or** when nothing with that id was
    /// painted, which are different problems with the same answer here: there is
    /// nowhere to put it.
    java.util.Optional<Popup> popup(Widget content, String anchorId, Placement placement);

    /// Runs `action` on the UI thread after `delay`.
    ///
    /// The frame loop's own timer: it shortens its next wait so the action lands
    /// on time, where anything sleeping elsewhere would fire on time and then wait
    /// for the loop to come back and notice.
    ///
    /// What §8's "hover-intent timing" is made of, and §7's tooltip delay before
    /// that. An application wanting to do something in half a second wants this
    /// rather than a thread ([ADR-0105](../../../../../book/src/adr/0105-a-tooltip-is-an-attribute-not-a-widget.md)).
    ///
    /// @return a handle that cancels it
    io.github.digitalsmile.goldberry.backend.EventLoop.Timer after(
            java.time.Duration delay, Runnable action);

    /// What the frame loop has been managing lately.
    ///
    /// Live rather than a snapshot, and cheap to ask: it is the same object every
    /// call and reading it is a mean over at most [FrameStats#capacity] frames.
    ///
    /// This is the window's, not the application's — an application that wants a
    /// frame-rate display puts a `hud` in the overlay layer and never touches
    /// this. It is here for the ones that want to log it, assert on it in a test,
    /// or draw it themselves on a `canvas`.
    FrameStats frames();

    /// The font book the renderer is drawing with.
    ///
    /// For an application that measures text itself — a `canvas` laying out its
    /// own labels. Owned by the launcher and closed by it; an application that
    /// closes this closes the window's text.
    Fonts fonts();

    /// The window, for the handful of things this interface deliberately does not
    /// wrap: the close-request hook, the cursor, resize and scale notifications.
    ///
    /// An escape hatch, and named as one. Reaching for it is a signal that
    /// something belongs on [Host] instead — but a toolkit that made the window
    /// unreachable would be one an application has to fork to extend.
    Window window();
}
