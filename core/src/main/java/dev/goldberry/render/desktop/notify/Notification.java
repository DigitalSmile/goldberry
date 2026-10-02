package dev.goldberry.render.desktop.notify;

import java.nio.file.Path;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/// A desktop notification: a title, a line or two under it, and what to do if
/// the user clicks it.
///
/// ```java
/// host.notify(Notification.of("Gate waiting", "prod-eu needs an approval")
///         .onActivate(() -> router.show(gates)));
/// ```
///
/// The desktop draws it, in its own place, style and timing — GNOME's banner,
/// macOS's Notification Center, Windows' toast — so there is nothing here to
/// style and no widget to put in it. A value, like a tray's spec; showing one
/// is [dev.goldberry.Host#notify].
///
/// Read more: [Notifications](https://goldberry.dev/docs/guide/windows.html#notifications).
///
/// @param title      the summary line, never blank
/// @param body       the text under it, possibly empty. Plain text: a Linux
///                   daemon may render a little markup, so `<` and `&` are
///                   escaped for it
/// @param icon       an image file to show beside it, or null for the
///                   application's own. Linux only; macOS and Windows show the
///                   application's icon whatever this says
/// @param onActivate what runs, on the UI thread, when the user clicks it, or
///                   null for a notification that only informs. Linux and macOS
///                   only; Windows does not report the click
public record Notification(
        String title,
        String body,
        @Nullable Path icon,
        @Nullable Runnable onActivate) {

    /// Written out so that the parameters taking null can say so.
    public Notification(String title, String body, @Nullable Path icon, @Nullable Runnable onActivate) {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(body, "body");
        if (title.isBlank()) {
            throw new IllegalArgumentException("a notification needs a title; the body alone is not shown everywhere");
        }
        this.title = title;
        this.body = body;
        this.icon = icon;
        this.onActivate = onActivate;
    }

    /// A notification that only informs.
    public static Notification of(String title, String body) {
        return new Notification(title, body, null, null);
    }

    /// The same notification, with an image beside it.
    public Notification icon(@Nullable Path value) {
        return new Notification(title, body, value, onActivate);
    }

    /// The same notification, running `action` when the user clicks it.
    public Notification onActivate(@Nullable Runnable action) {
        return new Notification(title, body, icon, action);
    }

    /// The same notification, running `after` once its own action has: how
    /// the host gets a frame after a click that changed state, because the
    /// click arrives with no event behind it.
    public Notification andThen(Runnable after) {
        Objects.requireNonNull(after, "after");
        if (onActivate == null) {
            return this;
        }
        var action = onActivate;
        return onActivate(() -> {
            try {
                action.run();
            } finally {
                after.run();
            }
        });
    }

    /// The body as a Linux daemon should be sent it: the three characters its
    /// markup subset reserves, escaped.
    public String escapedBody() {
        return body.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
