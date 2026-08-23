package io.github.digitalsmile.goldberry;

import io.github.digitalsmile.goldberry.render.Clipboard;
import io.github.digitalsmile.goldberry.render.event.EventLoop;
import io.github.digitalsmile.goldberry.render.model.LogicalPoint;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.render.window.BackendWindow;
import io.github.digitalsmile.goldberry.stats.FrameStats;
import io.github.digitalsmile.goldberry.text.font.Fonts;
import io.github.digitalsmile.goldberry.widget.style.Corner;
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
    void shortcut(io.github.digitalsmile.goldberry.input.key.Shortcut accelerator, Runnable action);

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
    void removeShortcut(io.github.digitalsmile.goldberry.input.key.Shortcut accelerator);

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
    java.util.Optional<io.github.digitalsmile.goldberry.input.hit.HitTest.Region> anchor(String id);

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
            LogicalPoint at,
            LogicalSize size);

    /// [#popup(Widget, LogicalPoint, LogicalSize)] as a tooltip: never focusable,
    /// and treated as a tooltip by the window manager.
    java.util.Optional<Popup> tooltip(Widget content,
            LogicalPoint at,
            LogicalSize size);

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
                                    LogicalRect anchor, Placement placement);

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
                                    LogicalRect anchor, Placement placement,
                                    float minimumWidth);

    /// [#popup(Widget, LogicalRect, Placement, float)] with a say in what happens
    /// when the content turns out not to fit.
    ///
    /// The measure step above is "separately observable" and until now it was
    /// not: the facility measured, placed and opened, and a caller that needed to
    /// know how big its content came out had no way to ask. So both callers that
    /// needed it **guessed** — a menu decided whether it would be taller than the
    /// screen from its row count times an assumed height, and a `select` did not
    /// try, which is why a long list lost its bottom
    /// ([ADR-0179](../../../../../book/src/adr/0179-a-popup-says-what-it-measured.md)).
    ///
    /// @param fit consulted between the measure and the place, or null for the
    ///            behaviour of the overload above
    java.util.Optional<Popup> popup(Widget content,
                                    LogicalRect anchor, Placement placement,
                                    float minimumWidth, Fit fit);

    /// [#popup(Widget, LogicalRect, Placement, float, Fit)] as a panel that hangs
    /// off something the user is **still using**.
    ///
    /// §4's autocomplete "attaches a `popover` of suggestions to the field", and
    /// the field is what is being typed into — so this popup must never take the
    /// keyboard. Not at the router level, which is what
    /// [Popup#takesFocus(boolean)] settles, but at the **platform** level: a
    /// window opened as a menu is focusable, and every window manager will hand
    /// it the keyboard the moment it appears. A field whose suggestion list did
    /// that took one character and then went dead
    /// ([ADR-0186](../../../../../book/src/adr/0186-a-panel-that-hangs-off-a-field-is-not-a-menu.md)).
    ///
    /// So it is opened as the same *kind* of window a tooltip is — never
    /// focusable, and treated as an attached panel by the window manager — while
    /// still being measured, placed and light-dismissed like any other popup. The
    /// arrows reach it because the owner forwards keys to whatever popup is open
    /// ([ADR-0104](../../../../../book/src/adr/0104-a-popup-is-measured-then-placed.md)).
    java.util.Optional<Popup> attachedPopup(Widget content,
                                            LogicalRect anchor, Placement placement,
                                            float minimumWidth, Fit fit);

    /// What a caller does with a measurement, between the measure and the place.
    ///
    /// ## Why the facility asks rather than deciding
    ///
    /// **Whether content that does not fit should scroll or be clamped is a fact
    /// about the content.** A menu that lost its last three commands is the worst
    /// kind of wrong and wants a viewport; a tooltip that scrolled would be
    /// absurd and would rather be clamped — or rather should have been a dialog
    /// ([ADR-0118](../../../../../book/src/adr/0118-a-popup-that-does-not-fit-scrolls.md)).
    /// `:core` could not act on the answer anyway: a viewport is a widget, and
    /// `:core` has none
    /// ([ADR-0092](../../../../../book/src/adr/0092-a-primitive-is-a-widget-like-any-other.md)).
    ///
    /// So the facility reports and the caller answers. Returning `content`
    /// unchanged is the ordinary answer and costs nothing; returning anything
    /// else is paid for by a second measurement, which is the right way round —
    /// nearly every popup fits.
    @FunctionalInterface
    interface Fit {

        /// @param content  what was measured, so a lambda need capture nothing
        /// @param measured what it came out as, before any placement clamped it
        /// @param available where the popup may be placed — [#placeableArea()],
        ///                  which is what the content has to fit inside
        /// @return what to open: `content` itself when it fits, or something
        ///         that does when it does not
        Widget fit(Widget content, LogicalSize measured, LogicalRect available);
    }

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
    LogicalRect placeableArea();

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

    /// Moves the keyboard focus to the node with this `id`.
    ///
    /// The door for the thing a widget cannot describe: a dialog putting the
    /// caret in its first field, a form jumping to its first error, a wizard
    /// focusing the step it just opened. Everything else about focus is a
    /// property of the tree — where a press lands, what Tab enumerates — and is
    /// handled without anybody asking.
    ///
    /// **By id**, for [#anchor]'s reason: a widget has no element and never will,
    /// and an id is the one name a description and a tree agree on. It is also
    /// the name a document can write, so this works for a KDL screen as well as a
    /// Java one.
    ///
    /// Refused rather than obeyed when the node cannot take focus, is disabled,
    /// or is **outside a modal that is open** — a dialog's focus trap is not
    /// something a stray call gets to step around
    /// ([ADR-0176](../../../../../book/src/adr/0176-a-dialog-is-a-widget-and-showing-one-is-not.md)).
    ///
    /// @param id           the `id` of the node to focus
    /// @param fromKeyboard whether to show the focus ring — §7.2's
    ///                     `:focus-visible` distinction. A dialog opened by a
    ///                     keyboard shortcut says true; one opened by a click
    ///                     says false, or the ring appears under a pointer that
    ///                     nobody moved
    /// @return whether focus moved
    boolean focus(String id, boolean fromKeyboard);

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
    EventLoop.Timer after(
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
    /// [Clipboard#none()], which accepts
    /// nothing and always reads empty.
    Clipboard clipboard();

    /// Puts an icon in the desktop's notification area — `docs/core-widgets.md`
    /// §9's `tray-icon`.
    ///
    /// **Empty is an ordinary answer**: a session with no notification area, a
    /// Linux desktop without the AppIndicator library, a container with no shell.
    /// Every platform's own guidance says a tray-using application must work
    /// without one, so this reports the absence rather than throwing and the
    /// caller carries on.
    ///
    /// On [Host] rather than on a window because a tray belongs to the
    /// *application*, like the clipboard — and unlike a popup, which is anchored
    /// to something painted. The rows it describes are drawn by the platform, so
    /// none of the toolkit's styling, layout or input reaches them; see
    /// [io.github.digitalsmile.goldberry.render.tray.TrayItem].
    ///
    /// @param spec the icon, the tooltip and the menu
    /// @return the tray, or empty if this desktop has none
    java.util.Optional<io.github.digitalsmile.goldberry.render.tray.BackendTray> tray(
            io.github.digitalsmile.goldberry.render.tray.TraySpec spec);

    /// Asks the platform to start or stop delivering committed text to this
    /// window.
    ///
    /// What a field calls when focus arrives and when it leaves. Off by default
    /// and per window — see
    /// [BackendWindow#textInput(boolean)]
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
