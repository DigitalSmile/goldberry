package dev.goldberry.widgets.shell.tray;

import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.desktop.SystemTheme;
import dev.goldberry.render.tray.TraySpec;
import dev.goldberry.widgets.menu.Menu;

/// An icon in the desktop's notification area, with a tooltip and a menu the
/// desktop's shell draws when it is clicked.
///
/// ```java
/// var tray = Trays.show(host, TrayIcon.of("Goldberry", new Menu(List.of(
///         new Item("Open", app::open),
///         new Separator(),
///         new Item("Quit", app::quit)))).icons(onLightShell, onDarkShell));
/// // ...
/// tray.ifPresent(BackendTray::close);
/// ```
///
/// A value, not a widget, and there is no markup node for it: the shell owns
/// the pixels, the font, the spacing and the click, so there is no box to lay
/// out, no style to compute and no event to route. What it shares with the
/// catalogue is its **menu**: the same [Menu] of `item`s and `separator`s a
/// `menubar` holds, translated into the platform's rows by [Trays]. One
/// description can be shown in a window, in a context menu or in the tray.
///
/// A picture is in physical pixels, 32×32 or 64×64, because the icon sits on
/// the desktop's panel and inherits no window's scale. With [#icons] the tray
/// follows the desktop's light-or-dark setting while it is up.
///
/// Read more: [Menus and the tray](https://goldberry.dev/docs/components/menus.html#the-tray-icon).
///
/// @param picture what the shell shows, or null for whatever the desktop shows
///                for an application that supplied none
/// @param tooltip the hover text, or null — not every platform shows one
/// @param menu    the rows the shell draws when the icon is clicked
public record TrayIcon(@Nullable Picture picture, @Nullable String tooltip, Menu menu) {

    public TrayIcon {
        Objects.requireNonNull(menu, "menu");
    }

    /// What a tray shows: one picture, or one for each shade of shell.
    ///
    /// **Physical pixels**, both kinds: a tray icon is not on a Goldberry window
    /// and inherits no scale — see [TraySpec].
    public sealed interface Picture {

        /// The pixels to show while the desktop says `theme`, or says nothing.
        PixelBuffer on(Optional<SystemTheme> theme);
    }

    /// One picture, whatever the desktop is set to — an icon that reads on
    /// either background, or an application that follows the theme itself.
    ///
    /// @param pixels the icon
    public record Single(PixelBuffer pixels) implements Picture {

        public Single {
            Objects.requireNonNull(pixels, "pixels");
        }

        @Override
        public PixelBuffer on(Optional<SystemTheme> theme) {
            return pixels;
        }
    }

    /// Two pictures, each named for the **background** it is drawn to sit on;
    /// [Trays#show] swaps between them as the desktop's setting changes.
    ///
    /// Named for the shell rather than for the ink, because "the light icon" is
    /// ambiguous in exactly the way that matters: dark ink for a light panel, or
    /// a light mark for a dark one.
    ///
    /// @param forLightShell shown while the desktop says [SystemTheme#LIGHT], and
    ///                      while it says nothing, which is also how CSS reads
    ///                      "no preference"
    /// @param forDarkShell  shown while the desktop says [SystemTheme#DARK]
    public record ThemePair(PixelBuffer forLightShell, PixelBuffer forDarkShell) implements Picture {

        public ThemePair {
            Objects.requireNonNull(forLightShell, "forLightShell");
            Objects.requireNonNull(forDarkShell, "forDarkShell");
        }

        @Override
        public PixelBuffer on(Optional<SystemTheme> theme) {
            return theme.orElse(SystemTheme.LIGHT) == SystemTheme.DARK ? forDarkShell : forLightShell;
        }
    }

    /// A tray with a tooltip and a menu, and the platform's own icon.
    public static TrayIcon of(String tooltip, Menu menu) {
        return new TrayIcon(null, tooltip, menu);
    }

    /// The same tray with one picture, shown whatever the desktop is set to.
    public TrayIcon icon(PixelBuffer value) {
        return new TrayIcon(new Single(value), tooltip, menu);
    }

    /// The same tray with a picture for each shade of shell, kept matched to the
    /// desktop's setting by [Trays#show] for as long as the icon is up.
    ///
    /// The icon sits on the *desktop's* background and the cascade does not
    /// reach it, so this is the one place the swap can be said.
    ///
    /// @param forLightShell for a light panel — usually dark ink
    /// @param forDarkShell  for a dark panel — usually a light mark
    public TrayIcon icons(PixelBuffer forLightShell, PixelBuffer forDarkShell) {
        return new TrayIcon(new ThemePair(forLightShell, forDarkShell), tooltip, menu);
    }

    /// The same tray with different hover text.
    public TrayIcon tooltip(String value) {
        return new TrayIcon(picture, value, menu);
    }

    /// The pixels this tray shows while the desktop says `theme`, or null for the
    /// platform's own icon.
    public @Nullable PixelBuffer iconOn(Optional<SystemTheme> theme) {
        Objects.requireNonNull(theme, "theme");
        return picture == null ? null : picture.on(theme);
    }

    /// This tray as the backend SPI's description of one, on a desktop that says
    /// nothing about its theme.
    public TraySpec spec() {
        return spec(Optional.empty());
    }

    /// This tray as the backend SPI's description of one, showing the picture
    /// for `theme`.
    public TraySpec spec(Optional<SystemTheme> theme) {
        return new TraySpec(iconOn(theme), tooltip, Trays.rowsOf(menu));
    }
}
