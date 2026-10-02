package dev.goldberry.render.desktop.notify;

import org.jspecify.annotations.Nullable;

/// The backend SPI's notifications and badge: what posts a [Notification] and
/// sets the number on the application's dock or launcher icon.
///
/// Process-wide, like the tray: a notification is the application's, not a
/// window's. The headless backend answers [#NONE], which shows nothing, so a
/// test never puts a notification on the desktop of whoever runs it.
///
/// Every method fails soft. A desktop with no notification service, a macOS
/// process that is not an application bundle, a dock that does not show
/// badges: each is false, never an exception.
///
/// Read more: [Notifications](https://goldberry.dev/docs/guide/windows.html#notifications).
public interface BackendNotifier extends AutoCloseable {

    /// Shows nothing and sets nothing.
    BackendNotifier NONE = new BackendNotifier() {
        @Override
        public boolean post(String application, Notification notification) {
            return false;
        }

        @Override
        public boolean badge(@Nullable String label) {
            return false;
        }
    };

    /// Shows `notification`.
    ///
    /// @param application the application's name, which some desktops show
    ///                    beside it
    /// @return whether the desktop took it
    boolean post(String application, Notification notification);

    /// Sets the badge on the application's dock or launcher icon, or takes it
    /// away for null or empty.
    ///
    /// macOS shows any short text; a Linux launcher shows a number, so there
    /// the label must be one; Windows has no badge this sets.
    ///
    /// @return whether the desktop was told
    boolean badge(@Nullable String label);

    /// Reads what the desktop has said since the last call and runs the
    /// actions of the notifications the user clicked.
    ///
    /// Only Linux needs asking — a click there is a D-Bus signal that waits to
    /// be read — and the default does nothing.
    ///
    /// @return whether a posted notification could still be clicked, and so
    ///         whether to ask again
    default boolean poll() {
        return false;
    }

    /// Lets go of whatever the notifier holds — a bus connection, an icon in
    /// the notification area.
    @Override
    default void close() {}
}
