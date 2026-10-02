package dev.goldberry.render.desktop.menubar;

import java.util.List;

/// The backend SPI's application menu bar: the platform's own, at the top of
/// the screen, where there is one.
///
/// Only macOS has one. Everywhere else the answer is [#NONE], and a `menubar`
/// widget stays the bar inside the window it always was.
///
/// Read more: [Menus and the tray](https://goldberry.dev/docs/components/menus.html#the-macos-menu-bar).
public interface BackendMenuBar {

    /// No platform menu bar: shows nothing and says so.
    BackendMenuBar NONE = new BackendMenuBar() {
        @Override
        public boolean show(String application, List<AppMenuItem> headings) {
            return false;
        }

        @Override
        public void clear() {}
    };

    /// Makes `headings` the application's menu bar, after the platform's own
    /// application menu named `application`.
    ///
    /// @return whether the platform shows it; false means the caller draws the
    ///         bar itself
    boolean show(String application, List<AppMenuItem> headings);

    /// Puts back the menu bar that was there before [#show].
    void clear();
}
