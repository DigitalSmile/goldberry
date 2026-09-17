package io.github.digitalsmile.goldberry.natives.sdl.window;

import java.nio.ByteBuffer;
import java.util.Objects;

/// One size of a window's icon, as SDL wants it.
///
/// **Straight alpha**, not premultiplied. `SDL_PIXELFORMAT_ARGB8888` is
/// straight, and the X11 path copies these ints into `_NET_WM_ICON` as they are,
/// so a premultiplied buffer would dim every soft edge of the mark. The caller
/// unpremultiplies. This type only checks the shape (ADR-0351).
///
/// The layout is the tray icon's: native-order `0xAARRGGBB` ints, which on every
/// platform the toolkit ships is B, G, R, A in memory.
///
/// @param pixels a **direct** buffer, read from its position
/// @param width  in pixels
/// @param height in pixels
public record SdlIconImage(ByteBuffer pixels, int width, int height) {

    public SdlIconImage {
        Objects.requireNonNull(pixels, "pixels");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "an icon must have a positive size, and " + width + "x" + height + " does not");
        }
        if (!pixels.isDirect()) {
            throw new IllegalArgumentException(
                    "the pixel buffer must be direct: a heap buffer has no address SDL" + " could read");
        }
        var required = Math.multiplyExact(Math.multiplyExact((long) width, height), 4L);
        if (pixels.remaining() < required) {
            throw new IllegalArgumentException("a " + width + "x" + height + " icon needs " + required
                    + " bytes, and the buffer offers " + pixels.remaining());
        }
    }

    /// Bytes per row: tightly packed, four to a pixel.
    public int stride() {
        return Math.multiplyExact(width, 4);
    }
}
