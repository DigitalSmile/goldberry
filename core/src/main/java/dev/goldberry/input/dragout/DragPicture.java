package dev.goldberry.input.dragout;

import java.util.Objects;

import dev.goldberry.render.PixelBuffer;

/// The picture the platform draws under the pointer while a drag is out.
///
/// Without one, the toolkit hands over the source as it was painted, faded,
/// with the pointer where it took hold of it. An offer that wants something
/// else, a thumbnail of the file rather than the row that names it, gives its
/// own with [DragOffer#withPicture(DragPicture)].
///
/// @param pixels   premultiplied BGRA, at the window's scale
/// @param hotspotX where the pointer is in the picture, in physical pixels from its left edge
/// @param hotspotY where the pointer is in the picture, in physical pixels from its top edge
public record DragPicture(PixelBuffer pixels, int hotspotX, int hotspotY) {

    public DragPicture {
        Objects.requireNonNull(pixels, "pixels");
        var width = pixels.size().width();
        var height = pixels.size().height();
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("a drag picture needs at least one pixel, not " + pixels.size());
        }
        if (hotspotX < 0 || hotspotY < 0 || hotspotX >= width || hotspotY >= height) {
            throw new IllegalArgumentException(
                    "the hotspot " + hotspotX + "," + hotspotY + " is outside a " + width + "x" + height + " picture");
        }
    }
}
