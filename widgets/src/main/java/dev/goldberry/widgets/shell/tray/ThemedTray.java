package dev.goldberry.widgets.shell.tray;

import java.util.Objects;
import java.util.Optional;

import dev.goldberry.Host;
import dev.goldberry.bind.Subscription;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.desktop.SystemTheme;
import dev.goldberry.render.tray.BackendTray;

/// A tray whose picture follows the desktop's light-or-dark setting — the handle
/// [Trays#show] gives back for a [TrayIcon.ThemePair].
///
/// A handle around the backend's rather than something a backend does, because
/// the setting is the [Host]'s to report and a backend tray knows no host. What
/// it adds is **one listener, owned**: closing the tray closes the subscription
/// with it, so an application that rebuilds its tray to change the menu —
/// which is the only way a tray menu changes — does not leave one listener
/// behind per rebuild, each holding two pictures and a dead tray (ADR-0501).
///
/// UI-thread confined, like the tray it wraps; the host tells its listeners on
/// that thread.
final class ThemedTray implements BackendTray {

    private final BackendTray tray;
    private final TrayIcon.ThemePair pair;

    /// The picture last handed to the platform, compared by **identity**: a
    /// [PixelBuffer] is a record, so two blank icons of one size are `equals`,
    /// and the pair's two members are the only buffers this ever holds.
    private PixelBuffer shown;

    private Subscription following = () -> {};

    private ThemedTray(BackendTray tray, TrayIcon.ThemePair pair, PixelBuffer shown) {
        this.tray = tray;
        this.pair = pair;
        this.shown = shown;
    }

    /// Wraps `tray`, which was just shown with `pair`'s picture for what `host`
    /// says now, and starts listening for the next change.
    static ThemedTray follow(Host host, BackendTray tray, TrayIcon.ThemePair pair) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(tray, "tray");
        Objects.requireNonNull(pair, "pair");
        var themed = new ThemedTray(tray, pair, pair.on(host.systemTheme()));
        themed.following = host.onSystemThemeChanged(themed::themeChanged);
        return themed;
    }

    private void themeChanged(SystemTheme theme) {
        if (tray.isClosed()) {
            // Taken down underneath this handle — a backend closes every tray it
            // still has when it shuts down. Nothing left to swap, so stop asking.
            following.close();
            return;
        }
        var next = pair.on(Optional.of(theme));
        if (next != shown) {
            // Only on a real swap: on Linux every `SDL_SetTrayIcon` writes a PNG
            // for AppIndicator to read back.
            shown = next;
            tray.icon(next);
        }
    }

    /// Shows `icon` instead, and **stops following** the setting: an application
    /// that sets a picture of its own — a badge, a busy state — has taken the
    /// icon over, and a swap at dusk would quietly undo it.
    @Override
    public void icon(PixelBuffer icon) {
        following.close();
        tray.icon(icon);
    }

    @Override
    public void tooltip(String tooltip) {
        tray.tooltip(tooltip);
    }

    @Override
    public boolean isClosed() {
        return tray.isClosed();
    }

    @Override
    public void close() {
        following.close();
        tray.close();
    }

    @Override
    public String toString() {
        return "ThemedTray[" + tray + "]";
    }
}
