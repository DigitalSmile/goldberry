package io.github.digitalsmile.goldberry;

import io.github.digitalsmile.goldberry.text.Fonts;

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
