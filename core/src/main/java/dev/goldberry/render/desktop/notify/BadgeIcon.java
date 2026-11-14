package dev.goldberry.render.desktop.notify;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.assets.BundledFont;
import dev.goldberry.image.Image;
import dev.goldberry.paint.Frame;
import dev.goldberry.paint.Path;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.model.DisplayScale;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.render.model.PixelFormat;
import dev.goldberry.text.font.Font;

/// The picture a Windows taskbar button shows as its badge: the label, white
/// and centred, on a red disc, drawn by the toolkit's own painter.
///
/// macOS draws its dock badge itself from the text, and a Linux dock from the
/// number. Windows takes an icon, so the toolkit draws one. The shell scales it
/// to the small-icon size of the display.
final class BadgeIcon {

    /// The side of the picture, in pixels: twice the small-icon size at 100 %,
    /// so it stays sharp at 200 %.
    static final int SIZE = 32;

    /// Fluent's red, which Windows' own badges use.
    static final int RED = 0xFFD13438;

    static final int WHITE = 0xFFFFFFFF;

    /// Inter's cap height, in ems: 1490 of 2048 units.
    private static final double CAP_HEIGHT = 1490.0 / 2048;

    /// The most characters a badge holds before it stops being legible at
    /// sixteen pixels.
    private static final int MOST = 3;

    private BadgeIcon() {}

    /// A picture: `width` × `height` straight-alpha `0xAARRGGBB` pixels, row
    /// by row. Made once and handed straight to the taskbar, so the array is
    /// the picture's own and nobody else holds it.
    static final class Picture {

        private final int width;
        private final int height;
        private final int[] argb;

        Picture(int width, int height, int[] argb) {
            this.width = width;
            this.height = height;
            this.argb = Objects.requireNonNull(argb, "argb");
        }

        int width() {
            return width;
        }

        int height() {
            return height;
        }

        int[] argb() {
            return argb;
        }

        int at(int x, int y) {
            return argb[y * width + x];
        }
    }

    /// What a label shows: nothing for null, blank or zero, which takes the
    /// badge away as a Linux dock does; `99+` past ninety-nine; any other text
    /// cut to three characters.
    static String text(@Nullable String label) {
        if (label == null || label.isBlank()) {
            return "";
        }
        var text = label.strip();
        if (text.chars().allMatch(Character::isDigit)) {
            var digits = text.replaceFirst("^0+", "");
            if (digits.isEmpty()) {
                return "";
            }
            return digits.length() > 2 ? "99+" : digits;
        }
        var end = text.offsetByCodePoints(0, Math.min(MOST, text.codePointCount(0, text.length())));
        return text.substring(0, end);
    }

    /// The badge for `text`, which [#text] has already shortened.
    static Picture render(String text) {
        var buffer = PixelBuffer.allocate(new PhysicalSize(SIZE, SIZE), PixelFormat.BGRA32_PREMULTIPLIED);
        var frame = Frame.over(buffer, DisplayScale.ONE);
        try (var font = Font.bundled(BundledFont.UI_STRONG, fontSize(text))) {
            try {
                var centre = SIZE / 2.0;
                frame.fillPath(Path.circle(centre, centre, centre), RED);
                if (!text.isEmpty()) {
                    var width = font.widthOf(text);
                    // Digits stand on the baseline and reach the cap height,
                    // so that is what is centred, not the line's whole box.
                    var baseline = centre + font.size() * CAP_HEIGHT / 2;
                    font.draw(frame, centre - width / 2, baseline, text, WHITE);
                }
            } finally {
                // Joins Blend2D's workers before the font they draw with goes.
                frame.end();
            }
        }
        var image = Image.of(buffer);
        var argb = new int[SIZE * SIZE];
        for (var y = 0; y < SIZE; y++) {
            for (var x = 0; x < SIZE; x++) {
                argb[y * SIZE + x] = image.argb(x, y);
            }
        }
        return new Picture(SIZE, SIZE, argb);
    }

    /// Smaller as the text gets longer, so three characters still fit inside
    /// the disc.
    static double fontSize(String text) {
        return switch (text.codePointCount(0, text.length())) {
            case 0, 1 -> SIZE * 0.62;
            case 2 -> SIZE * 0.5;
            default -> SIZE * 0.38;
        };
    }
}
