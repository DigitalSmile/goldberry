package dev.goldberry.example.brand;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import dev.goldberry.image.Image;
import dev.goldberry.input.cursor.CursorImage;
import dev.goldberry.render.Cursor;

/// The showcase's own cursors: an open hand for `grab` and a closed one for
/// `grabbing`, the two shapes no platform has a system cursor for.
///
/// Computed rather than shipped as PNGs, for [ShowcaseIcon]'s reason, and
/// **drawn at every size** rather than scaled from one: 32 pixels is the shape
/// at 100%, and 48, 64 and 96 are the same hand for 150%, 200% and 300%. Each
/// size puts its hot spot at the middle of the palm, in its own pixels.
///
/// A light hand with a dark outline, so it reads on a dark theme and a light
/// one alike. Coverage is computed per pixel from a signed distance to the
/// shape, in straight alpha.
///
/// Read more: [Input and focus](https://goldberry.dev/docs/guide/input.html#the-cursor).
public final class ShowcaseCursors {

    /// The sizes each shape is drawn at, smallest first.
    static final int[] SIZES = {32, 48, 64, 96};

    /// The grid the hands are designed on: 32 units across.
    private static final double GRID = 32;

    /// Where the hot spot is on the grid: the middle of the palm.
    private static final double HOT_X = 16;

    private static final double HOT_Y = 18;

    private static final int FILL = 0xFFECEFF4;

    private static final int OUTLINE = 0xFF2E3440;

    /// How thick the outline is, on the grid.
    private static final double OUTLINE_WIDTH = 1.25;

    private ShowcaseCursors() {}

    /// Both hands at every size, for [dev.goldberry.Application#cursors()].
    public static List<CursorImage> all() {
        return Stream.of(Cursor.GRAB, Cursor.GRABBING)
                .flatMap(shape -> IntStream.of(SIZES).mapToObj(size -> at(shape, size)))
                .toList();
    }

    /// One hand at `size` pixels square, with its hot spot.
    ///
    /// @throws IllegalArgumentException for a shape the showcase draws no
    ///         picture for
    public static CursorImage at(Cursor shape, int size) {
        var hand = switch (shape) {
            case GRAB -> Hand.OPEN;
            case GRABBING -> Hand.CLOSED;
            default -> throw new IllegalArgumentException("the showcase draws no " + shape + " cursor");
        };
        var unit = size / GRID;
        return new CursorImage(shape, draw(hand, size), (int) Math.round(HOT_X * unit), (int) Math.round(HOT_Y * unit));
    }

    /// The two hands, as the parts they are drawn from, back to front: the
    /// palm, then the thumb and each finger over it with an outline of its own,
    /// which is what draws the line between two fingers.
    private record Hand(List<Part> parts) {

        static final Hand OPEN = new Hand(List.of(
                new RoundedBox(9, 15, 14, 12, 3.5),
                new Capsule(10, 22, 5.5, 16.5, 1.9),
                // Four fingers held up, the middle two the longest.
                new Capsule(10.75, 9, 10.75, 16.5, 1.75),
                new Capsule(14.25, 6.5, 14.25, 16.5, 1.75),
                new Capsule(17.75, 6.5, 17.75, 16.5, 1.75),
                new Capsule(21.25, 9, 21.25, 16.5, 1.75)));

        static final Hand CLOSED = new Hand(List.of(
                new RoundedBox(9, 14, 14, 12, 3.5),
                new Capsule(10, 20, 8, 17, 1.9),
                // The fingers curled down to their knuckles.
                new Capsule(10.75, 12.5, 10.75, 15.5, 1.75),
                new Capsule(14.25, 11.5, 14.25, 15.5, 1.75),
                new Capsule(17.75, 11.5, 17.75, 15.5, 1.75),
                new Capsule(21.25, 12.5, 21.25, 15.5, 1.75)));
    }

    /// One part of a hand, on the grid.
    private sealed interface Part {

        /// The signed distance from a point on the grid to this part: negative
        /// inside.
        double distance(double x, double y);
    }

    /// A rounded rectangle: a palm.
    private record RoundedBox(double left, double top, double width, double height, double radius) implements Part {

        @Override
        public double distance(double x, double y) {
            var qx = Math.abs(x - (left + width / 2)) - (width / 2 - radius);
            var qy = Math.abs(y - (top + height / 2)) - (height / 2 - radius);
            var outside = Math.hypot(Math.max(qx, 0), Math.max(qy, 0));
            return outside + Math.min(Math.max(qx, qy), 0) - radius;
        }
    }

    /// A segment from `(ax, ay)` to `(bx, by)` grown by `radius`: a finger or a
    /// thumb.
    private record Capsule(double ax, double ay, double bx, double by, double radius) implements Part {

        @Override
        public double distance(double x, double y) {
            var dx = bx - ax;
            var dy = by - ay;
            var t = Math.clamp(((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy), 0, 1);
            return Math.hypot(x - (ax + t * dx), y - (ay + t * dy)) - radius;
        }
    }

    private static Image draw(Hand hand, int size) {
        var unit = size / GRID;
        var outline = Math.max(1, OUTLINE_WIDTH * unit);
        var pixels = new int[size * size];
        for (var y = 0; y < size; y++) {
            for (var x = 0; x < size; x++) {
                // The pixel's centre on the grid; distances come back in pixels.
                var gx = (x + 0.5) / unit;
                var gy = (y + 0.5) / unit;
                var pixel = Ink.NONE;
                for (var part : hand.parts()) {
                    var distance = part.distance(gx, gy) * unit;
                    var ink = coverage(distance - outline);
                    if (ink > 0) {
                        pixel = Ink.of(coverage(distance) / ink, ink).over(pixel);
                    }
                }
                pixels[y * size + x] = pixel.argb();
            }
        }
        return Image.ofArgb(size, size, pixels);
    }

    /// A colour in straight alpha, with its channels as fractions, so parts can
    /// be laid over each other before it is rounded to a pixel.
    private record Ink(double red, double green, double blue, double alpha) {

        static final Ink NONE = new Ink(0, 0, 0, 0);

        /// The fill over the outline at `fill` of the inked area, `alpha`
        /// opaque.
        static Ink of(double fill, double alpha) {
            return new Ink(
                    mix(OUTLINE >> 16 & 0xFF, FILL >> 16 & 0xFF, fill),
                    mix(OUTLINE >> 8 & 0xFF, FILL >> 8 & 0xFF, fill),
                    mix(OUTLINE & 0xFF, FILL & 0xFF, fill),
                    alpha);
        }

        /// This ink laid over `below`: source-over, in straight alpha.
        Ink over(Ink below) {
            var out = alpha + below.alpha * (1 - alpha);
            if (out <= 0) {
                return NONE;
            }
            var under = below.alpha * (1 - alpha);
            return new Ink(
                    (red * alpha + below.red * under) / out,
                    (green * alpha + below.green * under) / out,
                    (blue * alpha + below.blue * under) / out,
                    out);
        }

        int argb() {
            return (int) Math.round(alpha * 255) << 24
                    | (int) Math.round(red) << 16
                    | (int) Math.round(green) << 8
                    | (int) Math.round(blue);
        }

        private static double mix(int from, int to, double t) {
            return from + (to - from) * t;
        }
    }

    /// How much of a pixel a shape covers, from its distance to the pixel's centre.
    private static double coverage(double distance) {
        return Math.clamp(0.5 - distance, 0, 1);
    }
}
