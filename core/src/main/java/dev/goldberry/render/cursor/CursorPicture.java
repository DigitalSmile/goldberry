package dev.goldberry.render.cursor;

import java.util.Objects;

import dev.goldberry.render.window.IconImage;

/// One size of a cursor's picture, and the pixel of it that points.
///
/// Straight alpha, as a window's icon is: a cursor leaves the toolkit for the
/// platform, which reads it that way.
///
/// Read more: [Input and focus](https://goldberry.dev/docs/guide/input.html#the-cursor).
///
/// @param image the pixels
/// @param hotX  the hot spot's column, from the left
/// @param hotY  its row, from the top
public record CursorPicture(IconImage image, int hotX, int hotY) {

    public CursorPicture {
        Objects.requireNonNull(image, "image");
        var size = image.size();
        if (hotX < 0 || hotY < 0 || hotX >= size.width() || hotY >= size.height()) {
            throw new IllegalArgumentException("the hot spot (" + hotX + ", " + hotY + ") is not inside a "
                    + size.width() + "x" + size.height() + " cursor");
        }
    }

    /// How wide this size is, in pixels.
    public int width() {
        return image.size().width();
    }
}
