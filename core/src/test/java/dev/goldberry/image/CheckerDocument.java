package dev.goldberry.image;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.render.model.PhysicalSize;

/// A scalable image for tests: a checkerboard of one-pixel cells at any size,
/// black where `x + y` is even and white where it is odd. Drawn from a raster of
/// the device size, it alternates on every device pixel; stretched from a
/// raster of another size, it does not, so sharpness is a pixel to read.
///
/// Remembers every size it was asked for, and refuses any wider than `widest`.
final class CheckerDocument implements ScalableImage {

    static final int BLACK = 0xFF000000;
    static final int WHITE = 0xFFFFFFFF;

    private final LogicalSize natural;
    private final int widest;
    private final List<PhysicalSize> asked = Collections.synchronizedList(new ArrayList<>());

    CheckerDocument(float width, float height) {
        this(width, height, Integer.MAX_VALUE);
    }

    CheckerDocument(float width, float height, int widest) {
        this.natural = new LogicalSize(width, height);
        this.widest = widest;
    }

    @Override
    public LogicalSize naturalSize() {
        return natural;
    }

    @Override
    public Image render(PhysicalSize size) {
        Objects.requireNonNull(size, "size");
        asked.add(size);
        if (size.width() > widest) {
            throw new IllegalArgumentException("wider than " + widest);
        }
        var argb = new int[size.width() * size.height()];
        for (var y = 0; y < size.height(); y++) {
            for (var x = 0; x < size.width(); x++) {
                argb[y * size.width() + x] = (x + y) % 2 == 0 ? BLACK : WHITE;
            }
        }
        return Image.ofArgb(size.width(), size.height(), argb);
    }

    /// Every size asked for, in order.
    List<PhysicalSize> asked() {
        synchronized (asked) {
            return List.copyOf(asked);
        }
    }

    /// Whether `image` is the checkerboard, pixel for pixel.
    static boolean isChecker(Image image) {
        for (var y = 0; y < image.height(); y++) {
            for (var x = 0; x < image.width(); x++) {
                if (image.argb(x, y) != ((x + y) % 2 == 0 ? BLACK : WHITE)) {
                    return false;
                }
            }
        }
        return true;
    }
}
