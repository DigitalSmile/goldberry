package io.github.digitalsmile.goldberry.render.tray;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.render.PixelBuffer;

/// What a tray icon is: a picture, a hover text, and a menu.
///
/// The icon is an ordinary [PixelBuffer], which the toolkit allocates in exactly
/// one format — premultiplied BGRA — so there is nothing to check and nothing to
/// convert. It *is* an ordinary raster: an application
/// paints it with Blend2D the way it paints anything, and the platform converts
/// it on the way in. Null means "whatever the desktop uses for an application
/// that supplied none", which is a real answer on Windows and macOS and a blank
/// square on some Linux shells.
///
/// **Nothing here is in logical pixels.** A tray icon is not on a Goldberry
/// window and does not inherit its scale; the shell picks a size and rescales,
/// and 32×32 or 64×64 physical is what every platform's guidance asks for.
///
/// @param icon    the icon's pixels, or null for the platform's default
/// @param tooltip the hover text, or null for none — not every platform shows one
/// @param items   the menu's rows, possibly empty
public record TraySpec(@Nullable PixelBuffer icon, String tooltip, List<TrayItem> items) {

    public TraySpec {
        items = List.copyOf(items == null ? List.of() : items);
        if (tooltip != null && tooltip.isBlank()) {
            // A blank tooltip is a tooltip that draws an empty box on hover,
            // which is worse than the none it was meant to be.
            tooltip = null;
        }
    }

    /// A tray with a menu and no icon of its own.
    public static TraySpec of(String tooltip, List<TrayItem> items) {
        return new TraySpec(null, tooltip, items);
    }

    /// The same tray, with every row running `after` once its own handler has —
    /// see [TrayItem#andThen].
    public TraySpec andThen(Runnable after) {
        return new TraySpec(
                icon, tooltip, items.stream().map(i -> i.andThen(after)).toList());
    }

    /// The same tray, with a different icon — what a theme switch builds.
    public TraySpec icon(PixelBuffer value) {
        return new TraySpec(value, tooltip, items);
    }
}
