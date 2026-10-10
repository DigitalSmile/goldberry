package dev.goldberry.render.cursor;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import dev.goldberry.render.Cursor;

/// Every size of one cursor shape's picture, smallest first.
///
/// The smallest is the shape at 100%. The others are the same picture for
/// larger display scales: a 32-pixel cursor drawn again at 48, 64 and 96 is
/// one for 100%, 150%, 200% and 300%.
///
/// Read more: [Input and focus](https://goldberry.dev/docs/guide/input.html#the-cursor).
///
/// @param shape the shape the pictures are shown for
/// @param sizes at least one, in any order; kept smallest first
public record CursorPictures(Cursor shape, List<CursorPicture> sizes) {

    public CursorPictures {
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(sizes, "sizes");
        if (sizes.isEmpty()) {
            throw new IllegalArgumentException("the " + shape + " cursor needs at least one picture");
        }
        sizes = sizes.stream()
                .sorted(Comparator.comparingInt(CursorPicture::width))
                .toList();
        for (var i = 1; i < sizes.size(); i++) {
            if (sizes.get(i).width() == sizes.get(i - 1).width()) {
                throw new IllegalArgumentException("the " + shape + " cursor has two pictures "
                        + sizes.get(i).width() + " pixels wide");
            }
        }
    }

    /// The picture at 100%: the smallest.
    public CursorPicture base() {
        return sizes.getFirst();
    }

    /// The picture to show at a display scale of `scale`, for a platform that
    /// shows a cursor's pixels as they are: the one whose width is nearest the
    /// base's times `scale`, the larger of two that are as near.
    public CursorPicture nearest(double scale) {
        var wanted = base().width() * scale;
        var best = base();
        for (var picture : sizes) {
            var distance = Math.abs(picture.width() - wanted);
            var bestDistance = Math.abs(best.width() - wanted);
            if (distance <= bestDistance) {
                best = picture;
            }
        }
        return best;
    }
}
