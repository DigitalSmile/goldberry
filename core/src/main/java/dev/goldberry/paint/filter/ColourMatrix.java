package dev.goldberry.paint.filter;

import dev.goldberry.css.compositing.FilterFunction;

/// One colour filter as the affine map CSS Filter Effects gives it: each
/// output channel a sum of the three inputs and a constant, on 0 to 1 values.
final class ColourMatrix {

    /// The three rows of three coefficients, red's first.
    private final double[][] m;

    /// The three constants.
    private final double[] c;

    private ColourMatrix(double[][] m, double[] c) {
        this.m = m;
        this.c = c;
    }

    /// The map `function` is.
    static ColourMatrix of(FilterFunction.ColourFunction function) {
        return switch (function) {
            case FilterFunction.Brightness(var amount) -> scale(amount, 0);
            case FilterFunction.Contrast(var amount) -> scale(amount, 0.5 - amount / 2);
            case FilterFunction.Invert(var amount) -> scale(1 - 2 * amount, amount);
            case FilterFunction.Opacity _ -> scale(1, 0);
            case FilterFunction.Grayscale(var amount) -> {
                var k = 1 - amount;
                yield linear(new double[][] {
                    {0.2126 + 0.7874 * k, 0.7152 - 0.7152 * k, 0.0722 - 0.0722 * k},
                    {0.2126 - 0.2126 * k, 0.7152 + 0.2848 * k, 0.0722 - 0.0722 * k},
                    {0.2126 - 0.2126 * k, 0.7152 - 0.7152 * k, 0.0722 + 0.9278 * k}
                });
            }
            case FilterFunction.Sepia(var amount) -> {
                var k = 1 - amount;
                yield linear(new double[][] {
                    {0.393 + 0.607 * k, 0.769 - 0.769 * k, 0.189 - 0.189 * k},
                    {0.349 - 0.349 * k, 0.686 + 0.314 * k, 0.168 - 0.168 * k},
                    {0.272 - 0.272 * k, 0.534 - 0.534 * k, 0.131 + 0.869 * k}
                });
            }
            case FilterFunction.Saturate(var s) ->
                linear(new double[][] {
                    {0.213 + 0.787 * s, 0.715 - 0.715 * s, 0.072 - 0.072 * s},
                    {0.213 - 0.213 * s, 0.715 + 0.285 * s, 0.072 - 0.072 * s},
                    {0.213 - 0.213 * s, 0.715 - 0.715 * s, 0.072 + 0.928 * s}
                });
            case FilterFunction.HueRotate(var degrees) -> {
                var cos = Math.cos(Math.toRadians(degrees));
                var sin = Math.sin(Math.toRadians(degrees));
                yield linear(new double[][] {
                    {
                        0.213 + cos * 0.787 - sin * 0.213,
                        0.715 - cos * 0.715 - sin * 0.715,
                        0.072 - cos * 0.072 + sin * 0.928
                    },
                    {
                        0.213 - cos * 0.213 + sin * 0.143,
                        0.715 + cos * 0.285 + sin * 0.140,
                        0.072 - cos * 0.072 - sin * 0.283
                    },
                    {
                        0.213 - cos * 0.213 - sin * 0.787,
                        0.715 - cos * 0.715 + sin * 0.715,
                        0.072 + cos * 0.928 + sin * 0.072
                    }
                });
            }
        };
    }

    /// Every channel times `factor`, plus `offset`.
    private static ColourMatrix scale(double factor, double offset) {
        return new ColourMatrix(
                new double[][] {{factor, 0, 0}, {0, factor, 0}, {0, 0, factor}}, new double[] {offset, offset, offset});
    }

    private static ColourMatrix linear(double[][] m) {
        return new ColourMatrix(m, new double[3]);
    }

    /// `(r, g, b)` mapped, unclamped.
    double[] apply(double r, double g, double b) {
        return new double[] {
            m[0][0] * r + m[0][1] * g + m[0][2] * b + c[0],
            m[1][0] * r + m[1][1] * g + m[1][2] * b + c[1],
            m[2][0] * r + m[2][1] * g + m[2][2] * b + c[2]
        };
    }
}
