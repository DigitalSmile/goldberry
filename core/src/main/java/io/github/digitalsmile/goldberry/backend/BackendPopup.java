package io.github.digitalsmile.goldberry.backend;

/// A popup window: a surface of the application's own drawing, parented to
/// another window and free of its bounds.
///
/// Everything a [BackendWindow] can do, because that is what it is — it acquires
/// a frame, is painted into and presents, and its events arrive through the same
/// pump identified by their own window. What it adds is the two things only a
/// popup has: an owner, and a position that means something relative to it.
///
/// ## Why this exists at all, given the overlay layer
///
/// [ADR-0100](../../../../../../book/src/adr/0100-a-window-has-a-layer-above-its-application.md)
/// put a layer above the application's root for the overlays that stay inside the
/// window — a toast, a scrim, a HUD. Three of `docs/core-widgets.md` §7's widgets
/// cannot use it, and for one reason each time: a `menu`, a `select`'s list and a
/// `tooltip` are routinely **taller than the space left below the thing that
/// opened them**, and clipping them to the window is the difference between a
/// dropdown and a dropdown that shows four of its nine options. Escaping the
/// window needs the platform's cooperation, and this is where it is asked for.
///
/// ## Confined to the UI thread, like every other window
public interface BackendPopup extends BackendWindow {

    /// The window this popup belongs to.
    ///
    /// It is destroyed with its owner by the platform, which is why closing a
    /// window does not require hunting down its popups first — and why a popup
    /// must not be used after its owner has gone.
    BackendWindow owner();

    /// What the platform was asked to treat this as.
    PopupKind kind();

    /// Where the popup was last asked to be, **as an offset from its owner's
    /// top-left**.
    ///
    /// Not [BackendWindow#position()], which is this window's place on the
    /// *desktop* — the two are different measurements and deliberately do not
    /// share a name. A popup is placed against the thing that opened it; the
    /// desktop only comes into it when a placement policy has to ask whether the
    /// result fits on the screen.
    ///
    /// **What was requested, not what the window system has got round to.** SDL
    /// cannot be asked reliably — `SDL_GetWindowPosition` reports the display's
    /// coordinates on some drivers and the parent's on others — and the request is
    /// the one answer that is the same everywhere.
    LogicalPoint offset();

    /// Moves the popup, as an offset from its owner's top-left.
    ///
    /// Cheaper than closing and reopening, and visibly different: a menu that is
    /// destroyed and recreated flickers and loses whatever the platform was
    /// animating. What a `popover` does when its anchor scrolls.
    ///
    /// A **request**, like [#resize] — see there.
    void move(LogicalPoint position);

    /// Asks for the popup to be resized, in logical pixels.
    ///
    /// A filtering autocomplete narrows as the list shortens, and a submenu is as
    /// tall as its items — neither is known when the popup is created.
    ///
    /// **A request, not an assignment.** On X11 and Wayland the window manager
    /// decides when a resize happens, so [#size()] keeps reporting the old size
    /// until the platform has processed it — one event pump later, in practice —
    /// and a [BackendEvent.Resized] is what says it has. Code that measured
    /// straight after this call and drew to that number would draw at the old size
    /// on two of the three desktops.
    ///
    /// The `headless` backend defers it the same way, deliberately: a fake that
    /// applied it instantly would let exactly that bug pass its tests.
    ///
    /// @throws IllegalArgumentException if the size is not positive
    void resize(LogicalSize size);
}
