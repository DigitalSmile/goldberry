package dev.goldberry.natives.sdl.desktop;

import java.util.List;
import java.util.Objects;

import dev.goldberry.natives.sdl.window.SdlIconImage;

/// A cursor drawn from a picture, as `SDL_CreateColorCursor` wants it.
///
/// The first image is the cursor at 100%, and the hot spot is in its pixels.
/// The rest are the same picture at larger sizes, hung off it as the surface's
/// alternates: SDL picks one per display scale on Wayland and macOS, and on
/// Windows with `SDL_MOUSE_DPI_SCALE_CURSORS` on. Straight alpha, as a window
/// icon's is.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
///
/// @param images the 100% picture first, then any larger ones
/// @param hotX   the hot spot's column in the first picture
/// @param hotY   its row
public record SdlColorCursor(List<SdlIconImage> images, int hotX, int hotY) {

    public SdlColorCursor {
        images = List.copyOf(Objects.requireNonNull(images, "images"));
        if (images.isEmpty()) {
            throw new IllegalArgumentException("a cursor needs at least one picture");
        }
        var base = images.getFirst();
        if (hotX < 0 || hotY < 0 || hotX >= base.width() || hotY >= base.height()) {
            throw new IllegalArgumentException("the hot spot (" + hotX + ", " + hotY + ") is not inside a "
                    + base.width() + "x" + base.height() + " cursor");
        }
    }
}
