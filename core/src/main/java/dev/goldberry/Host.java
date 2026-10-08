package dev.goldberry;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.bind.Subscription;
import dev.goldberry.input.key.Repeat;
import dev.goldberry.input.key.Shortcut;
import dev.goldberry.motion.Clock;
import dev.goldberry.render.clipboard.Clipboard;
import dev.goldberry.render.clipboard.PrimarySelection;
import dev.goldberry.render.desktop.SystemTheme;
import dev.goldberry.render.desktop.menubar.AppMenuItem;
import dev.goldberry.render.desktop.notify.Notification;
import dev.goldberry.render.dialog.FileChoice;
import dev.goldberry.render.dialog.FileDialogSpec;
import dev.goldberry.render.dialog.FileDialogs;
import dev.goldberry.render.display.Display;
import dev.goldberry.render.event.EventLoop;
import dev.goldberry.render.model.LogicalPoint;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.render.window.BackendWindow;
import dev.goldberry.render.window.WindowSpec;
import dev.goldberry.stats.FrameStats;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widget.style.Corner;
import dev.goldberry.widget.Widget;

/// What a running [Application] can ask of the toolkit.
///
/// ```java
/// host.title("Notes");
/// host.shortcut(Mod.CTRL.and(Key.S), this::save);
/// host.popup(new Menu(items), "menu-button", Placement.BELOW);
/// ```
///
/// Handed to [Application#start], and the only handle an application needs: the
/// window, the frame loop and the trees are the launcher's, and everything an
/// application legitimately wants from them is a method here. A widget reaches
/// it through [dev.goldberry.widget.BuildContext#host()].
///
/// Confined to the UI thread, except [#repaint()], which is safe from any thread.
///
/// Read more: [The host](https://goldberry.dev/docs/guide/windows.html#the-host).
public interface Host {

    /// The clock this window's frames are timed against.
    ///
    /// A widget that needs to know how long ago something happened asks here
    /// rather than reading `System.nanoTime()`, and the difference is what makes
    /// a timeout testable: a test drives a [Clock#virtual()] and can then assert
    /// what happens *after* a timeout, where against the real clock it would have
    /// to sleep and hope
    /// ([The virtual clock](https://goldberry.dev/docs/guide/testing.html#the-virtual-clock)).
    ///
    /// Distinct from the frame time a painter is handed. That one is read once
    /// per frame and shared, so two spinners tick together
    /// ([dev.goldberry.widget.WidgetRenderer]); this is the
    /// clock *behind* it, and it is what input handling has to use because input
    /// does not run inside a frame.
    ///
    /// Defaulted rather than abstract because almost no implementation has an
    /// opinion — the real one is the launcher's, which hands over the renderer's,
    /// and a test that never asks is unaffected.
    default Clock clock() {
        return Clock.system();
    }

    /// Asks for another frame.
    ///
    /// Coalesced, so calling it ten times before the next frame costs one frame.
    /// The framework does **not** call this for you after a `setState`: it does
    /// not know whether the change is visible, and an application does.
    ///
    /// The one method here that is safe from any thread: there is one UI thread
    /// and background work runs on virtual threads behind it, so a value set from
    /// a virtual thread wanting to redraw is the ordinary case.
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
    /// where a string is only checked when it is parsed. The modifiers must match
    /// exactly, so `Ctrl+S` does not fire on `Ctrl+Shift+S`.
    ///
    /// Read more: [Accelerators](https://goldberry.dev/docs/guide/input.html#accelerators).
    void shortcut(Shortcut accelerator, Runnable action);

    /// Binds a window accelerator and remembers **who** bound it.
    ///
    /// The owner is a token for [#removeShortcut(Shortcut, Object)], compared by
    /// identity and never called. A widget that binds keys while it is mounted —
    /// `menubar` is the one in the toolkit — passes itself, so that giving them
    /// back cannot take somebody else's binding with it.
    void shortcut(Shortcut accelerator, Runnable action,
            Object owner);

    /// Binds a window accelerator and says what it does while its key is held
    /// down: [Repeat#IGNORE] for a toggle, so that holding `Escape` does not open
    /// and close a menu at the platform's repeat rate; [Repeat#FIRE], which is
    /// what the other forms bind, for an accelerator that should repeat.
    ///
    /// Read more: [Accelerators](https://goldberry.dev/docs/guide/input.html#accelerators).
    void shortcut(Shortcut accelerator, Runnable action, Repeat repeat);

    /// The same, remembering **who** bound it, as
    /// [#shortcut(Shortcut, Runnable, Object)] does.
    void shortcut(Shortcut accelerator, Runnable action, Object owner, Repeat repeat);

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
    /// it is mounted and has to give them back when it is not.
    ///
    /// **This form removes whatever is bound to `accelerator`**, including a
    /// binding somebody else made — which is what an application unbinding its own
    /// key means. A widget giving back keys it took should pass an owner, so that
    /// a binding made after its own is left alone
    /// ([#removeShortcut(Shortcut, Object)]).
    void removeShortcut(Shortcut accelerator);

    /// Unbinds a window accelerator **only if `owner` still holds it**.
    ///
    /// The other half of [#shortcut(Shortcut, Runnable, Object)], and the reason
    /// both exist: a `menubar` registers every accelerator in its menus when it is
    /// mounted and has to give them back when it is not, and giving them back by
    /// shortcut alone would take `Ctrl+O` with it even when the application had
    /// bound that key to something else in between.
    ///
    /// Owners are compared by identity. Harmless when nothing was bound, and a
    /// no-op when something else was.
    void removeShortcut(Shortcut accelerator,
            Object owner);

    /// Unbinds a window accelerator written the way a menu prints it.
    ///
    /// @throws IllegalArgumentException if the text names no key this toolkit has
    void removeShortcut(String accelerator);

    /// Binds a **tap** of a bare modifier key — pressed and released with nothing
    /// in between.
    ///
    /// `Alt`-style keyboard activation, which is how a `menubar` takes the
    /// keyboard. It is not a [dev.goldberry.input.key.Shortcut] because an
    /// accelerator is a key plus modifiers and fires on the press of the key, and
    /// there is no key here. What the rule is — and everything that spoils it —
    /// is [dev.goldberry.input.tap.ModifierTaps].
    ///
    /// The owner is the same token [#shortcut(Shortcut, Runnable, Object)] takes,
    /// compared by identity and never called.
    ///
    /// Reach for this **only** for activation a modifier is the whole gesture of.
    /// Anything with a key in it is an accelerator, and an accelerator is cheaper
    /// to reason about: it cannot be spoiled by what the user did next.
    void modifierTap(dev.goldberry.input.tap.ModifierKey modifier, Runnable action,
            Object owner);

    /// Unbinds a modifier tap **only if `owner` still holds it**.
    ///
    /// The other half of [#modifierTap], and it exists for the reason
    /// [#removeShortcut(Shortcut, Object)] does: a widget that binds while it is
    /// mounted has to give the binding back, and must not take a later one with it.
    ///
    /// Harmless when nothing was bound, and a no-op when something else was.
    void removeModifierTap(dev.goldberry.input.tap.ModifierKey modifier,
            Object owner);

    /// Floats `widget` over the window's content, pinned to `corner`.
    ///
    /// The in-window overlay layer: the widget is a sibling of the application's
    /// root rather than a descendant of it, so it is
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
    /// the widget it is describing, so it has to reach every edge.
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
    /// geometry exists after a paint and it is the router that has it.
    ///
    /// By `id` rather than by element because a `tour` names its target by id,
    /// and because an application holds ids, not elements.
    ///
    /// Empty before the first frame, and for a node that was not painted: a
    /// rectangle for something invisible would be a lie a menu would then point
    /// at.
    java.util.Optional<dev.goldberry.input.hit.HitTest.Region> anchor(String id);

    /// What the desktop's appearance is set to, or empty where it does not say.
    ///
    /// **`Optional`, and that is the whole design.** SDL answers
    /// `SDL_SYSTEM_THEME_UNKNOWN` on a desktop that has no such setting, and an
    /// application needs to tell "the desktop says light" from "the desktop does
    /// not say": the first is a theme and the second is a default.
    ///
    /// The toolkit does **not** act on the answer. Goldberry ships `nord-light` and
    /// `nord-dark`, and which one an application uses — and whether it follows the
    /// desktop at all, or offers a three-way choice of its own — is the
    /// application's. This is the one input to that decision an application cannot
    /// get for itself: every way of asking is a platform call, and only the
    /// native layer may make one.
    ///
    /// Read more: [The desktop's theme](https://goldberry.dev/docs/guide/windows.html#the-desktops-theme).
    java.util.Optional<SystemTheme> systemTheme();

    /// Whether the desktop asks for less movement, or empty where it does not
    /// say: the desktop's reduce-motion switch.
    ///
    /// The toolkit already obeys it: a renderer built by the launcher starts
    /// with it applied. This is here for an application that wants to say so on
    /// screen — a settings page showing what it inherited — or that draws
    /// motion of its own on a `canvas` and has to decide about it itself.
    default java.util.Optional<Boolean> reducedMotion() {
        return java.util.Optional.empty();
    }

    /// Hands a URL to the desktop — the browser for `https:`, the mail client
    /// for `mailto:` — through the platform, which is the one place a process
    /// may ask for one to be opened.
    ///
    /// What a `link` does with an `href`. A request rather than a result:
    /// the desktop opens it in its own time, and false means the platform would
    /// not — a headless run, a library without the export, a scheme nothing
    /// handles — which a caller reports rather than retries.
    ///
    /// @param url what to open
    /// @return whether the platform took the request
    default boolean openExternal(String url) {
        return false;
    }

    /// Told when that setting changes, on the UI thread, for as long as the window
    /// is open.
    ///
    /// Once a day on any desktop with a sunset schedule, and at any moment on one
    /// where the user flips the switch. Asking at start-up and never again is the
    /// bug this exists to prevent: an application that started in the light theme
    /// at noon should not still be in it at dusk.
    ///
    /// The listener is handed the **new** setting, never empty: a desktop that has
    /// gone from saying nothing to saying nothing does not report a change, and
    /// [#systemTheme()] is still the way to ask what it is now.
    ///
    /// **A [Subscription] comes back**, as [#onFullscreenChanged]'s does. An
    /// application's own listener lives as long as the window and may drop it,
    /// but a tray that swaps its icon at dusk is closed and shown again every
    /// time its menu changes, and each showing that could not stop listening
    /// would leave one listener behind. A widget that wants to follow
    /// the desktop should still let the application tell it, in whatever it
    /// already rebuilds from.
    ///
    /// @param listener told the new setting, on the UI thread
    /// @return what stops the listening; closing it twice is harmless
    Subscription onSystemThemeChanged(Consumer<SystemTheme> listener);

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
    /// cost of being clipped to the window.
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
    /// The form almost every caller wants, and the one a `popover` is: a
    /// dropdown belongs under its control, a submenu beside
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
    /// of the same rule.
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
    /// This is what makes the measure step above separately observable: a caller
    /// that needs to know how big its content came out is told, between the
    /// measure and the place. Without it a menu would have to **guess** whether
    /// it will be taller than the screen from its row count times an assumed
    /// height, and a long `select` list would lose its bottom.
    ///
    /// @param fit consulted between the measure and the place, or null for the
    ///            behaviour of the overload above
    java.util.Optional<Popup> popup(Widget content,
                                    LogicalRect anchor, Placement placement,
                                    float minimumWidth, @Nullable Fit fit);

    /// [#popup(Widget, LogicalRect, Placement, float, Fit)] as a panel that hangs
    /// off something the user is **still using**.
    ///
    /// An autocomplete attaches a list of suggestions to its field, and the field
    /// is what is being typed into — so this popup must never take the keyboard.
    /// Not at the router level, which is what [Popup#takesFocus(boolean)]
    /// settles, but at the **platform** level: a window opened as a menu is
    /// focusable, and every window manager will hand it the keyboard the moment
    /// it appears. A field whose suggestion list did that would take one
    /// character and then go dead.
    ///
    /// So it is opened as the same *kind* of window a tooltip is — never
    /// focusable, and treated as an attached panel by the window manager — while
    /// still being measured, placed and light-dismissed like any other popup. The
    /// arrows reach it because the owner forwards keys to whatever popup is open.
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
    /// absurd and would rather be clamped — or rather should have been a dialog.
    /// `:core` could not act on the answer anyway: a viewport is a widget, and
    /// `:core` has none.
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
    /// the thing building the menu can decide that.
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
    default Optional<Popup> popup(Widget content, String anchorId, Placement placement) {
        return popup(content, anchorId, placement, 0, null);
    }

    /// [#popup(Widget, String, Placement)] with a floor under the width **and** a
    /// say in what happens when the content does not fit.
    ///
    /// Anchoring by id is what makes a popup **follow** when its window moves:
    /// the name is a question the next painted frame can answer again, where a
    /// rectangle is only ever the answer it already was. A menu or a dropdown
    /// that also needs a minimum width or a [Fit] anchors by name here rather
    /// than resolving the rectangle itself, which would give the following up.
    ///
    /// Empty for [#popup(Widget, String, Placement)]'s two reasons: no popup
    /// windows, or nothing painted under that id.
    ///
    /// **A `default` rather than an abstract method**, unlike the `minimumWidth`
    /// overload, and for a reason that does not apply there: a floor is
    /// something an implementation has to *do*, while resolving
    /// a name is [#anchor(String)] followed by the rectangle overload, and an
    /// implementation that wrote that out by hand could only write it out
    /// differently. What a [Launcher] adds on top is the remembering, which is
    /// not behaviour a caller can observe on a host that has no popups at all.
    ///
    /// @param anchorId     the `id` of a node this window painted
    /// @param minimumWidth the least the popup may be, in logical pixels
    /// @param fit          consulted between the measure and the place, or null
    default Optional<Popup> popup(
            Widget content, String anchorId, Placement placement, float minimumWidth, @Nullable Fit fit) {

        Objects.requireNonNull(anchorId, "anchorId");
        // `painted()` and not `bounds()`: a menu belongs under where its anchor
        // was drawn, and a button inside a `scroll` is laid out where it always
        // was and drawn a long way from there.
        return anchor(anchorId)
                .flatMap(region -> popup(content, region.painted(), placement, minimumWidth, fit));
    }

    /// What to do when a widget carrying `context-menu="…"` is right-clicked.
    ///
    /// `context-menu="…"` attaches a context menu to **any** widget by name, and
    /// this is the seam between the two halves of that: the toolkit notices the
    /// right-click, walks up from what is under the pointer to find the name, and
    /// hands it over with the point it happened at. What the name *means* — and
    /// the opening — is the catalogue's, because a menu is a widget and opening
    /// one needs `Menus`.
    ///
    /// An application using the catalog writes one line:
    ///
    /// ```java
    /// Menus.contextMenus(host, Map.of("row", rowMenu()));
    /// ```
    ///
    /// One handler, not a list: two things deciding what a right-click means is
    /// two menus opening.
    ///
    /// Read more: [Context menus](https://goldberry.dev/docs/guide/input.html#context-menus).
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
    /// something a stray call gets to step around.
    ///
    /// @param id           the `id` of the node to focus
    /// @param fromKeyboard whether to show the focus ring — the
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
    /// What a tooltip's delay and a menu's hover-intent timing are made of. An
    /// application wanting to do something in half a second wants this rather
    /// than a thread: the action lands on the UI thread, where a thread's would
    /// have to be handed back to it.
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
    /// widget is what [dev.goldberry.widget.BuildContext#host()]
    /// exists to avoid.
    ///
    /// Never null: a platform with no clipboard reports
    /// [Clipboard#none()], which accepts
    /// nothing and always reads empty.
    ///
    /// Read more: [The clipboard](https://goldberry.dev/docs/guide/text.html#the-clipboard).
    Clipboard clipboard();

    /// The session's primary selection — X11's middle-click buffer — or empty
    /// where the platform has none.
    ///
    /// On [Host] for [#clipboard()]'s reason: the consumer is a widget. What a
    /// `text-input` does with it is publish a finished selection and paste on a
    /// middle click, and what it does **without** it is neither — so a field is
    /// never told which platform it is on, only whether this exists.
    ///
    /// Empty by default, which is the right answer for a host that has not said:
    /// a primary selection nobody else can paste from is not one.
    default Optional<PrimarySelection> primarySelection() {
        return Optional.empty();
    }

    /// The platform's own open, save and folder dialogs.
    ///
    /// On [Host] for the clipboard's reason: the consumer is a widget — a
    /// toolbar's "Export…" — and reaching a window's backend from a widget is
    /// what [dev.goldberry.widget.BuildContext#host()] exists
    /// to avoid.
    ///
    /// Never null: a backend with no dialogs reports [FileDialogs#none()], which
    /// answers every request with a [FileChoice.Failed]. Ask
    /// [FileDialogs#supported()] before offering the menu item.
    ///
    /// Most callers want [#fileDialog] instead, which fills in this window as the
    /// one to be modal for and asks for the frame the answer needs.
    FileDialogs fileDialogs();

    /// Puts a file dialog up over this window and returns at once.
    ///
    /// ```java
    /// host.fileDialog(FileDialogSpec.saveFile().filters(FileFilter.of("PNG image", "png")), choice -> {
    ///     if (choice instanceof FileChoice.Chosen chosen) {
    ///         Files.write(chosen.path(), board.encodePng());
    ///     }
    /// });
    /// ```
    ///
    /// **The answer arrives with a repaint behind it**, and without one a dialog
    /// looks broken in a way nothing reports — the same failure a tray row has,
    /// and for the same reason: the user's choice comes back from the
    /// platform's own thread, produces no input event, and so nothing would ask
    /// for the frame that draws what it changed.
    ///
    /// On the UI thread, exactly once per call.
    ///
    /// @param spec     which dialog, and what to suggest
    /// @param onChoice told once, with one of [FileChoice]'s three cases
    default void fileDialog(FileDialogSpec spec, Consumer<FileChoice> onChoice) {
        fileDialogs().show(null, spec, choice -> {
            try {
                onChoice.accept(choice);
            } finally {
                repaint();
            }
        });
    }

    /// Puts an icon in the desktop's notification area: what a `tray-icon` is.
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
    /// [dev.goldberry.render.tray.TrayItem].
    ///
    /// Read more: [The tray icon](https://goldberry.dev/docs/components/menus.html#the-tray-icon).
    ///
    /// @param spec the icon, the tooltip and the menu
    /// @return the tray, or empty if this desktop has none
    java.util.Optional<dev.goldberry.render.tray.BackendTray> tray(
            dev.goldberry.render.tray.TraySpec spec);

    /// Opens a web page in a window of the engine's own: what a `web-view` opens.
    ///
    /// **Empty is the usual answer, not an unlucky one.** A page needs
    /// `libgoldberry-webview`, which is a separate library precisely so that GTK
    /// and WebKit are not load-time dependencies of the toolkit, and most builds
    /// do not have it.
    /// An application that wants to know before it offers the button asks
    /// [dev.goldberry.Goldberry#capabilities()] for
    /// [dev.goldberry.platform.Capability#WEB_VIEW].
    ///
    /// On [Host] for the tray's reason, and more strongly: what opens is not a
    /// Goldberry window, has no box and is in no element tree. It is a window on
    /// the desktop that this application happens to have asked for, and the only
    /// thing that connects it to the toolkit is the handle returned here.
    ///
    /// **The caller closes it.**
    /// [dev.goldberry.Goldberry#run()] returns when the last
    /// *Goldberry* window closes, and a page's window is not one.
    ///
    /// Read more: [The web view](https://goldberry.dev/docs/components/content.html#the-web-view).
    ///
    /// @param spec where the page starts, its title and its window size
    /// @return the page, or empty where no page can be opened here
    java.util.Optional<dev.goldberry.render.web.BackendWebView> webView(
            dev.goldberry.render.web.WebViewSpec spec);

    /// Opens a page **inside this window**, at the given rectangle in its own
    /// logical coordinates: a `web-view` as a widget.
    ///
    /// **Empty on Wayland, always.** Embedding means reparenting the engine's
    /// window into this one; X11, Win32 and Cocoa allow that and Wayland does
    /// not. A caller that gets empty is expected to say so on screen rather than
    /// to open a window beside the application — which is the whole difference
    /// between this and [#webView].
    ///
    /// @param spec   where the page starts; its width and height are ignored
    /// @param bounds where to put it, in this window's logical coordinates
    /// @return the page, or empty where none can be embedded here
    java.util.Optional<dev.goldberry.render.web.BackendWebView> embeddedWebView(
            dev.goldberry.render.web.WebViewSpec spec,
            dev.goldberry.render.model.LogicalRect bounds);

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

    /// How many device pixels one logical pixel covers in this window right now —
    /// 1 at 100%, 2 on a retina display.
    ///
    /// For a widget choosing between rasters of one picture, which must not reach
    /// for [#window()] to ask: a widget never names a window. 1 by default, which
    /// is right for a host with no window under it.
    default double displayScale() {
        return 1.0;
    }

    /// Whether a modal is in force in this window — a `dialog` is up, and the
    /// keyboard and pointer are trapped inside it.
    ///
    /// A fact about the **window**, which is why it is here rather than found by
    /// walking the tree: a filling [Overlay] is a *sibling* of the content under
    /// the window's root, not an ancestor of it, so
    /// `BuildContext.findAncestorState` cannot see one from inside the
    /// application's own widgets. The router already knows — it finds the
    /// deepest modal once per frame beside the hit-test regions — and this is
    /// that answer, read.
    ///
    /// Added for `web-view`, which is the one widget that cannot be covered by
    /// an overlay: a page is a platform window above the frame, so a dialog over
    /// it is painted and invisible. The widget takes its page off the screen
    /// while this is true. Nothing else needs it yet, and other
    /// widgets that want to stand aside for a modal now can.
    ///
    /// **A frame behind**, like [#anchor]: it describes the frame that was
    /// painted, which is the frame the user is looking at.
    ///
    /// False by default, which is right for a host with no router under it — an
    /// offscreen render, and every golden image.
    default boolean isModal() {
        return false;
    }

    /// Whether this host has a window that can be asked to fill its display.
    ///
    /// What a control decides by whether to **offer** fullscreen at all: a
    /// `media-player` shows its fullscreen button only where pressing it could
    /// do something. False by default, which is the answer for a host with no
    /// window under it: an offscreen render, and every golden image.
    default boolean canFullscreen() {
        return false;
    }

    /// Whether the window fills its display, as the platform last reported it:
    /// [Window#isFullscreen()]'s answer, and its rule about the time between an
    /// ask and the event. False for a host with no window.
    default boolean isFullscreen() {
        return false;
    }

    /// Asks for the window to fill its display, or to be a window again:
    /// [Window#setFullscreen(boolean)], a request whose answer arrives through
    /// [#onFullscreenChanged]. Does nothing where [#canFullscreen()] is false.
    ///
    /// @param fullscreen true to fill the display, false for a window again
    default void setFullscreen(boolean fullscreen) {}

    /// Called with the new state each time the window comes to fill its display
    /// or stops, whoever asked: this application, or the user with the
    /// platform's own button. On the UI thread.
    ///
    /// A [dev.goldberry.bind.Subscription]: a widget listens
    /// here, and a widget leaves the tree long before its window closes, so it
    /// has to be able to stop.
    ///
    /// @param listener told `true` on entering fullscreen and `false` on leaving
    /// @return what stops the listening; closing it twice is harmless
    default dev.goldberry.bind.Subscription onFullscreenChanged(Consumer<Boolean> listener) {
        Objects.requireNonNull(listener, "listener");
        return () -> {};
    }

    /// The window, for the handful of things this interface deliberately does not
    /// wrap: the close-request hook, the cursor, resize and scale notifications.
    ///
    /// An escape hatch, and named as one. Reaching for it is a signal that
    /// something belongs on [Host] instead — but a toolkit that made the window
    /// unreachable would be one an application has to fork to extend.
    Window window();

    /// The displays connected now, the primary one first.
    ///
    /// Asked fresh each time, because a display can be plugged in or taken away
    /// at any moment. Empty by default, which is the answer for a host with no
    /// desktop under it: an offscreen render, and every golden image.
    ///
    /// Read more: [Displays](https://goldberry.dev/docs/guide/windows.html#displays).
    default List<Display> displays() {
        return List.of();
    }

    /// Opens a second top-level window with `root` in it, and returns the host
    /// that window answers to.
    ///
    /// The new window has a tree, a router, overlays, focus, accelerators and
    /// popups of its own, and shares everything that belongs to the
    /// application rather than to a window: the stylesheets — a
    /// [#restyle()] restyles every window — the fonts, the models and the
    /// clock. It closes when the user closes it, when [WindowHost#close()] is
    /// called, or when the application's first window closes, which ends the
    /// application.
    ///
    /// ```java
    /// var spec = WindowSpec.of("Settings", LogicalSize.of(480, 360)).withOwnership(Ownership.OWNED);
    /// settings = host.openWindow(spec, new SettingsPage(model)).orElseThrow();
    /// settings.onClose(() -> settings = null);
    /// ```
    ///
    /// A window whose spec says it is owned or modal belongs to the window of
    /// the host it was opened from — see
    /// [dev.goldberry.render.window.Ownership].
    ///
    /// Read more: [More than one window](https://goldberry.dev/docs/guide/windows.html#more-than-one-window).
    ///
    /// @return the new window's host, or empty where this host has no desktop
    ///         to open one on — the default
    default Optional<WindowHost> openWindow(WindowSpec spec, Widget root) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(root, "root");
        return Optional.empty();
    }

    /// Shows a desktop notification: GNOME's banner, macOS's Notification
    /// Center, Windows' toast.
    ///
    /// ```java
    /// host.notify(Notification.of("Gate waiting", "prod-eu needs an approval")
    ///         .onActivate(() -> showGates()));
    /// ```
    ///
    /// **False is an ordinary answer**: a Linux session with no notification
    /// daemon, a macOS process that is not an application bundle, a headless
    /// run. [dev.goldberry.platform.Capability#NOTIFICATIONS] says whether this
    /// build can ask at all. The action runs on the UI thread, and the window
    /// repaints after it.
    ///
    /// On [Host] for the tray's reason: a notification is the application's.
    ///
    /// Read more: [Notifications](https://goldberry.dev/docs/guide/windows.html#notifications).
    ///
    /// @param notification what to show
    /// @return whether the desktop took it
    default boolean notify(Notification notification) {
        Objects.requireNonNull(notification, "notification");
        return false;
    }

    /// Puts `label` on the application's dock or launcher icon, or takes the
    /// badge away for null or empty.
    ///
    /// macOS shows any short text. A Linux dock shows a number — Ubuntu's,
    /// Dash to Dock, Plank, KDE's — and finds the application by its desktop
    /// entry, which `-Dgoldberry.desktop.id` names. Windows has none.
    ///
    /// Read more: [Notifications](https://goldberry.dev/docs/guide/windows.html#the-badge).
    ///
    /// @return whether the desktop was told
    default boolean badge(@Nullable String label) {
        return false;
    }

    /// A number on the dock or launcher icon, or none for 0 or less — see
    /// [#badge(String)].
    default boolean badge(int count) {
        return badge(count <= 0 ? null : Integer.toString(count));
    }

    /// Makes `headings` the application's own menu bar, where the platform has
    /// one: on macOS, the bar at the top of the screen, after the application
    /// menu (About, Hide, Quit). An empty list puts back what was there.
    ///
    /// A `menubar` widget calls this itself, and draws nothing in the window
    /// when it answers true. **False everywhere but macOS**, and the widget
    /// then stays the bar inside the window it has always been.
    ///
    /// Read more: [Menus and the tray](https://goldberry.dev/docs/components/menus.html#the-macos-menu-bar).
    ///
    /// @param headings the menus, each a submenu row
    /// @return whether the platform shows them
    default boolean applicationMenu(List<AppMenuItem> headings) {
        Objects.requireNonNull(headings, "headings");
        return false;
    }
}
