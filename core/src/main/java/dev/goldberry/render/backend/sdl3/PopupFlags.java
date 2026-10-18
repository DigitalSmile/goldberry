package dev.goldberry.render.backend.sdl3;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import dev.goldberry.natives.sdl.window.SdlWindowFlag;
import dev.goldberry.render.popup.PopupKind;

/// The SDL window flags a popup of each kind is created with.
///
/// Exactly one kind flag, `POPUP_MENU` or `TOOLTIP`, because SDL refuses a
/// popup that claims to be both. `TOOLTIP` alone does not stop a popup taking
/// focus; `NOT_FOCUSABLE` is a separate flag.
///
/// **No popup of any kind takes the platform keyboard.** `NOT_FOCUSABLE` on all
/// three reads like a restriction and is the opposite. A popup never relies on
/// having focus: the owner forwards keys to whatever popup is open, so a menu is
/// operable by arrows either way. And every driver SDL ships hands a popup the
/// keyboard on show, and back to its parent on hide, only when it is focusable
/// (X11, Wayland, Windows and Cocoa alike), so a popup that is not leaves the
/// platform's focus on the owner from start to finish. Nothing has to be given
/// back when it closes, and opening one sends no focus-lost for the owner.
///
/// That is also what `anyWindowFocused` relies on to decide a popup has been
/// left behind: the application is focused exactly when one of its own windows
/// is. A focusable popup could hold that answer true after the user had
/// switched to another application, and the menu stayed on screen over
/// somebody else's window.
final class PopupFlags {

    private PopupFlags() {}

    /// The flags for a popup of `kind`, as a fresh set the caller may add to.
    static Set<SdlWindowFlag> of(PopupKind kind) {
        Objects.requireNonNull(kind, "kind");
        var flags = EnumSet.of(
                SdlWindowFlag.HIGH_PIXEL_DENSITY,
                SdlWindowFlag.HIDDEN,
                SdlWindowFlag.NOT_FOCUSABLE,
                SdlWindowFlag.TRANSPARENT);
        flags.add(
                switch (kind) {
                    // A menu window in everything the window manager cares
                    // about: it takes the pointer, which is what tells an
                    // attached panel apart from a tooltip.
                    case MENU, ATTACHED -> SdlWindowFlag.POPUP_MENU;
                    case TOOLTIP -> SdlWindowFlag.TOOLTIP;
                });
        return flags;
    }
}
