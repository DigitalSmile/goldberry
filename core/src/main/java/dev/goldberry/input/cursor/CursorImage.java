package dev.goldberry.input.cursor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import dev.goldberry.image.Image;
import dev.goldberry.render.Cursor;
import dev.goldberry.render.cursor.CursorPicture;
import dev.goldberry.render.cursor.CursorPictures;
import dev.goldberry.render.window.IconImage;

/// A picture for a cursor shape, at one size, with the pixel of it that points.
///
/// ```java
/// @Override public List<CursorImage> cursors() {
///     return List.of(
///             new CursorImage(Cursor.GRAB, Image.decode(read("cursors/grab-32.png")), 12, 4),
///             new CursorImage(Cursor.GRAB, Image.decode(read("cursors/grab-64.png")), 24, 8));
/// }
/// ```
///
/// A shape may be given several sizes. The smallest is the shape at 100%, and
/// the others are the same picture for larger display scales: a shape drawn
/// at 32, 48, 64 and 96 pixels has one for 100%, 150%, 200% and 300%. The
/// toolkit shows the one the display's scale wants. Each size has its own hot
/// spot, in its own pixels, because a picture drawn again at another size
/// rarely puts the tip at exactly the scaled place.
///
/// Read more: [Input and focus](https://goldberry.dev/docs/guide/input.html#the-cursor).
///
/// @param shape   the shape this picture is shown for
/// @param picture the picture, of any size
/// @param hotX    the hot spot's column in `picture`, from the left
/// @param hotY    its row, from the top
public record CursorImage(Cursor shape, Image picture, int hotX, int hotY) {

    public CursorImage {
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(picture, "picture");
        if (hotX < 0 || hotY < 0 || hotX >= picture.width() || hotY >= picture.height()) {
            throw new IllegalArgumentException("the hot spot (" + hotX + ", " + hotY + ") of the " + shape
                    + " cursor is not inside its " + picture.width() + "x" + picture.height() + " picture");
        }
    }

    /// `images`, gathered by shape for a backend: every size of a shape
    /// together, in the order the shapes were first named.
    ///
    /// @throws IllegalArgumentException if a shape is given two pictures of one
    ///         width
    public static List<CursorPictures> byShape(List<CursorImage> images) {
        Objects.requireNonNull(images, "images");
        // Insertion-ordered, so the shapes keep the order they were first named in.
        var shapes = new LinkedHashMap<Cursor, List<CursorPicture>>();
        for (var image : images) {
            shapes.computeIfAbsent(image.shape(), _ -> new ArrayList<>()).add(image.toPicture());
        }
        return shapes.entrySet().stream()
                .map(entry -> new CursorPictures(entry.getKey(), entry.getValue()))
                .toList();
    }

    /// This picture as a backend wants it: straight alpha, read once.
    private CursorPicture toPicture() {
        return new CursorPicture(IconImage.of(picture.width(), picture.height(), picture::argb), hotX, hotY);
    }
}
