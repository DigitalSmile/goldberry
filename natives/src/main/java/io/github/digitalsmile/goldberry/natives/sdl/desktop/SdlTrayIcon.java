package io.github.digitalsmile.goldberry.natives.sdl.desktop;

import java.nio.ByteBuffer;
import java.util.Objects;

/// The pixels a tray icon is drawn from.
///
/// The same shape a frame arrives in — premultiplied BGRA, a stride that may
/// exceed the row — because it *is* a frame: Goldberry paints the icon with
/// Blend2D like everything else, at whatever size the tray asked for, and hands
/// over the buffer.
///
/// **Nothing is copied here.** SDL wraps the buffer as a surface and the platform
/// converts it immediately — a `HICON` on Windows, an `NSImage` on macOS, a file
/// in the user's cache directory on Linux — so the buffer has to outlive the
/// [SdlTray#open] call and no longer. That is checked against the pinned SDL
/// source rather than assumed, and it is why the surface is destroyed as soon as
/// the create returns.
///
/// @param pixels a **direct** buffer in BGRA memory order
/// @param width  in pixels
/// @param height in pixels
/// @param stride bytes per row, which may exceed `width * 4`
public record SdlTrayIcon(ByteBuffer pixels, int width, int height, int stride) {

    public SdlTrayIcon {
        Objects.requireNonNull(pixels, "pixels");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "an icon must have a positive size, and " + width + "x" + height + " does not");
        }
        if (stride < Math.multiplyExact(width, 4)) {
            throw new IllegalArgumentException(
                    "a stride of " + stride + " cannot hold a row of " + width
                            + " 32-bit pixels, which needs " + (width * 4));
        }
        if (!pixels.isDirect()) {
            throw new IllegalArgumentException(
                    "the pixel buffer must be direct: a heap buffer has no address SDL could read,"
                            + " and copying it would hand over pixels nobody painted");
        }
        // The last row needs no padding after it, which is why this is one row
        // short of stride * height -- the other arithmetic rejects a legal
        // tightly-packed buffer.
        var required = Math.addExact(
                Math.multiplyExact((long) stride, height - 1), Math.multiplyExact(width, 4L));
        if (pixels.remaining() < required) {
            throw new IllegalArgumentException(
                    "a " + width + "x" + height + " icon at stride " + stride + " needs "
                            + required + " bytes, and the buffer offers " + pixels.remaining());
        }
    }

    /// A tightly packed icon.
    public static SdlTrayIcon of(ByteBuffer pixels, int width, int height) {
        return new SdlTrayIcon(pixels, width, height, Math.multiplyExact(width, 4));
    }
}
