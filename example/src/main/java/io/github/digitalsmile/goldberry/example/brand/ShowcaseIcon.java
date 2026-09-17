package io.github.digitalsmile.goldberry.example.brand;

import java.util.List;
import java.util.stream.IntStream;

import io.github.digitalsmile.goldberry.image.Image;

/// The showcase's window icon: four tiles on a rounded square, computed rather
/// than shipped as PNGs (`docs/gaps.md` G40, ADR-0351).
///
/// Computed so the example needs no binary assets to show
/// [io.github.digitalsmile.goldberry.Application#icon()] working, and **drawn at
/// every size** rather than scaled from one, which is the reason the method takes
/// a list. At 16 pixels the gaps between the tiles are one pixel wide. At 256 they
/// are sixteen.
///
/// Coverage is computed per pixel from a signed distance to each shape, so the
/// edges are anti-aliased in straight alpha, which is what the method is handed.
public final class ShowcaseIcon {

    /// The sizes a desktop asks for: a Windows taskbar at 100% and 200%, a dock,
    /// and a switcher.
    private static final int[] SIZES = {16, 32, 48, 256};

    /// Nord's polar night for the plate, and four of its aurora colours for the
    /// tiles, clockwise from the top left.
    private static final int PLATE = 0xFF2E3440;

    private static final int[] TILES = {0xFF88C0D0, 0xFFA3BE8C, 0xFFEBCB8B, 0xFFB48EAD};

    private ShowcaseIcon() {}

    /// Every size, smallest first.
    public static List<Image> sizes() {
        return IntStream.of(SIZES).mapToObj(ShowcaseIcon::at).toList();
    }

    /// The icon drawn at `size` pixels square.
    public static Image at(int size) {
        var pixels = new int[size * size];
        var unit = size / 16.0;
        var plateRadius = 3.5 * unit;
        var gap = unit;
        var inset = 2.5 * unit;
        var tile = (size - 2 * inset - gap) / 2;
        for (var y = 0; y < size; y++) {
            for (var x = 0; x < size; x++) {
                var px = x + 0.5;
                var py = y + 0.5;
                var plate = coverage(roundedBox(px, py, 0, 0, size, size, plateRadius));
                var colour = PLATE;
                var tileCoverage = 0.0;
                for (var row = 0; row < 2; row++) {
                    for (var column = 0; column < 2; column++) {
                        var left = inset + column * (tile + gap);
                        var top = inset + row * (tile + gap);
                        var covered = coverage(roundedBox(px, py, left, top, tile, tile, unit));
                        if (covered > tileCoverage) {
                            tileCoverage = covered;
                            colour = TILES[row * 2 + (row == 0 ? column : 1 - column)];
                        }
                    }
                }
                pixels[y * size + x] = over(colour, tileCoverage, PLATE, plate);
            }
        }
        return Image.ofArgb(size, size, pixels);
    }

    /// The signed distance from a point to a rounded rectangle: negative inside.
    private static double roundedBox(
            double px, double py, double left, double top, double width, double height, double radius) {
        var cx = px - (left + width / 2);
        var cy = py - (top + height / 2);
        var qx = Math.abs(cx) - (width / 2 - radius);
        var qy = Math.abs(cy) - (height / 2 - radius);
        var outside = Math.hypot(Math.max(qx, 0), Math.max(qy, 0));
        return outside + Math.min(Math.max(qx, qy), 0) - radius;
    }

    /// How much of a pixel a shape covers, from its distance to the pixel's centre.
    private static double coverage(double distance) {
        return Math.clamp(0.5 - distance, 0, 1);
    }

    /// A tile over the plate, both at their own coverage, in straight alpha.
    private static int over(int tile, double tileCoverage, int plate, double plateCoverage) {
        var alpha = plateCoverage;
        if (alpha <= 0) {
            return 0;
        }
        // The tile sits wholly inside the plate, so where it covers it is the
        // colour and the plate's coverage is the alpha.
        var red = mix(plate >> 16 & 0xFF, tile >> 16 & 0xFF, tileCoverage);
        var green = mix(plate >> 8 & 0xFF, tile >> 8 & 0xFF, tileCoverage);
        var blue = mix(plate & 0xFF, tile & 0xFF, tileCoverage);
        return (int) Math.round(alpha * 255) << 24 | red << 16 | green << 8 | blue;
    }

    private static int mix(int from, int to, double t) {
        return (int) Math.round(from + (to - from) * t);
    }
}
