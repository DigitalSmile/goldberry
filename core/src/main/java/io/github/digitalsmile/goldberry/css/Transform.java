package io.github.digitalsmile.goldberry.css;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/// CSS's `transform` and `transform-origin`, as the cascade resolves them.
///
/// ## Why this is a list of functions and not a matrix
///
/// The obvious computed value for `transform` is the [Affine] the functions
/// multiply out to, and it is the wrong one, for two independent reasons.
///
/// **Percentages need a box.** `translate(50%, 0)` and the `transform-origin`
/// default of `50% 50%` are both proportions of the element's own border box, and
/// a box does not know its size until Yoga has run — long after the cascade. A
/// matrix resolved at cascade time would have to guess, and the guess would be
/// wrong for every element that is not the size it guessed. So the functions are
/// carried and [#matrix(double, double)] resolves them when the rectangle is
/// known, which is inside the paint walk.
///
/// **Interpolation needs the parts.** Halfway between `rotate(0)` and
/// `rotate(180deg)` is `rotate(90deg)`, and halfway between the two *matrices*,
/// entry by entry, is a matrix of zeroes — a box collapsed to a point. Keeping
/// the functions means the common case interpolates the numbers an author wrote,
/// which is both correct and obvious. [Affine.Decomposed] is what handles the
/// case where the two lists have different shapes.
///
/// The one property [Decoration] and this one do not share, then, is that a
/// `Decoration` is finished when the cascade produces it and a `Transform` is
/// not.
///
/// ## The subset
///
/// `translate`, `translateX`, `translateY`, `scale`, `scaleX`, `scaleY`,
/// `rotate`, `skew`, `skewX`, `skewY` and `matrix` — the 2D functions.
/// `docs/design-system.md` §1.7 whitelists `transform` for animation and names
/// exactly one use for it, the check mark's `scale 0.6→1`, so this is the 2D set
/// and no more. The 3D functions would need a projection the painter has no
/// concept of, and `perspective` on a CPU rasterizer is a different feature
/// wearing this one's name.
///
/// @param functions in the order they were written, which is the **reverse** of
///                  the order they apply — CSS's `transform: a b c` runs `c`
///                  first
/// @param origin    the point they are applied about; `50% 50%` by default,
///                  which is CSS's and is why `scale()` grows a control from its
///                  middle
public record Transform(List<Function> functions, Origin origin) {

    /// No transform. The value every box starts at and almost every box keeps.
    public static final Transform NONE = new Transform(List.of(), Origin.CENTER);

    public Transform {
        functions = List.copyOf(Objects.requireNonNull(functions, "functions"));
        Objects.requireNonNull(origin, "origin");
    }

    /// A transform with the default origin.
    public static Transform of(Function... functions) {
        return new Transform(List.of(functions), Origin.CENTER);
    }

    /// This transform applied about a different point.
    public Transform origin(Origin value) {
        return new Transform(functions, value);
    }

    /// Whether this transform would move anything.
    ///
    /// Asked once per box per frame. An empty list is the answer for every box in
    /// an ordinary frame, and it costs a field read.
    public boolean isNone() {
        return functions.isEmpty();
    }

    /// The matrix for a border box of `width` × `height` logical pixels.
    ///
    /// Percentages resolve here and nowhere else. The list is folded right to
    /// left, because CSS applies the last function first: in
    /// `transform: translate(10px) scale(2)` the box is scaled and *then* moved,
    /// so the point nearest the box is the rightmost one written.
    public Affine matrix(double width, double height) {
        if (functions.isEmpty()) {
            return Affine.IDENTITY;
        }
        var matrix = Affine.IDENTITY;
        for (var i = functions.size() - 1; i >= 0; i--) {
            matrix = matrix.then(functions.get(i).resolve(width, height));
        }
        return matrix.about(origin.x().resolve(width), origin.y().resolve(height));
    }

    /// This transform `t` of the way to `to`.
    ///
    /// Function by function while the two lists line up, which is CSS's own rule
    /// and is what makes `scale(0.6)` → `scale(1)` interpolate the number an
    /// author wrote rather than a matrix derived from it. A list shorter than the
    /// other is padded with the **identity of the other's function** — so `none` →
    /// `scale(1.1)` grows from `scale(1)` rather than snapping, which is the
    /// transition every `:hover` rule will write.
    ///
    /// Where two functions at the same position are different kinds the lists are
    /// incompatible and the value **swaps at the halfway point**. CSS resolves
    /// that case by multiplying both sides out and decomposing, which cannot be
    /// done here without a box to resolve percentages against — and this method is
    /// called from the animation overlay, which runs before layout. Nothing in the
    /// design system asks for it; a stylesheet that does gets a jump rather than a
    /// wrong shape, and the limitation is on the record rather than in the
    /// behaviour.
    public Transform mix(Transform to, double t) {
        if (t <= 0) {
            return this;
        }
        if (t >= 1) {
            return to;
        }
        var length = Math.max(functions.size(), to.functions.size());
        var mixed = new ArrayList<Function>(length);
        for (var i = 0; i < length; i++) {
            var from = i < functions.size() ? functions.get(i) : null;
            var target = i < to.functions.size() ? to.functions.get(i) : null;
            // One side ran out: the missing function is the identity of whatever
            // the other side is, so the value grows out of nothing rather than
            // out of a different kind of thing.
            if (from == null) {
                from = target.identity();
            } else if (target == null) {
                target = from.identity();
            }
            if (from.getClass() != target.getClass()) {
                return t < 0.5 ? this : to;
            }
            mixed.add(from.mix(target, t));
        }
        return new Transform(mixed, origin.mix(to.origin, t));
    }

    @Override
    public String toString() {
        if (functions.isEmpty()) {
            return "none";
        }
        var text = new StringBuilder();
        for (var function : functions) {
            if (!text.isEmpty()) {
                text.append(' ');
            }
            text.append(function);
        }
        return text.toString();
    }

    /// A length that may be a percentage of the box it is on.
    ///
    /// A type of its own rather than Yoga's `StyleLength`, which carries `auto`
    /// and `undefined` — two states a transform has no meaning for, and that every
    /// `switch` over one would have to answer for anyway.
    ///
    /// @param value      the number
    /// @param percentage whether it is a proportion of the box rather than
    ///                   logical pixels; `50` means 50%, not 0.5
    public record Length(double value, boolean percentage) {

        public static final Length ZERO = new Length(0, false);

        /// Half the box — the `transform-origin` default on both axes.
        public static final Length HALF = new Length(50, true);

        public Length {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        "a transform length must be a finite number, not " + value);
            }
        }

        public static Length px(double value) {
            return new Length(value, false);
        }

        public static Length percent(double value) {
            return new Length(value, true);
        }

        /// This length in logical pixels, against a box dimension of `basis`.
        public double resolve(double basis) {
            return percentage ? value * basis / 100 : value;
        }

        Length mix(Length to, double t) {
            // Two lengths in different units cannot be added without a box, and
            // this is called before there is one. Mixing the numbers when the
            // units agree covers every case a stylesheet writes; the rest swaps.
            if (percentage != to.percentage) {
                return t < 0.5 ? this : to;
            }
            return new Length(value + (to.value - value) * t, percentage);
        }

        @Override
        public String toString() {
            return percentage ? value + "%" : value + "px";
        }
    }

    /// The point a transform is applied about — CSS's `transform-origin`.
    public record Origin(Length x, Length y) {

        /// `50% 50%`, CSS's default and the reason a scaled control grows from
        /// its middle instead of its top-left corner.
        public static final Origin CENTER = new Origin(Length.HALF, Length.HALF);

        /// `0 0` — the border box's top-left corner.
        public static final Origin TOP_LEFT = new Origin(Length.ZERO, Length.ZERO);

        public Origin {
            Objects.requireNonNull(x, "x");
            Objects.requireNonNull(y, "y");
        }

        Origin mix(Origin to, double t) {
            return this.equals(to) ? this : new Origin(x.mix(to.x, t), y.mix(to.y, t));
        }

        @Override
        public String toString() {
            return x + " " + y;
        }
    }

    /// One entry in a `transform` list.
    ///
    /// Sealed, so the interpolation `switch` in [Transform#mix] is exhaustive and
    /// a function added here fails to compile until it says how it interpolates —
    /// which is the property that stops a new function silently animating as a
    /// jump.
    public sealed interface Function {

        /// This function's contribution, for a box of `width` × `height`.
        Affine resolve(double width, double height);

        /// The same function with values that change nothing.
        ///
        /// What the other side of an interpolation gets when a list is shorter
        /// than its counterpart. It is per-function rather than a single
        /// [Affine#IDENTITY] because `scale`'s identity is `1` and `translate`'s
        /// is `0`, and interpolating a scale from zero would collapse the box on
        /// the first frame.
        Function identity();

        /// This function `t` of the way to `to`, which is the same kind.
        Function mix(Function to, double t);

        /// `translate(x, y)`, `translateX(x)`, `translateY(y)`.
        ///
        /// Percentages are of the element's own border box — its width for `x`,
        /// its height for `y` — which is CSS's rule and the reason this cannot be
        /// resolved until layout has run.
        record Translate(Length x, Length y) implements Function {

            public Translate {
                Objects.requireNonNull(x, "x");
                Objects.requireNonNull(y, "y");
            }

            @Override
            public Affine resolve(double width, double height) {
                return Affine.translate(x.resolve(width), y.resolve(height));
            }

            @Override
            public Function identity() {
                return new Translate(Length.ZERO, Length.ZERO);
            }

            @Override
            public Function mix(Function to, double t) {
                var target = (Translate) to;
                return new Translate(x.mix(target.x, t), y.mix(target.y, t));
            }

            @Override
            public String toString() {
                return "translate(" + x + ", " + y + ")";
            }
        }

        /// `scale(s)`, `scale(x, y)`, `scaleX(x)`, `scaleY(y)`.
        record Scale(double x, double y) implements Function {

            @Override
            public Affine resolve(double width, double height) {
                return Affine.scale(x, y);
            }

            @Override
            public Function identity() {
                return new Scale(1, 1);
            }

            @Override
            public Function mix(Function to, double t) {
                var target = (Scale) to;
                return new Scale(x + (target.x - x) * t, y + (target.y - y) * t);
            }

            @Override
            public String toString() {
                return x == y ? "scale(" + x + ")" : "scale(" + x + ", " + y + ")";
            }
        }

        /// `rotate(angle)`, clockwise, about the transform origin.
        ///
        /// @param radians the angle; CSS writes `deg`, `rad`, `grad` or `turn`
        ///                and the parser converts
        record Rotate(double radians) implements Function {

            @Override
            public Affine resolve(double width, double height) {
                return Affine.rotate(radians);
            }

            @Override
            public Function identity() {
                return new Rotate(0);
            }

            @Override
            public Function mix(Function to, double t) {
                // Interpolated as an angle rather than through the matrix, which
                // is the whole reason the functions are kept: 0° to 180° passes
                // through 90° here and through a degenerate matrix otherwise.
                return new Rotate(radians + (((Rotate) to).radians - radians) * t);
            }

            @Override
            public String toString() {
                return "rotate(" + Math.toDegrees(radians) + "deg)";
            }
        }

        /// `skew(ax, ay)`, `skewX(ax)`, `skewY(ay)`, in radians.
        record Skew(double xRadians, double yRadians) implements Function {

            @Override
            public Affine resolve(double width, double height) {
                return Affine.skew(xRadians, yRadians);
            }

            @Override
            public Function identity() {
                return new Skew(0, 0);
            }

            @Override
            public Function mix(Function to, double t) {
                var target = (Skew) to;
                return new Skew(
                        xRadians + (target.xRadians - xRadians) * t,
                        yRadians + (target.yRadians - yRadians) * t);
            }

            @Override
            public String toString() {
                return "skew(" + Math.toDegrees(xRadians) + "deg, "
                        + Math.toDegrees(yRadians) + "deg)";
            }
        }

        /// `matrix(a, b, c, d, e, f)` — the six numbers written out.
        ///
        /// Interpolated by decomposition, because there is nothing else to go on:
        /// an author who writes a matrix has thrown away the rotation and the
        /// scale that produced it, and taking them back out is exactly what
        /// [Affine#decompose()] is for.
        record Matrix(Affine value) implements Function {

            public Matrix {
                Objects.requireNonNull(value, "value");
            }

            @Override
            public Affine resolve(double width, double height) {
                return value;
            }

            @Override
            public Function identity() {
                return new Matrix(Affine.IDENTITY);
            }

            @Override
            public Function mix(Function to, double t) {
                return new Matrix(value.mix(((Matrix) to).value, t));
            }

            @Override
            public String toString() {
                return value.toString();
            }
        }
    }

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------

    /// Parses a `transform` value, or returns null if it is not one.
    ///
    /// Null rather than an exception, because §8's rule for a declaration that
    /// does not parse is to drop it and carry on. A transform that half-parsed
    /// would be worse than none: the box would move somewhere nobody wrote.
    static Transform parse(List<Token> value, Origin origin) {
        var tokens = value.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
        if (tokens.isEmpty()) {
            return null;
        }
        if (tokens.size() == 1 && tokens.getFirst().isIdent("none")) {
            return new Transform(List.of(), origin);
        }

        var functions = new ArrayList<Function>();
        var index = 0;
        while (index < tokens.size()) {
            var token = tokens.get(index);
            if (!token.is(TokenType.FUNCTION)) {
                return null;
            }
            var close = matchingParen(tokens, index);
            if (close < 0) {
                return null;
            }
            var function = function(
                    token.text().toLowerCase(Locale.ROOT),
                    arguments(tokens.subList(index + 1, close)));
            if (function == null) {
                return null;
            }
            functions.add(function);
            index = close + 1;
        }
        return functions.isEmpty() ? null : new Transform(functions, origin);
    }

    /// The index of the `)` closing the function that starts at `open`.
    ///
    /// Counted rather than searched for, because a `var()` substituted into an
    /// argument leaves nested parentheses behind and taking the first `)` would
    /// end the function in the middle of one.
    private static int matchingParen(List<Token> tokens, int open) {
        var depth = 0;
        for (var i = open; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.is(TokenType.FUNCTION) || token.is(TokenType.OPEN_PAREN)) {
                depth++;
            } else if (token.is(TokenType.CLOSE_PAREN)) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /// A function's arguments split on commas.
    private static List<List<Token>> arguments(List<Token> tokens) {
        var parts = new ArrayList<List<Token>>();
        var current = new ArrayList<Token>();
        for (var token : tokens) {
            if (token.is(TokenType.COMMA)) {
                parts.add(List.copyOf(current));
                current.clear();
            } else {
                current.add(token);
            }
        }
        if (!current.isEmpty() || !parts.isEmpty()) {
            parts.add(List.copyOf(current));
        }
        return parts;
    }

    private static Function function(String name, List<List<Token>> arguments) {
        var count = arguments.size();
        switch (name) {
            case "translate" -> {
                // The one-argument form leaves y at zero, which is CSS's rule and
                // not the same as leaving it unset: `translate(10px)` moves
                // horizontally only.
                var values = count == 1 || count == 2 ? lengths(arguments, count) : null;
                return values == null
                        ? null
                        : new Function.Translate(
                                values[0], count == 2 ? values[1] : Length.ZERO);
            }
            case "translatex", "translatey" -> {
                var values = count == 1 ? lengths(arguments, 1) : null;
                if (values == null) {
                    return null;
                }
                return name.endsWith("x")
                        ? new Function.Translate(values[0], Length.ZERO)
                        : new Function.Translate(Length.ZERO, values[0]);
            }

            case "scale" -> {
                // `scale(2)` is uniform. Unlike translate, the missing argument
                // repeats the first rather than defaulting to the identity —
                // CSS's rule, and the one that makes `scale(0.6)` mean what a
                // designer means by it.
                var values = count == 1 || count == 2 ? numbers(arguments, count) : null;
                return values == null
                        ? null
                        : new Function.Scale(values[0], count == 2 ? values[1] : values[0]);
            }
            case "scalex", "scaley" -> {
                var values = count == 1 ? numbers(arguments, 1) : null;
                if (values == null) {
                    return null;
                }
                return name.endsWith("x")
                        ? new Function.Scale(values[0], 1)
                        : new Function.Scale(1, values[0]);
            }

            case "rotate" -> {
                var values = count == 1 ? angles(arguments, 1) : null;
                return values == null ? null : new Function.Rotate(values[0]);
            }

            case "skew" -> {
                var values = count == 1 || count == 2 ? angles(arguments, count) : null;
                return values == null
                        ? null
                        : new Function.Skew(values[0], count == 2 ? values[1] : 0);
            }
            case "skewx", "skewy" -> {
                var values = count == 1 ? angles(arguments, 1) : null;
                if (values == null) {
                    return null;
                }
                return name.endsWith("x")
                        ? new Function.Skew(values[0], 0)
                        : new Function.Skew(0, values[0]);
            }

            case "matrix" -> {
                var values = count == 6 ? numbers(arguments, 6) : null;
                return values == null
                        ? null
                        : new Function.Matrix(new Affine(
                                values[0], values[1], values[2],
                                values[3], values[4], values[5]));
            }

            default -> {
                return null;
            }
        }
    }

    private static Length[] lengths(List<List<Token>> arguments, int count) {
        var values = new Length[count];
        for (var i = 0; i < count; i++) {
            var length = length(arguments.get(i));
            if (length == null) {
                return null;
            }
            values[i] = length;
        }
        return values;
    }

    private static Length length(List<Token> argument) {
        if (argument.size() != 1) {
            return null;
        }
        var token = argument.getFirst();
        if (token.is(TokenType.PERCENTAGE)) {
            return Length.percent(token.numeric());
        }
        if (token.is(TokenType.NUMBER)) {
            // Unitless zero only — the same allowance CSS makes everywhere a
            // length is expected, and for the same reason: zero has no unit to
            // be wrong about.
            return token.numeric() == 0 ? Length.ZERO : null;
        }
        if (!token.is(TokenType.DIMENSION)) {
            return null;
        }
        return switch (token.unit()) {
            case "px" -> Length.px(token.numeric());
            // `em` and `rem` against the fixed context numbers, which is the same
            // approximation the rest of the cascade makes and the same known gap:
            // they do not resolve against the node's own font-size (ADR-0066).
            case "em" -> Length.px(token.numeric() * CssLength.Context.DEFAULT.fontSize());
            case "rem" -> Length.px(token.numeric() * CssLength.Context.DEFAULT.rootFontSize());
            default -> null;
        };
    }

    private static double[] numbers(List<List<Token>> arguments, int count) {
        var values = new double[count];
        for (var i = 0; i < count; i++) {
            var argument = arguments.get(i);
            if (argument.size() != 1) {
                return null;
            }
            var token = argument.getFirst();
            if (token.is(TokenType.NUMBER)) {
                values[i] = token.numeric();
            } else if (token.is(TokenType.PERCENTAGE)) {
                // `scale(50%)` is the modern spelling of `scale(0.5)`.
                values[i] = token.numeric() / 100;
            } else {
                return null;
            }
        }
        return values;
    }

    /// Angles as radians. CSS's four units, because refusing three of them would
    /// be refusing valid CSS to save a `switch` arm.
    private static double[] angles(List<List<Token>> arguments, int count) {
        var values = new double[count];
        for (var i = 0; i < count; i++) {
            var argument = arguments.get(i);
            if (argument.size() != 1) {
                return null;
            }
            var token = argument.getFirst();
            if (token.is(TokenType.NUMBER) && token.numeric() == 0) {
                values[i] = 0;
                continue;
            }
            if (!token.is(TokenType.DIMENSION)) {
                return null;
            }
            var radians = switch (token.unit()) {
                case "deg" -> Math.toRadians(token.numeric());
                case "rad" -> token.numeric();
                case "grad" -> token.numeric() * Math.PI / 200;
                case "turn" -> token.numeric() * 2 * Math.PI;
                default -> Double.NaN;
            };
            if (Double.isNaN(radians)) {
                return null;
            }
            values[i] = radians;
        }
        return values;
    }

    /// Parses a `transform-origin` value, or returns null.
    ///
    /// One or two components, each a length, a percentage or one of CSS's five
    /// keywords. A single component sets the horizontal one and leaves the
    /// vertical centred, which is CSS's rule.
    static Origin parseOrigin(List<Token> value) {
        var parts = new ArrayList<List<Token>>();
        var current = new ArrayList<Token>();
        for (var token : value) {
            if (token.is(TokenType.WHITESPACE)) {
                if (!current.isEmpty()) {
                    parts.add(List.copyOf(current));
                    current.clear();
                }
            } else {
                current.add(token);
            }
        }
        if (!current.isEmpty()) {
            parts.add(List.copyOf(current));
        }

        return switch (parts.size()) {
            case 1 -> {
                // A lone `top` or `bottom` names the *vertical* axis, so it
                // centres the other one rather than being taken as horizontal.
                var only = keyword(parts.getFirst());
                if (only == Axis.VERTICAL) {
                    yield new Origin(Length.HALF, originLength(parts.getFirst()));
                }
                var x = originLength(parts.getFirst());
                yield x == null ? null : new Origin(x, Length.HALF);
            }
            case 2 -> {
                var first = parts.get(0);
                var second = parts.get(1);
                // `top left` is as valid as `left top`, so a pair naming both
                // axes by keyword is put back in order rather than refused.
                if (keyword(first) == Axis.VERTICAL || keyword(second) == Axis.HORIZONTAL) {
                    var swap = first;
                    first = second;
                    second = swap;
                }
                var x = originLength(first);
                var y = originLength(second);
                yield x == null || y == null ? null : new Origin(x, y);
            }
            default -> null;
        };
    }

    /// Which axis a `transform-origin` keyword names, or null if it is not one.
    private enum Axis { HORIZONTAL, VERTICAL }

    private static Axis keyword(List<Token> part) {
        if (part.size() != 1 || !part.getFirst().is(TokenType.IDENT)) {
            return null;
        }
        return switch (part.getFirst().text().toLowerCase(Locale.ROOT)) {
            case "left", "right" -> Axis.HORIZONTAL;
            case "top", "bottom" -> Axis.VERTICAL;
            default -> null;
        };
    }

    private static Length originLength(List<Token> part) {
        if (part.size() == 1 && part.getFirst().is(TokenType.IDENT)) {
            return switch (part.getFirst().text().toLowerCase(Locale.ROOT)) {
                case "left", "top" -> Length.ZERO;
                case "center" -> Length.HALF;
                case "right", "bottom" -> Length.percent(100);
                default -> null;
            };
        }
        return length(part);
    }
}
