package dev.goldberry.css.value;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.Decoration;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.css.value.calc.Maths;
import dev.goldberry.css.value.calc.Quantity;

/// CSS's `transform` and `transform-origin`, as the cascade resolves them.
///
/// ```css
/// checkbox-mark { transform: scale(0.6); transform-origin: 50% 50% }
/// checkbox:checked checkbox-mark { transform: scale(1) }
/// ```
///
/// This is a list of functions and not a matrix. The obvious computed value for
/// `transform` is the [Affine] the functions multiply out to, and it is the wrong
/// one, for two independent reasons.
///
/// **Percentages need a box.** `translate(50%, 0)` and the `transform-origin`
/// default of `50% 50%` are both proportions of the element's own border box, and
/// a box does not know its size until layout has run — long after the cascade. A
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
/// The subset is `translate`, `translateX`, `translateY`, `scale`, `scaleX`,
/// `scaleY`, `rotate`, `skew`, `skewX`, `skewY` and `matrix`: the 2D functions.
/// The design system allows `transform` to animate and names exactly one use for
/// it, the check mark's `scale 0.6→1`, so this is the 2D set and no more. The 3D
/// functions would need a projection the painter has no concept of, and
/// `perspective` on a CPU rasterizer is a different feature wearing this one's
/// name.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#transform).
///
/// @param functions in the order they were written, which is the **reverse** of
///                  the order they apply — CSS's `transform: a b c` runs `c`
///                  first
/// @param origin     the point they are applied about; `50% 50%` by default,
///                   which is CSS's and is why `scale()` grows a control from its
///                   middle
/// @param individual the `translate`, `rotate` and `scale` properties, applied
///                   before the functions
/// @param box        what the origin and the percentages are measured against
public record Transform(List<Function> functions, Origin origin, IndividualTransform individual, TransformBox box) {

    /// No transform. The value every box starts at and almost every box keeps.
    public static final Transform NONE =
            new Transform(List.of(), Origin.CENTER, IndividualTransform.NONE, TransformBox.BORDER_BOX);

    public Transform {
        functions = List.copyOf(Objects.requireNonNull(functions, "functions"));
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(individual, "individual");
        Objects.requireNonNull(box, "box");
    }

    /// `functions` about `origin`, with none of the individual properties.
    public Transform(List<Function> functions, Origin origin) {
        this(functions, origin, IndividualTransform.NONE, TransformBox.BORDER_BOX);
    }

    /// This transform with other functions, its origin and the rest kept.
    public Transform functions(List<Function> value) {
        return new Transform(value, origin, individual, box);
    }

    /// This transform with other individual properties.
    public Transform individual(IndividualTransform value) {
        return new Transform(functions, origin, value, box);
    }

    /// This transform measured against another box.
    public Transform box(TransformBox value) {
        return new Transform(functions, origin, individual, value);
    }

    /// A transform with the default origin.
    public static Transform of(Function... functions) {
        return new Transform(List.of(functions), Origin.CENTER);
    }

    /// This transform applied about a different point.
    public Transform origin(Origin value) {
        return new Transform(functions, value, individual, box);
    }

    /// Whether this transform would move anything.
    ///
    /// Asked once per box per frame. An empty list is the answer for every box in
    /// an ordinary frame, and it costs a field read.
    public boolean isNone() {
        return functions.isEmpty() && individual.isNone();
    }

    /// The matrix for a border box of `width` × `height` logical pixels.
    ///
    /// Percentages resolve here and nowhere else. The list is folded right to
    /// left, because CSS applies the last function first: in
    /// `transform: translate(10px) scale(2)` the box is scaled and *then* moved,
    /// so the point nearest the box is the rightmost one written.
    public Affine matrix(double width, double height) {
        return matrix(width, height, 0, 0, 0, 0);
    }

    /// The matrix for a border box of `width` × `height` whose content box is
    /// inset by `top`, `right`, `bottom` and `left`: what [#box()] measures
    /// against when it is the content box.
    ///
    /// `translate`, `rotate` and `scale` come first, in that order, and the
    /// functions after them, as if they had been written at the front of the
    /// list.
    public Affine matrix(double width, double height, double top, double right, double bottom, double left) {
        if (isNone()) {
            return Affine.IDENTITY;
        }
        var content = box == TransformBox.CONTENT_BOX;
        var w = content ? Math.max(0, width - left - right) : width;
        var h = content ? Math.max(0, height - top - bottom) : height;
        var all = individual.functions();
        all.addAll(functions);
        var matrix = Affine.IDENTITY;
        for (var i = all.size() - 1; i >= 0; i--) {
            matrix = matrix.then(all.get(i).resolve(w, h));
        }
        return matrix.about(
                (content ? left : 0) + origin.x().resolve(w),
                (content ? top : 0) + origin.y().resolve(h));
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
            // One side ran out: the missing function is the identity of whatever
            // the other side is, so the value grows out of nothing rather than
            // out of a different kind of thing. `length` is the longer list's, so
            // at least one side always has a function at `i` to grow from.
            var from = i < functions.size()
                    ? functions.get(i)
                    : to.functions.get(i).identity();
            var target = i < to.functions.size() ? to.functions.get(i) : from.identity();
            if (from.getClass() != target.getClass()) {
                return t < 0.5 ? this : to;
            }
            mixed.add(from.mix(target, t));
        }
        return new Transform(mixed, origin.mix(to.origin, t), individual.mix(to.individual, t), t < 0.5 ? box : to.box);
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
    /// @param offset     logical pixels added to a percentage, which is what
    ///                   `calc(-50% + 4px)` folds to; zero otherwise
    public record Length(double value, boolean percentage, double offset) {

        public static final Length ZERO = new Length(0, false);

        /// Half the box — the `transform-origin` default on both axes.
        public static final Length HALF = new Length(50, true);

        public Length {
            if (!Double.isFinite(value) || !Double.isFinite(offset)) {
                throw new IllegalArgumentException(
                        "a transform length must be a finite number, not " + value + " and " + offset);
            }
            if (!percentage && offset != 0) {
                // Pixels plus pixels is one number of pixels.
                value += offset;
                offset = 0;
            }
        }

        /// A length with no offset.
        public Length(double value, boolean percentage) {
            this(value, percentage, 0);
        }

        /// A percentage of the box plus `pixels`.
        public static Length percentPlus(double percent, double pixels) {
            return new Length(percent, true, pixels);
        }

        public static Length px(double value) {
            return new Length(value, false);
        }

        public static Length percent(double value) {
            return new Length(value, true);
        }

        /// This length in logical pixels, against a box dimension of `basis`.
        public double resolve(double basis) {
            return percentage ? value * basis / 100 + offset : value;
        }

        Length mix(Length to, double t) {
            // Two lengths in different units cannot be added without a box, and
            // this is called before there is one. Mixing the numbers when the
            // units agree covers every case a stylesheet writes; the rest swaps.
            if (percentage != to.percentage) {
                return t < 0.5 ? this : to;
            }
            return new Length(value + (to.value - value) * t, percentage, offset + (to.offset - offset) * t);
        }

        @Override
        public String toString() {
            if (!percentage) {
                return value + "px";
            }
            return offset == 0 ? value + "%" : "calc(" + value + "% + " + offset + "px)";
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
            // Both required.
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
                        xRadians + (target.xRadians - xRadians) * t, yRadians + (target.yRadians - yRadians) * t);
            }

            @Override
            public String toString() {
                return "skew(" + Math.toDegrees(xRadians) + "deg, " + Math.toDegrees(yRadians) + "deg)";
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
    /// Null rather than an exception, because the rule for a declaration that
    /// does not parse is to drop it and carry on. A transform that half-parsed
    /// would be worse than none: the box would move somewhere nobody wrote.
    /// Public because the computed style that calls it lives in `css` and a value
    /// type lives here.
    public static @Nullable Transform parse(List<Token> value, Origin origin, CssLength.Context context) {
        var tokens = outsideFunctions(value);
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
                    token.text().toLowerCase(Locale.ROOT), arguments(tokens.subList(index + 1, close)), context);
            if (function == null) {
                return null;
            }
            functions.add(function);
            index = close + 1;
        }
        return functions.isEmpty() ? null : new Transform(functions, origin);
    }

    /// `value` with the whitespace between functions taken out and the
    /// whitespace inside them kept, since a `calc()` argument needs it to tell a
    /// subtraction from a negative number.
    private static List<Token> outsideFunctions(List<Token> value) {
        var kept = new ArrayList<Token>(value.size());
        var depth = 0;
        for (var token : value) {
            if (token.is(TokenType.FUNCTION) || token.is(TokenType.OPEN_PAREN)) {
                depth++;
            } else if (token.is(TokenType.CLOSE_PAREN)) {
                depth = Math.max(0, depth - 1);
            }
            if (depth > 0 || !token.is(TokenType.WHITESPACE)) {
                kept.add(token);
            }
        }
        return kept;
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

    /// A function's arguments split on the commas between them, each trimmed
    /// of whitespace. A comma inside a nested function, `min(1px, 2px)`, is
    /// that function's.
    private static List<List<Token>> arguments(List<Token> tokens) {
        var parts = new ArrayList<List<Token>>();
        var current = new ArrayList<Token>();
        var depth = 0;
        for (var token : tokens) {
            if (token.is(TokenType.FUNCTION) || token.is(TokenType.OPEN_PAREN)) {
                depth++;
            } else if (token.is(TokenType.CLOSE_PAREN)) {
                depth = Math.max(0, depth - 1);
            }
            if (depth == 0 && token.is(TokenType.COMMA)) {
                parts.add(trimmed(current));
                current.clear();
            } else {
                current.add(token);
            }
        }
        if (!current.isEmpty() || !parts.isEmpty()) {
            parts.add(trimmed(current));
        }
        return parts;
    }

    private static List<Token> trimmed(List<Token> tokens) {
        var from = 0;
        var to = tokens.size();
        while (from < to && tokens.get(from).is(TokenType.WHITESPACE)) {
            from++;
        }
        while (to > from && tokens.get(to - 1).is(TokenType.WHITESPACE)) {
            to--;
        }
        return List.copyOf(tokens.subList(from, to));
    }

    // RefactorSwitch wants this turned into a switch *expression*. It would be
    // one `return switch` over eleven cases, several of which need two statements
    // before they can answer -- so every one of them grows a `yield` and the
    // method gets longer to satisfy a preference. The suggestion is about style
    // and there is no defect under it.
    @SuppressWarnings("RefactorSwitch")
    static @Nullable Function function(String name, List<List<Token>> arguments, CssLength.Context context) {
        var count = arguments.size();
        switch (name) {
            case "translate" -> {
                // The one-argument form leaves y at zero, which is CSS's rule and
                // not the same as leaving it unset: `translate(10px)` moves
                // horizontally only.
                var values = count == 1 || count == 2 ? lengths(arguments, count, context) : null;
                return values == null ? null : new Function.Translate(values[0], count == 2 ? values[1] : Length.ZERO);
            }
            case "translatex", "translatey" -> {
                var values = count == 1 ? lengths(arguments, 1, context) : null;
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
                var values = count == 1 || count == 2 ? numbers(arguments, count, context) : null;
                return values == null ? null : new Function.Scale(values[0], count == 2 ? values[1] : values[0]);
            }
            case "scalex", "scaley" -> {
                var values = count == 1 ? numbers(arguments, 1, context) : null;
                if (values == null) {
                    return null;
                }
                return name.endsWith("x") ? new Function.Scale(values[0], 1) : new Function.Scale(1, values[0]);
            }

            case "rotate" -> {
                var values = count == 1 ? angles(arguments, 1, context) : null;
                return values == null ? null : new Function.Rotate(values[0]);
            }

            case "skew" -> {
                var values = count == 1 || count == 2 ? angles(arguments, count, context) : null;
                return values == null ? null : new Function.Skew(values[0], count == 2 ? values[1] : 0);
            }
            case "skewx", "skewy" -> {
                var values = count == 1 ? angles(arguments, 1, context) : null;
                if (values == null) {
                    return null;
                }
                return name.endsWith("x") ? new Function.Skew(values[0], 0) : new Function.Skew(0, values[0]);
            }

            case "matrix" -> {
                var values = count == 6 ? numbers(arguments, 6, context) : null;
                return values == null
                        ? null
                        : new Function.Matrix(
                                new Affine(values[0], values[1], values[2], values[3], values[4], values[5]));
            }

            default -> {
                return null;
            }
        }
    }

    private static Length @Nullable [] lengths(List<List<Token>> arguments, int count, CssLength.Context context) {
        var values = new Length[count];
        for (var i = 0; i < count; i++) {
            var length = length(arguments.get(i), context);
            if (length == null) {
                return null;
            }
            values[i] = length;
        }
        return values;
    }

    /// A length argument: any length [CssLength] reads, a math function
    /// included, and a percentage of the box plus pixels as one.
    ///
    /// The layout engine's length is spelled out: its simple name is this
    /// class's own.
    private static @Nullable Length length(List<Token> argument, CssLength.Context context) {
        return switch (CssLength.parse(argument, context)) {
            case dev.goldberry.layout.Length.Points(var points) -> Length.px(points);
            case dev.goldberry.layout.Length.Percent(var percent) -> Length.percent(percent);
            case dev.goldberry.layout.Length.Mixed(var points, var percent) -> Length.percentPlus(percent, points);
            case null, default -> null;
        };
    }

    private static double @Nullable [] numbers(List<List<Token>> arguments, int count, CssLength.Context context) {
        var values = new double[count];
        for (var i = 0; i < count; i++) {
            var argument = arguments.get(i);
            var math = Maths.fold(argument, context);
            if (math != null) {
                if (math.kind() != Quantity.Kind.NUMBER) {
                    return null;
                }
                values[i] = math.value();
                continue;
            }
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
    private static double @Nullable [] angles(List<List<Token>> arguments, int count, CssLength.Context context) {
        var values = new double[count];
        for (var i = 0; i < count; i++) {
            var argument = arguments.get(i);
            var math = Maths.fold(argument, context);
            if (math != null) {
                if (math.kind() != Quantity.Kind.ANGLE) {
                    return null;
                }
                values[i] = Math.toRadians(math.value());
                continue;
            }
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
    /// Public for the same reason [#parse(List, Origin, CssLength.Context)] is.
    // NullTernary reads `yield x == null ? null : ...` as a ternary that will be
    // unboxed. It will not: this method returns `Origin`, a record, and null is
    // its documented "this is not an origin" answer -- the same shape every
    // parser in this package uses. Error Prone's heuristic does not model a
    // switch expression's target type.
    @SuppressWarnings("NullTernary")
    public static @Nullable Origin parseOrigin(List<Token> value, CssLength.Context context) {
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
                    yield new Origin(
                            Length.HALF,
                            Objects.requireNonNull(
                                    originLength(parts.getFirst(), context),
                                    "top and bottom are origin keywords, so they always resolve"));
                }
                var x = originLength(parts.getFirst(), context);
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
                var x = originLength(first, context);
                var y = originLength(second, context);
                yield x == null || y == null ? null : new Origin(x, y);
            }
            default -> null;
        };
    }

    /// Which axis a `transform-origin` keyword names, or null if it is not one.
    private enum Axis {
        HORIZONTAL,
        VERTICAL
    }

    private static @Nullable Axis keyword(List<Token> part) {
        if (part.size() != 1 || !part.getFirst().is(TokenType.IDENT)) {
            return null;
        }
        return switch (part.getFirst().text().toLowerCase(Locale.ROOT)) {
            case "left", "right" -> Axis.HORIZONTAL;
            case "top", "bottom" -> Axis.VERTICAL;
            default -> null;
        };
    }

    private static @Nullable Length originLength(List<Token> part, CssLength.Context context) {
        if (part.size() == 1 && part.getFirst().is(TokenType.IDENT)) {
            return switch (part.getFirst().text().toLowerCase(Locale.ROOT)) {
                case "left", "top" -> Length.ZERO;
                case "center" -> Length.HALF;
                case "right", "bottom" -> Length.percent(100);
                default -> null;
            };
        }
        return length(part, context);
    }
}
