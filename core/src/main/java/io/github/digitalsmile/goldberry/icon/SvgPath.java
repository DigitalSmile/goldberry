package io.github.digitalsmile.goldberry.icon;

import java.util.Locale;
import java.util.Objects;

import io.github.digitalsmile.goldberry.paint.Path;

/// Turns SVG path data into Blend2D path commands.
///
/// The bundled icon table is 1544 lines of SVG `d` attributes (ADR-0033), and
/// Blend2D has a command for every one of SVG's — including the elliptic arc and
/// the two "smooth" curves, which is why this is a reader rather than a geometry
/// library (ADR-0043). What is here is the grammar and nothing else: scan a
/// command letter, scan its numbers, call the corresponding method.
///
/// Three things in that grammar are easy to get wrong, and each is a bug that
/// draws *something* rather than failing:
///
/// - **Numbers run together.** `1.5.5` is two numbers, and so is `1-2`. A
///   scanner that split on whitespace and commas would read the first as one
///   number and produce an icon missing half its geometry.
/// - **Arc flags are single characters.** `a1 1 0 011 1` packs `large-arc=0`,
///   `sweep=1` and `x=1` into `011`, because the flags are defined as one
///   character each rather than as numbers. Parsing them as numbers reads `011`
///   as eleven and then runs out of arguments.
/// - **A repeated `M` is an `L`.** Extra coordinate pairs after a move-to
///   continue the sub-path as lines; treating them as more move-tos produces an
///   outline of disconnected points that strokes as nothing.
///
/// Malformed input is refused rather than half-drawn. The icon set is a pinned,
/// checksummed archive compiled by `:assets`, so a parse failure means the
/// compiler emitted something this cannot read — which is a build problem worth
/// hearing about, not a glyph to skip.
public final class SvgPath {

    private SvgPath() {}

    /// Appends `data` to `path`, scaling every coordinate by `scale`.
    ///
    /// Scaling here rather than transforming the context is the same decision
    /// ADR-0034 made for text: an icon is built at the size it is drawn at, and
    /// the size lives in exactly one place. It also keeps the context's
    /// transform meaning only the display scale, which is the one thing that
    /// must not be disturbed mid-frame.
    ///
    /// @throws IllegalArgumentException if the data is not valid SVG path data
    public static void appendTo(Path.Builder path, String data, double scale) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(data, "data");
        if (!Double.isFinite(scale) || scale <= 0) {
            throw new IllegalArgumentException(
                    "an icon scale must be a positive, finite number, and " + scale + " is not");
        }
        new Reader(data, scale, path).run();
    }

    /// Appends `data` unscaled — in its own coordinate space.
    public static void appendTo(Path.Builder path, String data) {
        appendTo(path, data, 1.0);
    }

    /// One pass over one path-data string.
    ///
    /// A class rather than a long method because the position, the current point
    /// and the sub-path start are all state the commands share, and threading
    /// six mutable doubles through static methods is how one of them ends up
    /// stale.
    private static final class Reader {

        private final String data;
        private final double scale;
        private final Path.Builder path;

        private int at;

        /// The current point, in **scaled** coordinates — the space the path is
        /// being built in, so relative commands add without converting.
        private double x;
        private double y;

        /// Where the current sub-path started, which is where `Z` returns to.
        private double startX;
        private double startY;

        private boolean started;

        /// Set by `Z`, cleared by the next command.
        ///
        /// SVG says a drawing command after a close starts a new sub-path at the
        /// closed one's start point. Blend2D says the same thing more firmly:
        /// `bl_path_line_to` after `bl_path_close` is an error, because there is
        /// no current figure to extend. So the implicit move has to be issued,
        /// not merely accounted for.
        private boolean closedAndNotResumed;

        /// What kind of curve the previous command was, and its last control
        /// point — the state SVG's `S` and `T` reflect about the current point.
        ///
        /// [io.github.digitalsmile.goldberry.paint.Path] has no smooth-curve
        /// verb, deliberately: `S` and `T` mean nothing without the command
        /// before them, and a stateful verb would make every consumer of
        /// [io.github.digitalsmile.goldberry.paint.Path#segments()] carry this
        /// bookkeeping instead of one reader doing it once (ADR-0277). So the
        /// reflection happens here and a plain cubic or quadratic comes out.
        private char controlKind;

        private double controlX;
        private double controlY;

        Reader(String data, double scale, Path.Builder path) {
            this.data = data;
            this.scale = scale;
            this.path = path;
        }

        void run() {
            skipSeparators();
            var command = '\0';
            while (at < data.length()) {
                var c = data.charAt(at);
                if (isCommand(c)) {
                    command = c;
                    at++;
                    skipSeparators();
                } else if (command == '\0') {
                    throw error("expected a command letter");
                } else if (command == 'M' || command == 'm') {
                    // An implicit repeat of a move-to is a line-to, per SVG.
                    command = command == 'M' ? 'L' : 'l';
                } else if (command == 'Z' || command == 'z') {
                    throw error("Z takes no arguments");
                }
                apply(command);
                skipSeparators();
            }
        }

        /// A cubic-family command — `C`, `c`, `S`, `s` — which is what `S`
        /// reflects.
        private static final char CUBIC = 'C';

        /// A quadratic-family command — `Q`, `q`, `T`, `t` — which is what `T`
        /// reflects.
        private static final char QUAD = 'Q';

        private void apply(char command) {
            var relative = Character.isLowerCase(command);
            // Captured and cleared before the command runs: SVG says a smooth
            // curve after anything that is not its own family reflects about the
            // current point, which is the same as having no control at all.
            var previous = controlKind;
            controlKind = '\0';
            switch (Character.toUpperCase(command)) {
                case 'M' -> {
                    x = coordinate(relative, x);
                    y = coordinate(relative, y);
                    path.moveTo(x, y);
                    startX = x;
                    startY = y;
                    started = true;
                    closedAndNotResumed = false;
                }
                case 'L' -> {
                    requireStarted('L');
                    x = coordinate(relative, x);
                    y = coordinate(relative, y);
                    path.lineTo(x, y);
                }
                case 'H' -> {
                    requireStarted('H');
                    x = coordinate(relative, x);
                    path.lineTo(x, y);
                }
                case 'V' -> {
                    requireStarted('V');
                    y = coordinate(relative, y);
                    path.lineTo(x, y);
                }
                case 'C' -> {
                    requireStarted('C');
                    var x1 = coordinate(relative, x);
                    var y1 = coordinate(relative, y);
                    var x2 = coordinate(relative, x);
                    var y2 = coordinate(relative, y);
                    x = coordinate(relative, x);
                    y = coordinate(relative, y);
                    path.cubicTo(x1, y1, x2, y2, x, y);
                    remember(CUBIC, x2, y2);
                }
                case 'S' -> {
                    requireStarted('S');
                    // The reflection is taken before the new coordinates are
                    // read, because it is about where the pen is *now*.
                    var x1 = previous == CUBIC ? 2 * x - controlX : x;
                    var y1 = previous == CUBIC ? 2 * y - controlY : y;
                    var x2 = coordinate(relative, x);
                    var y2 = coordinate(relative, y);
                    x = coordinate(relative, x);
                    y = coordinate(relative, y);
                    path.cubicTo(x1, y1, x2, y2, x, y);
                    remember(CUBIC, x2, y2);
                }
                case 'Q' -> {
                    requireStarted('Q');
                    var x1 = coordinate(relative, x);
                    var y1 = coordinate(relative, y);
                    x = coordinate(relative, x);
                    y = coordinate(relative, y);
                    path.quadTo(x1, y1, x, y);
                    remember(QUAD, x1, y1);
                }
                case 'T' -> {
                    requireStarted('T');
                    var cx = previous == QUAD ? 2 * x - controlX : x;
                    var cy = previous == QUAD ? 2 * y - controlY : y;
                    x = coordinate(relative, x);
                    y = coordinate(relative, y);
                    path.quadTo(cx, cy, x, y);
                    remember(QUAD, cx, cy);
                }
                case 'A' -> {
                    requireStarted('A');
                    // The radii scale with everything else; the rotation does
                    // not, and it is in degrees here and radians in a Path.
                    var rx = number() * scale;
                    var ry = number() * scale;
                    var rotation = Math.toRadians(number());
                    var largeArc = flag();
                    var sweep = flag();
                    x = coordinate(relative, x);
                    y = coordinate(relative, y);
                    path.arcTo(rx, ry, rotation, largeArc, sweep, x, y);
                }
                case 'Z' -> {
                    requireStarted('Z');
                    path.close();
                    // The current point returns to where the sub-path began.
                    // Getting this wrong makes the next relative command start
                    // from the end of the outline instead of its beginning.
                    x = startX;
                    y = startY;
                    closedAndNotResumed = true;
                }
                default -> throw error("unknown command '" + command + "'");
            }
        }

        /// One coordinate: scaled, and added to `origin` when the command is
        /// relative.
        private double coordinate(boolean relative, double origin) {
            var value = number() * scale;
            return relative ? origin + value : value;
        }

        private void requireStarted(char command) {
            if (!started) {
                throw error(command + " before any move-to");
            }
            if (closedAndNotResumed) {
                // The pen is already back at the sub-path start -- `Z` put it
                // there -- so this re-opens the figure at the same point and
                // draws nothing of its own.
                path.moveTo(x, y);
                closedAndNotResumed = false;
            }
        }

        /// A single-character `0` or `1`.
        ///
        /// Not `number()`: SVG defines the arc flags as one character each,
        /// precisely so a generator may pack them against the coordinate that
        /// follows.
        private boolean flag() {
            skipSeparators();
            if (at >= data.length()) {
                throw error("expected an arc flag");
            }
            var c = data.charAt(at);
            if (c != '0' && c != '1') {
                throw error("an arc flag must be 0 or 1, not '" + c + "'");
            }
            at++;
            return c == '1';
        }

        /// One number, ending where the number ends rather than where a
        /// separator does.
        private double number() {
            skipSeparators();
            var start = at;
            if (at < data.length() && (data.charAt(at) == '+' || data.charAt(at) == '-')) {
                at++;
            }
            var digits = false;
            while (at < data.length() && isDigit(data.charAt(at))) {
                at++;
                digits = true;
            }
            if (at < data.length() && data.charAt(at) == '.') {
                at++;
                while (at < data.length() && isDigit(data.charAt(at))) {
                    at++;
                    digits = true;
                }
            }
            if (!digits) {
                throw error("expected a number");
            }
            // An exponent only counts if a digit follows it -- otherwise the `e`
            // belongs to whatever comes next, which in practice is nothing legal
            // but must not swallow a character either way.
            if (at < data.length() && (data.charAt(at) == 'e' || data.charAt(at) == 'E')) {
                var mark = at;
                at++;
                if (at < data.length() && (data.charAt(at) == '+' || data.charAt(at) == '-')) {
                    at++;
                }
                if (at < data.length() && isDigit(data.charAt(at))) {
                    while (at < data.length() && isDigit(data.charAt(at))) {
                        at++;
                    }
                } else {
                    at = mark;
                }
            }

            var text = data.substring(start, at);
            try {
                var value = Double.parseDouble(text);
                if (!Double.isFinite(value)) {
                    throw error("\"" + text + "\" is not a finite number");
                }
                return value;
            } catch (NumberFormatException e) {
                throw error("\"" + text + "\" is not a number");
            }
        }

        private void skipSeparators() {
            while (at < data.length()) {
                var c = data.charAt(at);
                if (c == ',' || Character.isWhitespace(c)) {
                    at++;
                } else {
                    return;
                }
            }
        }

        /// Records the control point a following `S` or `T` would reflect.
        private void remember(char kind, double x, double y) {
            controlKind = kind;
            controlX = x;
            controlY = y;
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }

        private static boolean isCommand(char c) {
            return switch (Character.toUpperCase(c)) {
                case 'M', 'L', 'H', 'V', 'C', 'S', 'Q', 'T', 'A', 'Z' -> true;
                default -> false;
            };
        }

        private IllegalArgumentException error(String what) {
            return new IllegalArgumentException(
                    String.format(Locale.ROOT, "%s at index %d of SVG path data: %s", what, at, excerpt()));
        }

        /// The failing region rather than the whole string: an icon's data can
        /// be a few hundred characters, and a message that quotes all of it
        /// hides the position it just reported.
        private String excerpt() {
            var from = Math.max(0, at - 20);
            var to = Math.min(data.length(), at + 20);
            return (from > 0 ? "..." : "") + data.substring(from, to) + (to < data.length() ? "..." : "");
        }
    }
}
