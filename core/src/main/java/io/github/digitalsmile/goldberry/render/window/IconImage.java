package io.github.digitalsmile.goldberry.render.window;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

import io.github.digitalsmile.goldberry.render.model.PhysicalSize;

/// One size of a window's icon: pixels in **straight** alpha, as a platform wants
/// an icon, rather than the premultiplied form the toolkit paints in.
///
/// A window icon leaves the toolkit, so it goes in the form the desktop reads.
/// X11's `_NET_WM_ICON`, a Windows `HICON` and an `xdg-toplevel-icon` buffer all
/// take straight ARGB. Premultiplied pixels would dim every anti-aliased edge of
/// the mark by its own coverage (`docs/gaps.md` G40, ADR-0351).
///
/// The buffer is direct and native-ordered `0xAARRGGBB` ints, tightly packed,
/// which is what a backend can hand to the platform without another copy.
public final class IconImage {

    private final PhysicalSize size;
    private final ByteBuffer pixels;

    private IconImage(PhysicalSize size, ByteBuffer pixels) {
        this.size = size;
        this.pixels = pixels;
    }

    /// What reads one straight-alpha pixel of a picture.
    @FunctionalInterface
    public interface Source {

        /// The pixel at (`x`, `y`), as `0xAARRGGBB`, not premultiplied.
        int argb(int x, int y);
    }

    /// An icon of `width` by `height`, read pixel by pixel from `source`.
    ///
    /// @throws IllegalArgumentException if the size is not positive
    public static IconImage of(int width, int height, Source source) {
        Objects.requireNonNull(source, "source");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "an icon needs a positive size, and " + width + "x" + height + " is not");
        }
        var pixels = ByteBuffer.allocateDirect(Math.multiplyExact(Math.multiplyExact(width, height), 4))
                .order(ByteOrder.nativeOrder());
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                pixels.putInt(source.argb(x, y));
            }
        }
        return new IconImage(new PhysicalSize(width, height), pixels.flip());
    }

    /// How big this size of the icon is.
    public PhysicalSize size() {
        return size;
    }

    /// The pixels, read-only, positioned at the first one.
    public ByteBuffer pixels() {
        return pixels.asReadOnlyBuffer().order(ByteOrder.nativeOrder());
    }

    /// The pixel at (`x`, `y`), as it was read — what a test compares.
    public int argb(int x, int y) {
        Objects.checkIndex(x, size.width());
        Objects.checkIndex(y, size.height());
        return pixels.getInt((y * size.width() + x) * 4);
    }

    @Override
    public String toString() {
        return "IconImage[" + size.width() + "x" + size.height() + "]";
    }
}
