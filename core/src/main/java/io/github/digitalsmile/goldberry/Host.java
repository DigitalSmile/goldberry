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

    /// Unbinds a window accelerator. Harmless when nothing was bound.
    ///
    /// The other end of [#shortcut(Shortcut, Runnable)], and it exists because
    /// `menubar` registers the accelerators of every command in its menus when
    /// it is mounted and has to give them back when it is not
    /// ([ADR-0163](../../../../../book/src/adr/0163-a-menu-bar-owns-its-menus.md)).
    ///
    /// **The map is keyed by the shortcut and not by who bound it**, so this
    /// removes whatever is bound to `accelerator` — including a binding somebody
    /// else made. Two things claiming `Ctrl+O` is already a conflict the last
    /// registration wins; this is the same conflict at the other end.
    void removeShortcut(io.github.digitalsmile.goldberry.input.Shortcut accelerator);

    /// Unbinds a window accelerator written the way a menu prints it.
    ///
    /// @throws IllegalArgumentException if the text names no key this toolkit has
    void removeShortcut(String accelerator);

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

    /// An overlay covering the **whole window** rather than tucked into a corner.
    ///
    /// For the one thing a corner cannot express: a `tour` dims everything except
    /// the widget it is describing, so it has to reach every edge
    /// ([ADR-0121](../../../../../../book/src/adr/0121-a-tour-is-a-veil-and-a-sequence.md)).
    ///
    /// It takes the pointer wherever it is opaque, which for a veil is
    /// everywhere except the cut-out — that is the point of a veil, and it is
    /// what makes a tour modal without anything having to say so.
    Overlay fill(Widget widget);

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

    /// [#popup(Widget, LogicalRect, Placement)] with a floor under the width.
    ///
    /// **What a dropdown is**, and the one thing a content measurement cannot
    /// say: a `select`'s list belongs under its field and at least as wide as it,
    /// because a wide control with a narrow panel hanging off its left-hand end
    /// reads as a mistake rather than as a menu. The options decide the rest —
    /// one longer than the field widens the list past it, which is the other half
    /// of the same rule
    /// ([ADR-0145](../../../../../book/src/adr/0145-a-dropdown-is-as-wide-as-what-it-drops-from.md)).
    ///
    /// A floor and not a width: this is still measure-then-place, and a caller
    /// asking for less than its content needs would get its content's size.
    ///
    /// Opt-in per call rather than a property of [Placement], because it is
    /// false for the other two callers — a menu is as wide as its commands and a
    /// tooltip as wide as its text, and neither has any business being as wide as
    /// the thing it points at.
    ///
    /// @param minimumWidth the least the popup may be, in logical pixels
    java.util.Optional<Popup> popup(Widget content,
            io.github.digitalsmile.goldberry.backend.LogicalRect anchor, Placement placement,
            float minimumWidth);

    /// Where a popup is allowed to be, in this window's coordinates.
    ///
    /// The display's work area, which [Placement] already places against. Exposed
    /// because a caller may need to keep its **content** inside it rather than
    /// leaving the placement to clamp: a menu longer than the screen wants to
    /// become a menu of the screen's height with a scroll view in it, and only
    /// the thing building the menu can decide that
    /// ([ADR-0118](../../../../../../book/src/adr/0118-a-popup-that-does-not-fit-scrolls.md)).
    ///
    /// **A rectangle and not just a height**, because the same question arises
    /// horizontally for a wide popup and answering half of it would mean
    /// answering it twice.
    io.github.digitalsmile.goldberry.backend.LogicalRect placeableArea();

    /// [#popup(Widget, LogicalRect, Placement)] against the node with this id —
    /// "open this under that button", in one call.
    ///
    /// Empty when the platform has no popups **or** when nothing with that id was
    /// painted, which are different problems with the same answer here: there is
    /// nowhere to put it.
    java.util.Optional<Popup> popup(Widget content, String anchorId, Placement placement);

    /// What to do when a widget carrying `context-menu="…"` is right-clicked.
    ///
    /// §8 attaches a context menu to **any** widget by name, and this is the seam
    /// between the two halves of that: the toolkit notices the right-click, walks
    /// up from what is under the pointer to find the name, and hands it over with
    /// the point it happened at. What the name *means* — and the opening — is the
    /// catalog's, because a menu is a widget and opening one needs `Menus`
    /// ([ADR-0108](../../../../../book/src/adr/0108-a-context-menu-is-a-name-on-a-widget.md)).
    ///
    /// An application using the catalog writes one line:
    ///
    /// ```java
    /// Menus.contextMenus(host, Map.of("row", rowMenu()));
    /// ```
    ///
    /// One handler, not a list: two things deciding what a right-click means is
    /// two menus opening.
    void onContextMenu(ContextMenuHandler handler);

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

    /// The session's clipboard.
    ///
    /// On [Host] rather than reached through [#window()] because a widget is the
    /// consumer — `text-input`'s `Ctrl+C` — and reaching a window's backend from a
    /// widget is what [io.github.digitalsmile.goldberry.widget.BuildContext#host()]
    /// exists to avoid (ADR-0140).
    ///
    /// Never null: a platform with no clipboard reports
    /// [io.github.digitalsmile.goldberry.backend.Clipboard#none()], which accepts
    /// nothing and always reads empty.
    io.github.digitalsmile.goldberry.backend.Clipboard clipboard();

    /// Asks the platform to start or stop delivering committed text to this
    /// window.
    ///
    /// What a field calls when focus arrives and when it leaves. Off by default
    /// and per window — see
    /// [io.github.digitalsmile.goldberry.backend.BackendWindow#textInput(boolean)]
    /// for why a toolkit must not simply turn it on and leave it on.
    void textInput(boolean active);

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
