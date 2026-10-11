package dev.goldberry.layout;

import java.util.Locale;

/// A length in a layout — a number and the unit it is in.
///
/// ## Why the toolkit owns this
///
/// It is the value `css` resolves a declaration to and the value a [dev.goldberry.paint.Box] is
/// built
/// from, which makes it the single most-written type in the widget catalogue —
/// nineteen files in `:widgets` name it. Were it the layout engine's own type,
/// every application writing a widget would read a `:natives` class to say
/// `8px`.
///
/// Nothing about a length is native. It is a number, a unit and two keywords.
///
/// ## Five kinds, and why the interface is sealed
///
/// CSS writes one property with four possible values; the engine underneath
/// exports up to three separate functions per property and spells the fourth by
/// passing a NaN to the first of them. That shape belongs at the boundary, not
/// in the vocabulary — so the value is modelled once here and translated in one
/// place.
///
/// The fifth kind, [Mixed], is a length and a percentage at once, which is what
/// a `calc()` mixing the two folds to. The engine has no spelling for it at all,
/// so it is for painted properties only.
///
/// Sealed, so the translation is an exhaustive `switch` with no default arm: a
/// kind added here fails to compile everywhere it is not handled, rather than
/// falling through to a silent no-op.
///
/// Read more: [How layout works](https://goldberry.dev/docs/layout/index.html#logical-pixels).
public sealed interface Length {

    /// Size to the content, or — for a margin — absorb the free space. The
    /// second meaning is what centres a node between `auto` margins.
    Length AUTO = Keyword.AUTO;

    /// No value. Sets the property back to the engine's own default, which is
    /// what removing a declaration from a stylesheet has to mean.
    Length UNDEFINED = Keyword.UNDEFINED;

    /// A length in points — logical pixels, before the window's scale is applied.
    ///
    /// @throws IllegalArgumentException if the value is NaN or infinite
    static Length points(float value) {
        return new Points(value);
    }

    /// A percentage of the parent's corresponding dimension. `50` means 50%,
    /// not 0.5.
    ///
    /// @throws IllegalArgumentException if the value is NaN or infinite
    static Length percent(float value) {
        return new Percent(value);
    }

    /// A length plus a percentage — what `calc(100% - 8px)` folds to.
    ///
    /// Only a painted property can take one: a radius, a background position,
    /// a translation are resolved against a box that has been laid out. The
    /// layout engine takes a length or a percentage and never both, so a
    /// stylesheet drops one written for `width` or `margin`, and the layout
    /// bridge refuses one handed to it directly.
    ///
    /// Folds to [#points] when the percentage is zero and to [#percent] when the
    /// points are, so that a [Mixed] always has both.
    ///
    /// @throws IllegalArgumentException if either value is NaN or infinite
    static Length mixed(float points, float percent) {
        if (percent == 0) {
            return points(points);
        }
        return points == 0 ? percent(percent) : new Mixed(points, percent);
    }

    /// This length as a number of logical pixels, against the dimension a
    /// percentage would be a percentage *of*.
    ///
    /// Here rather than at each caller because two of them want it and they must
    /// agree: the painter resolves a box's padding to decide where a `canvas`
    /// draws, and the hit-test snapshot resolves the same padding to decide where
    /// its input lands. Two resolutions kept alike by hand is how a pointer ends
    /// up a few pixels from the ink.
    ///
    /// Both keywords resolve to zero. Neither is a length: `auto` is a question
    /// for the layout engine and `undefined` is the absence of a declaration, and
    /// a caller asking "how many pixels" about either has already decided it
    /// wants a number.
    /// Static rather than a `default` method, and not by preference: an interface
    /// that holds constants of its own subtypes **and** declares a default method
    /// is initialized whenever one of those subtypes is, which Error Prone flags
    /// as a possible class-initialization deadlock. `AUTO` and `UNDEFINED` are
    /// worth more than the dot.
    static float resolve(Length length, float base) {
        return switch (length) {
            case Points points -> points.value();
            case Percent percent -> percent.value() / 100 * base;
            case Mixed mixed -> mixed.points() + mixed.percent() / 100 * base;
            case Keyword _ -> 0;
        };
    }

    /// A length in points.
    record Points(float value) implements Length {

        public Points {
            requireFinite(value, "points");
        }

        @Override
        public String toString() {
            return value + "px";
        }
    }

    /// A percentage of the parent's corresponding dimension.
    record Percent(float value) implements Length {

        public Percent {
            requireFinite(value, "percent");
        }

        @Override
        public String toString() {
            return value + "%";
        }
    }

    /// A length plus a percentage. See [Length#mixed].
    ///
    /// @param points  the logical pixels
    /// @param percent the percentage of the basis, `50` meaning 50%
    record Mixed(float points, float percent) implements Length {

        public Mixed {
            requireFinite(points, "points");
            requireFinite(percent, "percent");
        }

        @Override
        public String toString() {
            return "calc(" + percent + "% + " + points + "px)";
        }
    }

    /// The two lengths that carry no number.
    ///
    /// An enum rather than two singleton records so that they compare by
    /// identity and print as themselves.
    enum Keyword implements Length {

        /// See [Length#AUTO].
        AUTO,

        /// See [Length#UNDEFINED].
        UNDEFINED;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /// Negative lengths are allowed — a negative margin is meaningful, and the
    /// layout engine clamps the ones that are not. NaN is not: it is how the
    /// engine spells [#UNDEFINED], so admitting it here would give one state two
    /// spellings and make `equals` disagree with itself. Infinity is rejected for
    /// the same reason it is not a length.
    private static void requireFinite(float value, String unit) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(
                    Float.isNaN(value)
                            ? "a " + unit + " length may not be NaN — use Length.UNDEFINED"
                            : "a " + unit + " length may not be infinite, and " + value + " is");
        }
    }
}
